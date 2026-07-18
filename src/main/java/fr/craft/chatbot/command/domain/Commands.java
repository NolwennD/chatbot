package fr.craft.chatbot.command.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Commands {

  private final Map<CommandName, CommandResponse> commands;

  public Commands(Map<CommandName, CommandResponse> commands) {
    this.commands = Map.copyOf(commands);
  }

  public CommandOutcome outcomeFor(CommandName askedCommand) {
    if (commands.isEmpty()) {
      return new CommandOutcome.NoCommandsAvailable();
    }
    if (askedCommand.isListCommands()) {
      return new CommandOutcome.CommandsListed(knownCommands());
    }

    return Optional.ofNullable(commands.get(askedCommand))
      .<CommandOutcome>map(CommandOutcome.CommandFound::new)
      .orElseGet(() -> new CommandOutcome.UnknownCommand(knownCommands()));
  }

  private List<CommandName> knownCommands() {
    return commands.keySet().stream().sorted(Comparator.comparing(CommandName::value)).toList();
  }
}
