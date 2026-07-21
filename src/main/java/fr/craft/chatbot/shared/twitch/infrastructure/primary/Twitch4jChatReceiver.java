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
