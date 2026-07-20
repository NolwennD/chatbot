package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record SearchResponse(String value) {
  public SearchResponse(String value) {
    Assert.field("value", value).notNull();
    this.value = value.trim();
  }
}
