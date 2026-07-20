package fr.craft.chatbot.shared.twitch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class Twitch4jChatFacadeTest {

  @Mock
  private ITwitchChat twitchChat;

  @Mock
  private EventManager eventManager;

  @Test
  void shouldSendMessagesToTheConfiguredChannel() {
    new Twitch4jChatFacade(twitchChat, "mychannel").sendMessage("Un chatbot Twitch");

    verify(twitchChat).sendMessage("mychannel", "Un chatbot Twitch");
  }

  @Test
  void shouldMapIncomingChannelMessageEventsToChatMessages() {
    when(twitchChat.getEventManager()).thenReturn(eventManager);

    var received = new ArrayList<ChatMessage>();
    new Twitch4jChatFacade(twitchChat, "mychannel").onChatMessage(received::add);

    var event = mock(ChannelMessageEvent.class);
    when(event.getMessage()).thenReturn("!projet");

    captureRegisteredEventConsumer().accept(event);

    assertThat(received).containsExactly(new ChatMessage("!projet"));
  }

  @SuppressWarnings("unchecked")
  private Consumer<ChannelMessageEvent> captureRegisteredEventConsumer() {
    var captor = ArgumentCaptor.forClass(Consumer.class);

    verify(eventManager).onEvent(eq(ChannelMessageEvent.class), captor.capture());

    return captor.getValue();
  }
}
