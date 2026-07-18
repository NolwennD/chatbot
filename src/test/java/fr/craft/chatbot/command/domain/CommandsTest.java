package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@UnitTest
class CommandsTest {

  private static final CommandName PROJET = new CommandName("!projet");
  private static final CommandName LIST_COMMANDS = new CommandName("!commands");
  private static final CommandResponse RESPONSE = new CommandResponse("Un chatbot Twitch qui répond aux commandes du chat");

  @Test
  void shouldReturnNoCommandsAvailableWhenThereAreNoKnownCommands() {
    var commands = new Commands(Map.of());

    assertThat(commands.outcomeFor(PROJET)).isEqualTo(new CommandOutcome.NoCommandsAvailable());
  }

  @Test
  void shouldReturnCommandsListedWhenTheAskedCommandListsCommands() {
    var commands = new Commands(Map.of(PROJET, RESPONSE));

    assertThat(commands.outcomeFor(LIST_COMMANDS)).isEqualTo(new CommandOutcome.CommandsListed(List.of(PROJET)));
  }

  @Test
  void shouldReturnCommandFoundWhenTheAskedCommandIsKnown() {
    var commands = new Commands(Map.of(PROJET, RESPONSE));

    assertThat(commands.outcomeFor(PROJET)).isEqualTo(new CommandOutcome.CommandFound(RESPONSE));
  }

  @Test
  void shouldReturnUnknownCommandWhenTheAskedCommandIsNotKnown() {
    var commands = new Commands(Map.of(PROJET, RESPONSE));

    assertThat(commands.outcomeFor(new CommandName("!doesnotexist"))).isEqualTo(new CommandOutcome.UnknownCommand(List.of(PROJET)));
  }
}
