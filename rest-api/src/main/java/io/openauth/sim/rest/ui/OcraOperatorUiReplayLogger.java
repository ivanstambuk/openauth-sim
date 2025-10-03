package io.openauth.sim.rest.ui;

import io.openauth.sim.application.telemetry.OcraTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

@Component
final class OcraOperatorUiReplayLogger {

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.ui.telemetry");

  private final OcraTelemetryAdapter adapter;
  private final Logger logger;

  OcraOperatorUiReplayLogger() {
    this(TelemetryContracts.ocraVerificationAdapter(), TELEMETRY_LOGGER);
  }

  OcraOperatorUiReplayLogger(OcraTelemetryAdapter adapter, Logger logger) {
    this.adapter = Objects.requireNonNull(adapter, "adapter");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.logger.setLevel(Level.ALL);
  }

  void record(OcraReplayUiEventRequest request) {
    Objects.requireNonNull(request, "request");
    String telemetryId = normalize(request.telemetryId());
    if (telemetryId == null) {
      return;
    }

    String status = normalize(request.status());
    String reasonCode = normalize(request.reasonCode());
    boolean sanitized = request.sanitized() != null ? request.sanitized() : true;
    String reason = normalize(request.reason());

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("origin", "ui");
    fields.put("uiView", "replay");
    putIfPresent(fields, "mode", normalize(request.mode()));
    putIfPresent(fields, "credentialSource", normalize(request.credentialSource()));
    putIfPresent(fields, "outcome", normalize(request.outcome()));
    putIfPresent(fields, "contextFingerprint", normalize(request.contextFingerprint()));

    TelemetryFrame frame = createFrame(status, telemetryId, reasonCode, reason, sanitized, fields);
    logFrame(frame);
  }

  private TelemetryFrame createFrame(
      String status,
      String telemetryId,
      String reasonCode,
      String reason,
      boolean sanitized,
      Map<String, Object> fields) {

    String normalizedStatus = status != null && !status.isBlank() ? status : "unknown";

    String normalizedReasonCode = reasonCode != null && !reasonCode.isBlank() ? reasonCode : null;
    String normalizedReason = reason != null && !reason.isBlank() ? reason.trim() : null;

    if (normalizedReasonCode == null) {
      if ("invalid".equals(normalizedStatus)) {
        normalizedReasonCode = "validation_error";
      } else if ("error".equals(normalizedStatus)) {
        normalizedReasonCode = "unexpected_error";
      } else if (!"unknown".equals(normalizedStatus)) {
        normalizedReasonCode = normalizedStatus;
      }
    }

    if (normalizedReason == null) {
      if ("invalid".equals(normalizedStatus)) {
        normalizedReason = "Replay payload invalid";
      } else if ("error".equals(normalizedStatus)) {
        normalizedReason = "Replay request failed";
      }
    }

    return adapter.status(
        normalizedStatus, telemetryId, normalizedReasonCode, sanitized, normalizedReason, fields);
  }

  private void logFrame(TelemetryFrame frame) {
    Map<String, Object> fields = frame.fields();
    StringBuilder builder =
        new StringBuilder("event=ui.ocra.replay status=").append(frame.status()).append(' ');
    builder.append("telemetryId=").append(fields.getOrDefault("telemetryId", "unknown"));
    builder.append(' ').append("reasonCode=").append(fields.getOrDefault("reasonCode", "unknown"));
    builder.append(' ').append("sanitized=").append(frame.sanitized());

    fields.forEach(
        (key, value) -> {
          if ("telemetryId".equals(key) || "reasonCode".equals(key)) {
            return;
          }
          builder.append(' ').append(key).append('=').append(value);
        });

    LogRecord record = new LogRecord(Level.INFO, builder.toString());
    logger.log(record);
    for (Handler handler : logger.getHandlers()) {
      handler.publish(record);
      handler.flush();
    }
  }

  private static void putIfPresent(Map<String, Object> fields, String key, String value) {
    if (value != null && !value.isEmpty()) {
      fields.put(key, value);
    }
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
