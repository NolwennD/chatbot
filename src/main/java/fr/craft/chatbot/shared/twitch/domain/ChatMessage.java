package fr.craft.chatbot.shared.twitch.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record ChatMessage(String content) {
  public ChatMessage {
    Assert.field("content", content).notBlank();
  }
}
