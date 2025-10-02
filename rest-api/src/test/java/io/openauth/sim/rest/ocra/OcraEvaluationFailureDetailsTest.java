package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraEvaluationFailureDetailsTest {

  @Test
  @DisplayName("fromIllegalArgument returns invalid_input for null message")
  void fromIllegalArgumentNullMessage() {
    OcraEvaluationService.FailureDetails details =
        OcraEvaluationService.FailureDetails.fromIllegalArgument(null);

    assertEquals("request", details.field());
    assertEquals("invalid_input", details.reasonCode());
    assertTrue(details.sanitized());
  }

  @Test
  @DisplayName("fromIllegalArgument maps credential/shared secret conflict")
  void fromIllegalArgumentCredentialConflict() {
    OcraEvaluationService.FailureDetails details =
        OcraEvaluationService.FailureDetails.fromIllegalArgument(
            "credentialId and sharedSecretHex provided");

    assertEquals("credentialId", details.field());
    assertEquals("credential_missing", details.reasonCode());
  }

  @Test
  @DisplayName("fromIllegalArgument defaults to invalid_input for unmatched message")
  void fromIllegalArgumentDefault() {
    OcraEvaluationService.FailureDetails details =
        OcraEvaluationService.FailureDetails.fromIllegalArgument("unrecognized error");

    assertEquals("request", details.field());
    assertEquals("invalid_input", details.reasonCode());
  }
}
