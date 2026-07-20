package fr.craft.chatbot.shared.twitch.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class ChatMessageTest {

  @Test
  void shouldRejectABlankContent() {
    assertThatThrownBy(() -> new ChatMessage(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }
}
