# Central chat message dispatch via Spring events

## Context and goal

Today, each bounded context that reacts to incoming Twitch chat messages
(`command`, `search`) has its own primary adapter
(`TwitchChatMessageListener`, `SearchChatMessageListener`) that
independently injects `shared.twitch.infrastructure.TwitchChatFacade` and
subscribes to it via `@PostConstruct`. `TwitchChatFacade.onChatMessage`
already fans out correctly to any number of subscribers (each call
registers its own handler), so adding a third context today would not
break anything technically — but it means repeating the same
facade-subscription boilerplate in every context.

Goal: introduce a single place that subscribes to `TwitchChatFacade` and
republishes each `ChatMessage` as a Spring application event, so that each
context reacts to that event instead of subscribing to the facade
directly. A future third (or Nth) context then only adds its own
`@EventListener`, with no change to shared/wire code.

Outbound message sending is explicitly **out of scope**: it's already
centralized today (`shared.twitch.domain.ChatMessagePublisher` port, single
`TwitchChatMessagePublisher` implementation) — every context already calls
through that one port, there's no equivalent duplication to remove there.

## Components

### `shared.twitch.infrastructure` (existing shared kernel, new classes)

- `ChatMessageReceivedEvent` — plain POJO wrapping a `ChatMessage` (no need
  to extend `ApplicationEvent`, Spring has supported arbitrary event
  objects since 4.2).
- `ChatMessageEventPublisher` — `@Component`, package-private. Injects
  `TwitchChatFacade` and `ApplicationEventPublisher`; on `@PostConstruct`,
  subscribes once via `twitchChatFacade.onChatMessage(...)` and republishes
  every message received as a `ChatMessageReceivedEvent`. This is now the
  _only_ caller of `TwitchChatFacade.onChatMessage` in the codebase.

### `command.infrastructure.primary.TwitchChatMessageListener` and `search.infrastructure.primary.SearchChatMessageListener` (modified)

No longer inject `TwitchChatFacade` or use `@PostConstruct`. Instead, each
gets an `@EventListener` method reacting to `ChatMessageReceivedEvent` that
delegates to its own application service exactly as before:

```java
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

`SearchChatMessageListener` mirrors this exactly, delegating to
`HandleSearchMessageService`. A future third context's listener follows the
same shape.

### `wire` (new package, cross-cutting config — no business-context dependency)

- `EventMulticasterConfiguration` — `@Configuration`, package-private.
  Defines the bean named `applicationEventMulticaster` (Spring's reserved
  name for overriding the default multicaster), using a
  `SimpleApplicationEventMulticaster` configured with a custom
  `ErrorHandler`.
- `LoggingErrorHandler implements ErrorHandler` — its own small
  package-private class (not an inline lambda), so it can be unit tested in
  isolation: `handleError(Throwable)` logs the error via SLF4J and returns
  normally (swallows it).

## Data flow

```
Twitch4jChatFacade (existing)
        |  Twitch4j event -> ChatMessage
        v
ChatMessageEventPublisher (shared.twitch, new)
   subscribes once to twitchChatFacade.onChatMessage
        |  publishEvent(ChatMessageReceivedEvent)
        v
Custom ApplicationEventMulticaster (wire, new - with LoggingErrorHandler)
        |                    |
        v                    v
TwitchChatMessageListener   SearchChatMessageListener   (future 3rd, 4th...)
  @EventListener               @EventListener
        |                          |
        v                          v
HandleChatMessageService     HandleSearchMessageService
        |                          |
        v                          v
        ChatMessagePublisher (existing single port)
                    |
                    v
        TwitchChatMessagePublisher -> TwitchChatFacade.sendMessage
```

## Error isolation

Publishing stays synchronous (same thread, no `@Async`) — no change to the
existing timing behavior. The custom multicaster's `ErrorHandler` means
that if `TwitchChatMessageListener` throws, the exception is logged and
`SearchChatMessageListener` (and any other listener) is still invoked —
unlike Spring's default multicaster, which would stop calling further
listeners once one throws.

Listener invocation order is not guaranteed (depends on bean registration
order), same as today.

## Known limitations (out of scope for this iteration)

- No distributed/async dispatch: everything runs synchronously in the
  thread that received the Twitch event, as today.
- The `ErrorHandler` only logs; it doesn't retry or notify anywhere else.
  If a listener's failures need surfacing beyond logs (metrics, alerting),
  that's a future iteration.

## Tests

- **`ChatMessageEventPublisher`** (shared.twitch): verify that a message
  received from a fake `TwitchChatFacade` republishes a
  `ChatMessageReceivedEvent`, using Spring Test's native
  `@RecordApplicationEvents` + injected `ApplicationEvents` — no custom
  fake needed for the event-capturing side.
- **`TwitchChatMessageListener` / `SearchChatMessageListener`**: existing
  coverage at the application-service level is unaffected; only the
  triggering mechanism (event instead of direct facade subscription)
  changes, which the two existing `*IT` tests already exercise unchanged
  (see below).
- **`LoggingErrorHandler`**: a plain unit test —
  `new LoggingErrorHandler().handleError(new RuntimeException("boom"))` —
  covers the log line without needing to boot a Spring context or wire
  dummy listeners. Chosen over both a heavier "two dummy listeners, one
  throwing" integration test and over `@ExcludeFromGeneratedCodeCoverage`
  (reserved for genuinely untestable code, which this isn't).
- **`TwitchChatCommandIT` / `SearchChatCommandIT`**: unchanged. Both boot
  the full Spring context already, and `RecordingTwitchChatFacade.receiveMessage(...)`
  stays synchronous end-to-end (facade -> event -> listeners -> reply)
  since the multicaster isn't async — assertions immediately after
  `receiveMessage(...)` keep working as-is.
- Strict JaCoCo coverage to respect as everywhere else in the project (see
  CLAUDE.md: zero missed line/branch per class).
