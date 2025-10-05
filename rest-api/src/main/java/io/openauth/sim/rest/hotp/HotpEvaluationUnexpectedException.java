package io.openauth.sim.rest.hotp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Wraps unexpected evaluation errors so the controller can emit a 500 response with metadata. */
final class HotpEvaluationUnexpectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String telemetryId;
  private final String credentialSource;
  private final Map<String, String> details;

  HotpEvaluationUnexpectedException(
      String telemetryId, String credentialSource, String message, Map<String, String> details) {
    super(message);
    this.telemetryId = Objects.requireNonNull(telemetryId, "telemetryId");
    this.credentialSource = credentialSource;
    this.details =
        details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
  }

  String telemetryId() {
    return telemetryId;
  }

  String credentialSource() {
    return credentialSource;
  }

  Map<String, String> details() {
    return details;
  }
}
