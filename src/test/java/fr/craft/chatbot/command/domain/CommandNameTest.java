package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(strings = { "!projet", "!projet please", " !projet", "!projet ", "  !projet  " })
  void shouldExtractTheCommandNameWhenContentIsACommand(String content) {
    assertThat(CommandName.parse(content)).contains(new CommandName("!projet"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = { "", "   ", "hello everyone" })
  void shouldReturnEmptyWhenTheContentIsNotACommand(String content) {
    assertThat(CommandName.parse(content)).isEmpty();
  }
}
