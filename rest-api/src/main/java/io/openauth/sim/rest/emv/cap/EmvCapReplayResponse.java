package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapReplayResponse(
        @JsonProperty("status") String status,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {"match", "otp_mismatch", "invalid_input", "unexpected_error"})
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("metadata") EmvCapReplayMetadata metadata,
        @JsonProperty("trace") EmvCapReplayVerboseTracePayload trace) {
    // canonical response envelope
}
