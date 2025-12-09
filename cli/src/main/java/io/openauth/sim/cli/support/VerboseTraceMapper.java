package io.openauth.sim.cli.support;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.LinkedHashMap;
import java.util.Map;

/** Maps {@link VerboseTrace} instances into JSON-friendly structures. */
public final class VerboseTraceMapper {
    private VerboseTraceMapper() {}

    public static Map<String, Object> toMap(VerboseTrace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", trace.operation());
        payload.put("metadata", trace.metadata());
        payload.put(
                "steps",
                trace.steps().stream().map(VerboseTraceMapper::mapTraceStep).toList());
        return payload;
    }

    private static Map<String, Object> mapTraceStep(VerboseTrace.TraceStep step) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", step.id());
        payload.put("summary", step.summary());
        if (hasText(step.detail())) {
            payload.put("detail", step.detail());
        }
        if (hasText(step.specAnchor())) {
            payload.put("spec", step.specAnchor());
        }
        payload.put("attributes", step.attributes());
        payload.put(
                "orderedAttributes",
                step.typedAttributes().stream()
                        .map(attribute -> Map.of(
                                "type", attribute.type().label(),
                                "name", attribute.name(),
                                "value", String.valueOf(attribute.value())))
                        .toList());
        payload.put("notes", step.notes());
        return payload;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
