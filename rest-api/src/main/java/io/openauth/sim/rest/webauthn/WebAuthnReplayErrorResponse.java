package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnReplayErrorResponse(
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
                    "webauthn_replay_failed",
                    "unexpected_error"
                })
        @JsonProperty("reasonCode")
        String reasonCode,

        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("trace") VerboseTracePayload trace) {

    WebAuthnReplayErrorResponse {
        details = Map.copyOf(details == null ? Map.of() : details);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
