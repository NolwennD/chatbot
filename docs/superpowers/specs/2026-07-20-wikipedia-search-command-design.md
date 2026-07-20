# `?wp`/`?wiki {mot}` — recherche Wikipedia dans le chat

## Contexte et objectif

Ajouter une commande `?wp {mot}` (ou `?wiki {mot}`, les deux préfixes sont
équivalents) qui lance une recherche Wikipedia (dans la
langue configurée du bot en priorité, puis en anglais en repli) et envoie
dans le chat Twitch un court résumé de la page trouvée, suivi du lien vers
l'article.

Ce n'est pas une commande `!` classique (celles définies dans
`commands.txt`) : elle a sa propre logique métier (appel HTTP, choix de
langue, troncature), portée par un nouveau bounded context `search`, sans
dépendance vers le bounded context `command` existant.

## Refactor préalable : sortir le plumbing Twitch et la locale de `command`

Aujourd'hui, `ChatMessage`, `ChatMessagePublisher`, `TwitchChatFacade` et la
résolution de locale (fr/en, fallback anglais) vivent dans le bounded
context `command`. Le contexte `search` a besoin des deux mêmes choses
(recevoir/envoyer des messages Twitch, connaître la langue du bot), et les
règles ArchUnit du projet interdisent à un contexte de dépendre du domaine
d'un autre. Ces éléments sont donc extraits en shared kernels.

### `shared.twitch` (nouveau shared kernel)

- `domain.ChatMessage` — déplacé depuis `command.domain` (inchangé).
- `domain.ChatMessagePublisher` — déplacé depuis `command.domain` (inchangé).
- `infrastructure.TwitchChatFacade`, `infrastructure.Twitch4jChatFacade`,
  `infrastructure.TwitchClientConfiguration`, `infrastructure.TwitchProperties`
  — déplacés depuis `command.infrastructure`, package bare (ni `primary` ni
  `secondary`) comme aujourd'hui : ce sont des adaptateurs utilisés
  directement par les listeners primaires de chaque contexte, pas des
  implémentations de port appelées uniquement par une couche secondaire.
- `infrastructure.secondary.TwitchChatMessagePublisher` — déplacé depuis
  `command.infrastructure.secondary` (implémente `ChatMessagePublisher`).

Chaque bounded context (`command`, `search`) garde son propre listener
primaire (`TwitchChatMessageListener` / `SearchChatMessageListener`) qui
s'abonne indépendamment à `TwitchChatFacade` et route les messages reçus
vers son propre service applicatif. Les deux contextes reçoivent donc tous
les messages du chat et filtrent chacun ce qui les concerne (`!` pour l'un,
`?wp` pour l'autre).

### `shared.locale` (nouveau shared kernel)

- `infrastructure.LocaleConfiguration` (package-privée) : une seule classe,
  qui expose un bean `Locale` résolu une fois au démarrage à partir de
  `chatbot.locale` (avec repli sur `Locale.getDefault()` puis sur l'anglais
  si la langue configurée n'est ni `fr` ni `en` — logique reprise telle
  quelle de l'actuel `SupportedLocale`).
- Pas de type domaine exposé : la logique de résolution reste un détail
  privé de cette classe de configuration, rien d'autre n'en a besoin.
- `command` et `search` injectent simplement un `Locale` dans leurs
  constructeurs plutôt que de relire `chatbot.locale` et de résoudre la
  locale chacun de leur côté.

### Ce qui reste dans `command`

`CommandName`, `Commands`, `CommandOutcome`, `CommandRepository`,
`CommandResponse`, `CommandResponseTranslator`, `FileCommandRepository`
restent inchangés. Seuls les imports de `ChatMessage`/`ChatMessagePublisher`
pointent désormais vers `shared.twitch.domain`, et
`SpringCommandResponseTranslator` reçoit un `Locale` injecté au lieu de
résoudre `chatbot.locale` lui-même.

## Nouveau bounded context `search`

### Domaine (`search.domain`)

- `SearchQuery(String value)` — record validé (non vide). Méthode statique
  `Optional<SearchQuery> parse(String content)` qui reconnaît l'un des
  préfixes déclencheurs `?wp ` ou `?wiki ` (même style que
  `CommandName.parse`) et garde le reste comme terme recherché.
- `PageSummary(String extract, String url, boolean disambiguation)` —
  record. `disambiguation` reflète directement le champ `type` renvoyé par
  l'API Wikipedia (`"disambiguation"` ou non) — voir plus bas pourquoi on
  se base sur ce champ explicite plutôt que de deviner à partir du contenu
  de `extract` (une page d'homonymie peut très bien avoir un extrait non
  vide mais générique, ex: "Idris may refer to:"). Seul un `Assert.notNull`
  est appliqué sur `extract` (pas `notBlank`, un extrait vide reste
  possible). Le constructeur compact tronque `extract` à 200 caractères
  s'il n'est pas vide : si le texte dépasse la limite, on coupe à 200, on
  recule jusqu'au dernier espace (pour ne pas trancher un mot), et on
  ajoute `…`. En dessous de la limite (ou vide), le texte passe tel quel.
- `SearchOutcome` (sealed) :
  - `Found(PageSummary)` — un résumé exploitable a été trouvé.
  - `Ambiguous(SearchQuery query, PageSummary summary)` — la page trouvée
    est une page d'homonymie (`summary.disambiguation() == true`).
  - `NotFound(SearchQuery query)` — rien trouvé (404 ou erreur), y compris
    après repli anglais.
  - Méthode statique `SearchOutcome.from(SearchQuery query, Optional<PageSummary> summary)`
    qui fait cette classification : `Optional.empty()` → `NotFound` ;
    `summary.disambiguation()` → `Ambiguous` ; sinon → `Found`.
- `PageLookup` (port) : `Optional<PageSummary> findSummary(SearchQuery query, Locale locale)`.
  `Optional.empty()` signifie une absence réelle (404 ou erreur) ; une
  page d'homonymie (200, `type: "disambiguation"`) reste un
  `Optional.of(...)` avec `disambiguation = true` — c'est
  `SearchOutcome.from` qui fait la distinction, pas le port.
- `SearchResponse(String value)` — record validé non-null/trim, miroir de
  `CommandResponse`.
- `SearchResponseTranslator` (port) : `List<SearchResponse> translate(SearchOutcome outcome)`.
  - `Found` → deux réponses, dans l'ordre : le résumé, puis le lien.
  - `Ambiguous` → une seule réponse traduite combinant terme et lien :
    `Pas de résumé pour "{0}", voici la page d'homonymie : {1}`.
  - `NotFound` → un seul message d'erreur traduit.

### Application (`search.application.HandleSearchMessageService`)

```
SearchQuery.parse(message.content())
  .map(this::resolveOutcome)
  .map(translator::translate)
  .ifPresent(responses -> responses.forEach(r -> chatMessagePublisher.send(r.value())));
```

`resolveOutcome` :

1. Essaie `pageLookup.findSummary(query, botLocale)`, puis classe le
   résultat avec `SearchOutcome.from(query, résultat)`.
2. Si cette classification est `NotFound` (absence réelle) et que
   `botLocale` n'est pas déjà l'anglais, retente en anglais et reclasse le
   nouveau résultat — c'est le résultat final.
3. Si la classification initiale est `Found` ou `Ambiguous`, aucun repli
   n'est tenté : le repli anglais ne s'applique qu'à une absence réelle
   (`NotFound`), jamais à une page d'homonymie. Un test a montré qu'une
   page d'homonymie en français (ex: "Java") peut correspondre en anglais
   à un article "standard" mais sur un tout autre sujet (l'île de Java,
   pas le langage) — remplacer l'homonymie locale par ce résultat serait
   trompeur plutôt qu'utile.

Cette logique de repli reste dans l'application (orchestration : quel
appel faire et dans quel ordre), la classification elle-même
(`Found`/`Ambiguous`/`NotFound`) reste dans le domaine via
`SearchOutcome.from`.

### Infrastructure primaire (`search.infrastructure.primary`)

`SearchChatMessageListener` — mirror exact de
`command.infrastructure.primary.TwitchChatMessageListener` : s'abonne à
`shared.twitch.infrastructure.TwitchChatFacade` et route vers
`HandleSearchMessageService`.

### Infrastructure secondaire (`search.infrastructure.secondary`)

- `RestWikipediaPageLookup` implémente `PageLookup` :
  - utilise `RestClient` de Spring (déjà disponible via
    `spring-boot-starter-webmvc`, aucune nouvelle dépendance) ;
  - appelle `GET https://{locale.getLanguage()}.wikipedia.org/api/rest_v1/page/summary/{query encodé}` ;
  - désérialise un DTO interne minimal
    (`@JsonIgnoreProperties(ignoreUnknown = true)`) exposant `type`,
    `extract` et `content_urls.desktop.page` ; `disambiguation` du
    `PageSummary` construit vaut `"disambiguation".equals(type)` ;
  - un 404 (`HttpClientErrorException.NotFound`, confirmé par test manuel
    contre l'API réelle) → `Optional.empty()` ;
  - toute autre erreur réseau/HTTP (5xx, timeout) → également
    `Optional.empty()`, traitée comme "page non trouvée" côté chat.
    Simplification volontaire : pas de distinction "page absente" vs "API
    indisponible", pas de retry. Marqué en commentaire `ponytail:` dans le
    code, avec piste d'amélioration si ça devient gênant en usage réel.
- `SpringSearchResponseTranslator` implémente `SearchResponseTranslator` :
  - `NotFound` → message traduit via `MessageSource` + `Locale` injecté,
    nouvelle clé `search.notfound` :
    - fr : `Aucun article trouvé pour "{0}".`
    - en : `No article found for "{0}".`
  - `Ambiguous` → message traduit, nouvelle clé `search.ambiguous` :
    - fr : `Pas de résumé pour "{0}", voici la page d'homonymie : {1}`
    - en : `No summary for "{0}", here is the disambiguation page: {1}`
  - `Found` → construit directement les deux `SearchResponse` (résumé,
    lien), pas de traduction nécessaire.

## Limites connues (hors scope de cette itération)

- **Pas de recherche floue** : `?wp {mot}` fait un lookup direct du titre
  (pas d'appel à l'API de recherche full-text de Wikipedia). Un terme
  imprécis (qui ne correspond à aucun titre ni page d'homonymie) ne sera
  pas résolu intelligemment. Piste pour une prochaine itération : en cas de
  404, retenter via l'API de recherche (`action=query&list=search`) pour
  proposer le meilleur titre correspondant.
- **Page d'homonymie : pas de liste des significations proposée.** On
  renvoie juste le lien vers la page d'homonymie (`Ambiguous`), sans
  extraire ni proposer les différentes significations sous forme de
  commandes pré-construites. Une tentative de récupération des liens de la
  page (`action=query&prop=links`) montre que l'API renvoie tous les liens
  de la page, pas seulement les significations listées — il faudrait
  parser la structure de la liste d'homonymie pour filtrer correctement.
  Piste pour une prochaine itération.
- **Troncature à 200 caractères fixe**, non configurable. Si le besoin
  d'ajuster cette valeur sans recompiler apparaît, l'extraire en propriété
  (`chatbot.wikipedia.summary-limit` ou similaire) sera trivial.
- **Erreurs API (hors homonymie) traitées uniformément comme "non
  trouvé"** (voir ci-dessus).

## Tests

- Domaine : tests unitaires classiques sur `SearchQuery.parse`,
  `PageSummary` (troncature, cas limite et cas sans coupure),
  `SearchOutcome`.
- Application : `HandleSearchMessageService` testé avec des fakes pour
  `PageLookup` / `ChatMessagePublisher` / `SearchResponseTranslator`, dans
  le même style que les tests existants de `HandleChatMessageService`.
- `RestWikipediaPageLookup` : testé avec `MockRestServiceServer` (déjà
  disponible via `spring-boot-starter-test`) pour simuler les réponses
  200/404 de Wikipedia sans appel réseau réel.
- Coverage JaCoCo stricte à respecter comme partout ailleurs dans le
  projet (voir CLAUDE.md : zéro ligne/branche manquée par classe).
