package fr.craft.chatbot.command.infrastructure.secondary;

import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandRepository;
import fr.craft.chatbot.command.domain.CommandResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
class FileCommandRepository implements CommandRepository {

  private final Path commandsFile;

  FileCommandRepository(@Value("${chatbot.commands.file}") String commandsFile) {
    this.commandsFile = Path.of(commandsFile);
  }

  @Override
  public Optional<CommandResponse> find(CommandName name) {
    return Optional.ofNullable(readCommands().get(name.value())).map(CommandResponse::new);
  }

  private Map<String, String> readCommands() {
    try {
      return Files.readAllLines(commandsFile)
        .stream()
        .map(String::trim)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .map(line -> line.split("=", 2))
        .filter(parts -> parts.length == 2)
        .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1].trim()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
