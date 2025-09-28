package io.openauth.sim.rest.ocra;

import java.io.Serial;

final class OcraEvaluationValidationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String suite;
  private final String field;
  private final String reasonCode;
  private final boolean sanitized;

  OcraEvaluationValidationException(
      String telemetryId,
      String suite,
      String field,
      String reasonCode,
      String message,
      boolean sanitized,
      Throwable cause) {
    super(message, cause);
    this.telemetryId = telemetryId;
    this.suite = suite;
    this.field = field;
    this.reasonCode = reasonCode;
    this.sanitized = sanitized;
  }

  String telemetryId() {
    return telemetryId;
  }

  String suite() {
    return suite;
  }

  String field() {
    return field;
  }

  String reasonCode() {
    return reasonCode;
  }

  boolean sanitized() {
    return sanitized;
  }
}
