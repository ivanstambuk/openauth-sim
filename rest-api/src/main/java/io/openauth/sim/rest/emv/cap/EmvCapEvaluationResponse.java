package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.OtpPreviewResponse;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapEvaluationResponse(
        @JsonProperty("otp") String otp,
        @JsonProperty("maskLength") int maskLength,
        @JsonProperty("previews") List<OtpPreviewResponse> previews,
        @JsonProperty("trace") EmvCapTracePayload trace,
        @JsonProperty("telemetry") EmvCapTelemetryPayload telemetry) {
    // no additional members
}
