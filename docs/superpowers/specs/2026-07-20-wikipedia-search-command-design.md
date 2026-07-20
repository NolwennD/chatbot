# `?wp`/`?wiki {term}` — Wikipedia search in chat

## Context and goal

Add a `?wp {term}` command (or `?wiki {term}`, the two prefixes are
equivalent) that triggers a Wikipedia search (in the bot's configured
language first, then English as a fallback) and sends a short summary of
the page found to Twitch chat, followed by the link to the article.

This is not a classic `!` command (the kind defined in `commands.txt`): it
has its own business logic (HTTP call, language choice, truncation),
carried by a new `search` bounded context, with no dependency on the
existing `command` bounded context.

## Preliminary refactor: extract Twitch plumbing and locale out of `command`

Today, `ChatMessage`, `ChatMessagePublisher`, `TwitchChatFacade`, and locale
resolution (fr/en, English fallback) live in the `command` bounded context.
The `search` context needs the same two things (receive/send Twitch
messages, know the bot's language), and the project's ArchUnit rules
forbid a context from depending on another context's domain. These
elements are therefore extracted into shared kernels.

### `shared.twitch` (new shared kernel)

- `domain.ChatMessage` — moved from `command.domain` (unchanged).
- `domain.ChatMessagePublisher` — moved from `command.domain` (unchanged).
- `infrastructure.TwitchChatFacade`, `infrastructure.Twitch4jChatFacade`,
  `infrastructure.TwitchClientConfiguration`, `infrastructure.TwitchProperties`
  — moved from `command.infrastructure`, bare package (neither `primary`
  nor `secondary`) as they are today: these are adapters used directly by
  each context's primary listeners, not port implementations called only
  by a secondary layer.
- `infrastructure.secondary.TwitchChatMessagePublisher` — moved from
  `command.infrastructure.secondary` (implements `ChatMessagePublisher`).

Each bounded context (`command`, `search`) keeps its own primary listener
(`TwitchChatMessageListener` / `SearchChatMessageListener`), which
independently subscribes to `TwitchChatFacade` and routes received
messages to its own application service. Both contexts therefore receive
every chat message and each filters what's relevant to it (`!` for one,
`?wp` for the other).

### `shared.locale` (new shared kernel)

- `infrastructure.LocaleConfiguration` (package-private): a single class
  that exposes a `Locale` bean resolved once at startup from
  `chatbot.locale` (falling back to `Locale.getDefault()`, then to English
  if the configured language is neither `fr` nor `en` — logic carried over
  as-is from the current `SupportedLocale`).
- No domain type exposed: the resolution logic stays a private detail of
  this configuration class, nothing else needs it.
- `command` and `search` simply inject a `Locale` into their constructors
  instead of each independently re-reading `chatbot.locale` and resolving
  the locale themselves.

### What stays in `command`

`CommandName`, `Commands`, `CommandOutcome`, `CommandRepository`,
`CommandResponse`, `CommandResponseTranslator`, `FileCommandRepository`
remain unchanged. Only the `ChatMessage`/`ChatMessagePublisher` imports now
point to `shared.twitch.domain`, and `SpringCommandResponseTranslator`
receives an injected `Locale` instead of resolving `chatbot.locale` itself.

## New `search` bounded context

### Domain (`search.domain`)

- `SearchQuery(String value)` — validated record (non-blank). Static
  method `Optional<SearchQuery> parse(String content)` that recognizes
  either trigger prefix `?wp ` or `?wiki ` (same style as
  `CommandName.parse`) and keeps the rest as the search term.
- `PageSummary(String extract, String url, boolean disambiguation)` —
  record. `disambiguation` reflects directly the `type` field returned by
  the Wikipedia API (`"disambiguation"` or not) — see below for why we
  rely on this explicit field rather than guessing from the content of
  `extract` (a disambiguation page can very well have a non-empty but
  generic extract, e.g. "Idris may refer to:"). Only an `Assert.notNull`
  is applied to `extract` (not `notBlank`, an empty extract remains
  possible). The compact constructor truncates `extract` to 200 characters
  if it's not empty: if the text exceeds the limit, it's cut at 200,
  backed off to the last space (so as not to slice a word in half), and
  `…` is appended. Below the limit (or empty), the text passes through
  unchanged.
- `SearchOutcome` (sealed):
  - `Found(PageSummary)` — a usable summary was found.
  - `Ambiguous(SearchQuery query, PageSummary summary)` — the page found
    is a disambiguation page (`summary.disambiguation() == true`).
  - `NotFound(SearchQuery query)` — nothing found (404 or error), including
    after the English fallback.
  - Static method `SearchOutcome.from(SearchQuery query, Optional<PageSummary> summary)`
    performs this classification: `Optional.empty()` → `NotFound`;
    `summary.disambiguation()` → `Ambiguous`; otherwise → `Found`.
- `PageLookup` (port): `Optional<PageSummary> findSummary(SearchQuery query, Locale locale)`.
  `Optional.empty()` means a genuine absence (404 or error); a
  disambiguation page (200, `type: "disambiguation"`) remains an
  `Optional.of(...)` with `disambiguation = true` — it's `SearchOutcome.from`
  that makes the distinction, not the port.
- `SearchResponse(String value)` — record validated non-null/trimmed,
  mirroring `CommandResponse`.
- `SearchResponseTranslator` (port): `List<SearchResponse> translate(SearchOutcome outcome)`.
  - `Found` → two responses, in order: the summary, then the link.
  - `Ambiguous` → a single translated response combining the term and the
    link: `Pas de résumé pour "{0}", voici la page d'homonymie : {1}`.
  - `NotFound` → a single translated error message.

### Application (`search.application.HandleSearchMessageService`)

```
SearchQuery.parse(message.content())
  .map(this::resolveOutcome)
  .map(translator::translate)
  .ifPresent(responses -> responses.forEach(r -> chatMessagePublisher.send(r.value())));
```

`resolveOutcome`:

1. Tries `pageLookup.findSummary(query, botLocale)`, then classifies the
   result with `SearchOutcome.from(query, result)`.
2. If this classification is `NotFound` (genuine absence) and `botLocale`
   isn't already English, retries in English and reclassifies the new
   result — this is the final result.
3. If the initial classification is `Found` or `Ambiguous`, no fallback is
   attempted: the English fallback only applies to a genuine absence
   (`NotFound`), never to a disambiguation page. A test showed that a
   French disambiguation page (e.g. "Java") can correspond in English to a
   "standard" article but on a completely different topic (the island of
   Java, not the programming language) — replacing the local
   disambiguation with that result would be misleading rather than
   helpful.

This fallback logic stays in the application layer (orchestration: which
call to make and in what order); the classification itself
(`Found`/`Ambiguous`/`NotFound`) stays in the domain via
`SearchOutcome.from`.

### Primary infrastructure (`search.infrastructure.primary`)

`SearchChatMessageListener` — exact mirror of
`command.infrastructure.primary.TwitchChatMessageListener`: subscribes to
`shared.twitch.infrastructure.TwitchChatFacade` and routes to
`HandleSearchMessageService`.

### Secondary infrastructure (`search.infrastructure.secondary`)

- `RestWikipediaPageLookup` implements `PageLookup`:
  - uses Spring's `RestClient` (already available via
    `spring-boot-starter-webmvc`, no new dependency);
  - calls `GET https://{locale.getLanguage()}.wikipedia.org/api/rest_v1/page/summary/{encoded query}`;
  - deserializes a minimal internal DTO
    (`@JsonIgnoreProperties(ignoreUnknown = true)`) exposing `type`,
    `extract`, and `content_urls.desktop.page`; the constructed
    `PageSummary`'s `disambiguation` is `"disambiguation".equals(type)`;
  - a 404 (`HttpClientErrorException.NotFound`, confirmed by manual testing
    against the real API) → `Optional.empty()`;
  - any other network/HTTP error (5xx, timeout) → also `Optional.empty()`,
    treated as "page not found" on the chat side. Deliberate
    simplification: no distinction between "page absent" and "API
    unavailable", no retry. Marked with a `ponytail:` comment in the code,
    with a path forward if this becomes an issue in real usage.
- `SpringSearchResponseTranslator` implements `SearchResponseTranslator`:
  - `NotFound` → message translated via `MessageSource` + injected
    `Locale`, new key `search.notfound`:
    - fr: `Aucun article trouvé pour "{0}".`
    - en: `No article found for "{0}".`
  - `Ambiguous` → translated message, new key `search.ambiguous`:
    - fr: `Pas de résumé pour "{0}", voici la page d'homonymie : {1}`
    - en: `No summary for "{0}", here is the disambiguation page: {1}`
  - `Found` → builds the two `SearchResponse`s directly (summary, link),
    no translation needed.

## Known limitations (out of scope for this iteration)

- **No fuzzy search**: `?wp {term}` does a direct title lookup (no call to
  Wikipedia's full-text search API). An imprecise term (matching no title
  and no disambiguation page) won't be resolved intelligently. Path for a
  future iteration: on a 404, retry via the search API
  (`action=query&list=search`) to suggest the best-matching title.
- **Disambiguation page: no list of meanings offered.** We just return the
  link to the disambiguation page (`Ambiguous`), without extracting or
  offering the different meanings as pre-built commands. An attempt to
  fetch the page's links (`action=query&prop=links`) shows the API returns
  every link on the page, not just the listed meanings — the
  disambiguation list's structure would need to be parsed to filter
  correctly. Path for a future iteration.
- **Fixed 200-character truncation**, not configurable. If the need to
  adjust this value without recompiling arises, extracting it into a
  property (`chatbot.wikipedia.summary-limit` or similar) will be trivial.
- **API errors (other than disambiguation) uniformly treated as "not
  found"** (see above).

## Tests

- Domain: classic unit tests on `SearchQuery.parse`, `PageSummary`
  (truncation, boundary case and no-cut case), `SearchOutcome`.
- Application: `HandleSearchMessageService` tested with fakes for
  `PageLookup` / `ChatMessagePublisher` / `SearchResponseTranslator`, in
  the same style as the existing `HandleChatMessageService` tests.
- `RestWikipediaPageLookup`: tested with `MockRestServiceServer` (already
  available via `spring-boot-starter-test`) to simulate Wikipedia's
  200/404 responses without a real network call.
- Strict JaCoCo coverage to respect as everywhere else in the project (see
  CLAUDE.md: zero missed line/branch per class).
