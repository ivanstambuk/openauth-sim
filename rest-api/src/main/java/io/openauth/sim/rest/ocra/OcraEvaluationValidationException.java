package io.openauth.sim.rest.ocra;

import java.io.Serial;

final class OcraEvaluationValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String suite;

  OcraEvaluationValidationException(
      String telemetryId, String suite, String message, Throwable cause) {
    super(message, cause);
    this.telemetryId = telemetryId;
    this.suite = suite;
  }

  String telemetryId() {
    return telemetryId;
  }

  String suite() {
    return suite;
  }
}
