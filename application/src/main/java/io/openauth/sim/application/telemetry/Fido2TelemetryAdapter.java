package io.openauth.sim.application.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds canonical telemetry frames for FIDO2/WebAuthn flows. */
public final class Fido2TelemetryAdapter {

  private final String event;

  public Fido2TelemetryAdapter(String event) {
    this.event = Objects.requireNonNull(event, "event");
  }

  public TelemetryFrame success(String telemetryId, Map<String, Object> fields) {
    return status("success", telemetryId, "validated", true, null, fields);
  }

  public TelemetryFrame invalid(
      String telemetryId, String reasonCode, String reason, Map<String, Object> fields) {
    return status("invalid", telemetryId, reasonCode, true, reason, fields);
  }

  public TelemetryFrame error(
      String telemetryId, String reasonCode, String reason, Map<String, Object> fields) {
    return status("error", telemetryId, reasonCode, true, reason, fields);
  }

  public TelemetryFrame status(
      String status,
      String telemetryId,
      String reasonCode,
      boolean sanitized,
      String reason,
      Map<String, Object> fields) {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(telemetryId, "telemetryId");

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("telemetryId", telemetryId);

    if (fields != null && !fields.isEmpty()) {
      payload.putAll(new LinkedHashMap<>(fields));
    }

    if (reason != null && !reason.isBlank()) {
      payload.put("reason", reason.trim().replaceAll("\\s+", " "));
    }

    if (reasonCode != null && !reasonCode.isBlank()) {
      payload.putIfAbsent("reasonCode", reasonCode);
    } else {
      payload.putIfAbsent("reasonCode", "unspecified");
    }

    payload.putIfAbsent("sanitized", sanitized);

    return new TelemetryFrame(event, status, sanitized, payload);
  }
}
