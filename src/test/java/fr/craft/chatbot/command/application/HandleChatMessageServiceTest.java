package fr.craft.chatbot.command.application;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.domain.ChatMessagePublisher;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandRepository;
import fr.craft.chatbot.command.domain.CommandResponse;
import java.util.Optional;
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

  @Test
  void shouldReplyWhenTheMessageMatchesAKnownCommand() {
    when(commandRepository.find(new CommandName("!projet"))).thenReturn(Optional.of(new CommandResponse("Un chatbot Twitch")));

    new HandleChatMessageService(commandRepository, chatMessagePublisher).handle(new ChatMessage("!projet"));

    verify(chatMessagePublisher).send("Un chatbot Twitch");
  }

  @Test
  void shouldStaySilentWhenTheCommandIsUnknown() {
    when(commandRepository.find(eq(new CommandName("!doesnotexist")))).thenReturn(Optional.empty());

    new HandleChatMessageService(commandRepository, chatMessagePublisher).handle(new ChatMessage("!doesnotexist"));

    verify(chatMessagePublisher, never()).send(anyString());
  }
}
