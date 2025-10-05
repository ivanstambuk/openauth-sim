package io.openauth.sim.rest.hotp;

import java.util.Map;

/** Signals HOTP replay unexpected failures. */
class HotpReplayUnexpectedException extends RuntimeException {

  private final String telemetryId;
  private final String credentialSource;
  private final Map<String, String> details;

  HotpReplayUnexpectedException(
      String telemetryId, String credentialSource, String message, Map<String, String> details) {
    super(message);
    this.telemetryId = telemetryId;
    this.credentialSource = credentialSource;
    this.details = details;
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
