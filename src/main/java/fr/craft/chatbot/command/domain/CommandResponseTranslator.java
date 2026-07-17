package fr.craft.chatbot.command.domain;

public interface CommandResponseTranslator {
  CommandResponse translate(CommandOutcome outcome);
}
