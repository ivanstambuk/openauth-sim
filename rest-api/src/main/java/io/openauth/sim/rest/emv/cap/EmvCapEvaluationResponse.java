package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.OtpPreviewResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapEvaluationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("otp")
        String otp,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("maskLength")
        int maskLength,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("previews")
        List<OtpPreviewResponse> previews,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("trace")
        EmvCapTracePayload trace,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("telemetry")
        EmvCapTelemetryPayload telemetry) {
    // no additional members
}
