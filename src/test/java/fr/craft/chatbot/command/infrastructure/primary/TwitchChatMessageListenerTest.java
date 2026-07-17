package fr.craft.chatbot.command.infrastructure.primary;

import static org.mockito.Mockito.verify;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.application.HandleChatMessageService;
import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.infrastructure.TwitchChatFacade;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TwitchChatMessageListenerTest {

  @Mock
  private TwitchChatFacade twitchChatFacade;

  @Mock
  private HandleChatMessageService handleChatMessageService;

  @Test
  void shouldForwardIncomingChatMessagesToTheHandleChatMessageService() {
    new TwitchChatMessageListener(twitchChatFacade, handleChatMessageService).subscribeToChatMessages();

    var listener = captureRegisteredListener();
    var message = new ChatMessage("!projet");

    listener.accept(message);

    verify(handleChatMessageService).handle(message);
  }

  @SuppressWarnings("unchecked")
  private Consumer<ChatMessage> captureRegisteredListener() {
    var captor = ArgumentCaptor.forClass(Consumer.class);

    verify(twitchChatFacade).onChatMessage(captor.capture());

    return captor.getValue();
  }
}
