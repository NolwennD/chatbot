package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import fr.craft.chatbot.shared.text.domain.ChatbotStrings;

public record SearchResponse(String value) {
  public SearchResponse(String value) {
    Assert.field("value", value).notNull();
    this.value = ChatbotStrings.stripSurroundingSpaces(value);
  }
}
