package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchResponseTest {

  @Test
  void shouldBuildASearchResponse() {
    var response = new SearchResponse(" Un langage de programmation. ");

    assertThat(response.value()).isEqualTo("Un langage de programmation.");
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullValue() {
    assertThatThrownBy(() -> new SearchResponse(null)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
