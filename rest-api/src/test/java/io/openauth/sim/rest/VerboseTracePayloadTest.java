package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import io.openauth.sim.rest.VerboseTracePayload.TraceAttributePayload;
import io.openauth.sim.rest.VerboseTracePayload.TraceStepPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class VerboseTracePayloadTest {

    @Test
    @DisplayName("Formats hex attributes consistently for empty and non-empty byte arrays")
    void formatsHexAttributes() {
        VerboseTrace trace = VerboseTrace.builder("test.operation")
                .addStep(step -> step.id("step.hex")
                        .attribute(AttributeType.HEX, "empty-bytes", new byte[0])
                        .attribute(AttributeType.HEX, "non-empty", new byte[] {0x0F}))
                .build();

        VerboseTracePayload payload = VerboseTracePayload.from(trace);
        TraceStepPayload stepPayload = payload.steps().get(0);
        assertThat(stepPayload.orderedAttributes())
                .extracting(TraceAttributePayload::name, TraceAttributePayload::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("empty-bytes", ""),
                        org.assertj.core.groups.Tuple.tuple("non-empty", "0f"));
    }
}
