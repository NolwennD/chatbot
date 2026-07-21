# Split TwitchChatFacade along primary/secondary responsibilities

## Context and goal

`shared.twitch.infrastructure.TwitchChatFacade` (and its implementation
`Twitch4jChatFacade`) currently mix two unrelated responsibilities in one
interface: `onChatMessage(Consumer<ChatMessage>)`, a driving/primary
concern (only `ChatMessageEventPublisher` subscribes to it), and
`sendMessage(String)`, a driven/secondary concern (only
`TwitchChatMessagePublisher`, in `infrastructure.secondary`, calls it).
Both classes sit in a "bare" package — neither `.primary` nor
`.secondary` — because that was the only way to avoid the mixed
responsibility being flagged by the project's hexagonal layering.

Goal: split the facade into two single-responsibility pieces, placed in
`infrastructure.primary` and `infrastructure.secondary` respectively, and
collapse the secondary side's redundant indirection now that it has
exactly one caller.

## Why the receiving side keeps an interface, but the sending side doesn't

Both sides now have exactly one caller, which raises the question of
whether to collapse each all the way down to a single twitch4j-backed
class. The two sides differ:

- **Sending**: `TwitchChatSender.sendMessage(String)` and the domain port
  `ChatMessagePublisher.send(String)` have the exact same shape, and the
  adapter between them (`TwitchChatMessagePublisher`) does no translation
  — pure passthrough. Collapsing them (the twitch4j-backed class directly
  implements `ChatMessagePublisher`) removes a file and an indirection for
  zero cost: the domain port itself remains the test seam, and faking a
  domain port directly in ITs is exactly the pattern CLAUDE.md's TDD
  section recommends.
- **Receiving**: there is no domain-port equivalent to fake on this side.
  Today's IT fixture (`RecordingTwitchChatFacade.receiveMessage(...)`)
  fakes the infrastructure-level `TwitchChatFacade` specifically so the
  translation logic in `ChatMessageEventPublisher` (chat message → Spring
  event) still runs for real during the IT, with only the twitch4j-facing
  boundary faked. Collapsing this side into one class would mean ITs have
  to bypass it entirely (publish `ChatMessageReceivedEvent` directly via
  `ApplicationEventPublisher`), which works fine as a _simulation_ tool
  but leaves the twitch4j-event-to-`ChatMessage` translation logic
  uncovered by any automated test. So `TwitchChatReceiver` stays a
  separate interface, keeping that translation on the fakeable side of the
  boundary.

## Components

### `shared.twitch.infrastructure.primary` (new package)

- `TwitchChatReceiver` — public interface, single method
  `void onChatMessage(Consumer<ChatMessage> listener)`.
- `Twitch4jChatReceiver` — `@Component`, `@Profile("!test")`,
  package-private. Implements `TwitchChatReceiver` using an injected
  `ITwitchChat`:

  ```java
  @Override
  public void onChatMessage(Consumer<ChatMessage> listener) {
    twitchChat.getEventManager().onEvent(ChannelMessageEvent.class, event -> listener.accept(new ChatMessage(event.getMessage())));
  }
  ```

- `ChatMessageEventPublisher` and `ChatMessageReceivedEvent` move here
  from the bare `shared.twitch.infrastructure` package (unchanged
  otherwise), now depending on `TwitchChatReceiver` instead of
  `TwitchChatFacade`.

### `shared.twitch.infrastructure.secondary` (existing package)

- `TwitchChatMessagePublisher` — same file, same name, rewritten to
  implement `ChatMessagePublisher` (the domain port) directly instead of
  delegating to `TwitchChatFacade`. Gains `@Profile("!test")`:

  ```java
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

### `shared.twitch.infrastructure` (bare package, narrowed)

- `TwitchClientConfiguration` shrinks to producing only `TwitchProperties`
  and `ITwitchChat` — the one bean that opens the real connection stays
  `@ExcludeFromGeneratedCodeCoverage`:

  ```java
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

  `Twitch4jChatReceiver` and `TwitchChatMessagePublisher` pick up
  `ITwitchChat` via ordinary constructor injection (`@Component`
  autowiring) rather than being built manually inside a `@Bean` method —
  this means `TwitchClientConfiguration` never references any
  `.primary`/`.secondary` class, sidestepping any question of whether a
  bare-package class may depend into those layers.

### Deleted

- `shared.twitch.infrastructure.TwitchChatFacade` (interface)
- `shared.twitch.infrastructure.Twitch4jChatFacade` (class)

## Data flow

```
ITwitchChat (twitch4j, single bean from TwitchClientConfiguration)
        |                                    |
        v (receiving)                        v (sending)
Twitch4jChatReceiver (.primary)      TwitchChatMessagePublisher (.secondary)
   implements TwitchChatReceiver        implements ChatMessagePublisher
        |                                    ^
        v                                    |
ChatMessageEventPublisher (.primary)         |
   publishEvent(ChatMessageReceivedEvent)    |
        |                                    |
        v                                    |
Custom ApplicationEventMulticaster (wire)    |
        |              |                     |
        v              v                     |
TwitchChatMessageListener   SearchChatMessageListener
        |                          |
        v                          v
HandleChatMessageService     HandleSearchMessageService
        `--------------+-----------'
                        v
              chatMessagePublisher.send(...) -----------------'
```

## Tests

- **`TwitchTestClientConfiguration`** (`@Profile("test")` default beans):
  splits its single no-op `TwitchChatFacade` into two no-op beans —
  `TwitchChatReceiver` and `ChatMessagePublisher`.
- **`TwitchChatCommandIT` / `SearchChatCommandIT`**: their nested
  `RecordingTwitchChatFacade` splits into `FakeTwitchChatReceiver
implements TwitchChatReceiver` (drives scenarios via `receiveMessage`,
  same as today) and `RecordingChatMessagePublisher implements
ChatMessagePublisher` (writes the reply file, same as today). Both
  registered `@Primary` from the same nested `@TestConfiguration` as
  before — mechanical split, no new test logic.
- **`Twitch4jChatFacadeTest` deleted**, replaced by:
  - `Twitch4jChatReceiverTest` (`.primary`) — carries over the existing
    "maps twitch4j events to `ChatMessage`" test, mocking `EventManager`/
    `ChannelMessageEvent` as today.
  - `TwitchChatMessagePublisherTest` (`.secondary`, **new** — this class
    never had a dedicated unit test before) — carries over the existing
    "sends to the configured channel" test, mocking `ITwitchChat`.
- **`TwitchClientConfigurationTest`**: unchanged. `twitchChat(...)` stays
  `@ExcludeFromGeneratedCodeCoverage`, nothing new to cover there.
- Rationale for the two new unit tests: since the ITs now fake
  `TwitchChatReceiver`/`ChatMessagePublisher` directly, the real
  twitch4j-backed classes (`@Profile("!test")`) are never constructed
  during any test — coverage that used to come from the IT chain (for
  sending — this is new coverage, the class never had it) now has to come
  from dedicated mocked unit tests, matching the pattern
  `Twitch4jChatFacadeTest` already used for the receiving side today.
- Strict JaCoCo coverage to respect as everywhere else in the project (see
  CLAUDE.md: zero missed line/branch per class).
- Open verification point for the implementation plan: confirm via
  `HexagonalArchTest` that a bare-package class in `shared.twitch`
  depending into `.primary`/`.secondary` is actually restricted by
  ArchUnit's layered-architecture check — this design avoids the question
  entirely (see `TwitchClientConfiguration` above), so it's not a
  blocker, just worth confirming empirically rather than assuming.
