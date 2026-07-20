package fr.craft.chatbot.shared.twitch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TwitchClientConfigurationTest {

  @Test
  void shouldExposeAnEmptyTwitchPropertiesBeanReadyForBinding() {
    assertThat(new TwitchClientConfiguration().twitchProperties()).isNotNull();
  }
}
