package fr.craft.chatbot.shared.result.domain;

import java.time.LocalDate;

public class UnTrucAvecResult {

  record Challenge(LocalDate date) {}

  sealed interface ChallengeError {
    record ChallengeNotFound(int id) implements ChallengeError {}

    record ChallengeAlreadyExists(int id) implements ChallengeError {}
  }

  private static String success(Result<Challenge, ChallengeError> result) {
    return switch (result) {
      case Result.Success(Challenge date) -> date.toString();
      case Result.Failure(ChallengeError error) -> dealWithError(error);
    };
  }

  private static String dealWithError(ChallengeError error) {
    return switch (error) {
      case ChallengeError.ChallengeNotFound _ -> "404";
      case ChallengeError.ChallengeAlreadyExists _ -> "409";
    };
  }
}
