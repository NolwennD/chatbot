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
