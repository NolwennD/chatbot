package fr.craft.chatbot.command.domain;

import java.util.List;
import java.util.Optional;

public interface CommandRepository {
  Optional<CommandResponse> find(CommandName name);

  List<CommandName> findAll();
}
