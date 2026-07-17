package fr.craft.chatbot.command.infrastructure.secondary;

import fr.craft.chatbot.command.domain.ChatMessagePublisher;
import fr.craft.chatbot.command.infrastructure.TwitchChatFacade;
import org.springframework.stereotype.Component;

@Component
class TwitchChatMessagePublisher implements ChatMessagePublisher {

  private final TwitchChatFacade twitchChatFacade;

  TwitchChatMessagePublisher(TwitchChatFacade twitchChatFacade) {
    this.twitchChatFacade = twitchChatFacade;
  }

  @Override
  public void send(String message) {
    twitchChatFacade.sendMessage(message);
  }
}
