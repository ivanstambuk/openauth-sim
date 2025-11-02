package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapEvaluationResponse(
        @JsonProperty("otp") String otp,
        @JsonProperty("maskLength") int maskLength,
        @JsonProperty("trace") EmvCapTracePayload trace,
        @JsonProperty("telemetry") EmvCapTelemetryPayload telemetry) {
    // no additional members
}
