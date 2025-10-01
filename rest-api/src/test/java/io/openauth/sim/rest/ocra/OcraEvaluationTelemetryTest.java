package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OcraEvaluationTelemetryTest {

  private final TestHandler handler = new TestHandler();
  private final Logger logger = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");
  private final OcraEvaluationTelemetry telemetry = new OcraEvaluationTelemetry();

  @BeforeEach
  void attachHandler() {
    logger.addHandler(handler);
    logger.setLevel(Level.ALL);
  }

  @AfterEach
  void detachHandler() {
    logger.removeHandler(handler);
    handler.records.clear();
  }

  @Test
  @DisplayName("telemetry sanitizes null and blank reasons")
  void telemetrySanitizesReasons() {
    telemetry.recordValidationFailure(
        "telemetry", "suite", true, true, false, false, false, false, "reason", null, true, 5L);

    telemetry.recordValidationFailure(
        "telemetry-blank",
        "suite",
        false,
        false,
        false,
        false,
        false,
        false,
        "reason",
        "   ",
        true,
        4L);

    telemetry.recordError(
        "telemetry",
        "suite",
        true,
        false,
        false,
        false,
        false,
        true,
        "unexpected",
        "  reason with   spaces  ",
        false,
        5L);

    assertTrue(
        handler.records.stream()
            .map(LogRecord::getMessage)
            .anyMatch(
                message -> message.contains("sanitized=true") && !message.contains("reason=")));
    assertTrue(
        handler.records.stream()
            .map(LogRecord::getMessage)
            .anyMatch(message -> message.contains("reason=reason with spaces")));
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new java.util.ArrayList<>();

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
  }
}
