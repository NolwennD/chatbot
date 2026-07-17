package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class CommandResponseTest {

  @Test
  void shouldBuildACommandResponse() {
    var response = new CommandResponse("Un chatbot Twitch");

    assertThat(response.value()).isEqualTo("Un chatbot Twitch");
  }

  @Test
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new CommandResponse(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }

  @Test
  void shouldListKnownCommandsWhenSomeExist() {
    var knownCommands = List.of(new CommandName("!projet"), new CommandName("!discord"));

    assertThat(CommandResponse.fromCommands(knownCommands)).isEqualTo(
      new CommandResponse("Commande inconnue. Commandes disponibles : !projet, !discord")
    );
  }

  @Test
  void shouldSayThereAreNoCommandsWhenNoneExist() {
    assertThat(CommandResponse.fromCommands(List.of())).isEqualTo(new CommandResponse("Aucune commande n'est disponible pour le moment."));
  }
}
