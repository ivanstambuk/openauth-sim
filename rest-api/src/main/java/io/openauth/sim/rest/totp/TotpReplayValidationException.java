package io.openauth.sim.rest.totp;

import java.util.Collections;
import java.util.Map;

final class TotpReplayValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String credentialSource;
  private final String reasonCode;
  private final boolean sanitized;
  private final Map<String, Object> details;

  TotpReplayValidationException(
      String telemetryId,
      String credentialSource,
      String reasonCode,
      String message,
      boolean sanitized,
      Map<String, Object> details) {
    super(message);
    this.telemetryId = telemetryId;
    this.credentialSource = credentialSource;
    this.reasonCode = reasonCode;
    this.sanitized = sanitized;
    this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
  }

  String telemetryId() {
    return telemetryId;
  }

  String credentialSource() {
    return credentialSource;
  }

  String reasonCode() {
    return reasonCode;
  }

  boolean sanitized() {
    return sanitized;
  }

  Map<String, Object> details() {
    return details;
  }
}
