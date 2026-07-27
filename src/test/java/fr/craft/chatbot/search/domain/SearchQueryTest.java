package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class SearchQueryTest {

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new SearchQuery(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "?wp java", "?wp java ", " ?wp java", "?wp  java" })
  void shouldExtractTheSearchTermWhenContentUsesTheWpPrefix(String content) {
    assertThat(SearchQuery.parse(content)).contains(new SearchQuery("java"));
  }

  @ParameterizedTest
  @ValueSource(strings = { "?wiki java", "?wiki java ", " ?wiki java" })
  void shouldExtractTheSearchTermWhenContentUsesTheWikiPrefix(String content) {
    assertThat(SearchQuery.parse(content)).contains(new SearchQuery("java"));
  }

  @Test
  void shouldKeepTheFullMultiWordTermAfterThePrefix() {
    assertThat(SearchQuery.parse("?wp coq de java")).contains(new SearchQuery("coq de java"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = { "", "   ", "hello everyone", "?wp", "?wiki", "?wp ", "!projet" })
  void shouldReturnEmptyWhenTheContentIsNotASearchCommand(String content) {
    assertThat(SearchQuery.parse(content)).isEmpty();
  }

  @Property
  void shouldExtractTheWholeTermAfterAKnownPrefix(
    @ForAll("triggerPrefix") String prefix,
    @ForAll @IntRange(min = 1, max = 4) int gap,
    @ForAll("searchTerm") String term
  ) {
    assertThat(SearchQuery.parse(prefix + " ".repeat(gap) + term)).contains(new SearchQuery(term));
  }

  @Property
  void shouldIgnoreWhitespaceAroundASearchCommand(
    @ForAll("asciiWhitespace") String left,
    @ForAll("triggerPrefix") String prefix,
    @ForAll("searchTerm") String term,
    @ForAll("asciiWhitespace") String right
  ) {
    assertThat(SearchQuery.parse(left + prefix + " " + term + right)).contains(new SearchQuery(term));
  }

  @Property
  void shouldReturnEmptyWithoutAKnownPrefixFollowedByASpace(@ForAll("nonSearchContent") String content) {
    assertThat(SearchQuery.parse(content)).isEmpty();
  }

  @Provide
  Arbitrary<String> triggerPrefix() {
    return Arbitraries.of("?wp", "?wiki");
  }

  @Provide
  Arbitrary<String> searchTerm() {
    return Arbitraries.strings()
      .ofMinLength(1)
      .ofMaxLength(30)
      .filter(term -> term.charAt(0) > ' ' && term.charAt(term.length() - 1) > ' ');
  }

  @Provide
  Arbitrary<String> asciiWhitespace() {
    return Arbitraries.strings().withChars(' ', '\t', '\n', '\u000B', '\f', '\r').ofMaxLength(3);
  }

  @Provide
  Arbitrary<String> nonSearchContent() {
    return Arbitraries.strings().filter(content -> {
      var trimmed = content.trim();
      return !trimmed.startsWith("?wp ") && !trimmed.startsWith("?wiki ");
    });
  }
}
