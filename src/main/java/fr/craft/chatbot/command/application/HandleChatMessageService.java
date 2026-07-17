package fr.craft.chatbot.command.application;

import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.domain.ChatMessagePublisher;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandRepository;
import org.springframework.stereotype.Service;

@Service
public class HandleChatMessageService {

  private final CommandRepository commandRepository;
  private final ChatMessagePublisher chatMessagePublisher;

  public HandleChatMessageService(CommandRepository commandRepository, ChatMessagePublisher chatMessagePublisher) {
    this.commandRepository = commandRepository;
    this.chatMessagePublisher = chatMessagePublisher;
  }

  public void handle(ChatMessage message) {
    CommandName.fromChatMessageContent(message.content())
      .flatMap(commandRepository::find)
      .ifPresent(response -> chatMessagePublisher.send(response.value()));
  }
}
