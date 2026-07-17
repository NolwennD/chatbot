package fr.craft.chatbot.command.domain;

import fr.craft.chatbot.shared.error.domain.Assert;

public record CommandResponse(String value) {
  public CommandResponse {
    Assert.field("value", value).notBlank();
  }
}
