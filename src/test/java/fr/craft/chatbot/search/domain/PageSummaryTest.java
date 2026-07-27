package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

@UnitTest
class PageSummaryTest {

  private static final int MAX_EXTRACT_LENGTH = 200;
  private static final String URL = "https://fr.wikipedia.org/wiki/Test";

  @Property
  void shouldKeepAnyExtractAtOrUnderTheLimitUnchanged(@ForAll @StringLength(max = MAX_EXTRACT_LENGTH) String extract) {
    assertThat(new PageSummary(extract, URL, false).extract()).isEqualTo(extract);
  }

  @Property
  void shouldNeverExceedTheLimitPlusTheEllipsis(@ForAll String extract) {
    assertThat(new PageSummary(extract, URL, false).extract().length()).isLessThanOrEqualTo(MAX_EXTRACT_LENGTH + 1);
  }

  @Property
  void shouldEndWithAnEllipsisOverAPrefixOfTheInputWhenTooLong(
    @ForAll @StringLength(min = MAX_EXTRACT_LENGTH + 1, max = 400) String extract
  ) {
    var result = new PageSummary(extract, URL, false).extract();
    var body = result.substring(0, result.length() - 1);

    assertThat(result).endsWith("…");
    assertThat(extract).startsWith(body);
  }

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
  void shouldKeepTheWholeCutWhenTheOnlySpaceIsTheLeadingCharacter() {
    var extract = " " + "a".repeat(250);

    var summary = new PageSummary(extract, "https://fr.wikipedia.org/wiki/Test", false);

    assertThat(summary.extract()).isEqualTo(" " + "a".repeat(MAX_EXTRACT_LENGTH - 1) + "…");
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
