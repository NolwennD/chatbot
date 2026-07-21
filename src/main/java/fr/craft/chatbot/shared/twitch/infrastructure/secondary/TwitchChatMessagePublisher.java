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
