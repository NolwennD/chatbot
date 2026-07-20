package fr.craft.chatbot.search.application;

import fr.craft.chatbot.search.domain.PageLookup;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponseTranslator;
import fr.craft.chatbot.shared.twitch.domain.ChatMessage;
import fr.craft.chatbot.shared.twitch.domain.ChatMessagePublisher;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class HandleSearchMessageService {

  private final PageLookup pageLookup;
  private final ChatMessagePublisher chatMessagePublisher;
  private final SearchResponseTranslator translator;
  private final Locale botLocale;

  public HandleSearchMessageService(
    PageLookup pageLookup,
    ChatMessagePublisher chatMessagePublisher,
    SearchResponseTranslator translator,
    Locale botLocale
  ) {
    this.pageLookup = pageLookup;
    this.chatMessagePublisher = chatMessagePublisher;
    this.translator = translator;
    this.botLocale = botLocale;
  }

  public void handle(ChatMessage message) {
    SearchQuery.parse(message.content())
      .map(this::resolveOutcome)
      .map(translator::translate)
      .ifPresent(responses -> responses.forEach(response -> chatMessagePublisher.send(response.value())));
  }

  private SearchOutcome resolveOutcome(SearchQuery query) {
    var outcome = SearchOutcome.from(query, botLocale, pageLookup.findSummary(query, botLocale));

    if (outcome instanceof SearchOutcome.NotFound notFound && !notFound.isEnglish()) {
      return SearchOutcome.from(query, Locale.ENGLISH, pageLookup.findSummary(query, Locale.ENGLISH));
    }

    return outcome;
  }
}
