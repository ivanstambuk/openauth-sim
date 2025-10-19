package io.openauth.sim.rest.hotp;

import java.util.Map;

/** Signals HOTP replay validation failures. */
class HotpReplayValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String credentialSource;
  private final String credentialId;
  private final String reasonCode;
  private final boolean sanitized;
  private final transient Map<String, String> details;

  HotpReplayValidationException(
      String telemetryId,
      String credentialSource,
      String credentialId,
      String reasonCode,
      boolean sanitized,
      Map<String, String> details,
      String message) {
    super(message);
    this.telemetryId = telemetryId;
    this.credentialSource = credentialSource;
    this.credentialId = credentialId;
    this.reasonCode = reasonCode;
    this.sanitized = sanitized;
    this.details = details;
  }

  String telemetryId() {
    return telemetryId;
  }

  String credentialSource() {
    return credentialSource;
  }

  String credentialId() {
    return credentialId;
  }

  String reasonCode() {
    return reasonCode;
  }

  boolean sanitized() {
    return sanitized;
  }

  Map<String, String> details() {
    return details;
  }
}
