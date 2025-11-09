package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.emv.cap.EmvCapTraceProvenanceSchema;
import io.openauth.sim.core.json.SimpleJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EmvCliTraceAssertions {

    private EmvCliTraceAssertions() {
        // no instances
    }

    static void assertTraceSchema(Map<String, Object> trace) {
        List<String> missing = EmvCapTraceProvenanceSchema.missingFields(trace);
        assertTrue(missing.isEmpty(), () -> "Trace schema mismatch: " + missing);
    }

    static void assertMatchesFixture(Map<String, Object> trace) {
        assertEquals(traceFixture(), trace, "Trace payload should match the published fixture");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> traceFixture() {
        Object parsed = SimpleJson.parse(EmvCapTraceProvenanceSchema.rawJson());
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IllegalStateException("Trace fixture must be a JSON object");
        }
        Object trace = ((Map<String, Object>) root).get("trace");
        if (!(trace instanceof Map<?, ?> traceMap)) {
            throw new IllegalStateException("Trace fixture is missing a 'trace' object");
        }
        return deepCopyObject((Map<?, ?>) traceMap);
    }

    private static Map<String, Object> deepCopyObject(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            copy.put(key, deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyObject(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) {
                copy.add(deepCopyValue(element));
            }
            return copy;
        }
        return value;
    }
}
