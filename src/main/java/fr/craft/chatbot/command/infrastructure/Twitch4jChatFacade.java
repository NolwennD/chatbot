package fr.craft.chatbot.command.infrastructure;

import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import fr.craft.chatbot.command.domain.ChatMessage;
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
