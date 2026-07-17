package fr.craft.chatbot.command.infrastructure;

import fr.craft.chatbot.command.domain.ChatMessage;
import java.util.function.Consumer;

public interface TwitchChatFacade {
  void onChatMessage(Consumer<ChatMessage> listener);

  void sendMessage(String message);
}
