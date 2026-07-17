package fr.craft.chatbot.command.infrastructure.primary;

import fr.craft.chatbot.command.application.HandleChatMessageService;
import fr.craft.chatbot.command.infrastructure.TwitchChatFacade;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
class TwitchChatMessageListener {

  private final TwitchChatFacade twitchChatFacade;
  private final HandleChatMessageService handleChatMessageService;

  TwitchChatMessageListener(TwitchChatFacade twitchChatFacade, HandleChatMessageService handleChatMessageService) {
    this.twitchChatFacade = twitchChatFacade;
    this.handleChatMessageService = handleChatMessageService;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatFacade.onChatMessage(handleChatMessageService::handle);
  }
}
