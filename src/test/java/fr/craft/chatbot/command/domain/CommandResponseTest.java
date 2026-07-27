package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class CommandResponseTest {

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new CommandResponse(null)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
