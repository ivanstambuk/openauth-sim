package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraVerificationTelemetryTest {

  private final Logger logger = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");
  private final CapturingHandler handler = new CapturingHandler();
  private final OcraVerificationTelemetry telemetry = new OcraVerificationTelemetry();
  private final OcraVerificationAuditContext auditContext =
      new OcraVerificationAuditContext("telemetry-test", "client-1", "operator-1");

  @BeforeEach
  void setUp() {
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
  }

  @AfterEach
  void tearDown() {
    logger.removeHandler(handler);
    handler.records.clear();
  }

  @Test
  @DisplayName("validation failure telemetry includes supplied reason message")
  void validationFailureIncludesReason() {
    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            "telemetry-1",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "stored",
            "credential-1",
            "otp-hash",
            "context-hash",
            "validation_failure",
            "invalid",
            5L,
            true);

    telemetry.recordValidationFailure(auditContext, frame, "input missing");

    assertTrue(handler.contains("reason=input missing"));
  }

  @Test
  @DisplayName("mismatch telemetry omits reason when none supplied")
  void mismatchTelemetryOmitsReasonWhenNull() {
    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            "telemetry-2",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "stored",
            "credential-2",
            "otp-hash",
            "context-hash",
            "strict_mismatch",
            "mismatch",
            7L,
            true);

    telemetry.recordMismatch(auditContext, frame);

    assertFalse(handler.contains("reason="));
  }

  @Test
  @DisplayName("credential_not_found telemetry includes sanitized reason")
  void credentialNotFoundIncludesReason() {
    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            "telemetry-3",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "stored",
            "credential-3",
            "otp-hash",
            "context-hash",
            "credential_not_found",
            "invalid",
            11L,
            true);

    telemetry.recordCredentialNotFound(auditContext, frame, "credential missing");

    assertTrue(handler.contains("status=invalid"));
    assertTrue(handler.contains("reason=credential missing"));
  }

  @Test
  @DisplayName("unexpected error telemetry marks sanitized=false")
  void unexpectedErrorMarksUnsanitized() {
    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            "telemetry-4",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "stored",
            "credential-4",
            "otp-hash",
            "context-hash",
            "unexpected_error",
            "error",
            13L,
            false);

    telemetry.recordUnexpectedError(auditContext, frame, "boom");

    assertTrue(handler.contains("status=error"));
    assertTrue(handler.contains("sanitized=false"));
    assertTrue(handler.contains("reason=boom"));
  }

  @Test
  @DisplayName("unexpected error omits reason when blank")
  void unexpectedErrorOmitsBlankReason() {
    OcraVerificationTelemetry.TelemetryFrame frame =
        new OcraVerificationTelemetry.TelemetryFrame(
            "telemetry-5",
            "OCRA-1:HOTP-SHA1-6:QN08",
            "inline",
            "credential-5",
            "otp-hash",
            "context-hash",
            "unexpected_error",
            "error",
            21L,
            false);

    telemetry.recordUnexpectedError(auditContext, frame, "   ");

    assertFalse(handler.contains("reason="));
  }

  private static final class CapturingHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public void close() {
      records.clear();
    }

    boolean contains(String fragment) {
      return records.stream().map(LogRecord::getMessage).anyMatch(msg -> msg.contains(fragment));
    }
  }
}
