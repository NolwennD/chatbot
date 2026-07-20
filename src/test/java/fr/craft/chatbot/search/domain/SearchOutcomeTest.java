package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.chatbot.UnitTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchOutcomeTest {

  private static final SearchQuery QUERY = new SearchQuery("java");

  @Test
  void shouldReturnFoundWhenTheSummaryIsNotADisambiguationPage() {
    var summary = new PageSummary("Un langage de programmation.", "https://fr.wikipedia.org/wiki/Java_(langage)", false);

    assertThat(SearchOutcome.from(QUERY, Optional.of(summary))).isEqualTo(new SearchOutcome.Found(summary));
  }

  @Test
  void shouldReturnAmbiguousWhenTheSummaryIsADisambiguationPage() {
    var summary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    assertThat(SearchOutcome.from(QUERY, Optional.of(summary))).isEqualTo(new SearchOutcome.Ambiguous(QUERY, summary));
  }

  @Test
  void shouldReturnNotFoundWhenNoSummaryIsPresent() {
    assertThat(SearchOutcome.from(QUERY, Optional.empty())).isEqualTo(new SearchOutcome.NotFound(QUERY));
  }
}
