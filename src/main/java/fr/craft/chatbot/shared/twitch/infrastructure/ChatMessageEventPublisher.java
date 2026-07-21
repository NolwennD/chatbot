package fr.craft.chatbot.shared.twitch.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class ChatMessageEventPublisher {

  private final TwitchChatFacade twitchChatFacade;
  private final ApplicationEventPublisher eventPublisher;

  ChatMessageEventPublisher(TwitchChatFacade twitchChatFacade, ApplicationEventPublisher eventPublisher) {
    this.twitchChatFacade = twitchChatFacade;
    this.eventPublisher = eventPublisher;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatFacade.onChatMessage(message -> eventPublisher.publishEvent(new ChatMessageReceivedEvent(message)));
  }
}
