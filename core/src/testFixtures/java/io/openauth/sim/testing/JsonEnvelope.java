package io.openauth.sim.testing;

import io.openauth.sim.core.json.SimpleJson;
import java.util.Map;
import java.util.Objects;

public record JsonEnvelope(Map<String, Object> raw) {

    public JsonEnvelope {
        raw = raw == null ? Map.of() : raw;
    }

    public static JsonEnvelope parse(String json) {
        Objects.requireNonNull(json, "json");
        Object parsed = SimpleJson.parse(json);
        return new JsonEnvelope(asMap(parsed));
    }

    public String event() {
        return text(raw.get("event"));
    }

    public String status() {
        return text(raw.get("status"));
    }

    public String reasonCode() {
        return text(raw.get("reasonCode"));
    }

    public String telemetryId() {
        Object value = raw.get("telemetryId");
        return value == null ? null : String.valueOf(value);
    }

    public boolean telemetryIdPresent() {
        return telemetryId() != null;
    }

    public Map<String, Object> data() {
        return asMap(raw.get("data"));
    }

    public Object dataValue(String key) {
        Objects.requireNonNull(key, "key");
        return data().get(key);
    }

    public String dataString(String key) {
        Object value = dataValue(key);
        return value == null ? null : String.valueOf(value);
    }

    public Long dataLong(String key) {
        Object value = dataValue(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return Long.parseLong(text);
    }

    public boolean dataBoolean(String key) {
        Object value = dataValue(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public boolean tracePresent() {
        return data().get("trace") != null;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        throw new IllegalArgumentException(
                "Expected JSON object but was: " + value.getClass().getSimpleName());
    }
}
