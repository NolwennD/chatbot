package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class ChatMessageEventPublisher {

  private final TwitchChatReceiver twitchChatReceiver;
  private final ApplicationEventPublisher eventPublisher;

  ChatMessageEventPublisher(TwitchChatReceiver twitchChatReceiver, ApplicationEventPublisher eventPublisher) {
    this.twitchChatReceiver = twitchChatReceiver;
    this.eventPublisher = eventPublisher;
  }

  @PostConstruct
  void subscribeToChatMessages() {
    twitchChatReceiver.onChatMessage(message -> eventPublisher.publishEvent(new ChatMessageReceivedEvent(message)));
  }
}
