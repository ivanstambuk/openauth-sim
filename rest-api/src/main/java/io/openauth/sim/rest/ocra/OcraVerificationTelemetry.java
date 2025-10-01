package io.openauth.sim.rest.ocra;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

@Component
class OcraVerificationTelemetry {

  private static final Logger LOGGER = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  void recordMatch(OcraVerificationAuditContext context, TelemetryFrame frame) {
    log(Level.INFO, 200, "match", context, frame, null);
  }

  void recordMismatch(OcraVerificationAuditContext context, TelemetryFrame frame) {
    log(Level.INFO, 200, "mismatch", context, frame, null);
  }

  void recordValidationFailure(
      OcraVerificationAuditContext context, TelemetryFrame frame, String reason) {
    log(Level.WARNING, 422, "invalid", context, frame, reason);
  }

  void recordCredentialNotFound(
      OcraVerificationAuditContext context, TelemetryFrame frame, String reason) {
    log(Level.WARNING, 404, "invalid", context, frame, reason);
  }

  void recordUnexpectedError(
      OcraVerificationAuditContext context, TelemetryFrame frame, String reason) {
    log(Level.SEVERE, 500, "error", context, frame, reason);
  }

  private void log(
      Level level,
      int httpStatus,
      String status,
      OcraVerificationAuditContext context,
      TelemetryFrame frame,
      String reason) {

    StringBuilder builder =
        new StringBuilder("event=rest.ocra.verify")
            .append(' ')
            .append("status=")
            .append(status)
            .append(' ')
            .append("outcome=")
            .append(frame.outcome())
            .append(' ')
            .append("reasonCode=")
            .append(frame.reasonCode())
            .append(' ')
            .append("telemetryId=")
            .append(frame.telemetryId())
            .append(' ')
            .append("credentialSource=")
            .append(frame.credentialSource())
            .append(' ')
            .append("credentialId=")
            .append(Objects.requireNonNullElse(frame.credentialId(), "unknown"))
            .append(' ')
            .append("otpHash=")
            .append(Objects.requireNonNullElse(frame.otpHash(), "unavailable"))
            .append(' ')
            .append("contextFingerprint=")
            .append(Objects.requireNonNullElse(frame.contextFingerprint(), "unavailable"))
            .append(' ')
            .append("sanitized=")
            .append(frame.sanitized())
            .append(' ')
            .append("durationMillis=")
            .append(frame.durationMillis())
            .append(' ')
            .append("httpStatus=")
            .append(httpStatus)
            .append(' ')
            .append("requestId=")
            .append(Objects.requireNonNullElse(context.requestId(), "rest-ocra-request-unknown"))
            .append(' ')
            .append("clientId=")
            .append(Objects.requireNonNullElse(context.clientId(), "unspecified"))
            .append(' ')
            .append("operator=")
            .append(context.resolvedOperatorPrincipal());

    if (reason != null && !reason.isBlank()) {
      builder.append(' ').append("reason=").append(reason.trim().replaceAll("\\s+", " "));
    }

    LOGGER.log(level, builder.toString());
  }

  record TelemetryFrame(
      String telemetryId,
      String suite,
      String credentialSource,
      String credentialId,
      String otpHash,
      String contextFingerprint,
      String reasonCode,
      String outcome,
      long durationMillis,
      boolean sanitized) {

    TelemetryFrame {
      Objects.requireNonNull(telemetryId, "telemetryId");
      Objects.requireNonNull(credentialSource, "credentialSource");
      Objects.requireNonNull(reasonCode, "reasonCode");
      Objects.requireNonNull(outcome, "outcome");
    }

    String normalizedSuite() {
      return suite == null ? "unknown" : suite;
    }

    TelemetryFrame withReasonCode(String updatedReason) {
      return new TelemetryFrame(
          telemetryId,
          suite,
          credentialSource,
          credentialId,
          otpHash,
          contextFingerprint,
          Objects.requireNonNullElse(updatedReason, reasonCode),
          outcome,
          durationMillis,
          sanitized);
    }

    TelemetryFrame withOutcome(String updatedOutcome) {
      return new TelemetryFrame(
          telemetryId,
          suite,
          credentialSource,
          credentialId,
          otpHash,
          contextFingerprint,
          reasonCode,
          Objects.requireNonNullElse(updatedOutcome, outcome),
          durationMillis,
          sanitized);
    }
  }
}
