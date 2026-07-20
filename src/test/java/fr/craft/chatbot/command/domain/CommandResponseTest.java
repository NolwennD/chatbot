package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class CommandResponseTest {

  @Test
  void shouldBuildACommandResponse() {
    var response = new CommandResponse(" Un chatbot Twitch ");

    assertThat(response.value()).isEqualTo("Un chatbot Twitch");
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new CommandResponse(null)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
