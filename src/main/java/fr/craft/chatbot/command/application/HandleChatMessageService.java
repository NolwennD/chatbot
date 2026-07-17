package fr.craft.chatbot.command.application;

import fr.craft.chatbot.command.domain.ChatMessage;
import fr.craft.chatbot.command.domain.ChatMessagePublisher;
import fr.craft.chatbot.command.domain.CommandName;
import fr.craft.chatbot.command.domain.CommandOutcome;
import fr.craft.chatbot.command.domain.CommandRepository;
import fr.craft.chatbot.command.domain.CommandResponseTranslator;
import org.springframework.stereotype.Service;

@Service
public class HandleChatMessageService {

  private final CommandRepository commandRepository;
  private final ChatMessagePublisher chatMessagePublisher;
  private final CommandResponseTranslator translator;

  public HandleChatMessageService(
    CommandRepository commandRepository,
    ChatMessagePublisher chatMessagePublisher,
    CommandResponseTranslator translator
  ) {
    this.commandRepository = commandRepository;
    this.chatMessagePublisher = chatMessagePublisher;
    this.translator = translator;
  }

  public void handle(ChatMessage message) {
    CommandName.fromChatMessageContent(message.content())
      .map(this::resolveOutcome)
      .map(outcome -> outcome.response(translator))
      .ifPresent(response -> chatMessagePublisher.send(response.value()));
  }

  private CommandOutcome resolveOutcome(CommandName name) {
    return commandRepository
      .find(name)
      .<CommandOutcome>map(CommandOutcome.CommandFound::new)
      .orElseGet(() -> {
        var knownCommands = commandRepository.findAll();

        return knownCommands.isEmpty() ? new CommandOutcome.NoCommandsAvailable() : new CommandOutcome.UnknownCommand(knownCommands);
      });
  }
}
