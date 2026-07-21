package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.function.Consumer;

public interface TwitchChatReceiver {
  void onChatMessage(Consumer<ChatMessage> listener);
}
