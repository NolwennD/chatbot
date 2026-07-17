package fr.craft.chatbot.command.domain;

public interface CommandResponseTranslator {
  CommandResponse unknownCommand(CommandOutcome.UnknownCommand knownCommands);

  CommandResponse noCommandsAvailable();
}
