package io.openauth.sim.rest.ocra;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

@Component
class OcraEvaluationTelemetry {

  private static final Logger LOGGER = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  static {
    LOGGER.setLevel(Level.ALL);
  }

  void recordSuccess(
      String telemetryId,
      String suite,
      boolean hasCredentialReference,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      long durationMillis) {
    log(
        Level.INFO,
        "success",
        telemetryId,
        suite,
        hasCredentialReference,
        hasSession,
        hasClientChallenge,
        hasServerChallenge,
        hasPin,
        hasTimestamp,
        "success",
        null,
        true,
        durationMillis);
  }

  void recordValidationFailure(
      String telemetryId,
      String suite,
      boolean hasCredentialReference,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      String reasonCode,
      String reason,
      boolean sanitized,
      long durationMillis) {
    log(
        Level.WARNING,
        "invalid",
        telemetryId,
        suite,
        hasCredentialReference,
        hasSession,
        hasClientChallenge,
        hasServerChallenge,
        hasPin,
        hasTimestamp,
        reasonCode,
        reason,
        sanitized,
        durationMillis);
  }

  void recordError(
      String telemetryId,
      String suite,
      boolean hasCredentialReference,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      String reasonCode,
      String reason,
      boolean sanitized,
      long durationMillis) {
    log(
        Level.SEVERE,
        "error",
        telemetryId,
        suite,
        hasCredentialReference,
        hasSession,
        hasClientChallenge,
        hasServerChallenge,
        hasPin,
        hasTimestamp,
        reasonCode,
        reason,
        sanitized,
        durationMillis);
  }

  private void log(
      Level level,
      String status,
      String telemetryId,
      String suite,
      boolean hasCredentialReference,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      String reasonCode,
      String reason,
      boolean sanitized,
      long durationMillis) {
    LogRecord record =
        new LogRecord(
            level,
            buildMessage(
                status,
                telemetryId,
                suite,
                hasCredentialReference,
                hasSession,
                hasClientChallenge,
                hasServerChallenge,
                hasPin,
                hasTimestamp,
                reasonCode,
                sanitize(reason),
                sanitized,
                durationMillis));

    LOGGER.log(record);
    for (Handler handler : LOGGER.getHandlers()) {
      handler.publish(record);
      handler.flush();
    }
  }

  private static String buildMessage(
      String status,
      String telemetryId,
      String suite,
      boolean hasCredentialReference,
      boolean hasSession,
      boolean hasClientChallenge,
      boolean hasServerChallenge,
      boolean hasPin,
      boolean hasTimestamp,
      String reasonCode,
      String reason,
      boolean sanitized,
      long durationMillis) {
    String base =
        String.format(
            Locale.ROOT,
            "event=rest.ocra.evaluate status=%s telemetryId=%s suite=%s hasCredentialReference=%s "
                + "hasSessionPayload=%s hasClientChallenge=%s hasServerChallenge=%s hasPin=%s hasTimestamp=%s durationMillis=%d",
            status,
            Objects.requireNonNullElse(telemetryId, "unknown"),
            Objects.requireNonNullElse(suite, "unknown"),
            hasCredentialReference,
            hasSession,
            hasClientChallenge,
            hasServerChallenge,
            hasPin,
            hasTimestamp,
            durationMillis);
    base +=
        String.format(
            Locale.ROOT,
            " reasonCode=%s sanitized=%s",
            Objects.requireNonNullElse(reasonCode, "unspecified"),
            sanitized);
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
