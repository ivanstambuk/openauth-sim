package io.openauth.sim.rest.totp;

import java.util.Map;

final class TotpEvaluationValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String reasonCode;
  private final Map<String, Object> details;

  TotpEvaluationValidationException(
      String reasonCode, String message, Map<String, Object> details) {
    super(message);
    this.reasonCode = reasonCode;
    this.details = Map.copyOf(details);
  }

  String reasonCode() {
    return reasonCode;
  }

  Map<String, Object> details() {
    return details;
  }
}
