package fr.craft.chatbot.search.infrastructure.primary;

import static org.mockito.Mockito.verify;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.search.application.HandleSearchMessageService;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchChatMessageListenerTest {

  @Mock
  private TwitchChatFacade twitchChatFacade;

  @Mock
  private HandleSearchMessageService handleSearchMessageService;

  @Test
  void shouldForwardIncomingChatMessagesToTheHandleSearchMessageService() {
    new SearchChatMessageListener(twitchChatFacade, handleSearchMessageService).subscribeToChatMessages();

    var listener = captureRegisteredListener();
    var message = new ChatMessage("?wp java");

    listener.accept(message);

    verify(handleSearchMessageService).handle(message);
  }

  @SuppressWarnings("unchecked")
  private Consumer<ChatMessage> captureRegisteredListener() {
    var captor = ArgumentCaptor.forClass(Consumer.class);

    verify(twitchChatFacade).onChatMessage(captor.capture());

    return captor.getValue();
  }
}
