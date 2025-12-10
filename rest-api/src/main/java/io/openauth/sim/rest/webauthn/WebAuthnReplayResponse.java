package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnReplayResponse(
        @JsonProperty("status") String status,

        @Schema(
                description = "Machine-readable outcome code",
                allowableValues = {
                    "match",
                    "verified",
                    "credential_not_found",
                    "client_data_type_mismatch",
                    "client_data_challenge_mismatch",
                    "origin_mismatch",
                    "rp_id_hash_mismatch",
                    "signature_invalid",
                    "user_verification_required",
                    "counter_regression",
                    "attestation_format_mismatch",
                    "attestation_object_invalid",
                    "verification_failed",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("match") boolean match,
        @JsonProperty("metadata") WebAuthnReplayMetadata metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {
    // DTO marker
}
