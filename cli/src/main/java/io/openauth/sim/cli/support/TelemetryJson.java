package io.openauth.sim.cli.support;

import io.openauth.sim.application.telemetry.TelemetryFrame;
import java.util.LinkedHashMap;
import java.util.Map;

/** Utility helpers for building JSON-friendly telemetry responses. */
public final class TelemetryJson {
    private TelemetryJson() {}

    public static Map<String, Object> response(String event, TelemetryFrame frame, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("status", frame.status());
        Object reasonCode = frame.fields().get("reasonCode");
        if (reasonCode == null && data != null) {
            reasonCode = data.get("reasonCode");
        }
        if (reasonCode != null) {
            payload.put("reasonCode", reasonCode);
        }
        Object telemetryId = frame.fields().get("telemetryId");
        if (telemetryId != null) {
            payload.put("telemetryId", telemetryId);
        }
        payload.put("sanitized", frame.sanitized());
        if (data != null && !data.isEmpty()) {
            payload.put("data", data);
        }
        return payload;
    }
}
