package io.openauth.sim.rest.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class HotpEvaluationErrorResponseTest {

  @Test
  @DisplayName("Details default to empty map when null provided")
  void detailsDefaultToEmptyMap() {
    HotpEvaluationErrorResponse response =
        new HotpEvaluationErrorResponse("invalid", "message", null);

    assertTrue(response.details().isEmpty());
  }

  @Test
  @DisplayName("Details map is defensively copied and unmodifiable")
  void detailsAreUnmodifiable() {
    HotpEvaluationErrorResponse response =
        new HotpEvaluationErrorResponse("invalid", "message", Map.of("field", "otp"));

    assertEquals("otp", response.details().get("field"));
    assertThrows(UnsupportedOperationException.class, () -> response.details().put("x", "y"));
  }
}
