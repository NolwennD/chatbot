package fr.craft.chatbot.command.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import java.util.Optional;
import java.util.function.Function;

public record CommandName(String value) {
  private static final CommandName LIST_COMMANDS = new CommandName("!commands");

  private static final String TRIGGER_PREFIX = "!";

  public CommandName {
    Assert.field("value", value).notBlank();

    if (!isTrigger(value)) {
      throw new IllegalArgumentException("A command name must start with '%s': %s".formatted(TRIGGER_PREFIX, value));
    }
  }

  public static Optional<CommandName> parse(String content) {
    return Optional.ofNullable(content).map(keepFirstWord()).filter(CommandName::isTrigger).map(CommandName::new);
  }

  private static Function<String, String> keepFirstWord() {
    return c -> c.trim().split("\\s+", 2)[0];
  }

  private static boolean isTrigger(String value) {
    return value.startsWith(TRIGGER_PREFIX);
  }

  public boolean isListCommands() {
    return LIST_COMMANDS.equals(this);
  }
}
