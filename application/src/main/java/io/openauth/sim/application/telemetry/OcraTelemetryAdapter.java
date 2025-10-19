package io.openauth.sim.application.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds canonical telemetry frames for OCRA flows. Facades supply contextual fields which are
 * merged with shared attributes (telemetryId, reasonCode, etc.) before emitting the event.
 */
public final class OcraTelemetryAdapter {

    private final String event;

    OcraTelemetryAdapter(String event) {
        this.event = Objects.requireNonNull(event, "event");
    }

    /** Generates a success frame with a default {@code reasonCode} of {@code success}. */
    public TelemetryFrame success(String telemetryId, Map<String, Object> fields) {
        return frame("success", telemetryId, "success", true, null, fields);
    }

    /** Generates a frame for validation failures (status {@code invalid}). */
    public TelemetryFrame validationFailure(
            String telemetryId, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {
        return frame("invalid", telemetryId, reasonCode, sanitized, reason, fields);
    }

    /** Generates a frame for unexpected errors (status {@code error}). */
    public TelemetryFrame error(
            String telemetryId, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {
        return frame("error", telemetryId, reasonCode, sanitized, reason, fields);
    }

    /**
     * Generates a frame for arbitrary statuses (e.g., {@code match}, {@code mismatch}). {@code
     * reasonCode} defaults to {@code unspecified} when absent.
     */
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
