package fr.craft.chatbot.shared.twitch.infrastructure;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;

public interface TwitchChatFacade {
  void onChatMessage(Consumer<ChatMessage> listener);

  void sendMessage(String message);
}
