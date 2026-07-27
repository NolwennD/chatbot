package fr.craft.chatbot.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.error.domain.MissingMandatoryValueException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class CommandNameTest {

  @Test
  void shouldRejectABlankValue() {
    assertThatThrownBy(() -> new CommandName(" ")).isInstanceOf(MissingMandatoryValueException.class);
  }

  @Test
  void shouldRejectAValueNotStartingWithTheTriggerPrefix() {
    assertThatThrownBy(() -> new CommandName("projet")).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = { "!projet", "!projet please", " !projet", "!projet ", "  !projet  " })
  void shouldExtractTheCommandNameWhenContentIsACommand(String content) {
    assertThat(CommandName.parse(content)).contains(new CommandName("!projet"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = { "", "   ", "hello everyone" })
  void shouldReturnEmptyWhenTheContentIsNotACommand(String content) {
    assertThat(CommandName.parse(content)).isEmpty();
  }

  @Property
  void shouldKeepOnlyTheFirstWordWhenParsing(
    @ForAll("commandWord") String command,
    @ForAll("asciiSeparator") String separator,
    @ForAll String rest
  ) {
    assertThat(CommandName.parse(command + separator + rest)).contains(new CommandName(command));
  }

  @Property
  void shouldIgnoreAsciiWhitespaceAroundACommand(
    @ForAll("asciiWhitespace") String left,
    @ForAll("commandWord") String command,
    @ForAll("asciiWhitespace") String right
  ) {
    assertThat(CommandName.parse(left + command + right)).contains(new CommandName(command));
  }

  @Property
  void shouldReturnEmptyWhenTheFirstWordIsNotACommand(@ForAll("nonCommand") String content) {
    assertThat(CommandName.parse(content)).isEmpty();
  }

  @Property
  void shouldParseBackAnyValidCommandName(@ForAll("commandWord") String command) {
    var name = new CommandName(command);

    assertThat(CommandName.parse(name.value())).contains(name);
  }

  @Provide
  Arbitrary<String> commandWord() {
    return Arbitraries.strings()
      .withCharRange('!', Character.MAX_VALUE)
      .ofMaxLength(20)
      .map(suffix -> "!" + suffix);
  }

  @Provide
  Arbitrary<String> asciiWhitespace() {
    return Arbitraries.strings().withChars(' ', '\t', '\n', '\u000B', '\f', '\r').ofMaxLength(3);
  }

  @Provide
  Arbitrary<String> asciiSeparator() {
    return Arbitraries.strings().withChars(' ', '\t', '\n', '\u000B', '\f', '\r').ofMinLength(1).ofMaxLength(3);
  }

  @Provide
  Arbitrary<String> nonCommand() {
    return Arbitraries.strings().filter(content -> !content.trim().startsWith("!"));
  }
}
