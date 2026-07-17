package fr.craft.chatbot.command.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import java.util.Optional;

public record CommandName(String value) {
  public static final CommandName LIST_COMMANDS = new CommandName("!commands");

  private static final String TRIGGER_PREFIX = "!";

  public CommandName {
    Assert.field("value", value).notBlank();

    if (!value.startsWith(TRIGGER_PREFIX)) {
      throw new IllegalArgumentException("A command name must start with '%s': %s".formatted(TRIGGER_PREFIX, value));
    }
  }

  public static Optional<CommandName> fromChatMessageContent(String content) {
    var firstWord = content.trim().split("\\s+", 2)[0];

    return Optional.of(firstWord)
      .filter(w -> w.startsWith(TRIGGER_PREFIX))
      .map(CommandName::new);
  }
}
