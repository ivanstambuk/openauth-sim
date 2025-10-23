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

            @Schema(
                    description = "Arbitrary attributes captured for the step.",
                    additionalPropertiesSchema = Object.class)
            Map<String, Object> attributes,

            @Schema(
                    description = "Free-form notes associated with the step.",
                    additionalPropertiesSchema = String.class)
            Map<String, String> notes) {

        static TraceStepPayload from(VerboseTrace.TraceStep step) {
            Objects.requireNonNull(step, "step");
            Map<String, Object> attributesCopy = Collections.unmodifiableMap(new LinkedHashMap<>(step.attributes()));
            Map<String, String> notesCopy = Collections.unmodifiableMap(new LinkedHashMap<>(step.notes()));
            return new TraceStepPayload(step.id(), step.summary(), step.detail(), attributesCopy, notesCopy);
        }
    }
}
