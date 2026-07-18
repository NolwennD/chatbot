package fr.craft.chatbot.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandOutcome;
import fr.craft.chatbot.command.domain.CommandResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class FileCommandRepositoryTest {

  private static final CommandName PROJET = new CommandName("!projet");
  private static final CommandName DISCORD = new CommandName("!discord");
  private static final CommandName LIST_COMMANDS = new CommandName("!commands");

  @TempDir
  private Path directory;

  @Test
  void shouldFindAKnownCommand() throws URISyntaxException {
    var commands = new FileCommandRepository(commandsResourceFile("command/commands.txt").toString()).findAll();

    assertThat(commands.outcomeFor(PROJET)).isEqualTo(
      new CommandOutcome.CommandFound(new CommandResponse("Un chatbot Twitch qui répond aux commandes du chat"))
    );
    assertThat(commands.outcomeFor(DISCORD)).isEqualTo(
      new CommandOutcome.CommandFound(new CommandResponse("Rejoins le Discord ici : https://discord.gg/exemple"))
    );
  }

  @Test
  void shouldReturnUnknownCommandWhenTheCommandIsUnknown() throws URISyntaxException {
    var commands = new FileCommandRepository(commandsResourceFile("command/commands.txt").toString()).findAll();

    assertThat(commands.outcomeFor(new CommandName("!doesnotexist"))).isEqualTo(
      new CommandOutcome.UnknownCommand(List.of(DISCORD, PROJET))
    );
  }

  @Test
  void shouldFailWhenTheCommandsFileDoesNotExist() {
    var repository = new FileCommandRepository(directory.resolve("missing.txt").toString());

    assertThatThrownBy(repository::findAll).isInstanceOf(UncheckedIOException.class);
  }

  @Test
  void shouldListAllKnownCommandNames() throws URISyntaxException {
    var commands = new FileCommandRepository(commandsResourceFile("command/commands.txt").toString()).findAll();

    assertThat(commands.outcomeFor(LIST_COMMANDS)).isEqualTo(new CommandOutcome.CommandsListed(List.of(DISCORD, PROJET)));
  }

  @Test
  void shouldIgnoreLinesWhoseNameIsNotACommandTrigger() throws IOException {
    var file = directory.resolve("commands.txt");
    Files.writeString(file, "discord=missing the bang\n!projet=Un chatbot Twitch qui répond aux commandes du chat\n");
    var commands = new FileCommandRepository(file.toString()).findAll();

    assertThat(commands.outcomeFor(LIST_COMMANDS)).isEqualTo(new CommandOutcome.CommandsListed(List.of(PROJET)));
  }

  @Test
  void shouldKeepEqualsSignsAfterTheFirstOneInTheResponseText() throws IOException {
    var file = directory.resolve("commands.txt");
    Files.writeString(file, "!bug= bugs = undocumented features\n");
    var commands = new FileCommandRepository(file.toString()).findAll();

    assertThat(commands.outcomeFor(new CommandName("!bug"))).isEqualTo(
      new CommandOutcome.CommandFound(new CommandResponse("bugs = undocumented features"))
    );
  }

  @Test
  void shouldReturnNoCommandsAvailableWhenThereAreNoCommands() throws IOException {
    var emptyFile = directory.resolve("commands.txt");
    Files.writeString(emptyFile, "# no commands yet\n");
    var commands = new FileCommandRepository(emptyFile.toString()).findAll();

    assertThat(commands.outcomeFor(new CommandName("!anything"))).isEqualTo(new CommandOutcome.NoCommandsAvailable());
  }

  private Path commandsResourceFile(String name) throws URISyntaxException {
    return Path.of(getClass().getClassLoader().getResource(name).toURI());
  }
}
