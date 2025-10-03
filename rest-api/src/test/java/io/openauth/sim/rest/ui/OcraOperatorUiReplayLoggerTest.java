package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraOperatorUiReplayLoggerTest {

  @Test
  @DisplayName("record emits match telemetry with mode and fingerprint")
  void recordMatchTelemetry() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-1",
            "match",
            "match",
            null,
            "stored",
            "stored",
            "match",
            "hash-value",
            Boolean.TRUE);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getLevel()).isEqualTo(Level.INFO);
    assertThat(record.getMessage())
        .contains("event=ui.ocra.replay")
        .contains("status=match")
        .contains("telemetryId=ui-telemetry-1")
        .contains("mode=stored")
        .contains("contextFingerprint=hash-value");
  }

  @Test
  @DisplayName("record validation failure emits sanitized flag and reason code")
  void recordValidationTelemetry() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-2",
            "invalid",
            "otp_missing",
            "OTP is required",
            "inline",
            "inline",
            "invalid",
            null,
            Boolean.TRUE);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=invalid")
        .contains("reasonCode=otp_missing")
        .contains("sanitized=true")
        .contains("mode=inline");
  }

  @Test
  @DisplayName("record invalid status derives default reason fields when absent")
  void recordInvalidWithoutReasonCode() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-7", "invalid", null, null, "inline", "inline", "invalid", null, null);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=invalid")
        .contains("reasonCode=validation_error")
        .contains("sanitized=true")
        .contains("mode=inline");
  }

  @Test
  @DisplayName("record error telemetry when UI reports network failure")
  void recordErrorTelemetry() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-3",
            "error",
            "network_error",
            "Network unavailable",
            "stored",
            "stored",
            "error",
            "unavailable",
            Boolean.FALSE);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=error")
        .contains("reasonCode=network_error")
        .contains("sanitized=false")
        .contains("mode=stored");
  }

  @Test
  @DisplayName("record error status derives default reason when absent")
  void recordErrorWithoutReason() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-8", "error", null, null, "stored", "stored", "error", null, null);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=error")
        .contains("reasonCode=unexpected_error")
        .contains("reason=Replay request failed")
        .contains("mode=stored");
  }

  @Test
  @DisplayName("record handles missing status by emitting unknown frame")
  void recordNullStatusTelemetry() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-4",
            null,
            null,
            null,
            "stored",
            "stored",
            "unknown",
            "unavailable",
            Boolean.TRUE);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=unknown")
        .contains("mode=stored")
        .contains("telemetryId=ui-telemetry-4");
  }

  @Test
  @DisplayName("record mismatch telemetry uses shared verification adapter")
  void recordMismatchTelemetry() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-5",
            "mismatch",
            "strict_mismatch",
            null,
            "inline",
            "inline",
            "mismatch",
            "hash",
            Boolean.TRUE);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=mismatch")
        .contains("reasonCode=strict_mismatch")
        .contains("mode=inline");
  }

  @Test
  @DisplayName("record success status falls back to shared adapter defaults")
  void recordSuccessStatusTelemetry() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            "ui-telemetry-6",
            "success",
            null,
            null,
            "stored",
            "stored",
            "match",
            "hash",
            Boolean.TRUE);

    telemetry.record(request);

    LogRecord record = handler.lastRecord.get();
    assertThat(record).isNotNull();
    assertThat(record.getMessage())
        .contains("status=success")
        .contains("reasonCode=success")
        .contains("mode=stored");
  }

  @Test
  @DisplayName("record skips logging when telemetryId missing")
  void recordSkipsWhenTelemetryMissing() {
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);

    OcraOperatorUiReplayLogger telemetry =
        new OcraOperatorUiReplayLogger(TelemetryContracts.ocraVerificationAdapter(), logger);

    OcraReplayUiEventRequest request =
        new OcraReplayUiEventRequest(
            null, "match", "match", null, "stored", "stored", "match", "", Boolean.TRUE);

    telemetry.record(request);

    assertThat(handler.lastRecord.get()).isNull();
  }

  private static final class TestHandler extends Handler {
    private final AtomicReference<LogRecord> lastRecord = new AtomicReference<>();

    @Override
    public void publish(LogRecord record) {
      lastRecord.set(record);
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public void close() {
      // no-op
    }
  }
}
