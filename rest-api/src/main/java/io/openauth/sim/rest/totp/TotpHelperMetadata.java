package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

record TotpHelperMetadata(
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("digits") Integer digits,
        @JsonProperty("stepSeconds") long stepSeconds,
        @JsonProperty("driftBackwardSteps") int driftBackwardSteps,
        @JsonProperty("driftForwardSteps") int driftForwardSteps,
        @JsonProperty("timestampOverrideProvided") boolean timestampOverrideProvided,
        @JsonProperty("telemetryId") String telemetryId,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {
                    "generated",
                    "validated",
                    "credential_not_found",
                    "otp_invalid_format",
                    "otp_out_of_window",
                    "shared_secret_invalid",
                    "validation_error",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode) {
    // no members
}
