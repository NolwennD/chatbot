package fr.craft.chatbot.shared.result.domain;

import static org.assertj.core.api.Assertions.*;

import fr.craft.chatbot.UnitTest;
import fr.craft.chatbot.shared.result.domain.Result.Failure;
import fr.craft.chatbot.shared.result.domain.Result.Success;
import org.junit.jupiter.api.Test;

@UnitTest
class ResultTest {

  @Test
  void shouldMatchSuccessOnItsValue() {
    Result<Integer, String> result = Result.success(42);

    assertThat(describe(result)).isEqualTo("success: 42");
  }

  @Test
  void shouldMatchFailureOnItsError() {
    Result<Integer, String> result = Result.failure("boom");

    assertThat(describe(result)).isEqualTo("failure: boom");
  }

  @Test
  void shouldMapSuccessValue() {
    Result<Integer, String> result = Result.success(42);

    assertThat(result.map(value -> value + 1)).isEqualTo(new Success<Integer, String>(43));
  }

  @Test
  void shouldNotMapFailure() {
    Result<Integer, String> result = new Failure<>("boom");

    assertThat(result.map(value -> value + 1)).isEqualTo(new Failure<Integer, String>("boom"));
  }

  @Test
  void shouldFlatMapSuccessToOtherResult() {
    Result<Integer, String> result = new Success<>(42);

    assertThat(result.flatMap(value -> new Failure<>("too big: " + value))).isEqualTo(new Failure<String, String>("too big: 42"));
  }

  @Test
  void shouldNotFlatMapFailure() {
    Result<Integer, String> result = new Failure<>("boom");

    assertThat(result.flatMap(value -> new Success<>(String.valueOf(value)))).isEqualTo(new Failure<String, String>("boom"));
  }

  private static String describe(Result<Integer, String> result) {
    return switch (result) {
      case Success<Integer, String>(Integer value) -> "success: " + value;
      case Failure<Integer, String>(String error) -> "failure: " + error;
    };
  }
}
