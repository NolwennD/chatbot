package fr.craft.chatbot.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandResponse;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@UnitTest
class FileCommandRepositoryTest {

  @TempDir
  private Path directory;

  @Test
  void shouldFindAKnownCommand() throws URISyntaxException {
    var repository = new FileCommandRepository(commandsResourceFile("command/commands.txt").toString());

    assertThat(repository.find(new CommandName("!projet"))).contains(
      new CommandResponse("Un chatbot Twitch qui répond aux commandes du chat")
    );
    assertThat(repository.find(new CommandName("!discord"))).contains(
      new CommandResponse("Rejoins le Discord ici : https://discord.gg/exemple")
    );
  }

  @Test
  void shouldReturnEmptyWhenTheCommandIsUnknown() throws URISyntaxException {
    var repository = new FileCommandRepository(commandsResourceFile("command/commands.txt").toString());

    assertThat(repository.find(new CommandName("!doesnotexist"))).isEmpty();
  }

  @Test
  void shouldFailWhenTheCommandsFileDoesNotExist() {
    var repository = new FileCommandRepository(directory.resolve("missing.txt").toString());

    assertThatThrownBy(() -> repository.find(new CommandName("!projet"))).isInstanceOf(UncheckedIOException.class);
  }

  private Path commandsResourceFile(String name) throws URISyntaxException {
    return Path.of(getClass().getClassLoader().getResource(name).toURI());
  }
}
