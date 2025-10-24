package io.openauth.sim.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.openauth.sim.core.trace.VerboseTrace;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "VerboseTracePayload",
        description =
                "Deterministic, ordered diagnostic trace emitted when verbose mode is enabled for a credential workflow.")
public record VerboseTracePayload(
        @Schema(description = "Stable identifier describing the traced operation (for example, totp.evaluate.stored).")
        String operation,

        @Schema(
                description = "Additional metadata associated with the trace.",
                additionalPropertiesSchema = String.class)
        Map<String, String> metadata,

        @ArraySchema(
                arraySchema = @Schema(description = "Ordered steps executed during the operation."),
                schema = @Schema(implementation = TraceStepPayload.class))
        List<TraceStepPayload> steps) {

    public static VerboseTracePayload from(VerboseTrace trace) {
        Objects.requireNonNull(trace, "trace");
        Map<String, String> metadataCopy = Collections.unmodifiableMap(new LinkedHashMap<>(trace.metadata()));
        List<TraceStepPayload> stepPayloads =
                trace.steps().stream().map(TraceStepPayload::from).collect(Collectors.toUnmodifiableList());
        return new VerboseTracePayload(trace.operation(), metadataCopy, stepPayloads);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            name = "VerboseTraceStepPayload",
            description = "Single diagnostic entry representing one action performed while handling the request.")
    public record TraceStepPayload(
            @Schema(description = "Stable identifier that enables downstream tooling to group similar steps.")
            String id,

            @Schema(description = "Human-readable summary of what the step accomplished.")
            String summary,

            @Schema(description = "Additional detail about the specific helper or component invoked.")
            String detail,

            @Schema(description = "Reference to the governing specification section when applicable.")
            String spec,

            @Schema(
                    description = "Arbitrary attributes captured for the step, grouped by attribute type.",
                    additionalPropertiesSchema = Object.class)
            Map<String, Object> attributes,

            @ArraySchema(
                    arraySchema = @Schema(description = "Ordered attribute entries preserved in capture order."),
                    schema = @Schema(implementation = TraceAttributePayload.class))
            List<TraceAttributePayload> orderedAttributes,

            @Schema(
                    description = "Free-form notes associated with the step.",
                    additionalPropertiesSchema = String.class)
            Map<String, String> notes) {

        static TraceStepPayload from(VerboseTrace.TraceStep step) {
            Objects.requireNonNull(step, "step");
            Map<String, Object> groupedAttributes = new LinkedHashMap<>();
            step.typedAttributes().forEach(attribute -> {
                String type = attribute.type().label();
                @SuppressWarnings("unchecked")
                Map<String, Object> bucket =
                        (Map<String, Object>) groupedAttributes.computeIfAbsent(type, key -> new LinkedHashMap<>());
                bucket.put(attribute.name(), attribute.value());
            });
            Map<String, Object> attributesCopy = Collections.unmodifiableMap(groupedAttributes);
            List<TraceAttributePayload> orderedAttributes = step.typedAttributes().stream()
                    .map(attribute -> new TraceAttributePayload(
                            attribute.type().label(), attribute.name(), formatAttributeValue(attribute.value())))
                    .collect(Collectors.toUnmodifiableList());
            Map<String, String> notesCopy = Collections.unmodifiableMap(new LinkedHashMap<>(step.notes()));
            return new TraceStepPayload(
                    step.id(),
                    step.summary(),
                    step.detail(),
                    step.specAnchor(),
                    attributesCopy,
                    orderedAttributes,
                    notesCopy);
        }
    }

    @Schema(
            name = "VerboseTraceAttributePayload",
            description = "Ordered attribute entry describing a captured value for a verbose trace step.")
    public record TraceAttributePayload(
            @Schema(description = "Attribute type (string, int, hex, etc.).")
            String type,

            @Schema(description = "Attribute name.") String name,

            @Schema(description = "Formatted attribute value.")
            String value) {

        public TraceAttributePayload {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    private static String formatAttributeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] bytes) {
            return HexFormatter.of(bytes);
        }
        if (value.getClass().isArray()) {
            return value.toString();
        }
        return String.valueOf(value);
    }

    private static final class HexFormatter {
        private static final char[] HEX = "0123456789abcdef".toCharArray();

        private HexFormatter() {
            // Utility class.
        }

        static String of(byte[] bytes) {
            if (bytes.length == 0) {
                return "";
            }
            char[] chars = new char[bytes.length * 2];
            for (int index = 0; index < bytes.length; index++) {
                int value = bytes[index] & 0xFF;
                chars[index * 2] = HEX[value >>> 4];
                chars[index * 2 + 1] = HEX[value & 0x0F];
            }
            return new String(chars);
        }
    }
}
