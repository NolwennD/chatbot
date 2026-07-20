package fr.craft.chatbot.search.domain;

import java.util.Locale;
import java.util.Optional;

public sealed interface SearchOutcome {
  record Found(PageSummary summary) implements SearchOutcome {}

  record Ambiguous(SearchQuery query, PageSummary summary) implements SearchOutcome {}

  record NotFound(SearchQuery query, Locale locale) implements SearchOutcome {
    public boolean isEnglish() {
      return locale.equals(Locale.ENGLISH);
    }
  }

  static SearchOutcome from(SearchQuery query, Locale locale, Optional<PageSummary> summary) {
    return summary
      .<SearchOutcome>map(found -> found.disambiguation() ? new Ambiguous(query, found) : new Found(found))
      .orElseGet(() -> new NotFound(query, locale));
  }
}
