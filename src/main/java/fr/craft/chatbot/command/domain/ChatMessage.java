package fr.craft.chatbot.command.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record ChatMessage(String content) {
  public ChatMessage {
    Assert.field("content", content).notBlank();
  }
}
