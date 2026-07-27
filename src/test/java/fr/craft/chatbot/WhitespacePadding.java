package fr.craft.chatbot;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ArbitrarySupplier;

public final class WhitespacePadding {

  public static final String NBSP = "\u00A0";
  public static final String NNBSP = "\u202F";

  private WhitespacePadding() {}

  private static Arbitrary<String> unpaddedText() {
    return Arbitraries.strings()
      .ofMinLength(1)
      .filter(text -> !isSpace(text.charAt(0)) && !isSpace(text.charAt(text.length() - 1)));
  }

  private static Arbitrary<String> surroundingSpaces() {
    return Arbitraries.strings().withChars(' ', '\t', '\n', NBSP.charAt(0), NNBSP.charAt(0)).ofMaxLength(3);
  }

  private static boolean isSpace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  public static final class UnpaddedText implements ArbitrarySupplier<String> {

    @Override
    public Arbitrary<String> get() {
      return unpaddedText();
    }
  }

  public static final class SurroundingSpaces implements ArbitrarySupplier<String> {

    @Override
    public Arbitrary<String> get() {
      return surroundingSpaces();
    }
  }
}
