# Twitch Facade Primary/Secondary Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split `TwitchChatFacade`/`Twitch4jChatFacade` into a `TwitchChatReceiver` interface in `infrastructure.primary`, and a `TwitchChatMessagePublisher` in `infrastructure.secondary` that implements the domain port `ChatMessagePublisher` directly (no more intermediate facade on the sending side).

**Architecture:** `TwitchClientConfiguration` narrows to producing just `TwitchProperties` and a single `ITwitchChat` bean (the only piece that opens a real network connection). `Twitch4jChatReceiver` (primary) and `TwitchChatMessagePublisher` (secondary) each pick up `ITwitchChat` via ordinary `@Component` constructor injection — neither is manually wired inside a `@Bean` method, so the bare `TwitchClientConfiguration` package never has to reference `.primary`/`.secondary` types. `ChatMessageEventPublisher` and `ChatMessageReceivedEvent` move into `.primary` alongside the new receiver, since `ChatMessageEventPublisher` is `TwitchChatReceiver`'s only consumer.

**Tech Stack:** Spring `@Component`/`@Profile`/`@Configuration` (no new dependency). Existing project stack: Java 25, Spring Boot 4.0.6, JUnit 5, Mockito, AssertJ, ArchUnit, twitch4j.

Spec: `docs/superpowers/specs/2026-07-21-twitch-facade-primary-secondary-split-design.md`.

## Global Constraints

- No new dependency.
- The real twitch4j-backed classes (`Twitch4jChatReceiver`, `TwitchChatMessagePublisher`) must carry `@Profile("!test")` — during tests, `ITwitchChat` is never available as a bean, so anything requiring it must be excluded from the `test` profile.
- `TwitchClientConfiguration` must not reference any `.primary`/`.secondary` class — `Twitch4jChatReceiver`/`TwitchChatMessagePublisher` pick up `ITwitchChat` via `@Component` autowiring, not manual `@Bean`-method construction. This sidesteps an open question about whether ArchUnit's layered-architecture check would allow a bare-package class to depend into `.primary`/`.secondary` — untested, so the design avoids it rather than relying on it.
- JaCoCo `check` (at `./mvnw verify`) requires zero missed lines/branches per class — `Twitch4jChatReceiver` and `TwitchChatMessagePublisher` each need their own mocked unit test now that the ITs no longer construct them for real (both are `@Profile("!test")`).
- Test classes ending in `Test` must be `@UnitTest`/`@ComponentTest`; classes ending in `IT` must be `@IntegrationTest` (enforced by `AnnotationArchTest`).
- Every top-level class under `fr.craft.chatbot..` must implement both `equals`/`hashCode` or neither (enforced by `EqualsHashcodeArchTest`) — none of the new/changed classes here override either, matching the existing adapter classes.
- Use `python3 .claude/tools/jdtls-driver.py create <path> <class|interface|record>` to scaffold every new Java type (per CLAUDE.md), then fill in with the code shown in each step.

---

### Task 1: `TwitchChatReceiver` + `Twitch4jChatReceiver` (primary, pure addition)

Adds the new receiving-side abstraction without touching anything that
depends on the old `TwitchChatFacade` yet — nothing else changes, the
build stays green exactly as it is today.

**Files:**

- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/package-info.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/TwitchChatReceiver.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiver.java`
- Test: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiverTest.java`

**Interfaces:**

- Consumes: `com.github.twitch4j.chat.ITwitchChat` (twitch4j, external); `fr.craft.chatbot.shared.twitch.domain.ChatMessage` (existing record).
- Produces: `fr.craft.chatbot.shared.twitch.infrastructure.primary.TwitchChatReceiver` — public interface, `void onChatMessage(Consumer<ChatMessage> listener)`. Consumed by Task 2.

- [ ] **Step 1: Package marker**

Create `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/package-info.java`:

```java
@org.jspecify.annotations.NullMarked
package fr.craft.chatbot.shared.twitch.infrastructure.primary;
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiverTest.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.primary;

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
class Twitch4jChatReceiverTest {

  @Mock
  private ITwitchChat twitchChat;

  @Mock
  private EventManager eventManager;

  @Test
  void shouldMapIncomingChannelMessageEventsToChatMessages() {
    when(twitchChat.getEventManager()).thenReturn(eventManager);

    var received = new ArrayList<ChatMessage>();
    new Twitch4jChatReceiver(twitchChat).onChatMessage(received::add);

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

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=Twitch4jChatReceiverTest`
Expected: FAIL — compile error, `TwitchChatReceiver` and `Twitch4jChatReceiver` do not exist yet.

- [ ] **Step 4: Create `TwitchChatReceiver`**

Create with `python3 .claude/tools/jdtls-driver.py create src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/TwitchChatReceiver.java interface`, then set its content to:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;

public interface TwitchChatReceiver {
  void onChatMessage(Consumer<ChatMessage> listener);
}
```

- [ ] **Step 5: Create `Twitch4jChatReceiver`**

Create with `python3 .claude/tools/jdtls-driver.py create src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiver.java class`, then set its content to:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;

class Twitch4jChatReceiver implements TwitchChatReceiver {

  private final ITwitchChat twitchChat;

  Twitch4jChatReceiver(ITwitchChat twitchChat) {
    this.twitchChat = twitchChat;
  }

  @Override
  public void onChatMessage(Consumer<ChatMessage> listener) {
    twitchChat.getEventManager().onEvent(ChannelMessageEvent.class, event -> listener.accept(new ChatMessage(event.getMessage())));
  }
}
```

Deliberately **not** `@Component`/`@Profile` yet: Spring eagerly instantiates every non-lazy `@Component` at startup, and `ITwitchChat` isn't a bean until Task 2 introduces it — annotating this now would make production startup (`./mvnw`, not exercised by any test in this task) fail between this commit and Task 2's. Task 2 adds the annotations in the same commit that introduces the `ITwitchChat` bean.

- [ ] **Step 6: Run test to verify it passes**

Run: `./mvnw test -Dtest=Twitch4jChatReceiverTest`
Expected: PASS

- [ ] **Step 7: Run the full unit test suite to check for regressions**

Run: `./mvnw test`
Expected: PASS (nothing else references the new types yet — this is a pure addition).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/package-info.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/TwitchChatReceiver.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiver.java src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiverTest.java
git commit -m "Add TwitchChatReceiver and its twitch4j implementation in infrastructure.primary"
```

---

### Task 2: Coordinated swap — rewire everything off `TwitchChatFacade`

This task cannot be split further without leaving the build in a
non-compiling state in between: `TwitchChatFacade` is referenced by
production code, test config, and both ITs, so all of it moves together.
It ends with one commit, once `./mvnw verify` is green.

**Files:**

- Modify: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchClientConfiguration.java`
- Modify: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiver.java` (adds `@Component`/`@Profile`, created plain in Task 1)
- Modify: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisher.java`
- Test: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisherTest.java` (new)
- Delete: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisher.java`
- Delete: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageReceivedEvent.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/ChatMessageEventPublisher.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/ChatMessageReceivedEvent.java`
- Modify: `src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java`
- Modify: `src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java`
- Delete: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchChatFacade.java`
- Delete: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacade.java`
- Delete: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacadeTest.java`
- Modify: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchTestClientConfiguration.java`
- Modify: `src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatCommandIT.java`
- Modify: `src/test/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatCommandIT.java`

**Interfaces:**

- Consumes: `TwitchChatReceiver` (Task 1), `com.github.twitch4j.chat.ITwitchChat`, `fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher.send(String)` (existing domain port), `fr.craft.chatbot.shared.twitch.infrastructure.TwitchProperties` (existing, `getChannel()`/`getBotUsername()`/`getOauthToken()`).
- Produces: `fr.craft.chatbot.shared.twitch.infrastructure.primary.ChatMessageReceivedEvent` (same shape as before, new package) — still consumed by `TwitchChatMessageListener`/`SearchChatMessageListener`.

- [ ] **Step 1: Narrow `TwitchClientConfiguration` to `TwitchProperties` + `ITwitchChat`**

Replace `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchClientConfiguration.java` with:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.ITwitchChat;
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
  ITwitchChat twitchChat(TwitchProperties properties) {
    var credential = new OAuth2Credential("twitch", properties.getOauthToken(), null, null, properties.getBotUsername(), null, null);
    var chat = TwitchChatBuilder.builder().withChatAccount(credential).build();
    chat.joinChannel(properties.getChannel());
    return chat;
  }
}
```

`TwitchClientConfigurationTest` needs no change — it only ever tested `twitchProperties()`, which is untouched.

- [ ] **Step 2: Wire `Twitch4jChatReceiver` into Spring now that `ITwitchChat` is a bean**

In `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiver.java` (created in Task 1), add the Spring annotations and their imports:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
class Twitch4jChatReceiver implements TwitchChatReceiver {

  private final ITwitchChat twitchChat;

  Twitch4jChatReceiver(ITwitchChat twitchChat) {
    this.twitchChat = twitchChat;
  }

  @Override
  public void onChatMessage(Consumer<ChatMessage> listener) {
    twitchChat.getEventManager().onEvent(ChannelMessageEvent.class, event -> listener.accept(new ChatMessage(event.getMessage())));
  }
}
```

- [ ] **Step 3: Rewrite `TwitchChatMessagePublisher` to implement `ChatMessagePublisher` directly**

Replace `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisher.java` with:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.secondary;

import com.github.twitch4j.chat.ITwitchChat;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
class TwitchChatMessagePublisher implements ChatMessagePublisher {

  private final ITwitchChat twitchChat;
  private final String channel;

  TwitchChatMessagePublisher(ITwitchChat twitchChat, TwitchProperties properties) {
    this.twitchChat = twitchChat;
    this.channel = properties.getChannel();
  }

  @Override
  public void send(String message) {
    twitchChat.sendMessage(channel, message);
  }
}
```

- [ ] **Step 4: Add `TwitchChatMessagePublisherTest`**

Create `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisherTest.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.secondary;

import static org.mockito.Mockito.verify;

import com.github.twitch4j.chat.ITwitchChat;
import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TwitchChatMessagePublisherTest {

  @Mock
  private ITwitchChat twitchChat;

  @Test
  void shouldSendMessagesToTheConfiguredChannel() {
    var properties = new TwitchProperties();
    properties.setChannel("mychannel");

    new TwitchChatMessagePublisher(twitchChat, properties).send("Un chatbot Twitch");

    verify(twitchChat).sendMessage("mychannel", "Un chatbot Twitch");
  }
}
```

- [ ] **Step 5: Move `ChatMessageEventPublisher` and `ChatMessageReceivedEvent` into `.primary`**

Delete `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisher.java` and `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageReceivedEvent.java`.

Create `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/ChatMessageReceivedEvent.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;

public record ChatMessageReceivedEvent(ChatMessage chatMessage) {}
```

Create `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/ChatMessageEventPublisher.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class ChatMessageEventPublisher {

  private final TwitchChatReceiver twitchChatReceiver;
  private final ApplicationEventPublisher eventPublisher;

  ChatMessageEventPublisher(TwitchChatReceiver twitchChatReceiver, ApplicationEventPublisher eventPublisher) {
    this.twitchChatReceiver = twitchChatReceiver;
    this.eventPublisher = eventPublisher;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatReceiver.onChatMessage(message -> eventPublisher.publishEvent(new ChatMessageReceivedEvent(message)));
  }
}
```

- [ ] **Step 6: Update the two context listeners' import**

In `src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java`, change:

```java
import fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent;
```

to:

```java
import fr.craft.chatbot.shared.twitch.infrastructure.primary.ChatMessageReceivedEvent;
```

In `src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java`, make the same change (same old import line, same new import line). Nothing else in either file changes.

- [ ] **Step 7: Delete the old facade**

```bash
rm src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchChatFacade.java
rm src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacade.java
rm src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacadeTest.java
```

- [ ] **Step 8: Rewrite `TwitchTestClientConfiguration`**

Replace `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchTestClientConfiguration.java` with:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import fr.craft.chatbot.shared.twitch.infrastructure.primary.TwitchChatReceiver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TwitchTestClientConfiguration {

  @Bean
  TwitchChatReceiver twitchChatReceiver() {
    return listener -> {
      // no real Twitch connection in tests: nothing to subscribe to
    };
  }

  @Bean
  ChatMessagePublisher chatMessagePublisher() {
    return message -> {
      // no real Twitch connection in tests: nothing to send
    };
  }
}
```

- [ ] **Step 9: Rewrite `TwitchChatCommandIT`**

Replace `src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatCommandIT.java` with:

```java
package fr.craft.chatbot.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.IntegrationTest;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import fr.craft.chatbot.shared.twitch.infrastructure.primary.TwitchChatReceiver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@IntegrationTest(properties = "chatbot.commands.file=src/test/resources/command/commands.txt")
@Import(TwitchChatCommandIT.RecordingConfiguration.class)
class TwitchChatCommandIT {

  private static final Path REPLY_FILE = Path.of("target/integration-test-output/chatbot-reply.txt");

  @Autowired
  private FakeTwitchChatReceiver twitchChatReceiver;

  @BeforeEach
  @AfterEach
  void deleteReplyFile() throws IOException {
    Files.deleteIfExists(REPLY_FILE);
  }

  @Test
  void shouldWriteTheKnownCommandContentToTheOutputFileWhenReceivedFromChat() {
    twitchChatReceiver.receiveMessage(new ChatMessage("!projet"));

    assertThat(REPLY_FILE).hasContent("Un chatbot Twitch qui répond aux commandes du chat");
  }

  @Test
  void shouldListKnownCommandsWhenTheCommandIsUnknown() {
    twitchChatReceiver.receiveMessage(new ChatMessage("!doesnotexist"));

    assertThat(REPLY_FILE).hasContent("Commande inconnue. Commandes disponibles : !discord, !projet");
  }

  @Test
  void shouldListKnownCommandsWhenTheCommandsCommandIsReceived() {
    twitchChatReceiver.receiveMessage(new ChatMessage("!commands"));

    assertThat(REPLY_FILE).hasContent("!discord, !projet");
  }

  @Test
  void shouldNotWriteAnOutputFileWhenTheMessageIsNotACommand() {
    twitchChatReceiver.receiveMessage(new ChatMessage("hello there"));

    assertThat(REPLY_FILE).doesNotExist();
  }

  @Nested
  @IntegrationTest(properties = "chatbot.commands.file=src/test/resources/command/empty-commands.txt")
  @Import(TwitchChatCommandIT.RecordingConfiguration.class)
  class NoCommandsAvailable {

    @Autowired
    private FakeTwitchChatReceiver twitchChatReceiver;

    @Test
    void shouldSayThereAreNoCommandsWhenNoneAreConfigured() {
      twitchChatReceiver.receiveMessage(new ChatMessage("!anything"));

      assertThat(REPLY_FILE).hasContent("Aucune commande n'est disponible pour le moment.");
    }
  }

  static class FakeTwitchChatReceiver implements TwitchChatReceiver {

    private final List<Consumer<ChatMessage>> listeners = new ArrayList<>();

    void receiveMessage(ChatMessage message) {
      listeners.forEach(listener -> listener.accept(message));
    }

    @Override
    public void onChatMessage(Consumer<ChatMessage> listener) {
      listeners.add(listener);
    }
  }

  static class RecordingChatMessagePublisher implements ChatMessagePublisher {

    @Override
    public void send(String message) {
      try {
        Files.createDirectories(REPLY_FILE.getParent());
        Files.writeString(REPLY_FILE, message);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @TestConfiguration
  static class RecordingConfiguration {

    @Bean
    @Primary
    FakeTwitchChatReceiver fakeTwitchChatReceiver() {
      return new FakeTwitchChatReceiver();
    }

    @Bean
    @Primary
    RecordingChatMessagePublisher recordingChatMessagePublisher() {
      return new RecordingChatMessagePublisher();
    }
  }
}
```

- [ ] **Step 10: Rewrite `SearchChatCommandIT`**

Replace `src/test/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatCommandIT.java` with:

```java
package fr.craft.chatbot.search.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.IntegrationTest;
import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import fr.craft.chatbot.shared.twitch.infrastructure.primary.TwitchChatReceiver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@IntegrationTest(properties = "chatbot.locale=fr")
@Import(SearchChatCommandIT.SearchTestConfiguration.class)
class SearchChatCommandIT {

  private static final Path REPLY_FILE = Path.of("target/integration-test-output/search-chatbot-reply.txt");

  @Autowired
  private FakeTwitchChatReceiver twitchChatReceiver;

  @BeforeEach
  @AfterEach
  void deleteReplyFile() throws IOException {
    Files.deleteIfExists(REPLY_FILE);
  }

  @Test
  void shouldNotWriteAnythingWhenTheMessageIsNotASearchCommand() {
    twitchChatReceiver.receiveMessage(new ChatMessage("wiki Idris"));

    assertThat(REPLY_FILE).doesNotExist();
  }

  @Test
  void shouldWriteTheDisambiguationMessageWhenTheResultIsAmbiguous() {
    twitchChatReceiver.receiveMessage(new ChatMessage("?wp java"));

    assertThat(REPLY_FILE).hasContent(
      """
      Pas de résumé pour "java", voici la page d'homonymie : https://fr.wikipedia.org/wiki/Java"""
    );
  }

  @Test
  void shouldWriteTheExtractThenTheLinkWhenFoundDirectlyInTheBotLocale() {
    twitchChatReceiver.receiveMessage(new ChatMessage("?wiki coq de java"));

    assertThat(REPLY_FILE).hasContent(
      """
      Une race de poule.
      https://fr.wikipedia.org/wiki/Coq_de_Java"""
    );
  }

  @Test
  void shouldFallBackToEnglishAndWriteBothLinesWhenNotFoundInTheBotLocale() {
    twitchChatReceiver.receiveMessage(new ChatMessage("?wiki onboarding"));

    assertThat(REPLY_FILE).hasContent(
      """
      Onboarding is the process of integrating a new employee.
      https://en.wikipedia.org/wiki/Onboarding"""
    );
  }

  static class FakeTwitchChatReceiver implements TwitchChatReceiver {

    private final List<Consumer<ChatMessage>> listeners = new ArrayList<>();

    void receiveMessage(ChatMessage message) {
      listeners.forEach(listener -> listener.accept(message));
    }

    @Override
    public void onChatMessage(Consumer<ChatMessage> listener) {
      listeners.add(listener);
    }
  }

  static class RecordingChatMessagePublisher implements ChatMessagePublisher {

    @Override
    public void send(String message) {
      try {
        Files.createDirectories(REPLY_FILE.getParent());
        var line = Files.exists(REPLY_FILE) ? "\n" + message : message;
        Files.writeString(REPLY_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  static class FakePageLookup implements PageLookup {

    @Override
    public Optional<PageSummary> findSummary(SearchQuery query, Locale locale) {
      return switch (query.value()) {
        case "java" -> locale.equals(Locale.FRENCH)
          ? Optional.of(new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true))
          : Optional.empty();
        case "coq de java" -> locale.equals(Locale.FRENCH)
          ? Optional.of(new PageSummary("Une race de poule.", "https://fr.wikipedia.org/wiki/Coq_de_Java", false))
          : Optional.empty();
        case "onboarding" -> locale.equals(Locale.ENGLISH)
          ? Optional.of(
              new PageSummary("Onboarding is the process of integrating a new employee.", "https://en.wikipedia.org/wiki/Onboarding", false)
            )
          : Optional.empty();
        default -> Optional.empty();
      };
    }
  }

  @TestConfiguration
  static class SearchTestConfiguration {

    @Bean
    @Primary
    FakeTwitchChatReceiver fakeTwitchChatReceiver() {
      return new FakeTwitchChatReceiver();
    }

    @Bean
    @Primary
    RecordingChatMessagePublisher recordingChatMessagePublisher() {
      return new RecordingChatMessagePublisher();
    }

    @Bean
    @Primary
    FakePageLookup fakePageLookup() {
      return new FakePageLookup();
    }
  }
}
```

- [ ] **Step 11: Run the full build**

Run: `./mvnw verify`
Expected: PASS — unit tests, both ITs, `HexagonalArchTest` (confirms the new `.primary`/`.secondary` split and the bare `TwitchClientConfiguration` don't violate the layered-architecture or wire-dependency rules), `EqualsHashcodeArchTest`, `AnnotationArchTest`, checkstyle, and the JaCoCo coverage gate. Pay particular attention to `HexagonalArchTest` output specifically — this is the open verification point from the spec.

If `HexagonalArchTest` fails on a dependency from `TwitchClientConfiguration` into `.primary`/`.secondary`: it shouldn't, since this design never makes that class reference `Twitch4jChatReceiver` or `TwitchChatMessagePublisher` directly (Step 1's version only returns `ITwitchChat`) — if it still fails, re-check Step 1 didn't accidentally reintroduce such a reference before investigating further.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchClientConfiguration.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/Twitch4jChatReceiver.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisher.java src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/secondary/TwitchChatMessagePublisherTest.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/ChatMessageEventPublisher.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/primary/ChatMessageReceivedEvent.java src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchTestClientConfiguration.java src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatCommandIT.java src/test/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatCommandIT.java
git add -u src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisher.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageReceivedEvent.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/TwitchChatFacade.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacade.java src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/Twitch4jChatFacadeTest.java
git commit -m "Split TwitchChatFacade into TwitchChatReceiver (primary) and a direct ChatMessagePublisher implementation (secondary)"
```

---

### Task 3: Final verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full build**

Run: `./mvnw verify`
Expected: PASS (same as Task 2 Step 11 — re-run here as the final gate in case anything drifted).

- [ ] **Step 2: Run formatting check**

Run: `pnpm prettier:check`
Expected: PASS on every file this plan touched. (A pre-existing `.claude/settings.local.json` formatting warning, if present, is gitignored local tooling state unrelated to this change — ignore it.)

- [ ] **Step 3: Manually confirm the behavior end to end (optional but recommended)**

Run the app locally with `TWITCH_CHANNEL`, `TWITCH_BOT_USERNAME`, `TWITCH_OAUTH_TOKEN` set, send `!projet` and `?wp java` in the configured channel's chat, and confirm both still get a reply — this is the only way to exercise the real `Twitch4jChatReceiver` → `ChatMessageEventPublisher` and `TwitchChatMessagePublisher` → real Twitch chain, since both are `@Profile("!test")` and therefore never run during any automated test.
