package fr.craft.chatbot.search.domain;

import java.util.Locale;
import java.util.Optional;

public interface PageLookup {
  Optional<PageSummary> findSummary(SearchQuery query, Locale locale);
}
