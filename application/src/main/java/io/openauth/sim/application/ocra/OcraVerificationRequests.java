package io.openauth.sim.application.ocra;

import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationCommand;
import java.time.Duration;
import java.util.Objects;

public final class OcraVerificationRequests {

  private OcraVerificationRequests() {
    throw new AssertionError("No instances");
  }

  public static VerificationCommand stored(StoredInputs inputs) {
    Objects.requireNonNull(inputs, "inputs");
    return new VerificationCommand.Stored(
        trimRequired(inputs.credentialId(), "credentialId"),
        trimRequired(inputs.otp(), "otp"),
        trim(inputs.challenge()),
        trim(inputs.clientChallenge()),
        trim(inputs.serverChallenge()),
        trim(inputs.sessionHex()),
        normalizeHex(inputs.pinHashHex()),
        normalizeHex(inputs.timestampHex()),
        inputs.counter());
  }

  public static VerificationCommand inline(InlineInputs inputs) {
    Objects.requireNonNull(inputs, "inputs");
    return new VerificationCommand.Inline(
        trim(inputs.identifier()),
        trimRequired(inputs.suite(), "suite"),
        normalizeHexRequired(inputs.sharedSecretHex(), "sharedSecretHex"),
        trimRequired(inputs.otp(), "otp"),
        trim(inputs.challenge()),
        trim(inputs.clientChallenge()),
        trim(inputs.serverChallenge()),
        trim(inputs.sessionHex()),
        normalizeHex(inputs.pinHashHex()),
        normalizeHex(inputs.timestampHex()),
        inputs.counter(),
        inputs.allowedDrift());
  }

  private static String trim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String trimRequired(String value, String field) {
    String trimmed = trim(value);
    if (trimmed == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return trimmed;
  }

  private static String normalizeHex(String value) {
    String trimmed = trim(value);
    if (trimmed == null) {
      return null;
    }
    return trimmed.replace(" ", "");
  }

  private static String normalizeHexRequired(String value, String field) {
    String normalized = normalizeHex(value);
    if (normalized == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    return normalized;
  }

  public record StoredInputs(
      String credentialId,
      String otp,
      String challenge,
      String clientChallenge,
      String serverChallenge,
      String sessionHex,
      String pinHashHex,
      String timestampHex,
      Long counter) {
    // Data carrier for stored OCRA verification inputs.
  }

  public record InlineInputs(
      String identifier,
      String suite,
      String sharedSecretHex,
      String otp,
      String challenge,
      String clientChallenge,
      String serverChallenge,
      String sessionHex,
      String pinHashHex,
      String timestampHex,
      Long counter,
      Duration allowedDrift) {
    // Data carrier for inline OCRA verification inputs.
  }
}
