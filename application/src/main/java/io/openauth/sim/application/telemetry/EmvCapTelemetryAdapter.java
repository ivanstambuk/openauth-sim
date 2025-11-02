package io.openauth.sim.application.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Builds telemetry frames for EMV/CAP evaluation flows. */
public final class EmvCapTelemetryAdapter {

    private final String event;

    EmvCapTelemetryAdapter(String event) {
        this.event = Objects.requireNonNull(event, "event");
    }

    public TelemetryFrame status(
            String status,
            String telemetryId,
            String reasonCode,
            boolean sanitized,
            String reason,
            Map<String, Object> fields) {
        return frame(status, telemetryId, reasonCode, sanitized, reason, fields);
    }

    private TelemetryFrame frame(
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
