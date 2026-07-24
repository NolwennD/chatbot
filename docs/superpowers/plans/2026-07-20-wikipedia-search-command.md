# `?wp`/`?wiki` Wikipedia Search Command — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `?wp {mot}` / `?wiki {mot}` Twitch chat command that searches Wikipedia (bot's locale first, English fallback) and replies with a short summary followed by the article link.

**Architecture:** Extract the Twitch chat plumbing (`ChatMessage`, `ChatMessagePublisher`, `TwitchChatFacade`) and the bot's locale resolution out of the `command` bounded context into two new shared kernels (`shared.twitch`, `shared.locale`), then add a brand-new `search` bounded context (hexagonal: domain / application / infrastructure.primary / infrastructure.secondary) that depends only on those shared kernels — never on `command`.

**Tech Stack:** Spring Boot 4 (Java 25), Spring's `RestClient` (via `spring-boot-starter-webmvc`, already a dependency — no new dependency needed), Jackson (already transitively present), JUnit 5 + AssertJ + Mockito + `MockRestServiceServer` (all already test dependencies).

## Global Constraints

- Spec: [docs/superpowers/specs/2026-07-20-wikipedia-search-command-design.md](../specs/2026-07-20-wikipedia-search-command-design.md) — read it before starting, every task below implements a piece of it.
- Hexagonal architecture is enforced by ArchUnit (`HexagonalArchTest`): `domain` depends on nothing but other domains, shared kernels, `java..`, Apache Commons, SLF4J; `application` has no business rules and never depends on `infrastructure`; `infrastructure.primary` never depends on `infrastructure.secondary`; `infrastructure.secondary` never depends on `application` or its own context's `primary`. A bounded context may never depend on another bounded context's `domain` — only on shared kernels.
- Every top-level package needs a `package-info.java`. Business contexts get `@fr.craft.chatbot.BusinessContext` (see `command/package-info.java`), shared kernels get `@fr.craft.chatbot.SharedKernel` (see `shared/error/package-info.java`). Nested packages (`domain`, `infrastructure`, `infrastructure.primary`, `infrastructure.secondary`) get `@org.jspecify.annotations.NullMarked` only (see `command/domain/package-info.java`).
- `@Repository`/`@Service`/`@Component` classes that aren't meant to be used outside their package are package-private (see `FileCommandRepository`, `HandleChatMessageService` is the one exception because `TwitchChatMessageListener` in `infrastructure.primary` needs it — application services are public, adapters are package-private).
- Every test class needs `@UnitTest`, `@ComponentTest`, or `@IntegrationTest` (checked by `AnnotationArchTest`); interfaces are exempt.
- JaCoCo `check` (at `mvn verify`) requires **zero missed lines and zero missed branches per class** — write tests that exercise every branch you add (see the coverage notes inside each task).
- Domain records validate through `fr.craft.chatbot.shared.error.domain.Assert` (`Assert.field("name", value).notBlank()` / `.notNull()`), never manual `if`/`throw`.
- Follow TDD: write the failing test first, run it, write the minimal code to pass, run again, commit. For pure refactors (moving existing, already-tested code) the "red" step is replaced by "run the full suite, confirm still green" — see Phase A.
- Format with `pnpm prettier:format` before each commit if you touched non-Java files (properties, yml); Java formatting is handled by the same command too — run it once per task before committing.

---

## File Structure

**New shared kernel `shared.locale`** (locale resolution used by both `command` and `search`):

- `shared/locale/package-info.java`
- `shared/locale/infrastructure/package-info.java`
- `shared/locale/infrastructure/LocaleConfiguration.java` — one `@Bean Locale chatbotLocale(...)`, resolution logic kept as a package-private static method for testability.

**New shared kernel `shared.twitch`** (moved out of `command`, unchanged behavior):

- `shared/twitch/package-info.java`
- `shared/twitch/domain/package-info.java`, `ChatMessage.java`, `ChatMessagePublisher.java`
- `shared/twitch/infrastructure/package-info.java`, `TwitchChatFacade.java`, `Twitch4jChatFacade.java`, `TwitchClientConfiguration.java`, `TwitchProperties.java`
- `shared/twitch/infrastructure/secondary/package-info.java`, `TwitchChatMessagePublisher.java`

**New bounded context `search`:**

- `search/package-info.java` (`@BusinessContext`)
- `search/domain/package-info.java`, `SearchQuery.java`, `PageSummary.java`, `SearchOutcome.java`, `PageLookup.java`, `SearchResponse.java`, `SearchResponseTranslator.java`
- `search/application/package-info.java`, `HandleSearchMessageService.java`
- `search/infrastructure/primary/package-info.java`, `SearchChatMessageListener.java`
- `search/infrastructure/secondary/package-info.java`, `RestWikipediaPageLookup.java`, `SpringSearchResponseTranslator.java`

**Modified in `command`:**

- `command/infrastructure/secondary/SpringCommandResponseTranslator.java` — inject `Locale` instead of resolving it itself.
- `command/application/HandleChatMessageService.java`, `command/infrastructure/primary/TwitchChatMessageListener.java` — import `ChatMessage`/`ChatMessagePublisher`/`TwitchChatFacade` from `shared.twitch` instead of `command`.

**Deleted from `command`:**

- `command/domain/ChatMessage.java`, `ChatMessagePublisher.java`
- `command/infrastructure/TwitchChatFacade.java`, `Twitch4jChatFacade.java`, `TwitchClientConfiguration.java`, `TwitchProperties.java`, `package-info.java` (becomes empty)
- `command/infrastructure/secondary/TwitchChatMessagePublisher.java`
- `command/infrastructure/secondary/SupportedLocale.java`
- All matching test files (moved, not deleted — see tasks).

**Modified resources:**

- `src/main/resources/messages.properties`, `messages_fr.properties`, `messages_en.properties` — add `search.notfound` and `search.ambiguous` keys.

---

### Task 1: `shared.locale` shared kernel

**Files:**

- Create: `src/main/java/fr/craft/chatbot/shared/locale/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/locale/infrastructure/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/locale/infrastructure/LocaleConfiguration.java`
- Test: `src/test/java/fr/craft/chatbot/shared/locale/infrastructure/LocaleConfigurationTest.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.shared.locale.infrastructure.LocaleConfiguration` with `@Bean Locale chatbotLocale(String configuredLanguage)` and package-private `static Locale resolve(@Nullable String configuredLanguage, Locale systemDefault)`. Later tasks (2, and `search`'s `HandleSearchMessageService`/`SpringSearchResponseTranslator`) receive a `Locale` bean by constructor injection — no code elsewhere calls `LocaleConfiguration` directly.

- [ ] **Step 1: Write the failing test for the pure resolution logic**

```java
package fr.craft.chatbot.shared.locale.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import java.util.Locale;
import org.junit.jupiter.api.Test;

@UnitTest
class LocaleConfigurationTest {

  @Test
  void shouldKeepExplicitFrenchRegardlessOfSystemLocale() {
    assertThat(LocaleConfiguration.resolve("fr", Locale.GERMANY)).isEqualTo(Locale.FRENCH);
  }

  @Test
  void shouldKeepExplicitEnglishRegardlessOfSystemLocale() {
    assertThat(LocaleConfiguration.resolve("en", Locale.FRANCE)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldFallBackToEnglishWhenTheExplicitLocaleIsNotSupported() {
    assertThat(LocaleConfiguration.resolve("de", Locale.FRANCE)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldFollowTheSystemLocaleWhenNoneIsConfigured() {
    assertThat(LocaleConfiguration.resolve("", Locale.FRANCE)).isEqualTo(Locale.FRENCH);
  }

  @Test
  void shouldTreatANullConfiguredValueAsNotConfigured() {
    assertThat(LocaleConfiguration.resolve(null, Locale.UK)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldFallBackToEnglishWhenNeitherTheConfiguredNorTheSystemLocaleIsSupported() {
    assertThat(LocaleConfiguration.resolve(null, Locale.GERMANY)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void shouldExposeTheResolvedLocaleAsASpringBean() {
    assertThat(new LocaleConfiguration().chatbotLocale("fr")).isEqualTo(Locale.FRENCH);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=LocaleConfigurationTest`
Expected: FAIL — compilation error, `LocaleConfiguration` doesn't exist yet.

- [ ] **Step 3: Create the package-info files**

`src/main/java/fr/craft/chatbot/shared/locale/package-info.java`:

```java
@fr.craft.chatbot.SharedKernel
package fr.craft.chatbot.shared.locale;
```

`src/main/java/fr/craft/chatbot/shared/locale/infrastructure/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.shared.locale.infrastructure;
```

- [ ] **Step 4: Implement `LocaleConfiguration`**

```java
package fr.craft.chatbot.shared.locale.infrastructure;

import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LocaleConfiguration {

  private static final Set<String> SUPPORTED_LANGUAGES = Set.of("fr", "en");
  private static final Locale FALLBACK = Locale.ENGLISH;

  @Bean
  Locale chatbotLocale(@Value("${chatbot.locale:}") String configuredLanguage) {
    return resolve(configuredLanguage, Locale.getDefault());
  }

  static Locale resolve(@Nullable String configuredLanguage, Locale systemDefault) {
    var language = configuredLanguage == null || configuredLanguage.isBlank() ? systemDefault.getLanguage() : configuredLanguage;

    return SUPPORTED_LANGUAGES.contains(language) ? Locale.forLanguageTag(language) : FALLBACK;
  }
}
```

Note: `LocaleConfiguration` is package-private, but the test lives in the same package (`fr.craft.chatbot.shared.locale.infrastructure`) so it can call `resolve` and `new LocaleConfiguration()` directly — same pattern as `SupportedLocaleTest` today.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=LocaleConfigurationTest`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/craft/chatbot/shared/locale src/test/java/fr/craft/chatbot/shared/locale
git commit -m "Add shared.locale kernel exposing a resolved Locale bean"
```

---

### Task 2: Wire `command` to the shared `Locale` bean, delete `SupportedLocale`

**Files:**

- Modify: `src/main/java/fr/craft/chatbot/command/infrastructure/secondary/SpringCommandResponseTranslator.java`
- Modify: `src/test/java/fr/craft/chatbot/command/infrastructure/secondary/SpringCommandResponseTranslatorTest.java`
- Delete: `src/main/java/fr/craft/chatbot/command/infrastructure/secondary/SupportedLocale.java`
- Delete: `src/test/java/fr/craft/chatbot/command/infrastructure/secondary/SupportedLocaleTest.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.shared.locale.infrastructure.LocaleConfiguration` (Task 1) — indirectly, via the `Locale` bean Spring now injects.
- Produces: `SpringCommandResponseTranslator(MessageSource, Locale)` constructor — no other class references this constructor directly (Spring wires it).

- [ ] **Step 1: Update the test to build the translator with a plain `Locale`**

Replace the whole file `src/test/java/fr/craft/chatbot/command/infrastructure/secondary/SpringCommandResponseTranslatorTest.java`:

```java
package fr.craft.chatbot.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandOutcome;
import fr.craft.chatbot.command.domain.CommandResponse;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

@UnitTest
class SpringCommandResponseTranslatorTest {

  private final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

  {
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
  }

  private final SpringCommandResponseTranslator translator = new SpringCommandResponseTranslator(messageSource, Locale.FRENCH);
  private final SpringCommandResponseTranslator englishTranslator = new SpringCommandResponseTranslator(messageSource, Locale.ENGLISH);

  @Test
  void shouldListKnownCommandsWhenTheCommandIsUnknown() {
    var knownCommands = List.of(new CommandName("!projet"), new CommandName("!discord"));

    assertThat(translator.translate(new CommandOutcome.UnknownCommand(knownCommands))).isEqualTo(
      new CommandResponse("Commande inconnue. Commandes disponibles : !projet, !discord")
    );
  }

  @Test
  void shouldSayThereAreNoCommandsWhenNoneExist() {
    assertThat(translator.translate(new CommandOutcome.NoCommandsAvailable())).isEqualTo(
      new CommandResponse("Aucune commande n'est disponible pour le moment.")
    );
  }

  @Test
  void shouldListKnownCommandsInEnglishWhenTheCommandIsUnknown() {
    var knownCommands = List.of(new CommandName("!projet"), new CommandName("!discord"));

    assertThat(englishTranslator.translate(new CommandOutcome.UnknownCommand(knownCommands))).isEqualTo(
      new CommandResponse("Unknown command. Available commands: !projet, !discord")
    );
  }

  @Test
  void shouldSayThereAreNoCommandsInEnglishWhenNoneExist() {
    assertThat(englishTranslator.translate(new CommandOutcome.NoCommandsAvailable())).isEqualTo(
      new CommandResponse("No commands are available at the moment.")
    );
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SpringCommandResponseTranslatorTest`
Expected: FAIL — compilation error, no `SpringCommandResponseTranslator(MessageSource, Locale)` constructor exists yet (current one takes `(MessageSource, String)`).

- [ ] **Step 3: Update `SpringCommandResponseTranslator` to take an injected `Locale`**

Replace the constructor and field in `src/main/java/fr/craft/chatbot/command/infrastructure/secondary/SpringCommandResponseTranslator.java`:

```java
package fr.craft.chatbot.command.infrastructure.secondary;

import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandOutcome;
import fr.craft.chatbot.command.domain.CommandResponse;
import fr.craft.chatbot.command.domain.CommandResponseTranslator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Repository;

@Repository
class SpringCommandResponseTranslator implements CommandResponseTranslator {

  private final MessageSource messageSource;
  private final Locale locale;

  SpringCommandResponseTranslator(MessageSource messageSource, Locale locale) {
    this.messageSource = messageSource;
    this.locale = locale;
  }

  @Override
  public CommandResponse translate(CommandOutcome outcome) {
    return switch (outcome) {
      case CommandOutcome.CommandFound(CommandResponse response) -> response;
      case CommandOutcome.NoCommandsAvailable _ -> new CommandResponse(messageSource.getMessage("command.none", null, locale));
      case CommandOutcome.CommandsListed(List<CommandName> names) -> new CommandResponse(
        names.stream().map(CommandName::value).collect(Collectors.joining(", "))
      );
      case CommandOutcome.UnknownCommand unknownCommand -> {
        String names = unknownCommand.values().stream().map(CommandName::value).collect(Collectors.joining(", "));

        yield new CommandResponse(messageSource.getMessage("command.unknown", new Object[] { names }, locale));
      }
    };
  }
}
```

- [ ] **Step 4: Delete the now-unused `SupportedLocale` and its test**

```bash
git rm src/main/java/fr/craft/chatbot/command/infrastructure/secondary/SupportedLocale.java
git rm src/test/java/fr/craft/chatbot/command/infrastructure/secondary/SupportedLocaleTest.java
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SpringCommandResponseTranslatorTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Run the full unit test suite to check nothing else broke**

Run: `./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Inject the resolved bot Locale into SpringCommandResponseTranslator"
```

---

### Task 3: `shared.twitch` domain — move `ChatMessage` and `ChatMessagePublisher`

**Files:**

- Create: `src/main/java/fr/craft/chatbot/shared/twitch/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/domain/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/domain/ChatMessage.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/domain/ChatMessagePublisher.java`
- Create: `src/test/java/fr/craft/chatbot/shared/twitch/domain/ChatMessageTest.java`
- Delete: `src/main/java/fr/craft/chatbot/command/domain/ChatMessage.java`
- Delete: `src/main/java/fr/craft/chatbot/command/domain/ChatMessagePublisher.java`
- Delete: `src/test/java/fr/craft/chatbot/command/domain/ChatMessageTest.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.shared.twitch.domain.ChatMessage` (record, `content()`), `fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher` (interface, `void send(String message)`). Every later task that used `command.domain.ChatMessage`/`ChatMessagePublisher` now imports these instead.

This is a pure move — no behavior change, so there's no new failing test to write; the existing test moves with the code and must stay green.

- [ ] **Step 1: Create the package-info files**

`src/main/java/fr/craft/chatbot/shared/twitch/package-info.java`:

```java
@fr.craft.chatbot.SharedKernel
package fr.craft.chatbot.shared.twitch;
```

`src/main/java/fr/craft/chatbot/shared/twitch/domain/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.shared.twitch.domain;
```

- [ ] **Step 2: Create `ChatMessage` and `ChatMessagePublisher` in the new package**

`src/main/java/fr/craft/chatbot/shared/twitch/domain/ChatMessage.java`:

```java
package fr.craft.chatbot.shared.twitch.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record ChatMessage(String content) {
  public ChatMessage {
    Assert.field("content", content).notBlank();
  }
}
```

`src/main/java/fr/craft/chatbot/shared/twitch/domain/ChatMessagePublisher.java`:

```java
package fr.craft.chatbot.shared.twitch.domain;

public interface ChatMessagePublisher {
  void send(String message);
}
```

- [ ] **Step 3: Move the test**

`src/test/java/fr/craft/chatbot/shared/twitch/domain/ChatMessageTest.java`:

```java
package fr.craft.chatbot.shared.twitch.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class ChatMessageTest {

  @Test
  void shouldRejectABlankContent() {
    assertThatThrownBy(() -> new ChatMessage(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }
}
```

```bash
git rm src/main/java/fr/craft/chatbot/command/domain/ChatMessage.java
git rm src/main/java/fr/craft/chatbot/command/domain/ChatMessagePublisher.java
git rm src/test/java/fr/craft/chatbot/command/domain/ChatMessageTest.java
```

- [ ] **Step 4: Fix every reference to the old location**

`src/main/java/fr/craft/chatbot/command/application/HandleChatMessageService.java` — change the import:

```java
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
```

(replacing `import fr.craft.chatbot.command.domain.ChatMessage;` and `import fr.craft.chatbot.command.domain.ChatMessagePublisher;` — the rest of the file is unchanged).

`src/test/java/fr/craft/chatbot/command/application/HandleChatMessageServiceTest.java` — same import swap:

```java
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
```

`src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java` — same import swap for `ChatMessage` is not needed here (it doesn't import `ChatMessage` directly today, only `TwitchChatFacade`), leave as-is for now (fixed in Task 4).

`src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListenerTest.java` — change:

```java
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
```

`src/main/java/fr/craft/chatbot/command/infrastructure/Twitch4jChatFacadeTest.java`, `TwitchTestClientConfiguration.java`, `TwitchChatCommandIT.java` still reference the old `command.domain.ChatMessage` / `command.infrastructure.TwitchChatFacade` — leave untouched for now, they move wholesale in Task 4.

- [ ] **Step 5: Run the full unit test suite**

Run: `./mvnw test`
Expected: Still fails to compile — `Twitch4jChatFacadeTest`, `TwitchTestClientConfiguration`, `TwitchChatCommandIT` still import `fr.craft.chatbot.command.domain.ChatMessage`, which no longer exists. That's expected: Task 4 fixes these when it moves the Twitch facade classes. **Do not try to fix them now** — commit this intermediate state only if your git workflow allows a temporarily red main; otherwise squash Tasks 3 and 4 into a single commit. For this plan, do Task 4 immediately before committing either.

- [ ] **Step 6: Continue directly into Task 4 before committing** (see below) — do not run `git commit` yet.

---

### Task 4: `shared.twitch` infrastructure — move the Twitch facade and config

**Files:**

- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchChatFacade.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacade.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchClientConfiguration.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchProperties.java`
- Create: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacadeTest.java`
- Create: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchClientConfigurationTest.java`
- Create: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchPropertiesTest.java`
- Create: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchTestClientConfiguration.java`
- Modify: `src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java`
- Modify: `src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListenerTest.java`
- Modify: `src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatCommandIT.java`
- Modify: `src/test/java/fr/craft/chatbot/IntegrationTest.java`
- Delete: `src/main/java/fr/craft/chatbot/command/infrastructure/TwitchChatFacade.java`, `Twitch4jChatFacade.java`, `TwitchClientConfiguration.java`, `TwitchProperties.java`, `package-info.java`
- Delete: `src/test/java/fr/craft/chatbot/command/infrastructure/Twitch4jChatFacadeTest.java`, `TwitchClientConfigurationTest.java`, `TwitchPropertiesTest.java`, `TwitchTestClientConfiguration.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade` (interface, `onChatMessage(Consumer<ChatMessage>)` / `sendMessage(String)`), used directly by both `command.infrastructure.primary.TwitchChatMessageListener` and (later, Task 14) `search.infrastructure.primary.SearchChatMessageListener`.

- [ ] **Step 1: Create the package-info file**

Note: `HexagonalArchTest.SharedKernels.topLevelSharedPackageShouldOnlyContainSharedKernels` excludes `shared..domain` and `shared..infrastructure.*` package-infos from needing `@SharedKernel` — but that `.*` exclusion only matches a package-info **nested inside** `infrastructure` (e.g. `infrastructure.primary`), not the bare `infrastructure` package-info itself (this was discovered the hard way in Task 2: `shared.locale.infrastructure.package-info.java`, which is bare, needed `@SharedKernel` added to pass). This package-info is also bare (`shared.twitch.infrastructure`, no further subpackage), so it needs both annotations from the start:

```java
@fr.craft.chatbot.SharedKernel
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.shared.twitch.infrastructure;
```

(at `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/package-info.java`)

- [ ] **Step 2: Create `TwitchChatFacade`**

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;

public interface TwitchChatFacade {
  void onChatMessage(Consumer<ChatMessage> listener);

  void sendMessage(String message);
}
```

- [ ] **Step 3: Create `Twitch4jChatFacade`**

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;

class Twitch4jChatFacade implements TwitchChatFacade {

  private final ITwitchChat twitchChat;
  private final String channel;

  Twitch4jChatFacade(ITwitchChat twitchChat, String channel) {
    this.twitchChat = twitchChat;
    this.channel = channel;
  }

  @Override
  public void onChatMessage(Consumer<ChatMessage> listener) {
    twitchChat.getEventManager().onEvent(ChannelMessageEvent.class, event -> listener.accept(new ChatMessage(event.getMessage())));
  }

  @Override
  public void sendMessage(String message) {
    twitchChat.sendMessage(channel, message);
  }
}
```

- [ ] **Step 4: Create `TwitchProperties`**

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

@SuppressWarnings("NullAway.Init")
public class TwitchProperties {

  private String channel;
  private String botUsername;
  private String oauthToken;

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getBotUsername() {
    return botUsername;
  }

  public void setBotUsername(String botUsername) {
    this.botUsername = botUsername;
  }

  public String getOauthToken() {
    return oauthToken;
  }

  public void setOauthToken(String oauthToken) {
    this.oauthToken = oauthToken;
  }
}
```

- [ ] **Step 5: Create `TwitchClientConfiguration`**

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.TwitchChatBuilder;
import fr.craft.chatbot.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
class TwitchClientConfiguration {

  @Bean
  @ConfigurationProperties(prefix = "chatbot.twitch", ignoreUnknownFields = false)
  TwitchProperties twitchProperties() {
    return new TwitchProperties();
  }

  @Bean
  @ExcludeFromGeneratedCodeCoverage(reason = "Builds a real network connection to Twitch, exercised manually against a live channel")
  TwitchChatFacade twitchChatFacade(TwitchProperties properties) {
    var credential = new OAuth2Credential("twitch", properties.getOauthToken(), null, null, properties.getBotUsername(), null, null);

    var chat = TwitchChatBuilder.builder().withChatAccount(credential).build();
    chat.joinChannel(properties.getChannel());

    return new Twitch4jChatFacade(chat, properties.getChannel());
  }
}
```

- [ ] **Step 6: Move the three unit tests**

`src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacadeTest.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class Twitch4jChatFacadeTest {

  @Mock
  private ITwitchChat twitchChat;

  @Mock
  private EventManager eventManager;

  @Test
  void shouldSendMessagesToTheConfiguredChannel() {
    new Twitch4jChatFacade(twitchChat, "mychannel").sendMessage("Un chatbot Twitch");

    verify(twitchChat).sendMessage("mychannel", "Un chatbot Twitch");
  }

  @Test
  void shouldMapIncomingChannelMessageEventsToChatMessages() {
    when(twitchChat.getEventManager()).thenReturn(eventManager);

    var received = new ArrayList<ChatMessage>();
    new Twitch4jChatFacade(twitchChat, "mychannel").onChatMessage(received::add);

    var event = mock(ChannelMessageEvent.class);
    when(event.getMessage()).thenReturn("!projet");

    captureRegisteredEventConsumer().accept(event);

    assertThat(received).containsExactly(new ChatMessage("!projet"));
  }

  @SuppressWarnings("unchecked")
  private Consumer<ChannelMessageEvent> captureRegisteredEventConsumer() {
    var captor = ArgumentCaptor.forClass(Consumer.class);

    verify(eventManager).onEvent(eq(ChannelMessageEvent.class), captor.capture());

    return captor.getValue();
  }
}
```

`src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchClientConfigurationTest.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TwitchClientConfigurationTest {

  @Test
  void shouldExposeAnEmptyTwitchPropertiesBeanReadyForBinding() {
    assertThat(new TwitchClientConfiguration().twitchProperties()).isNotNull();
  }
}
```

`src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchPropertiesTest.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TwitchPropertiesTest {

  @Test
  void shouldExposeItsConfiguredValues() {
    var properties = new TwitchProperties();

    properties.setChannel("mychannel");
    properties.setBotUsername("mybot");
    properties.setOauthToken("token");

    assertThat(properties.getChannel()).isEqualTo("mychannel");
    assertThat(properties.getBotUsername()).isEqualTo("mybot");
    assertThat(properties.getOauthToken()).isEqualTo("token");
  }
}
```

- [ ] **Step 7: Move `TwitchTestClientConfiguration`**

`src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchTestClientConfiguration.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TwitchTestClientConfiguration {

  @Bean
  TwitchChatFacade twitchChatFacade() {
    return new TwitchChatFacade() {
      @Override
      public void onChatMessage(Consumer<ChatMessage> listener) {
        // no real Twitch connection in tests: nothing to subscribe to
      }

      @Override
      public void sendMessage(String message) {
        // no real Twitch connection in tests: nothing to send
      }
    };
  }
}
```

- [ ] **Step 8: Delete the old files**

```bash
git rm src/main/java/fr/craft/chatbot/command/infrastructure/TwitchChatFacade.java
git rm src/main/java/fr/craft/chatbot/command/infrastructure/Twitch4jChatFacade.java
git rm src/main/java/fr/craft/chatbot/command/infrastructure/TwitchClientConfiguration.java
git rm src/main/java/fr/craft/chatbot/command/infrastructure/TwitchProperties.java
git rm src/main/java/fr/craft/chatbot/command/infrastructure/package-info.java
git rm src/test/java/fr/craft/chatbot/command/infrastructure/Twitch4jChatFacadeTest.java
git rm src/test/java/fr/craft/chatbot/command/infrastructure/TwitchClientConfigurationTest.java
git rm src/test/java/fr/craft/chatbot/command/infrastructure/TwitchPropertiesTest.java
git rm src/test/java/fr/craft/chatbot/command/infrastructure/TwitchTestClientConfiguration.java
```

- [ ] **Step 9: Fix every remaining reference**

`src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java` — update the import:

```java
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
```

(replacing `import fr.craft.chatbot.command.infrastructure.TwitchChatFacade;` — rest of the file unchanged).

`src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListenerTest.java` — update imports:

```java
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
```

`src/test/java/fr/craft/chatbot/IntegrationTest.java` — update the import and the `@SpringBootTest` classes list:

```java
package fr.craft.chatbot;

import fr.craft.chatbot.shared.twitch.infrastructure.TwitchTestClientConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DisplayNameGeneration(ReplaceCamelCase.class)
@SpringBootTest(classes = { ChatbotApp.class, TwitchTestClientConfiguration.class })
public @interface IntegrationTest {
  @AliasFor(annotation = SpringBootTest.class)
  String[] properties() default {};
}
```

`src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatCommandIT.java` — update the import:

```java
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
```

(replacing `import fr.craft.chatbot.command.domain.ChatMessage;` and `import fr.craft.chatbot.command.infrastructure.TwitchChatFacade;` — rest of the file, including `RecordingTwitchChatFacade` and `RecordingFacadeConfiguration`, unchanged).

- [ ] **Step 10: Run the full test suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS — every existing test still passes, just from new packages.

- [ ] **Step 11: Commit (this closes out Task 3 and Task 4 together)**

```bash
git add -A
git commit -m "Extract shared.twitch kernel: ChatMessage, ChatMessagePublisher, TwitchChatFacade"
```

---

### Task 5: `shared.twitch` infrastructure.secondary — move `TwitchChatMessagePublisher`

**Files:**

- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisher.java`
- Delete: `src/main/java/fr/craft/chatbot/command/infrastructure/secondary/TwitchChatMessagePublisher.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.shared.twitch.infrastructure.secondary.TwitchChatMessagePublisher` (implements `shared.twitch.domain.ChatMessagePublisher`) — wired by Spring automatically, nothing references the class by name.

There is no dedicated unit test for this class today (it's a one-line delegation, exercised through `TwitchChatCommandIT`); keep that as-is, just move the file.

- [ ] **Step 1: Create the package-info file**

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.shared.twitch.infrastructure.secondary;
```

- [ ] **Step 2: Create `TwitchChatMessagePublisher` in the new package**

```java
package fr.craft.chatbot.shared.twitch.infrastructure.secondary;

import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
import org.springframework.stereotype.Component;

@Component
class TwitchChatMessagePublisher implements ChatMessagePublisher {

  private final TwitchChatFacade twitchChatFacade;

  TwitchChatMessagePublisher(TwitchChatFacade twitchChatFacade) {
    this.twitchChatFacade = twitchChatFacade;
  }

  @Override
  public void send(String message) {
    twitchChatFacade.sendMessage(message);
  }
}
```

- [ ] **Step 3: Delete the old file**

```bash
git rm src/main/java/fr/craft/chatbot/command/infrastructure/secondary/TwitchChatMessagePublisher.java
```

- [ ] **Step 4: Run the full test suite, including the IT that exercises this class**

Run: `./mvnw verify`
Expected: BUILD SUCCESS (this also re-confirms Tasks 1–4 didn't break the JaCoCo coverage gate — run this once now to catch any coverage regression early).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Move TwitchChatMessagePublisher into shared.twitch"
```

---

### Task 6: `search` bounded context skeleton

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/search/domain/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/search/application/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/search/infrastructure/primary/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/search/infrastructure/secondary/package-info.java`

**Interfaces:**

- Produces: the four `search.*` packages that every following task's classes live in.

No test for package-info files (not a class with behavior) — this task just sets up the packages so `HexagonalArchTest` recognizes `search` as a business context from Task 7 onward.

- [ ] **Step 1: Create `search/package-info.java`**

```java
@fr.craft.chatbot.BusinessContext
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.search;
```

- [ ] **Step 2: Create the nested package-info files**

`src/main/java/fr/craft/chatbot/search/domain/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.search.domain;
```

`src/main/java/fr/craft/chatbot/search/application/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.search.application;
```

`src/main/java/fr/craft/chatbot/search/infrastructure/primary/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.search.infrastructure.primary;
```

`src/main/java/fr/craft/chatbot/search/infrastructure/secondary/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.search.infrastructure.secondary;
```

- [ ] **Step 3: Run the full test suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS (nothing to break yet, this just confirms the packages compile).

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Scaffold the search bounded context packages"
```

---

### Task 7: `SearchQuery` domain type

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/domain/SearchQuery.java`
- Test: `src/test/java/fr/craft/chatbot/search/domain/SearchQueryTest.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.search.domain.SearchQuery` record with `value()` accessor and `static Optional<SearchQuery> parse(String content)`. Used by Task 12 (`HandleSearchMessageService`), Task 10 (`SearchOutcome`), Task 15/16 (translators/adapters).

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class SearchQueryTest {

  @Test
  void shouldBuildASearchQuery() {
    var query = new SearchQuery("java");

    assertThat(query.value()).isEqualTo("java");
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new SearchQuery(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "?wp java", "?wp java ", " ?wp java", "?wp  java" })
  void shouldExtractTheSearchTermWhenContentUsesTheWpPrefix(String content) {
    assertThat(SearchQuery.parse(content)).contains(new SearchQuery("java"));
  }

  @ParameterizedTest
  @ValueSource(strings = { "?wiki java", "?wiki java ", " ?wiki java" })
  void shouldExtractTheSearchTermWhenContentUsesTheWikiPrefix(String content) {
    assertThat(SearchQuery.parse(content)).contains(new SearchQuery("java"));
  }

  @Test
  void shouldKeepTheFullMultiWordTermAfterThePrefix() {
    assertThat(SearchQuery.parse("?wp coq de java")).contains(new SearchQuery("coq de java"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = { "", "   ", "hello everyone", "?wp", "?wiki", "?wp ", "!projet" })
  void shouldReturnEmptyWhenTheContentIsNotASearchCommand(String content) {
    assertThat(SearchQuery.parse(content)).isEmpty();
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SearchQueryTest`
Expected: FAIL — `SearchQuery` doesn't exist yet.

- [ ] **Step 3: Implement `SearchQuery`**

```java
package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import java.util.Optional;
import java.util.Set;

public record SearchQuery(String value) {
  private static final Set<String> TRIGGER_PREFIXES = Set.of("?wp", "?wiki");

  public SearchQuery {
    Assert.field("value", value).notBlank();
  }

  public static Optional<SearchQuery> parse(String content) {
    return Optional.ofNullable(content).map(String::trim).flatMap(SearchQuery::extractTerm);
  }

  private static Optional<SearchQuery> extractTerm(String trimmed) {
    return TRIGGER_PREFIXES.stream()
      .filter(prefix -> trimmed.startsWith(prefix + " "))
      .findFirst()
      .map(prefix -> trimmed.substring(prefix.length()).trim())
      .filter(term -> !term.isBlank())
      .map(SearchQuery::new);
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SearchQueryTest`
Expected: PASS (11 tests across the parameterized cases)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/domain/SearchQuery.java src/test/java/fr/craft/chatbot/search/domain/SearchQueryTest.java
git commit -m "Add SearchQuery domain type parsing ?wp/?wiki triggers"
```

---

### Task 8: `PageSummary` domain type

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/domain/PageSummary.java`
- Test: `src/test/java/fr/craft/chatbot/search/domain/PageSummaryTest.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.search.domain.PageSummary` record — `extract()`, `url()`, `disambiguation()` — used by Task 9 (`SearchOutcome`), Task 13 (`PageLookup`), Task 15/16 (translators/adapters).

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class PageSummaryTest {

  @Test
  void shouldKeepAShortExtractUnchanged() {
    var summary = new PageSummary("Résumé court.", "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo("Résumé court.");
  }

  @Test
  void shouldTruncateAtTheLastSpaceBeforeTheLimitWhenTooLong() {
    var extract = "a".repeat(150) + " " + "b".repeat(100);

    var summary = new PageSummary(extract, "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo("a".repeat(150) + "…");
  }

  @Test
  void shouldTruncateAtTheLimitWhenThereIsNoSpaceToBackOffTo() {
    var extract = "a".repeat(250);

    var summary = new PageSummary(extract, "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo("a".repeat(200) + "…");
  }

  @Test
  void shouldExposeTheUrlAndDisambiguationFlag() {
    var summary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    assertThat(summary.url()).isEqualTo("https://fr.wikipedia.org/wiki/Java");
    assertThat(summary.disambiguation()).isTrue();
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullExtract() {
    assertThatThrownBy(() -> new PageSummary(null, "https://fr.wikipedia.org/wiki/Java", false)).isInstanceOf(
      MissingMandatoryValueException.class
    );
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullUrl() {
    assertThatThrownBy(() -> new PageSummary("extrait", null, false)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=PageSummaryTest`
Expected: FAIL — `PageSummary` doesn't exist yet.

- [ ] **Step 3: Implement `PageSummary`**

```java
package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record PageSummary(String extract, String url, boolean disambiguation) {
  private static final int MAX_EXTRACT_LENGTH = 200;

  public PageSummary {
    Assert.field("extract", extract).notNull();
    Assert.field("url", url).notNull();
    extract = truncate(extract);
  }

  private static String truncate(String text) {
    if (text.length() <= MAX_EXTRACT_LENGTH) {
      return text;
    }

    var cut = text.substring(0, MAX_EXTRACT_LENGTH);
    var lastSpace = cut.lastIndexOf(' ');

    // ponytail: naive word-boundary truncation, no sentence-aware trimming — good enough for a chat summary
    return (lastSpace > 0 ? cut.substring(0, lastSpace) : cut) + "…";
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=PageSummaryTest`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/domain/PageSummary.java src/test/java/fr/craft/chatbot/search/domain/PageSummaryTest.java
git commit -m "Add PageSummary domain type with 200-char truncation"
```

---

### Task 9: `SearchOutcome` domain type

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/domain/SearchOutcome.java`
- Test: `src/test/java/fr/craft/chatbot/search/domain/SearchOutcomeTest.java`

**Interfaces:**

- Consumes: `SearchQuery` (Task 7), `PageSummary` (Task 8).
- Produces: `fr.craft.chatbot.search.domain.SearchOutcome` sealed interface with records `Found(PageSummary summary)`, `Ambiguous(SearchQuery query, PageSummary summary)`, `NotFound(SearchQuery query)`, and `static SearchOutcome from(SearchQuery query, Optional<PageSummary> summary)`. Used by Task 12 (`HandleSearchMessageService`) and Task 16/17 (translator, port).

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchOutcomeTest {

  private static final SearchQuery QUERY = new SearchQuery("java");

  @Test
  void shouldReturnFoundWhenTheSummaryIsNotADisambiguationPage() {
    var summary = new PageSummary("Un langage de programmation.", "https://fr.wikipedia.org/wiki/Java_(langage)", false);

    assertThat(SearchOutcome.from(QUERY, Optional.of(summary))).isEqualTo(new SearchOutcome.Found(summary));
  }

  @Test
  void shouldReturnAmbiguousWhenTheSummaryIsADisambiguationPage() {
    var summary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    assertThat(SearchOutcome.from(QUERY, Optional.of(summary))).isEqualTo(new SearchOutcome.Ambiguous(QUERY, summary));
  }

  @Test
  void shouldReturnNotFoundWhenNoSummaryIsPresent() {
    assertThat(SearchOutcome.from(QUERY, Optional.empty())).isEqualTo(new SearchOutcome.NotFound(QUERY));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SearchOutcomeTest`
Expected: FAIL — `SearchOutcome` doesn't exist yet.

- [ ] **Step 3: Implement `SearchOutcome`**

```java
package fr.craft.chatbot.search.domain;

import java.util.Optional;

public sealed interface SearchOutcome {
  record Found(PageSummary summary) implements SearchOutcome {}

  record Ambiguous(SearchQuery query, PageSummary summary) implements SearchOutcome {}

  record NotFound(SearchQuery query) implements SearchOutcome {}

  static SearchOutcome from(SearchQuery query, Optional<PageSummary> summary) {
    return summary
      .<SearchOutcome>map(found -> found.disambiguation() ? new Ambiguous(query, found) : new Found(found))
      .orElseGet(() -> new NotFound(query));
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SearchOutcomeTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/domain/SearchOutcome.java src/test/java/fr/craft/chatbot/search/domain/SearchOutcomeTest.java
git commit -m "Add SearchOutcome sealed type classifying Wikipedia lookups"
```

---

### Task 10: `SearchResponse` domain type

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/domain/SearchResponse.java`
- Test: `src/test/java/fr/craft/chatbot/search/domain/SearchResponseTest.java`

**Interfaces:**

- Produces: `fr.craft.chatbot.search.domain.SearchResponse` record, `value()` accessor. Used by Task 11 (`SearchResponseTranslator`), Task 12 (`HandleSearchMessageService`), Task 16 (`SpringSearchResponseTranslator`).

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchResponseTest {

  @Test
  void shouldBuildASearchResponse() {
    var response = new SearchResponse(" Un langage de programmation. ");

    assertThat(response.value()).isEqualTo("Un langage de programmation.");
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullValue() {
    assertThatThrownBy(() -> new SearchResponse(null)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SearchResponseTest`
Expected: FAIL — `SearchResponse` doesn't exist yet.

- [ ] **Step 3: Implement `SearchResponse`**

```java
package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record SearchResponse(String value) {
  public SearchResponse(String value) {
    Assert.field("value", value).notNull();
    this.value = value.trim();
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SearchResponseTest`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/domain/SearchResponse.java src/test/java/fr/craft/chatbot/search/domain/SearchResponseTest.java
git commit -m "Add SearchResponse domain type"
```

---

### Task 11: `PageLookup` and `SearchResponseTranslator` ports

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/domain/PageLookup.java`
- Create: `src/main/java/fr/craft/chatbot/search/domain/SearchResponseTranslator.java`

**Interfaces:**

- Consumes: `SearchQuery` (Task 7), `PageSummary` (Task 8), `SearchOutcome` (Task 9), `SearchResponse` (Task 10).
- Produces: `fr.craft.chatbot.search.domain.PageLookup#findSummary(SearchQuery, Locale) : Optional<PageSummary>`, `fr.craft.chatbot.search.domain.SearchResponseTranslator#translate(SearchOutcome) : List<SearchResponse>`. Task 12 (`HandleSearchMessageService`) consumes both by constructor injection. Task 14 (`RestWikipediaPageLookup`) implements the first, Task 16 (`SpringSearchResponseTranslator`) implements the second.

Interfaces are exempt from the `@UnitTest`/`@ComponentTest` requirement and have no logic of their own — no test needed (`AnnotationArchTest.shouldHaveUnitTestOrComponentTestAnnotation` explicitly allows `orShould().beInterfaces()`).

- [ ] **Step 1: Create `PageLookup`**

```java
package fr.craft.chatbot.search.domain;

import java.util.Locale;
import java.util.Optional;

public interface PageLookup {
  Optional<PageSummary> findSummary(SearchQuery query, Locale locale);
}
```

- [ ] **Step 2: Create `SearchResponseTranslator`**

```java
package fr.craft.chatbot.search.domain;

import java.util.List;

public interface SearchResponseTranslator {
  List<SearchResponse> translate(SearchOutcome outcome);
}
```

- [ ] **Step 3: Run the full test suite to confirm nothing broke**

Run: `./mvnw test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/domain/PageLookup.java src/main/java/fr/craft/chatbot/search/domain/SearchResponseTranslator.java
git commit -m "Add PageLookup and SearchResponseTranslator ports"
```

---

### Task 12: `HandleSearchMessageService` application service

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/application/HandleSearchMessageService.java`
- Test: `src/test/java/fr/craft/chatbot/search/application/HandleSearchMessageServiceTest.java`

**Interfaces:**

- Consumes: `PageLookup`, `SearchResponseTranslator`, `SearchQuery`, `SearchOutcome` (search.domain), `ChatMessage`, `ChatMessagePublisher` (shared.twitch.domain), plus a `java.util.Locale` constructor parameter (the bot's locale, injected by Spring from Task 1's bean).
- Produces: `fr.craft.chatbot.search.application.HandleSearchMessageService#handle(ChatMessage)`. Consumed by Task 13 (`SearchChatMessageListener`).

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponse;
import fr.craft.chatbot.search.domain.SearchResponseTranslator;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HandleSearchMessageServiceTest {

  private static final SearchQuery QUERY = new SearchQuery("java");
  private static final PageSummary SUMMARY = new PageSummary(
    "Un langage de programmation.",
    "https://fr.wikipedia.org/wiki/Java_(langage)",
    false
  );

  @Mock
  private PageLookup pageLookup;

  @Mock
  private ChatMessagePublisher chatMessagePublisher;

  @Mock
  private SearchResponseTranslator translator;

  @Test
  void shouldStaySilentWhenTheMessageIsNotASearchCommand() {
    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("hello there"));

    verify(chatMessagePublisher, never()).send(anyString());
  }

  @Test
  void shouldSendEveryTranslatedResponseWhenFoundInTheBotLocale() {
    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.of(SUMMARY));
    when(translator.translate(new SearchOutcome.Found(SUMMARY))).thenReturn(
      List.of(new SearchResponse("Un langage de programmation."), new SearchResponse("https://fr.wikipedia.org/wiki/Java_(langage)"))
    );

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("Un langage de programmation.");
    verify(chatMessagePublisher).send("https://fr.wikipedia.org/wiki/Java_(langage)");
    verify(pageLookup, never()).findSummary(QUERY, Locale.ENGLISH);
  }

  @Test
  void shouldFallBackToEnglishWhenNotFoundInTheBotLocale() {
    var englishSummary = new PageSummary("A programming language.", "https://en.wikipedia.org/wiki/Java_(programming_language)", false);

    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.empty());
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.of(englishSummary));
    when(translator.translate(new SearchOutcome.Found(englishSummary))).thenReturn(List.of(new SearchResponse("A programming language.")));

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("A programming language.");
  }

  @Test
  void shouldNotFallBackToEnglishWhenTheBotLocaleIsAlreadyEnglish() {
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.empty());
    when(translator.translate(new SearchOutcome.NotFound(QUERY))).thenReturn(List.of(new SearchResponse("No article found.")));

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.ENGLISH).handle(new ChatMessage("?wp java"));

    verify(pageLookup, times(1)).findSummary(any(), any());
  }

  @Test
  void shouldNotFallBackToEnglishWhenTheResultIsAmbiguous() {
    var ambiguousSummary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.of(ambiguousSummary));
    when(translator.translate(new SearchOutcome.Ambiguous(QUERY, ambiguousSummary))).thenReturn(
      List.of(new SearchResponse("Pas de résumé, voici la page d'homonymie : https://fr.wikipedia.org/wiki/Java"))
    );

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(pageLookup, never()).findSummary(QUERY, Locale.ENGLISH);
  }

  @Test
  void shouldSendTheNotFoundResponseWhenNothingIsFoundInEitherLocale() {
    when(pageLookup.findSummary(QUERY, Locale.FRENCH)).thenReturn(Optional.empty());
    when(pageLookup.findSummary(QUERY, Locale.ENGLISH)).thenReturn(Optional.empty());
    when(translator.translate(new SearchOutcome.NotFound(QUERY))).thenReturn(List.of(new SearchResponse("Aucun article trouvé.")));

    new HandleSearchMessageService(pageLookup, chatMessagePublisher, translator, Locale.FRENCH).handle(new ChatMessage("?wp java"));

    verify(chatMessagePublisher).send("Aucun article trouvé.");
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=HandleSearchMessageServiceTest`
Expected: FAIL — `HandleSearchMessageService` doesn't exist yet.

- [ ] **Step 3: Implement `HandleSearchMessageService`**

```java
package fr.craft.chatbot.search.application;

import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponseTranslator;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class HandleSearchMessageService {

  private final PageLookup pageLookup;
  private final ChatMessagePublisher chatMessagePublisher;
  private final SearchResponseTranslator translator;
  private final Locale botLocale;

  public HandleSearchMessageService(
    PageLookup pageLookup,
    ChatMessagePublisher chatMessagePublisher,
    SearchResponseTranslator translator,
    Locale botLocale
  ) {
    this.pageLookup = pageLookup;
    this.chatMessagePublisher = chatMessagePublisher;
    this.translator = translator;
    this.botLocale = botLocale;
  }

  public void handle(ChatMessage message) {
    SearchQuery.parse(message.content())
      .map(this::resolveOutcome)
      .map(translator::translate)
      .ifPresent(responses -> responses.forEach(response -> chatMessagePublisher.send(response.value())));
  }

  private SearchOutcome resolveOutcome(SearchQuery query) {
    var outcome = SearchOutcome.from(query, pageLookup.findSummary(query, botLocale));

    if (outcome instanceof SearchOutcome.NotFound && !botLocale.equals(Locale.ENGLISH)) {
      return SearchOutcome.from(query, pageLookup.findSummary(query, Locale.ENGLISH));
    }

    return outcome;
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=HandleSearchMessageServiceTest`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/application/HandleSearchMessageService.java src/test/java/fr/craft/chatbot/search/application/HandleSearchMessageServiceTest.java
git commit -m "Add HandleSearchMessageService with English fallback on NotFound only"
```

---

### Task 13: `SearchChatMessageListener` primary adapter

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java`
- Test: `src/test/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListenerTest.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade` (Task 4), `fr.craft.chatbot.search.application.HandleSearchMessageService` (Task 12).

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.infrastructure.primary;

import static org.mockito.Mockito.verify;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.application.HandleSearchMessageService;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchChatMessageListenerTest {

  @Mock
  private TwitchChatFacade twitchChatFacade;

  @Mock
  private HandleSearchMessageService handleSearchMessageService;

  @Test
  void shouldForwardIncomingChatMessagesToTheHandleSearchMessageService() {
    new SearchChatMessageListener(twitchChatFacade, handleSearchMessageService).subscribeToChatMessages();

    var listener = captureRegisteredListener();
    var message = new ChatMessage("?wp java");

    listener.accept(message);

    verify(handleSearchMessageService).handle(message);
  }

  @SuppressWarnings("unchecked")
  private Consumer<ChatMessage> captureRegisteredListener() {
    var captor = ArgumentCaptor.forClass(Consumer.class);

    verify(twitchChatFacade).onChatMessage(captor.capture());

    return captor.getValue();
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SearchChatMessageListenerTest`
Expected: FAIL — `SearchChatMessageListener` doesn't exist yet.

- [ ] **Step 3: Implement `SearchChatMessageListener`**

```java
package fr.craft.chatbot.search.infrastructure.primary;

import fr.craft.chatbot.search.application.HandleSearchMessageService;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
class SearchChatMessageListener {

  private final TwitchChatFacade twitchChatFacade;
  private final HandleSearchMessageService handleSearchMessageService;

  SearchChatMessageListener(TwitchChatFacade twitchChatFacade, HandleSearchMessageService handleSearchMessageService) {
    this.twitchChatFacade = twitchChatFacade;
    this.handleSearchMessageService = handleSearchMessageService;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatFacade.onChatMessage(handleSearchMessageService::handle);
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SearchChatMessageListenerTest`
Expected: PASS (1 test)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java src/test/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListenerTest.java
git commit -m "Add SearchChatMessageListener primary adapter"
```

---

### Task 14: Add the `search.notfound` / `search.ambiguous` message keys

**Files:**

- Modify: `src/main/resources/messages.properties`
- Modify: `src/main/resources/messages_fr.properties`
- Modify: `src/main/resources/messages_en.properties`

No test in this task — the keys are exercised by Task 16's `SpringSearchResponseTranslatorTest`. This task exists on its own so Task 16 doesn't have to also touch resource files.

- [ ] **Step 1: Add the French keys (default bundle)**

Append to `src/main/resources/messages.properties`:

```properties
search.notfound=Aucun article trouvé pour "{0}".
search.ambiguous=Pas de résumé pour "{0}", voici la page d'homonymie : {1}
```

- [ ] **Step 2: Add the French keys (explicit `_fr` bundle)**

Append to `src/main/resources/messages_fr.properties`:

```properties
search.notfound=Aucun article trouvé pour "{0}".
search.ambiguous=Pas de résumé pour "{0}", voici la page d'homonymie : {1}
```

- [ ] **Step 3: Add the English keys**

Append to `src/main/resources/messages_en.properties`:

```properties
search.notfound=No article found for "{0}".
search.ambiguous=No summary for "{0}", here is the disambiguation page: {1}
```

- [ ] **Step 4: Format and commit**

```bash
pnpm prettier:format
git add src/main/resources/messages.properties src/main/resources/messages_fr.properties src/main/resources/messages_en.properties
git commit -m "Add search.notfound and search.ambiguous message keys"
```

---

### Task 15: `RestWikipediaPageLookup` secondary adapter

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/infrastructure/secondary/RestWikipediaPageLookup.java`
- Test: `src/test/java/fr/craft/chatbot/search/infrastructure/secondary/RestWikipediaPageLookupTest.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.search.domain.PageLookup` (Task 11), `SearchQuery` (Task 7), `PageSummary` (Task 8).
- Produces: implements `PageLookup#findSummary`. Nothing later depends on this class by name — Spring wires it as the `PageLookup` bean.

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchQuery;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@UnitTest
class RestWikipediaPageLookupTest {

  private final RestClient.Builder restClientBuilder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
  private final RestWikipediaPageLookup pageLookup = new RestWikipediaPageLookup(restClientBuilder);

  @Test
  void shouldReturnASummaryWhenThePageExists() {
    server.expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/java")).andRespond(
      withSuccess(
        """
        {"type":"standard","extract":"Un langage de programmation.","content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/Java_(langage)"}}}
        """,
        MediaType.APPLICATION_JSON
      )
    );

    var summary = pageLookup.findSummary(new SearchQuery("java"), Locale.FRENCH);

    assertThat(summary).contains(new PageSummary("Un langage de programmation.", "https://fr.wikipedia.org/wiki/Java_(langage)", false));
  }

  @Test
  void shouldFlagDisambiguationPages() {
    server.expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/java")).andRespond(
      withSuccess(
        """
        {"type":"disambiguation","extract":"","content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/Java"}}}
        """,
        MediaType.APPLICATION_JSON
      )
    );

    var summary = pageLookup.findSummary(new SearchQuery("java"), Locale.FRENCH);

    assertThat(summary).contains(new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true));
  }

  @Test
  void shouldReturnEmptyWhenThePageDoesNotExist() {
    server.expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/doesnotexist")).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(pageLookup.findSummary(new SearchQuery("doesnotexist"), Locale.FRENCH)).isEmpty();
  }

  @Test
  void shouldReturnEmptyOnServerError() {
    server.expect(requestTo("https://en.wikipedia.org/api/rest_v1/page/summary/java")).andRespond(withServerError());

    assertThat(pageLookup.findSummary(new SearchQuery("java"), Locale.ENGLISH)).isEmpty();
  }

  @Test
  void shouldEncodeMultiWordQueries() {
    server.expect(requestTo("https://fr.wikipedia.org/api/rest_v1/page/summary/coq%20de%20java")).andRespond(
      withSuccess(
        """
        {"type":"standard","extract":"Une race de poule.","content_urls":{"desktop":{"page":"https://fr.wikipedia.org/wiki/Coq_de_Java"}}}
        """,
        MediaType.APPLICATION_JSON
      )
    );

    var summary = pageLookup.findSummary(new SearchQuery("coq de java"), Locale.FRENCH);

    assertThat(summary).contains(new PageSummary("Une race de poule.", "https://fr.wikipedia.org/wiki/Coq_de_Java", false));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=RestWikipediaPageLookupTest`
Expected: FAIL — `RestWikipediaPageLookup` doesn't exist yet.

- [ ] **Step 3: Implement `RestWikipediaPageLookup`**

```java
package fr.craft.chatbot.search.infrastructure.secondary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchQuery;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Repository
class RestWikipediaPageLookup implements PageLookup {

  private static final String SUMMARY_URI = "https://{language}.wikipedia.org/api/rest_v1/page/summary/{title}";

  private final RestClient restClient;

  RestWikipediaPageLookup(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  @Override
  public Optional<PageSummary> findSummary(SearchQuery query, Locale locale) {
    try {
      var response = restClient.get().uri(SUMMARY_URI, locale.getLanguage(), query.value()).retrieve().body(WikipediaSummaryResponse.class);

      return Optional.of(toPageSummary(Objects.requireNonNull(response)));
    } catch (RestClientException e) {
      // ponytail: any HTTP/network failure (404, 5xx, timeout) is treated as "not found" —
      // no distinction, no retry. Revisit if silent Wikipedia outages become a real problem.
      return Optional.empty();
    }
  }

  private PageSummary toPageSummary(WikipediaSummaryResponse response) {
    return new PageSummary(response.extract(), response.contentUrls().desktop().page(), "disambiguation".equals(response.type()));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record WikipediaSummaryResponse(String type, String extract, @JsonProperty("content_urls") ContentUrls contentUrls) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentUrls(Desktop desktop) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Desktop(String page) {}
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=RestWikipediaPageLookupTest`
Expected: PASS (5 tests). If `shouldEncodeMultiWordQueries` fails because the actual captured URI differs from `.../summary/coq%20de%20java` (check the assertion error message, it shows the real request URI), update the `requestTo(...)` expectation in the test to match Spring's actual URI-template encoding output, then re-run — don't change the production code to force a specific encoding, `RestClient`'s default template expansion is correct as-is.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/infrastructure/secondary/RestWikipediaPageLookup.java src/test/java/fr/craft/chatbot/search/infrastructure/secondary/RestWikipediaPageLookupTest.java
git commit -m "Add RestWikipediaPageLookup calling the Wikipedia REST summary API"
```

---

### Task 16: `SpringSearchResponseTranslator` secondary adapter

**Files:**

- Create: `src/main/java/fr/craft/chatbot/search/infrastructure/secondary/SpringSearchResponseTranslator.java`
- Test: `src/test/java/fr/craft/chatbot/search/infrastructure/secondary/SpringSearchResponseTranslatorTest.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.search.domain.SearchResponseTranslator` (Task 11), `SearchOutcome`, `PageSummary`, `SearchQuery`, `SearchResponse` (Tasks 7–10), the `search.notfound`/`search.ambiguous` keys (Task 14), a `java.util.Locale` constructor parameter (Task 1's bean).
- Produces: implements `SearchResponseTranslator#translate`. Spring wires it as the bean; nothing depends on it by name.

- [ ] **Step 1: Write the failing test**

```java
package fr.craft.chatbot.search.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponse;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

@UnitTest
class SpringSearchResponseTranslatorTest {

  private final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

  {
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
  }

  private final SpringSearchResponseTranslator translator = new SpringSearchResponseTranslator(messageSource, Locale.FRENCH);
  private final SpringSearchResponseTranslator englishTranslator = new SpringSearchResponseTranslator(messageSource, Locale.ENGLISH);

  @Test
  void shouldReturnTheSummaryThenTheLinkWhenFound() {
    var summary = new PageSummary("Un langage de programmation.", "https://fr.wikipedia.org/wiki/Java_(langage)", false);

    assertThat(translator.translate(new SearchOutcome.Found(summary))).containsExactly(
      new SearchResponse("Un langage de programmation."),
      new SearchResponse("https://fr.wikipedia.org/wiki/Java_(langage)")
    );
  }

  @Test
  void shouldExplainTheDisambiguationPageWhenAmbiguous() {
    var summary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    assertThat(translator.translate(new SearchOutcome.Ambiguous(new SearchQuery("java"), summary))).containsExactly(
      new SearchResponse("Pas de résumé pour \"java\", voici la page d'homonymie : https://fr.wikipedia.org/wiki/Java")
    );
  }

  @Test
  void shouldSayNoArticleWasFoundWhenNotFound() {
    assertThat(translator.translate(new SearchOutcome.NotFound(new SearchQuery("doesnotexist")))).containsExactly(
      new SearchResponse("Aucun article trouvé pour \"doesnotexist\".")
    );
  }

  @Test
  void shouldSayNoArticleWasFoundInEnglishWhenNotFound() {
    assertThat(englishTranslator.translate(new SearchOutcome.NotFound(new SearchQuery("doesnotexist")))).containsExactly(
      new SearchResponse("No article found for \"doesnotexist\".")
    );
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SpringSearchResponseTranslatorTest`
Expected: FAIL — `SpringSearchResponseTranslator` doesn't exist yet.

- [ ] **Step 3: Implement `SpringSearchResponseTranslator`**

```java
package fr.craft.chatbot.search.infrastructure.secondary;

import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponse;
import fr.craft.chatbot.search.domain.SearchResponseTranslator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Repository;

@Repository
class SpringSearchResponseTranslator implements SearchResponseTranslator {

  private final MessageSource messageSource;
  private final Locale locale;

  SpringSearchResponseTranslator(MessageSource messageSource, Locale locale) {
    this.messageSource = messageSource;
    this.locale = locale;
  }

  @Override
  public List<SearchResponse> translate(SearchOutcome outcome) {
    return switch (outcome) {
      case SearchOutcome.Found(PageSummary summary) -> List.of(new SearchResponse(summary.extract()), new SearchResponse(summary.url()));
      case SearchOutcome.Ambiguous(SearchQuery query, PageSummary summary) -> List.of(
        new SearchResponse(messageSource.getMessage("search.ambiguous", new Object[] { query.value(), summary.url() }, locale))
      );
      case SearchOutcome.NotFound(SearchQuery query) -> List.of(
        new SearchResponse(messageSource.getMessage("search.notfound", new Object[] { query.value() }, locale))
      );
    };
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=SpringSearchResponseTranslatorTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/infrastructure/secondary/SpringSearchResponseTranslator.java src/test/java/fr/craft/chatbot/search/infrastructure/secondary/SpringSearchResponseTranslatorTest.java
git commit -m "Add SpringSearchResponseTranslator secondary adapter"
```

---

### Task 17: Full verification pass

**Files:** none (verification only).

- [ ] **Step 1: Run the full build with coverage gate and architecture tests**

Run: `./mvnw verify`
Expected: BUILD SUCCESS — this exercises `HexagonalArchTest` (confirms `search` is a valid bounded context depending only on `shared.twitch`/`shared.locale`, never on `command`), `AnnotationArchTest`, `EqualsHashcodeArchTest`, JaCoCo's 100%-line/branch check on every new and modified class, and Checkstyle.

- [ ] **Step 2: If JaCoCo reports missed coverage, identify the exact class/line**

Run: `open target/site/jacoco/index.html` (or read `target/site/jacoco/jacoco.csv`) to find which class is under 100%. Add the missing test case following the same pattern as its sibling tests in this plan — every branch in this plan's code was designed to be reachable from the tests already written, so a gap here means a step above was skipped, not that new production code is needed.

- [ ] **Step 3: Run the Prettier check**

Run: `pnpm prettier:check`
Expected: no formatting diffs. If there are any, run `pnpm prettier:format` and review the diff before committing.

- [ ] **Step 4: Commit if Step 2 or 3 required changes**

```bash
git add -A
git commit -m "Fix coverage/formatting gaps found during full verification"
```

(Skip this commit if nothing needed fixing.)

---

### Task 18: Review passes

Per the user's request, run both review skills over the branch's diff before considering this done — invoke them, don't skip either:

- [ ] **Step 1: Invoke `ponytail:ponytail-review`** on the full diff produced by Tasks 1–17, looking specifically for reinvented stdlib, unneeded abstractions (e.g. confirm `PageLookup`/`SearchResponseTranslator` earn their keep as ports rather than being speculative), and dead flexibility introduced by the shared-kernel extraction.

- [ ] **Step 2: Address any findings** — for each finding, either fix it with a small follow-up commit or note explicitly why it's intentional (e.g. the `ponytail:` comment in `PageSummary.truncate` and in `RestWikipediaPageLookup.findSummary` already document two deliberate simplifications from the spec — don't "fix" those).

- [ ] **Step 3: Invoke `superpowers:requesting-code-review`** for a full correctness/design review of the same diff.

- [ ] **Step 4: Address any findings**, committing fixes as needed, then stop — this plan's scope ends here. Merge/PR decisions belong to `superpowers:finishing-a-development-branch`, not this plan.
