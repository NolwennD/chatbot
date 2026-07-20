package fr.craft.chatbot.search.infrastructure.secondary;

import fr.craft.chatbot.search.domain.PageSummary;
import fr.craft.chatbot.search.domain.SearchOutcome;
import fr.craft.chatbot.search.domain.SearchQuery;
import fr.craft.chatbot.search.domain.SearchResponse;
import fr.craft.chatbot.search.domain.SearchResponseTranslator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Repository;

@Repository
class SpringSearchResponseTranslator implements SearchResponseTranslator {

  private final MessageSource messageSource;
  private final Locale locale;

  SpringSearchResponseTranslator(MessageSource messageSource, Locale locale) {
    this.messageSource = messageSource;
    this.locale = locale;
  }

  @Override
  public List<SearchResponse> translate(SearchOutcome outcome) {
    return switch (outcome) {
      case SearchOutcome.Found(PageSummary summary) -> List.of(new SearchResponse(summary.extract()), new SearchResponse(summary.url()));
      case SearchOutcome.Ambiguous(SearchQuery query, PageSummary summary) -> List.of(
        new SearchResponse(messageSource.getMessage("search.ambiguous", new Object[] { query.value(), summary.url() }, locale))
      );
      case SearchOutcome.NotFound(SearchQuery query) -> List.of(
        new SearchResponse(messageSource.getMessage("search.notfound", new Object[] { query.value() }, locale))
      );
    };
  }
}
