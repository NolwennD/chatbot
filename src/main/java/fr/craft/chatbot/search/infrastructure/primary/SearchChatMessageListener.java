package fr.craft.chatbot.search.infrastructure.primary;

import fr.craft.chatbot.search.application.HandleSearchMessageService;
import fr.craft.chatbot.shared.twitch.infrastructure.TwitchChatFacade;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
class SearchChatMessageListener {

  private final TwitchChatFacade twitchChatFacade;
  private final HandleSearchMessageService handleSearchMessageService;

  SearchChatMessageListener(TwitchChatFacade twitchChatFacade, HandleSearchMessageService handleSearchMessageService) {
    this.twitchChatFacade = twitchChatFacade;
    this.handleSearchMessageService = handleSearchMessageService;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatFacade.onChatMessage(handleSearchMessageService::handle);
  }
}
