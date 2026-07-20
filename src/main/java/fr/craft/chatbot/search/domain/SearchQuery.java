package fr.craft.chatbot.search.domain;

import fr.craft.chatbot.shared.error.domain.Assert;
import java.util.Optional;
import java.util.Set;

public record SearchQuery(String value) {
  private static final Set<String> TRIGGER_PREFIXES = Set.of("?wp", "?wiki");

  public SearchQuery {
    Assert.field("value", value).notBlank();
  }

  public static Optional<SearchQuery> parse(String content) {
    return Optional.ofNullable(content).map(String::trim).flatMap(SearchQuery::extractTerm);
  }

  private static Optional<SearchQuery> extractTerm(String trimmed) {
    return TRIGGER_PREFIXES.stream()
      .filter(prefix -> trimmed.startsWith(prefix + " "))
      .findFirst()
      .map(prefix -> trimmed.substring(prefix.length()).trim())
      .filter(term -> !term.isBlank())
      .map(SearchQuery::new);
  }
}
