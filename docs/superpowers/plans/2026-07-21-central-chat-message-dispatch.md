# Central Chat Message Dispatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace each bounded context's direct subscription to `TwitchChatFacade` with a single shared-kernel publisher that republishes incoming chat messages as a Spring application event, so a future context only needs to add its own `@EventListener`.

**Architecture:** A new `ChatMessageEventPublisher` in `shared.twitch.infrastructure` becomes the sole subscriber to `TwitchChatFacade.onChatMessage`, republishing each message as a `ChatMessageReceivedEvent` via `ApplicationEventPublisher`. `command.infrastructure.primary.TwitchChatMessageListener` and `search.infrastructure.primary.SearchChatMessageListener` switch from direct facade subscription to `@EventListener` methods. A new `wire.event` package configures a custom `ApplicationEventMulticaster` with an error-swallowing `ErrorHandler` so one listener throwing doesn't stop the others from running.

**Tech Stack:** Spring Framework's built-in `ApplicationEventPublisher` / `@EventListener` / `ApplicationEventMulticaster` (no new dependency). Existing project stack: Java 25, Spring Boot 4.0.6, JUnit 5, Mockito, AssertJ, ArchUnit.

Spec: `docs/superpowers/specs/2026-07-21-central-chat-message-dispatch-design.md`.

## Global Constraints

- No new dependency: everything is built on Spring Framework classes already on the classpath.
- Dispatch stays synchronous — no `@Async`, no thread pool. Listener invocation order is not guaranteed (unchanged from today).
- Outbound message sending (`ChatMessagePublisher` / `TwitchChatMessagePublisher`) is out of scope — it's already a single port/adapter pair, nothing to change there.
- Wire classes must be package-private (enforced by `HexagonalArchTest.Wire.shouldNotHavePublicClasses`).
- JaCoCo `check` (at `./mvnw verify`) requires zero missed lines/branches per class — every new class needs coverage; don't use `@ExcludeFromGeneratedCodeCoverage` for code that is reasonably testable.
- Follow the existing package convention: `shared.twitch.infrastructure` is a flat package (no `primary`/`secondary` split) for classes that talk directly to `TwitchChatFacade`; `wire.event` mirrors the existing `wire.security` layout for classes (`infrastructure.primary`), but without a `package-info.java` — see Task 4 Step 3 for why that annotation would break here.
- Test classes ending in `Test` must be annotated `@UnitTest` or `@ComponentTest` (enforced by `AnnotationArchTest`); classes ending in `IT` must be `@IntegrationTest`.
- Every top-level class residing under `fr.craft.chatbot..` must implement both `equals`/`hashCode` or neither (enforced by `EqualsHashcodeArchTest`) — records get both for free; plain classes here override neither, matching the existing listener/config classes.

---

### Task 1: `ChatMessageReceivedEvent` + `ChatMessageEventPublisher` (shared.twitch)

**Files:**

- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageReceivedEvent.java`
- Create: `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisher.java`
- Test: `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisherTest.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.shared.twitch.domain.ChatMessage` (existing record, single component `content()`); `fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade` (existing interface, `void onChatMessage(Consumer<ChatMessage> listener)`).
- Produces: `fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent` — public record, single component `ChatMessage chatMessage()`. Consumed by Task 2 and Task 3.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisherTest.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import static org.mockito.Mockito.verify;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ChatMessageEventPublisherTest {

  @Mock
  private TwitchChatFacade twitchChatFacade;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Test
  void shouldRepublishReceivedChatMessagesAsChatMessageReceivedEvents() {
    new ChatMessageEventPublisher(twitchChatFacade, eventPublisher).subscribeToChatMessages();

    var listener = captureRegisteredListener();
    var message = new ChatMessage("!projet");

    listener.accept(message);

    verify(eventPublisher).publishEvent(new ChatMessageReceivedEvent(message));
  }

  @SuppressWarnings("unchecked")
  private Consumer<ChatMessage> captureRegisteredListener() {
    var captor = ArgumentCaptor.forClass(Consumer.class);

    verify(twitchChatFacade).onChatMessage(captor.capture());

    return captor.getValue();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ChatMessageEventPublisherTest`
Expected: FAIL — compile error, `ChatMessageEventPublisher` and `ChatMessageReceivedEvent` do not exist yet.

- [ ] **Step 3: Create `ChatMessageReceivedEvent`**

Create `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageReceivedEvent.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;

public record ChatMessageReceivedEvent(ChatMessage chatMessage) {}
```

- [ ] **Step 4: Create `ChatMessageEventPublisher`**

Create `src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisher.java`:

```java
package fr.craft.chatbot.shared.twitch.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class ChatMessageEventPublisher {

  private final TwitchChatFacade twitchChatFacade;
  private final ApplicationEventPublisher eventPublisher;

  ChatMessageEventPublisher(TwitchChatFacade twitchChatFacade, ApplicationEventPublisher eventPublisher) {
    this.twitchChatFacade = twitchChatFacade;
    this.eventPublisher = eventPublisher;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatFacade.onChatMessage(message -> eventPublisher.publishEvent(new ChatMessageReceivedEvent(message)));
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=ChatMessageEventPublisherTest`
Expected: PASS

- [ ] **Step 6: Run the full unit test suite to check for regressions**

Run: `./mvnw test`
Expected: PASS (existing `command`/`search` listeners still subscribe directly to `TwitchChatFacade` at this point — both mechanisms coexist harmlessly since nothing consumes `ChatMessageReceivedEvent` yet).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageReceivedEvent.java src/main/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisher.java src/test/java/fr/craft/chatbot/shared/twitch/infrastructure/ChatMessageEventPublisherTest.java
git commit -m "Add ChatMessageEventPublisher to republish Twitch chat messages as a Spring event"
```

---

### Task 2: Switch `TwitchChatMessageListener` (command) to `@EventListener`

**Files:**

- Modify: `src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java`
- Modify: `src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListenerTest.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent` (from Task 1); `fr.craft.chatbot.command.application.HandleChatMessageService.handle(ChatMessage)` (existing, unchanged).
- Produces: nothing new consumed by later tasks.

- [ ] **Step 1: Rewrite the test to describe the new event-driven shape (this makes it fail against the current code)**

Replace `src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListenerTest.java` with:

```java
package fr.craft.chatbot.command.infrastructure.primary;

import static org.mockito.Mockito.verify;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.application.HandleChatMessageService;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TwitchChatMessageListenerTest {

  @Mock
  private HandleChatMessageService handleChatMessageService;

  @Test
  void shouldForwardReceivedChatMessagesToTheHandleChatMessageService() {
    var message = new ChatMessage("!projet");

    new TwitchChatMessageListener(handleChatMessageService).onChatMessageReceived(new ChatMessageReceivedEvent(message));

    verify(handleChatMessageService).handle(message);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TwitchChatMessageListenerTest`
Expected: FAIL — compile error, `TwitchChatMessageListener` still requires a `TwitchChatFacade` constructor argument and has no `onChatMessageReceived` method.

- [ ] **Step 3: Update the production class**

Replace `src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java` with:

```java
package fr.craft.chatbot.command.infrastructure.primary;

import fr.craft.chatbot.command.application.HandleChatMessageService;
import fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TwitchChatMessageListener {

  private final HandleChatMessageService handleChatMessageService;

  TwitchChatMessageListener(HandleChatMessageService handleChatMessageService) {
    this.handleChatMessageService = handleChatMessageService;
  }

  @EventListener
  void onChatMessageReceived(ChatMessageReceivedEvent event) {
    handleChatMessageService.handle(event.chatMessage());
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=TwitchChatMessageListenerTest`
Expected: PASS

- [ ] **Step 5: Run the command IT to confirm no regression**

Run: `./mvnw integration-test -Dit.test=TwitchChatCommandIT`
Expected: PASS — `TwitchChatCommandIT` boots the full context, and `receiveMessage(...)` still triggers the reply synchronously (facade → `ChatMessageEventPublisher` → event → `TwitchChatMessageListener` → `HandleChatMessageService`). (Deliberately `integration-test`, not `verify`: `-Dit.test` restricts which IT classes Failsafe runs, and `verify`'s JaCoCo coverage gate would then see other ITs' classes as unexercised in this one run and fail for a reason unrelated to this change — the full-coverage `verify` run happens once, at the end, in Task 5.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListener.java src/test/java/fr/craft/chatbot/command/infrastructure/primary/TwitchChatMessageListenerTest.java
git commit -m "Switch TwitchChatMessageListener to react to ChatMessageReceivedEvent"
```

---

### Task 3: Switch `SearchChatMessageListener` (search) to `@EventListener`

**Files:**

- Modify: `src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java`

**Interfaces:**

- Consumes: `fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent` (from Task 1); `fr.craft.chatbot.search.application.HandleSearchMessageService.handle(ChatMessage)` (existing, unchanged).
- Produces: nothing new consumed by later tasks.

There is no pre-existing unit test for this class (its only coverage today comes from `SearchChatCommandIT`) — this task is a like-for-like refactor under that existing test's safety net, mirroring Task 2's change exactly, rather than a new test-first cycle.

- [ ] **Step 1: Update the production class**

Replace `src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java` with:

```java
package fr.craft.chatbot.search.infrastructure.primary;

import fr.craft.chatbot.search.application.HandleSearchMessageService;
import fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SearchChatMessageListener {

  private final HandleSearchMessageService handleSearchMessageService;

  SearchChatMessageListener(HandleSearchMessageService handleSearchMessageService) {
    this.handleSearchMessageService = handleSearchMessageService;
  }

  @EventListener
  void onChatMessageReceived(ChatMessageReceivedEvent event) {
    handleSearchMessageService.handle(event.chatMessage());
  }
}
```

- [ ] **Step 2: Run the search IT to confirm no regression**

Run: `./mvnw integration-test -Dit.test=SearchChatCommandIT`
Expected: PASS — `SearchChatCommandIT` boots the full context, and `receiveMessage(...)` still triggers the reply synchronously through the new event path. (`integration-test`, not `verify` — same reasoning as Task 2 Step 5: avoid a false coverage-gate failure from other ITs not running in this restricted invocation.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/fr/craft/chatbot/search/infrastructure/primary/SearchChatMessageListener.java
git commit -m "Switch SearchChatMessageListener to react to ChatMessageReceivedEvent"
```

---

### Task 4: Error-isolating event multicaster (`wire.event`)

**Files:**

- Create: `src/main/java/fr/craft/chatbot/wire/event/infrastructure/primary/LoggingErrorHandler.java`
- Create: `src/main/java/fr/craft/chatbot/wire/event/infrastructure/primary/EventMulticasterConfiguration.java`
- Test: `src/test/java/fr/craft/chatbot/wire/event/infrastructure/primary/LoggingErrorHandlerTest.java`

**Interfaces:**

- Consumes: nothing from earlier tasks (independent wiring on top of Spring's own event bus).
- Produces: a bean named `applicationEventMulticaster` overriding Spring's default — nothing else in this plan references it by name; every existing `*IT` test picks it up implicitly by booting the full Spring context.

- [ ] **Step 1: Write the failing test for the error handler**

Create `src/test/java/fr/craft/chatbot/wire/event/infrastructure/primary/LoggingErrorHandlerTest.java`:

```java
package fr.craft.chatbot.wire.event.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThatCode;

import fr.craft.chatbot.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LoggingErrorHandlerTest {

  @Test
  void shouldLogTheErrorWithoutRethrowingIt() {
    assertThatCode(() -> new LoggingErrorHandler().handleError(new RuntimeException("boom"))).doesNotThrowAnyException();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=LoggingErrorHandlerTest`
Expected: FAIL — compile error, `LoggingErrorHandler` does not exist yet.

- [ ] **Step 3: No package-info needed**

Unlike `wire.security`, `wire.event` does **not** get a `package-info.java` with `@BusinessContext`. That annotation makes `HexagonalArchTest`'s `Wire.shouldNotDependOnBoundedContexts` rule treat the annotated package as a "business context", and then flags any class in `wire..` depending on it — including classes depending on siblings _inside that same package_. `wire.security`'s two classes (`CorsProperties`, `CorsFilterConfiguration`) never trip this because they only share a framework type (`CorsConfiguration`), not a direct reference to each other; `EventMulticasterConfiguration` does directly construct `LoggingErrorHandler`, so annotating `wire.event` this way causes a self-referential ArchUnit failure. `wire.event` isn't a bounded context anyway, so it doesn't need the annotation — the `Wire`-specific ArchUnit rules match on the literal `fr.craft.chatbot.wire..` package path, not on this annotation.

- [ ] **Step 4: Create `LoggingErrorHandler`**

Create `src/main/java/fr/craft/chatbot/wire/event/infrastructure/primary/LoggingErrorHandler.java`:

```java
package fr.craft.chatbot.wire.event.infrastructure.primary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ErrorHandler;

class LoggingErrorHandler implements ErrorHandler {

  private static final Logger log = LoggerFactory.getLogger(LoggingErrorHandler.class);

  @Override
  public void handleError(Throwable throwable) {
    log.error("Error while handling an application event", throwable);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=LoggingErrorHandlerTest`
Expected: PASS

- [ ] **Step 6: Create `EventMulticasterConfiguration`**

Create `src/main/java/fr/craft/chatbot/wire/event/infrastructure/primary/EventMulticasterConfiguration.java`:

```java
package fr.craft.chatbot.wire.event.infrastructure.primary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

@Configuration
class EventMulticasterConfiguration {

  @Bean(name = "applicationEventMulticaster")
  ApplicationEventMulticaster applicationEventMulticaster() {
    var multicaster = new SimpleApplicationEventMulticaster();
    multicaster.setErrorHandler(new LoggingErrorHandler());
    return multicaster;
  }
}
```

`"applicationEventMulticaster"` is Spring's reserved bean name for overriding the default multicaster (`ConfigurableApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME`) — any bean registered under that exact name replaces the framework default automatically, no further wiring needed. This bean method itself doesn't need a dedicated test: every existing `*IT` test already boots the full Spring context (`ChatbotApp` component-scans `fr.craft.chatbot..`), so its three straight-line statements get executed — and therefore covered — by tests that already exist.

- [ ] **Step 7: Run the full test suite (unit + IT + coverage gate)**

Run: `./mvnw verify`
Expected: PASS — all unit tests, `TwitchChatCommandIT`, `SearchChatCommandIT`, `CorsFilterConfigurationIT`, `HexagonalArchTest`, `EqualsHashcodeArchTest`, `AnnotationArchTest`, checkstyle, and the JaCoCo coverage gate all succeed.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/fr/craft/chatbot/wire/event/infrastructure/primary/LoggingErrorHandler.java src/main/java/fr/craft/chatbot/wire/event/infrastructure/primary/EventMulticasterConfiguration.java src/test/java/fr/craft/chatbot/wire/event/infrastructure/primary/LoggingErrorHandlerTest.java
git commit -m "Isolate chat event listeners from each other's failures via a custom ApplicationEventMulticaster"
```

---

### Task 5: Final verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full build**

Run: `./mvnw verify`
Expected: PASS (same as Task 4 Step 7 — re-run here as the final gate after all tasks, in case anything drifted).

- [ ] **Step 2: Run formatting check**

Run: `pnpm prettier:check`
Expected: PASS. If it fails, run `pnpm prettier:format`, review the diff, and commit separately.

- [ ] **Step 3: Manually confirm the behavior end to end (optional but recommended)**

Run the app locally with `TWITCH_CHANNEL`, `TWITCH_BOT_USERNAME`, `TWITCH_OAUTH_TOKEN` set, send `!projet` and `?wp java` in the configured channel's chat, and confirm both still get a reply — this exercises the real `Twitch4jChatFacade` → `ChatMessageEventPublisher` → event → both listeners path end to end, which no automated test covers (the ITs use a recording fake facade, not the real Twitch4j one).
