package fr.craft.chatbot.command.domain;

import java.util.Optional;

public interface CommandRepository {
  Optional<CommandResponse> find(CommandName name);
}
