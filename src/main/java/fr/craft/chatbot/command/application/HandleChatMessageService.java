package fr.craft.chatbot.command.application;

import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.domain.ChatMessagePublisher;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandRepository;
import fr.craft.chatbot.command.domain.CommandResponse;
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
    CommandName.fromChatMessageContent(message.content()).ifPresent(name -> chatMessagePublisher.send(resolveResponse(name).value()));
  }

  private CommandResponse resolveResponse(CommandName name) {
    return commandRepository.find(name).orElseGet(() -> CommandResponse.fromCommands(commandRepository.findAll()));
  }
}
