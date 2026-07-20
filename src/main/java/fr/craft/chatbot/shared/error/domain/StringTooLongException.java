package fr.craft.chatbot.shared.error.domain;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class StringTooLongException extends AssertionException {

  private final String maxLength;
  private final String currentLength;

  private StringTooLongException(StringTooLongExceptionBuilder builder) {
    super(requireNonNull(builder.field), message(builder));
    Assert.notNull("value", builder.value);
    maxLength = String.valueOf(builder.maxLength);
    currentLength = String.valueOf(builder.value.length());
  }

  public static StringTooLongExceptionBuilder builder() {
    return new StringTooLongExceptionBuilder();
  }

  static final class StringTooLongExceptionBuilder {

    private @Nullable String value;
    private int maxLength;
    private @Nullable String field;

    private StringTooLongExceptionBuilder() {}

    StringTooLongExceptionBuilder field(String field) {
      this.field = field;

      return this;
    }

    StringTooLongExceptionBuilder value(String value) {
      this.value = value;

      return this;
    }

    StringTooLongExceptionBuilder maxLength(int maxLength) {
      this.maxLength = maxLength;

      return this;
    }

    public StringTooLongException build() {
      return new StringTooLongException(this);
    }
  }

  private static String message(StringTooLongExceptionBuilder builder) {
    Assert.notNull("value", builder.value);

    return "The value \"%s\" in field \"%s\" must be at most %d long but was %d".formatted(
      builder.value,
      builder.field,
      builder.maxLength,
      builder.value.length()
    );
  }

  @Override
  public AssertionErrorType type() {
    return AssertionErrorType.STRING_TOO_LONG;
  }

  @Override
  public Map<String, String> parameters() {
    return Map.of("maxLength", maxLength, "currentLength", currentLength);
  }
}
