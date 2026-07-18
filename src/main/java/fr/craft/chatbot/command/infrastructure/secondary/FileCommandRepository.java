package fr.craft.chatbot.command.infrastructure.secondary;

import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandRepository;
import fr.craft.chatbot.command.domain.CommandResponse;
import fr.craft.chatbot.command.domain.Commands;
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
  public Commands findAll() {
    return new Commands(readCommands());
  }

  private Map<CommandName, CommandResponse> readCommands() {
    try {
      return Files.readAllLines(commandsFile)
        .stream()
        .map(RawLine::new)
        .map(RawLine::parse)
        .flatMap(Optional::stream)
        .collect(Collectors.toMap(CommandLine::name, CommandLine::value));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private record RawLine(String line) {
    private static final String COMMENT_PREFIX = "#";
    private static final String NAME_VALUE_SEPARATOR = "=";
    private static final int NAME_AND_VALUE = 2;

    RawLine(String line) {
      this.line = line.trim();
    }

    Optional<CommandLine> parse() {
      if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
        return Optional.empty();
      }

      var parts = line.split(NAME_VALUE_SEPARATOR, NAME_AND_VALUE);

      return parts.length == NAME_AND_VALUE
        ? CommandName.parse(parts[0]).map(name -> new CommandLine(name, new CommandResponse(parts[1])))
        : Optional.empty();
    }
  }

  private record CommandLine(CommandName name, CommandResponse value) {}
}
