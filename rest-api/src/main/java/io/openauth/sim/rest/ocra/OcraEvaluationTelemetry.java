package io.openauth.sim.rest.ocra;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

@Component
class OcraEvaluationTelemetry {

  private static final Logger LOGGER = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  void recordSuccess(
      String telemetryId,
      String suite,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      long durationMillis) {
    LOGGER.log(
        Level.INFO,
        buildMessage(
            "success",
            telemetryId,
            suite,
            hasSession,
            hasClientChallenge,
            hasServerChallenge,
            hasPin,
            hasTimestamp,
            durationMillis,
            null));
  }

  void recordValidationFailure(
      String telemetryId,
      String suite,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      String reason,
      long durationMillis) {
    LOGGER.log(
        Level.WARNING,
        buildMessage(
            "invalid",
            telemetryId,
            suite,
            hasSession,
            hasClientChallenge,
            hasServerChallenge,
            hasPin,
            hasTimestamp,
            durationMillis,
            sanitize(reason)));
  }

  void recordError(
      String telemetryId,
      String suite,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      String reason,
      long durationMillis) {
    LOGGER.log(
        Level.SEVERE,
        buildMessage(
            "error",
            telemetryId,
            suite,
            hasSession,
            hasClientChallenge,
            hasServerChallenge,
            hasPin,
            hasTimestamp,
            durationMillis,
            sanitize(reason)));
  }

  private static String buildMessage(
      String status,
      String telemetryId,
      String suite,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      long durationMillis,
      String reason) {
    String base =
        String.format(
            Locale.ROOT,
            "event=rest.ocra.evaluate status=%s telemetryId=%s suite=%s hasSessionPayload=%s "
                + "hasClientChallenge=%s hasServerChallenge=%s hasPin=%s hasTimestamp=%s durationMillis=%d",
            status,
            Objects.requireNonNullElse(telemetryId, "unknown"),
            Objects.requireNonNullElse(suite, "unknown"),
            hasSession,
            hasClientChallenge,
            hasServerChallenge,
            hasPin,
            hasTimestamp,
            durationMillis);
    if (reason == null) {
      return base;
    }
    return base + " reason=" + reason;
  }

  private static String sanitize(String reason) {
    if (reason == null) {
      return null;
    }
    String trimmed = reason.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.replaceAll("\\s+", " ");
  }
}
