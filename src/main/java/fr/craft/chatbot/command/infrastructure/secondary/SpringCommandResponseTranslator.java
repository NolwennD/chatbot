package fr.craft.chatbot.command.infrastructure.secondary;

import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandOutcome;
import fr.craft.chatbot.command.domain.CommandResponse;
import fr.craft.chatbot.command.domain.CommandResponseTranslator;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Repository;

@Repository
class SpringCommandResponseTranslator implements CommandResponseTranslator {

  private final MessageSource messageSource;
  private final Locale locale;

  SpringCommandResponseTranslator(MessageSource messageSource, @Value("${chatbot.locale}") String locale) {
    this.messageSource = messageSource;
    this.locale = Locale.forLanguageTag(locale);
  }

  @Override
  public CommandResponse unknownCommand(CommandOutcome.UnknownCommand knownCommands) {
    String names = knownCommands.values().stream().map(CommandName::value).collect(Collectors.joining(", "));

    return new CommandResponse(messageSource.getMessage("command.unknown", new Object[] { names }, locale));
  }

  @Override
  public CommandResponse noCommandsAvailable() {
    return new CommandResponse(messageSource.getMessage("command.none", null, locale));
  }
}
