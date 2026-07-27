package fr.craft.chatbot.command.domain;

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
class CommandResponseTest {

  @Property
  void shouldRecoverTextStrippedOfAnySurroundingSpaces(
    @ForAll(supplier = WhitespacePadding.UnpaddedText.class) String raw,
    @ForAll(supplier = WhitespacePadding.SurroundingSpaces.class) String left,
    @ForAll(supplier = WhitespacePadding.SurroundingSpaces.class) String right,
    @ForAll @IntRange(min = 0, max = 10) int leftRepeat,
    @ForAll @IntRange(min = 0, max = 10) int rightRepeat
  ) {
    var padded = left.repeat(leftRepeat) + raw + right.repeat(rightRepeat);

    assertThat(new CommandResponse(padded).value()).isEqualTo(raw);
  }

  @Property
  void shouldBeIdempotentOnItsOwnValue(@ForAll String raw) {
    var once = new CommandResponse(raw).value();

    assertThat(new CommandResponse(once).value()).isEqualTo(once);
  }

  @Test
  void shouldReduceAnAllSpaceValueToEmpty() {
    assertThat(new CommandResponse(" \t\n" + WhitespacePadding.NBSP + WhitespacePadding.NNBSP).value()).isEmpty();
  }

  @Test
  @SuppressWarnings("NullAway")
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new CommandResponse(null)).isInstanceOf(MissingMandatoryValueException.class);
  }
}
