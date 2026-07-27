package fr.craft.chatbot.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.WhitespacePadding;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

@UnitTest
class SearchResponseTest {

  @Property
  void shouldRecoverTextStrippedOfAnySurroundingSpaces(
    @ForAll(supplier = WhitespacePadding.UnpaddedText.class) String raw,
    @ForAll(supplier = WhitespacePadding.SurroundingSpaces.class) String left,
    @ForAll(supplier = WhitespacePadding.SurroundingSpaces.class) String right,
    @ForAll @IntRange(min = 0, max = 10) int leftRepeat,
    @ForAll @IntRange(min = 0, max = 10) int rightRepeat
  ) {
    var padded = left.repeat(leftRepeat) + raw + right.repeat(rightRepeat);

    assertThat(new SearchResponse(padded).value()).isEqualTo(raw);
  }

  @Property
  void shouldBeIdempotentOnItsOwnValue(@ForAll String raw) {
    var once = new SearchResponse(raw).value();

    assertThat(new SearchResponse(once).value()).isEqualTo(once);
  }

  @Test
  void shouldReduceAnAllSpaceValueToEmpty() {
    assertThat(new SearchResponse(" \t\n" + WhitespacePadding.NBSP + WhitespacePadding.NNBSP).value()).isEmpty();
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectANullValue() {
    assertThatThrownBy(() -> new SearchResponse(null)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
