package fr.craft.chatbot.command.infrastructure;

import fr.craft.chatbot.command.domain.ChatMessage;
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
