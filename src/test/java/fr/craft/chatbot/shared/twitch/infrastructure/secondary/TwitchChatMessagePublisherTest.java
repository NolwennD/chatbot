package fr.craft.chatbot.shared.twitch.infrastructure.secondary;

import static org.mockito.Mockito.verify;

import com.github.twitch4j.chat.ITwitchChat;
import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TwitchChatMessagePublisherTest {

  @Mock
  private ITwitchChat twitchChat;

  @Test
  void shouldSendMessagesToTheConfiguredChannel() {
    var properties = new TwitchProperties();
    properties.setChannel("mychannel");

    new TwitchChatMessagePublisher(twitchChat, properties).send("Un chatbot Twitch");

    verify(twitchChat).sendMessage("mychannel", "Un chatbot Twitch");
  }
}
