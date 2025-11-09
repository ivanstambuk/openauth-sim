package io.openauth.sim.core.emv.cap;

import io.openauth.sim.core.json.SimpleJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads the canonical EMV/CAP trace provenance example (defined in the specification) and exposes
 * helpers so tests can demand that responses emit the complete schema.
 */
public final class EmvCapTraceProvenanceSchema {

    private static final String RAW_JSON = readFixture();
    private static final Map<String, Object> TRACE_SCHEMA = loadSchema();

    private EmvCapTraceProvenanceSchema() {
        throw new AssertionError("Utility class");
    }

    /** Return the canonical trace schema map (deeply unmodifiable). */
    public static Map<String, Object> traceSchema() {
        return TRACE_SCHEMA;
    }

    /** Return the raw JSON text for the reference trace fixture. */
    public static String rawJson() {
        return RAW_JSON;
    }

    /**
     * Compare the provided trace object against the canonical schema and return any missing field
     * paths (dot/bracket notation). An empty list indicates that the trace contains every required
     * key.
     */
    public static List<String> missingFields(Map<String, Object> actualTrace) {
        Objects.requireNonNull(actualTrace, "actualTrace");
        List<String> missing = new ArrayList<>();
        assertObject("trace", TRACE_SCHEMA, actualTrace, missing);
        return List.copyOf(missing);
    }

    private static void assertObject(
            String path, Map<String, Object> expected, Map<String, Object> actual, List<String> missing) {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = actual.get(key);
            if (actualValue == null) {
                missing.add(path + "." + key);
                continue;
            }
            if (expectedValue instanceof Map<?, ?> expectedMap) {
                Optional<Map<String, Object>> actualMap = castMap(actualValue);
                if (actualMap.isEmpty()) {
                    missing.add(path + "." + key);
                    continue;
                }
                Map<String, Object> expectedSchema = castMap(expectedMap)
                        .orElseThrow(() -> new IllegalStateException("Trace schema objects must use string keys"));
                assertObject(path + "." + key, expectedSchema, actualMap.get(), missing);
            } else if (expectedValue instanceof List<?> expectedList) {
                Optional<List<?>> actualList = castList(actualValue);
                if (actualList.isEmpty()) {
                    missing.add(path + "." + key);
                    continue;
                }
                assertList(path + "." + key, expectedList, actualList.get(), missing);
            }
        }
    }

    private static void assertList(String path, List<?> expected, List<?> actual, List<String> missing) {
        if (expected.isEmpty() || actual.isEmpty()) {
            return;
        }
        Object expectedSample = expected.get(0);
        if (expectedSample instanceof Map<?, ?> expectedMap) {
            Map<String, Object> sampleSchema = castMap(expectedMap)
                    .orElseThrow(
                            () -> new IllegalStateException("Trace schema arrays must contain string-keyed objects"));
            for (int index = 0; index < actual.size(); index++) {
                Object element = actual.get(index);
                Optional<Map<String, Object>> actualElement = castMap(element);
                if (actualElement.isEmpty()) {
                    missing.add(path + "[" + index + "]");
                    continue;
                }
                assertObject(path + "[" + index + "]", sampleSchema, actualElement.get(), missing);
            }
        }
    }

    private static Map<String, Object> loadSchema() {
        Object parsed = SimpleJson.parse(RAW_JSON);
        Map<String, Object> root = castMap(parsed)
                .orElseThrow(() -> new IllegalStateException("Trace provenance fixture must contain a 'trace' object"));
        if (!root.containsKey("trace")) {
            throw new IllegalStateException("Trace provenance fixture must contain a 'trace' object");
        }
        Map<String, Object> trace = castMap(root.get("trace"))
                .orElseThrow(
                        () -> new IllegalStateException("Trace provenance fixture 'trace' entry must be an object"));
        return Collections.unmodifiableMap(deepCopy(trace));
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> child = castMap(mapValue)
                        .orElseThrow(() -> new IllegalStateException("Trace schema objects must use string keys"));
                copy.put(entry.getKey(), Collections.unmodifiableMap(deepCopy(child)));
            } else if (value instanceof List<?> listValue) {
                copy.put(entry.getKey(), Collections.unmodifiableList(deepCopyList(listValue)));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private static List<Object> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>(source.size());
        for (Object value : source) {
            if (value instanceof Map<?, ?> mapValue) {
                Map<String, Object> child = castMap(mapValue)
                        .orElseThrow(() ->
                                new IllegalStateException("Trace schema arrays must contain string-keyed objects"));
                copy.add(Collections.unmodifiableMap(deepCopy(child)));
            } else if (value instanceof List<?> listValue) {
                copy.add(Collections.unmodifiableList(deepCopyList(listValue)));
            } else {
                copy.add(value);
            }
        }
        return copy;
    }

    private static Optional<Map<String, Object>> castMap(Object candidate) {
        if (!(candidate instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Map<String, Object> cast = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String stringKey)) {
                continue;
            }
            cast.put(stringKey, entry.getValue());
        }
        return Optional.of(cast);
    }

    private static Optional<List<?>> castList(Object candidate) {
        if (candidate instanceof List<?> list) {
            return Optional.of(list);
        }
        return Optional.empty();
    }

    private static String readFixture() {
        Path path = resolveFixturePath();
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read trace provenance fixture", ex);
        }
    }

    private static Path resolveFixturePath() {
        Path path = Path.of("docs", "test-vectors", "emv-cap", "trace-provenance-example.json");
        if (Files.exists(path)) {
            return path;
        }
        Path modulePath = Path.of("..", "docs", "test-vectors", "emv-cap", "trace-provenance-example.json");
        if (Files.exists(modulePath)) {
            return modulePath;
        }
        throw new IllegalStateException("Unable to locate trace-provenance-example.json fixture");
    }
}
