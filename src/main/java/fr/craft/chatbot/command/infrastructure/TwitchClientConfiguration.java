package fr.craft.chatbot.command.infrastructure;

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
