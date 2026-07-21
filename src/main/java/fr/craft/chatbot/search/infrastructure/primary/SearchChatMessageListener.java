package fr.craft.chatbot.search.infrastructure.primary;

import fr.craft.chatbot.search.application.HandleSearchMessageService;
import fr.craft.chatbot.shared.twitch.infrastructure.primary.ChatMessageReceivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class SearchChatMessageListener {

  private final HandleSearchMessageService handleSearchMessageService;

  SearchChatMessageListener(HandleSearchMessageService handleSearchMessageService) {
    this.handleSearchMessageService = handleSearchMessageService;
  }

  @EventListener
  void onChatMessageReceived(ChatMessageReceivedEvent event) {
    handleSearchMessageService.handle(event.chatMessage());
  }
}
