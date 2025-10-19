package io.openauth.sim.rest.totp;

import java.util.Collections;
import java.util.Map;

final class TotpReplayUnexpectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String credentialSource;
  private final transient Map<String, Object> details;

  TotpReplayUnexpectedException(
      String telemetryId,
      String credentialSource,
      String message,
      Map<String, Object> details,
      Throwable cause) {
    super(message, cause);
    this.telemetryId = telemetryId;
    this.credentialSource = credentialSource;
    this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
  }

  String telemetryId() {
    return telemetryId;
  }

  String credentialSource() {
    return credentialSource;
  }

  Map<String, Object> details() {
    return details;
  }
}
