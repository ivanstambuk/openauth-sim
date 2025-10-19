package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.Map;

final class TelemetryOutputAssertions {

    private TelemetryOutputAssertions() {
        throw new AssertionError("No instances");
    }

    static Map<String, String> telemetryLine(String output, String event) {
        String[] lines = output.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("event=")) {
                continue;
            }
            Map<String, String> parsed = parseTelemetry(trimmed);
            String actualEvent = parsed.get("event");
            if (event.equals(actualEvent)) {
                return parsed;
            }
        }
        fail("No telemetry line found for event=" + event + " in output:\n" + output);
        return Map.of();
    }

    private static Map<String, String> parseTelemetry(String line) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String token : line.split("\\s+")) {
            int separator = token.indexOf('=');
            if (separator <= 0 || separator == token.length() - 1) {
                continue;
            }
            String key = token.substring(0, separator);
            String value = token.substring(separator + 1);
            values.put(key, value);
        }
        assertNotNull(values.get("event"), "Telemetry line missing event token: " + line);
        return values;
    }
}
