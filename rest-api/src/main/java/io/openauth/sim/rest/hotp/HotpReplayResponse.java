package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;

/** Response payload returned by the HOTP replay endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayResponse(
        @JsonProperty("status") String status,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {
                    "match",
                    "otp_mismatch",
                    "credential_not_found",
                    "invalid_hotp_metadata",
                    "validation_error",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("metadata") HotpReplayMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {

    // Canonical record; no additional behaviour.
}
