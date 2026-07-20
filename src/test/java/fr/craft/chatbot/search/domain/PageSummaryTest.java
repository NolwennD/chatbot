package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class PageSummaryTest {

  @Test
  void shouldKeepAShortExtractUnchanged() {
    var summary = new PageSummary("Résumé court.", "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo("Résumé court.");
  }

  @Test
  void shouldTruncateAtTheLastSpaceBeforeTheLimitWhenTooLong() {
    var extract = "a".repeat(150) + " " + "b".repeat(100);

    var summary = new PageSummary(extract, "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo("a".repeat(150) + "…");
  }

  @Test
  void shouldTruncateAtTheLimitWhenThereIsNoSpaceToBackOffTo() {
    var extract = "a".repeat(250);

    var summary = new PageSummary(extract, "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo("a".repeat(200) + "…");
  }

  @Test
  void shouldExposeTheUrlAndDisambiguationFlag() {
    var summary = new PageSummary("", "https://fr.wikipedia.org/wiki/Java", true);

    assertThat(summary.url()).isEqualTo("https://fr.wikipedia.org/wiki/Java");
    assertThat(summary.disambiguation()).isTrue();
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullExtract() {
    assertThatThrownBy(() -> new PageSummary(null, "https://fr.wikipedia.org/wiki/Java", false)).isInstanceOf(
      MissingMandatoryValueException.class
    );
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullUrl() {
    assertThatThrownBy(() -> new PageSummary("extrait", null, false)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
