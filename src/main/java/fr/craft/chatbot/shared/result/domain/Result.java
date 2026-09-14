package fr.craft.chatbot.shared.result.domain;

import java.util.function.Function;

public sealed interface Result<S, F> {
  record Success<S, F>(S value) implements Result<S, F> {}

  record Failure<S, F>(F error) implements Result<S, F> {}

  default <T> Result<T, F> map(Function<? super S, ? extends T> mapper) {
    return flatMap(value -> new Success<>(mapper.apply(value)));
  }

  default <T> Result<T, F> flatMap(Function<? super S, Result<T, F>> mapper) {
    return switch (this) {
      case Success<S, F>(S value) -> mapper.apply(value);
      case Failure<S, F>(F error) -> new Failure<>(error);
    };
  }

  static <S, F> Result<S, F> success(S value) {
    return new Success<>(value);
  }

  static <S, F> Result<S, F> failure(F error) {
    return new Failure<>(error);
  }
}
