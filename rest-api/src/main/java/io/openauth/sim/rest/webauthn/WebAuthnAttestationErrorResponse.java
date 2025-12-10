package io.openauth.sim.rest.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.VerboseTracePayload;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(
        name = "WebAuthnAttestationErrorResponse",
        description = "Error payload returned when WebAuthn attestation verification fails.")
@JsonInclude(JsonInclude.Include.NON_NULL)
record WebAuthnAttestationErrorResponse(
        @Schema(description = "High level status indicator for the failure", example = "invalid")
        @JsonProperty("status")
        String status,

        @Schema(
                description = "Reason code describing the failure",
                example = "client_data_challenge_mismatch",
                allowableValues = {
                    "generated",
                    "credential_id_required",
                    "challenge_required",
                    "invalid_format",
                    "invalid_payload",
                    "missing_option",
                    "credential_private_key_required",
                    "attestation_private_key_required",
                    "custom_root_required",
                    "attestation_id_not_applicable",
                    "input_source_invalid",
                    "missing_signing_mode",
                    "stored_credential_not_found",
                    "match",
                    "verified",
                    "self_attested",
                    "anchor_mismatch",
                    "stored_attestation_required",
                    "stored_attestation_missing_attribute",
                    "stored_attestation_invalid",
                    "stored_trust_anchor_unsupported",
                    "replay_failed",
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

        @Schema(description = "Human-readable error message", example = "Client data challenge mismatch.")
        @JsonProperty("message")
        String message,

        @Schema(description = "Additional details about the failure") @JsonProperty("details")
        Map<String, Object> details,

        @Schema(description = "Telemetry metadata captured during verification") @JsonProperty("metadata")
        Map<String, Object> metadata,

        @JsonProperty("trace") VerboseTracePayload trace) {

    WebAuthnAttestationErrorResponse {
        details = Map.copyOf(details == null ? Map.of() : details);
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
