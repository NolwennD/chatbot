package fr.craft.chatbot.command.application;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.domain.ChatMessagePublisher;
import fr.craft.chatbot.command.domain.CommandRepository;
import fr.craft.chatbot.command.domain.CommandResponseTranslator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class HandleChatMessageServiceTest {

  @Mock
  private CommandRepository commandRepository;

  @Mock
  private ChatMessagePublisher chatMessagePublisher;

  @Mock
  private CommandResponseTranslator translator;

  @Test
  void shouldStaySilentWhenTheMessageIsNotACommand() {
    new HandleChatMessageService(commandRepository, chatMessagePublisher, translator).handle(new ChatMessage("hello there"));

    verify(chatMessagePublisher, never()).send(anyString());
  }
}
