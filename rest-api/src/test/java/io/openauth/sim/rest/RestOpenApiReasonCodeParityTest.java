package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards that REST OpenAPI snapshots enumerate reasonCode values that cover telemetry outputs for each
 * response schema. Mirrors the CLI parity guard so CLI/REST stay aligned.
 */
final class RestOpenApiReasonCodeParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode schemas;

    @BeforeAll
    static void loadSchemas() throws IOException {
        Path path = Path.of("..", "docs", "3-reference", "rest-openapi.json").normalize();
        JsonNode root = MAPPER.readTree(Files.readString(path));
        schemas = root.path("components").path("schemas");
        if (schemas.isMissingNode() || !schemas.isObject()) {
            throw new IllegalStateException("Missing components.schemas in REST OpenAPI snapshot at " + path);
        }
    }

    @Test
    @DisplayName("HOTP evaluate reasonCode enums cover telemetry")
    void hotpEvaluateReasonCodes() {
        Set<String> expected =
                Set.of("generated", "credential_not_found", "validation_error", "counter_overflow", "unexpected_error");
        assertSchemaContains("HotpEvaluationResponse", expected);
        assertSchemaContains("HotpEvaluationErrorResponse", expected);
    }

    @Test
    @DisplayName("HOTP replay reasonCode enums cover telemetry")
    void hotpReplayReasonCodes() {
        Set<String> expected = Set.of(
                "match",
                "otp_mismatch",
                "credential_not_found",
                "invalid_hotp_metadata",
                "validation_error",
                "unexpected_error");
        assertSchemaContains("HotpReplayResponse", expected);
        assertSchemaContains("HotpReplayErrorResponse", expected);
    }

    @Test
    @DisplayName("TOTP evaluate reasonCode enums cover telemetry")
    void totpEvaluateReasonCodes() {
        Set<String> expected = Set.of(
                "generated",
                "validated",
                "credential_not_found",
                "otp_invalid_format",
                "otp_out_of_window",
                "shared_secret_invalid",
                "validation_error",
                "unexpected_error");
        assertSchemaContains("TotpEvaluationResponse", expected);
        assertSchemaContains("TotpEvaluationErrorResponse", expected);
        assertSchemaContains("TotpHelperMetadata", expected);
    }

    @Test
    @DisplayName("TOTP replay reasonCode enums cover telemetry")
    void totpReplayReasonCodes() {
        Set<String> expected =
                Set.of("match", "otp_out_of_window", "credential_not_found", "validation_error", "unexpected_error");
        assertSchemaContains("TotpReplayResponse", expected);
        assertSchemaContains("TotpReplayErrorResponse", expected);
    }

    @Test
    @DisplayName("EMV/CAP reasonCode enums cover telemetry")
    void emvCapReasonCodes() {
        Set<String> eval = Set.of("generated", "invalid_input", "unexpected_error");
        Set<String> replay = Set.of("match", "otp_mismatch", "invalid_input", "unexpected_error");
        assertSchemaContains("EmvCapEvaluationErrorResponse", eval);
        assertSchemaContains("EmvCapReplayResponse", replay);
        assertSchemaContains("EmvCapReplayErrorResponse", replay);
        // Telemetry payload is shared across evaluate/replay; allow the union.
        assertSchemaContains(
                "EmvCapTelemetryPayload",
                new LinkedHashSet<>(Set.of("generated", "match", "otp_mismatch", "invalid_input", "unexpected_error")));
    }

    @Test
    @DisplayName("OCRA verify reasonCode enums cover telemetry")
    void ocraVerifyReasonCodes() {
        Set<String> expected =
                Set.of("match", "strict_mismatch", "validation_error", "credential_not_found", "unexpected_error");
        assertSchemaContains("OcraVerificationResponse", expected);
    }

    @Test
    @DisplayName("WebAuthn replay reasonCode enums cover telemetry")
    void webAuthnReplayReasonCodes() {
        Set<String> expected = Set.of(
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
                "unexpected_error");
        assertSchemaContains("WebAuthnReplayResponse", expected);
        assertSchemaContains("WebAuthnReplayErrorResponse", expected);
    }

    @Test
    @DisplayName("WebAuthn evaluation error reasonCode enums cover telemetry")
    void webAuthnEvaluationReasonCodes() {
        Set<String> expected = Set.of("validation_error", "credential_not_found", "unexpected_error");
        assertSchemaContains("WebAuthnEvaluationErrorResponse", expected);
    }

    @Test
    @DisplayName("WebAuthn attestation reasonCode enums cover telemetry")
    void webAuthnAttestationReasonCodes() {
        Set<String> generation = Set.of(
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
                "unexpected_error");
        Set<String> replay = Set.of(
                "match",
                "verified",
                "self_attested",
                "anchor_mismatch",
                "stored_credential_not_found",
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
                "unexpected_error");
        Set<String> union = new LinkedHashSet<>(generation);
        union.addAll(replay);
        assertSchemaContains("WebAuthnAttestationMetadata", union);
        assertSchemaContains("WebAuthnAttestationErrorResponse", union);
    }

    private static void assertSchemaContains(String schemaName, Set<String> expectedCodes) {
        JsonNode schema = schemas.path(schemaName);
        if (schema.isMissingNode()) {
            throw new AssertionError("Schema " + schemaName + " missing from OpenAPI snapshot");
        }
        JsonNode reasonCodeEnum = schema.path("properties").path("reasonCode").path("enum");
        assertFalse(reasonCodeEnum.isMissingNode() || !reasonCodeEnum.isArray(), schemaName + " reasonCode lacks enum");

        Set<String> actual = new LinkedHashSet<>();
        reasonCodeEnum.forEach(node -> actual.add(node.asText()));

        assertTrue(
                actual.containsAll(expectedCodes),
                () -> schemaName + " reasonCode enum missing telemetry codes: " + difference(expectedCodes, actual));
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> diff = new LinkedHashSet<>(expected);
        diff.removeAll(actual);
        return diff;
    }
}
