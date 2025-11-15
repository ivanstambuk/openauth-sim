package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapReplayMetadata(
        @JsonProperty("credentialSource") String credentialSource,
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("mode") String mode,
        @JsonProperty("matchedDelta") Integer matchedDelta,
        @JsonProperty("driftBackward") Integer driftBackward,
        @JsonProperty("driftForward") Integer driftForward,
        @JsonProperty("branchFactor") Integer branchFactor,
        @JsonProperty("height") Integer height,
        @JsonProperty("ipbMaskLength") Integer ipbMaskLength,
        @JsonProperty("suppliedOtpLength") Integer suppliedOtpLength,
        @JsonProperty("telemetryId") String telemetryId,
        @JsonProperty("expectedOtpHash") String expectedOtpHash) {
    // canonical metadata envelope
}
