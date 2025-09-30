package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("ocra")
final class OcraChallengeFormatTest {

  @DisplayName("fromToken(char) resolves supported challenge formats case-insensitively")
  @ParameterizedTest(name = "{index} ⇒ token {0}")
  @MethodSource("supportedCharTokens")
  void fromTokenCharRecognisesSupportedTokens(char input, OcraChallengeFormat expected) {
    assertEquals(expected, OcraChallengeFormat.fromToken(input));
    assertEquals(expected, OcraChallengeFormat.fromToken(Character.toLowerCase(input)));
  }

  private static Stream<Arguments> supportedCharTokens() {
    return Stream.of(
        Arguments.of('A', OcraChallengeFormat.ALPHANUMERIC),
        Arguments.of('N', OcraChallengeFormat.NUMERIC),
        Arguments.of('H', OcraChallengeFormat.HEX),
        Arguments.of('C', OcraChallengeFormat.CHARACTER));
  }

  @DisplayName("fromToken(String) resolves supported challenge formats case-insensitively")
  @ParameterizedTest(name = "{index} ⇒ token {0}")
  @MethodSource("supportedStringTokens")
  void fromTokenStringRecognisesSupportedTokens(String token, OcraChallengeFormat expected) {
    assertEquals(expected, OcraChallengeFormat.fromToken(token));
    assertEquals(expected, OcraChallengeFormat.fromToken(token.toLowerCase()));
  }

  private static Stream<Arguments> supportedStringTokens() {
    return Stream.of(
        Arguments.of("A", OcraChallengeFormat.ALPHANUMERIC),
        Arguments.of("N", OcraChallengeFormat.NUMERIC),
        Arguments.of("H", OcraChallengeFormat.HEX),
        Arguments.of("C", OcraChallengeFormat.CHARACTER));
  }

  @DisplayName("fromToken(String) rejects blank inputs")
  @Test
  void fromTokenStringRejectsBlankInputs() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> OcraChallengeFormat.fromToken(" \t\n"));
    String message = exception.getMessage();
    if (message == null || !message.contains("must not be blank")) {
      throw new AssertionError("Expected blank-token error message but was: " + message);
    }
  }

  @DisplayName("fromToken throws when encountering unsupported tokens")
  @Test
  void fromTokenRejectsUnsupportedTokens() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> OcraChallengeFormat.fromToken('x'));
    String message = exception.getMessage();
    if (message == null || !message.contains("Unsupported OCRA challenge format token")) {
      throw new AssertionError("Expected unsupported-token error message but was: " + message);
    }
  }
}
