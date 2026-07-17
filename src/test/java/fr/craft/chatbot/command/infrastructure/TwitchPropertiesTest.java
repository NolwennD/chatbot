package fr.craft.chatbot.command.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TwitchPropertiesTest {

  @Test
  void shouldExposeItsConfiguredValues() {
    var properties = new TwitchProperties();

    properties.setChannel("mychannel");
    properties.setBotUsername("mybot");
    properties.setOauthToken("token");

    assertThat(properties.getChannel()).isEqualTo("mychannel");
    assertThat(properties.getBotUsername()).isEqualTo("mybot");
    assertThat(properties.getOauthToken()).isEqualTo("token");
  }
}
