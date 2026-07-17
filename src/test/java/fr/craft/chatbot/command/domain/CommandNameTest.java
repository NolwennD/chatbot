package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class CommandNameTest {

  @Test
  void shouldBuildACommandName() {
    var name = new CommandName("!projet");

    assertThat(name.value()).isEqualTo("!projet");
  }

  @Test
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new CommandName(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }

  @Test
  void shouldRejectAValueNotStartingWithTheTriggerPrefix() {
    assertThatThrownBy(() -> new CommandName("projet")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldExtractTheCommandNameFromChatMessageContent() {
    assertThat(CommandName.fromChatMessageContent("!projet")).contains(new CommandName("!projet"));
  }

  @Test
  void shouldExtractTheCommandNameIgnoringExtraWords() {
    assertThat(CommandName.fromChatMessageContent("!projet please")).contains(new CommandName("!projet"));
  }

  @Test
  void shouldReturnEmptyWhenTheContentIsNotACommand() {
    assertThat(CommandName.fromChatMessageContent("hello everyone")).isEmpty();
  }
}
