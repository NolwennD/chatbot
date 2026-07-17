package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.craft.chatbot.UnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CommandOutcomeTest {

  @Mock
  private CommandResponseTranslator translator;

  @Test
  void shouldReturnTheWrappedResponseWhenTheCommandWasFoundWithoutCallingTheTranslator() {
    var response = new CommandResponse("Un chatbot Twitch");

    assertThat(new CommandOutcome.CommandFound(response).response(translator)).isEqualTo(response);
    verifyNoInteractions(translator);
  }

  @Test
  void shouldDelegateToTheTranslatorWhenTheCommandIsUnknown() {
    var knownCommands = List.of(new CommandName("!projet"));
    var response = new CommandResponse("Commande inconnue. Commandes disponibles : !projet");
    when(translator.unknownCommand(new CommandOutcome.UnknownCommand(knownCommands))).thenReturn(response);

    assertThat(new CommandOutcome.UnknownCommand(knownCommands).response(translator)).isEqualTo(response);
  }

  @Test
  void shouldDelegateToTheTranslatorWhenNoCommandsAreAvailable() {
    var response = new CommandResponse("Aucune commande n'est disponible pour le moment.");
    when(translator.noCommandsAvailable()).thenReturn(response);

    assertThat(new CommandOutcome.NoCommandsAvailable().response(translator)).isEqualTo(response);
  }
}
