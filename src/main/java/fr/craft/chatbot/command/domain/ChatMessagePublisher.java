package fr.craft.chatbot.command.domain;

public interface ChatMessagePublisher {
  void send(String message);
}
