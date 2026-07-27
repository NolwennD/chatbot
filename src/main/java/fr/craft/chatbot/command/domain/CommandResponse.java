package fr.craft.chatbot.command.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import fr.craft.chatbot.shared.text.domain.ChatbotStrings;

public record CommandResponse(String value) {
  public CommandResponse(String value) {
    Assert.field("value", value).notNull();
    this.value = ChatbotStrings.stripSurroundingSpaces(value);
  }
}
