package fr.craft.chatbot.search.domain;

import java.util.Optional;

public sealed interface SearchOutcome {
  record Found(PageSummary summary) implements SearchOutcome {}

  record Ambiguous(SearchQuery query, PageSummary summary) implements SearchOutcome {}

  record NotFound(SearchQuery query) implements SearchOutcome {}

  static SearchOutcome from(SearchQuery query, Optional<PageSummary> summary) {
    return summary
      .<SearchOutcome>map(found -> found.disambiguation() ? new Ambiguous(query, found) : new Found(found))
      .orElseGet(() -> new NotFound(query));
  }
}
