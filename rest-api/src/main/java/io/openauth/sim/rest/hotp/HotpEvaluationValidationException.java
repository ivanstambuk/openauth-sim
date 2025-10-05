package io.openauth.sim.rest.hotp;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Placeholder validation exception surfaced by HOTP REST evaluation. */
final class HotpEvaluationValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String credentialSource;
  private final String credentialId;
  private final String reasonCode;
  private final boolean sanitized;
  private final Map<String, String> details;

  HotpEvaluationValidationException(
      String telemetryId,
      String credentialSource,
      String credentialId,
      String reasonCode,
      boolean sanitized,
      Map<String, String> details,
      String message) {
    super(message);
    this.telemetryId = Objects.requireNonNull(telemetryId, "telemetryId");
    this.credentialSource = credentialSource;
    this.credentialId = credentialId;
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
