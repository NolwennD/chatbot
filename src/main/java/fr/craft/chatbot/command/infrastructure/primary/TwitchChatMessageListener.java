package fr.craft.chatbot.command.infrastructure.primary;

import fr.craft.chatbot.command.application.HandleChatMessageService;
import fr.craft.chatbot.shared.twitch.infrastructure.ChatMessageReceivedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class TwitchChatMessageListener {

  private final HandleChatMessageService handleChatMessageService;

  TwitchChatMessageListener(HandleChatMessageService handleChatMessageService) {
    this.handleChatMessageService = handleChatMessageService;
  }

  @EventListener
  void onChatMessageReceived(ChatMessageReceivedEvent event) {
    handleChatMessageService.handle(event.chatMessage());
  }
}
