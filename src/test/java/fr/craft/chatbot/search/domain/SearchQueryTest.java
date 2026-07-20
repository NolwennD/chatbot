package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class SearchQueryTest {

  @Test
  void shouldBuildASearchQuery() {
    var query = new SearchQuery("java");

    assertThat(query.value()).isEqualTo("java");
  }

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
}
