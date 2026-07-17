package fr.craft.chatbot.command.domain;

import java.util.List;

public sealed interface CommandOutcome {
  record CommandFound(CommandResponse response) implements CommandOutcome {}

  record UnknownCommand(List<CommandName> values) implements CommandOutcome {}

  record CommandsListed(List<CommandName> values) implements CommandOutcome {}

  record NoCommandsAvailable() implements CommandOutcome {}
}
