package fr.craft.chatbot.shared.twitch.domain;

public interface ChatMessagePublisher {
  void send(String message);
}
