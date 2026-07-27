package fr.craft.chatbot.shared.text.domain;

/**
 * Utility class to normalize strings. Inputs are expected non-null (the package is {@code @NullMarked}).
 */
public final class ChatbotStrings {

  private ChatbotStrings() {}

  /**
   * Remove leading and trailing space characters, breakable or not (anything Java considers a space or whitespace).
   *
   * @param text
   *          input string
   * @return the string without surrounding spaces
   */
  public static String stripSurroundingSpaces(String text) {
    int start = 0;
    int end = text.length();

    while (start < end && isSpace(text.charAt(start))) {
      start++;
    }
    while (end > start && isSpace(text.charAt(end - 1))) {
      end--;
    }

    return text.substring(start, end);
  }

  private static boolean isSpace(char c) {
    return Character.isSpaceChar(c) || Character.isWhitespace(c);
  }
}
