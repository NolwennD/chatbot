package fr.craft.chatbot.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandOutcome;
import fr.craft.chatbot.command.domain.CommandResponse;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

@UnitTest
class SpringCommandResponseTranslatorTest {

  private final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

  {
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
  }

  private final SpringCommandResponseTranslator translator = new SpringCommandResponseTranslator(messageSource, Locale.FRENCH);
  private final SpringCommandResponseTranslator englishTranslator = new SpringCommandResponseTranslator(messageSource, Locale.ENGLISH);

  @Test
  void shouldListKnownCommandsWhenTheCommandIsUnknown() {
    var knownCommands = List.of(new CommandName("!projet"), new CommandName("!discord"));

    assertThat(translator.translate(new CommandOutcome.UnknownCommand(knownCommands))).isEqualTo(
      new CommandResponse("Commande inconnue. Commandes disponibles : !projet, !discord")
    );
  }

  @Test
  void shouldSayThereAreNoCommandsWhenNoneExist() {
    assertThat(translator.translate(new CommandOutcome.NoCommandsAvailable())).isEqualTo(
      new CommandResponse("Aucune commande n'est disponible pour le moment.")
    );
  }

  @Test
  void shouldListKnownCommandsInEnglishWhenTheCommandIsUnknown() {
    var knownCommands = List.of(new CommandName("!projet"), new CommandName("!discord"));

    assertThat(englishTranslator.translate(new CommandOutcome.UnknownCommand(knownCommands))).isEqualTo(
      new CommandResponse("Unknown command. Available commands: !projet, !discord")
    );
  }

  @Test
  void shouldSayThereAreNoCommandsInEnglishWhenNoneExist() {
    assertThat(englishTranslator.translate(new CommandOutcome.NoCommandsAvailable())).isEqualTo(
      new CommandResponse("No commands are available at the moment.")
    );
  }
}
