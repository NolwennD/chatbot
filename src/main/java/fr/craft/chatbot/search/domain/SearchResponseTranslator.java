package fr.craft.chatbot.search.domain;

import java.util.List;

public interface SearchResponseTranslator {
  List<SearchResponse> translate(SearchOutcome outcome);
}
