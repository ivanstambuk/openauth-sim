package io.openauth.sim.application.telemetry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical telemetry payload shared across facades. */
public record TelemetryFrame(String event, String status, boolean sanitized, Map<String, Object> fields) {

    public TelemetryFrame {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(fields, "fields");
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
