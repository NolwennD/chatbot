package fr.craft.chatbot.command.domain;

import java.util.List;

public sealed interface CommandOutcome {
  CommandResponse response(CommandResponseTranslator translator);

  record CommandFound(CommandResponse response) implements CommandOutcome {
    @Override
    public CommandResponse response(CommandResponseTranslator translator) {
      return response;
    }
  }

  record UnknownCommand(List<CommandName> values) implements CommandOutcome {
    @Override
    public CommandResponse response(CommandResponseTranslator translator) {
      return translator.unknownCommand(this);
    }
  }

  record NoCommandsAvailable() implements CommandOutcome {
    @Override
    public CommandResponse response(CommandResponseTranslator translator) {
      return translator.noCommandsAvailable();
    }
  }
}
