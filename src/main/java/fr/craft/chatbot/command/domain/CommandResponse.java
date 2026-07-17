package fr.craft.chatbot.command.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import java.util.List;
import java.util.stream.Collectors;

public record CommandResponse(String value) {
  public CommandResponse {
    Assert.field("value", value).notBlank();
  }

  public static CommandResponse fromCommands(List<CommandName> knownCommands) {
    if (knownCommands.isEmpty()) {
      return new CommandResponse("Aucune commande n'est disponible pour le moment.");
    }

    String names = knownCommands.stream().map(CommandName::value).collect(Collectors.joining(", "));

    return new CommandResponse("Commande inconnue. Commandes disponibles : " + names);
  }
}
