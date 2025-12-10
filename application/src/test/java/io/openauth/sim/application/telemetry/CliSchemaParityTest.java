package io.openauth.sim.application.telemetry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.testing.CliSchemaLoader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ensures CLI schema reasonCode enums stay in sync with telemetry reason codes emitted by the application layer for
 * core protocols. We only assert that the schema enums contain all telemetry codes (allowing the schema to be a superset
 * for future extensions) and, where applicable, that envelope and data.reasonCode enums remain aligned.
 */
final class CliSchemaParityTest {

    @Test
    void hotpEvaluateReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.hotp.evaluate",
                Set.of(
                        "generated",
                        "credential_not_found",
                        "validation_error",
                        "counter_overflow",
                        "unexpected_error"));
    }

    @Test
    void emvCapEvaluateReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.emv.cap.evaluate", Set.of("generated", "invalid_input", "unexpected_error"));
    }

    @Test
    void emvCapReplayReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.emv.cap.replay", Set.of("match", "otp_mismatch", "invalid_input", "unexpected_error"));
    }

    @Test
    void hotpIssueReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.hotp.issue", Set.of("issued", "validation_error", "type_mismatch", "unexpected_error"));
    }

    @Test
    void hotpListReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.hotp.list", Set.of("success", "validation_error", "unexpected_error"));
    }

    @Test
    void totpListReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.totp.list", Set.of("success", "validation_error", "unexpected_error"));
    }

    @Test
    void ocraEvaluateReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.ocra.evaluate",
                Set.of(
                        "success",
                        "credential_conflict",
                        "credential_missing",
                        "credential_not_found",
                        "challenge_not_permitted",
                        "challenge_required",
                        "challenge_length",
                        "challenge_format",
                        "timestamp_not_permitted",
                        "timestamp_invalid",
                        "missing_required",
                        "not_hexadecimal",
                        "invalid_hex_length",
                        "validation_error",
                        "unexpected_error"));
    }

    @Test
    void ocraVerifyReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.ocra.verify",
                Set.of("match", "strict_mismatch", "validation_error", "credential_not_found", "unexpected_error"));
    }

    @Test
    void ocraImportReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.ocra.import", Set.of("created", "validation_error", "unexpected_error"));
    }

    @Test
    void ocraListReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.ocra.list", Set.of("success", "validation_error", "unexpected_error"));
    }

    @Test
    void ocraDeleteReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.ocra.delete", Set.of("deleted", "credential_not_found", "validation_error", "unexpected_error"));
    }

    @Test
    void fido2EvaluateReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.fido2.evaluate", Set.of("generated", "validation_error", "unexpected_error"));
    }

    @Test
    void fido2AttestReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.fido2.attest",
                Set.of(
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
                        "unexpected_error"));
    }

    @Test
    void fido2ReplayReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.fido2.replay",
                Set.of(
                        "match",
                        "verified",
                        "unexpected_error",
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
                        "verification_failed"));
    }

    @Test
    void fido2AttestReplayReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes(
                "cli.fido2.attestReplay",
                Set.of(
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
                        "unexpected_error"));
    }

    @Test
    void fido2SeedAttestationsReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes("cli.fido2.seed-attestations", Set.of("success", "unexpected_error"));
    }

    @Test
    void fido2VectorsReasonCodesCoverTelemetry() {
        assertSchemaContainsTelemetryReasonCodes("cli.fido2.vectors", Set.of("success", "unexpected_error"));
    }

    @SuppressWarnings("unchecked")
    private static void assertSchemaContainsTelemetryReasonCodes(String definitionName, Set<String> telemetryCodes) {
        Map<String, Object> definitions = CliSchemaLoader.definitions();
        Map<String, Object> schema = (Map<String, Object>) definitions.get(definitionName);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        Set<String> envelopeEnums =
                new LinkedHashSet<>((List<String>) ((Map<String, Object>) properties.get("reasonCode")).get("enum"));
        Set<String> dataEnums = envelopeEnums;

        Object data = properties.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object dataProps = ((Map<String, Object>) dataMap).get("properties");
            if (dataProps instanceof Map<?, ?> dp && dp.containsKey("reasonCode")) {
                dataEnums =
                        new LinkedHashSet<>((List<String>) ((Map<String, Object>) dp.get("reasonCode")).get("enum"));
                // Keep the envelope/data enums aligned when both exist.
                final Set<String> finalDataEnums = dataEnums;
                assertTrue(
                        envelopeEnums.equals(finalDataEnums),
                        () -> definitionName
                                + " envelope/data reasonCode enums diverge: "
                                + envelopeEnums
                                + " vs "
                                + finalDataEnums);
            }
        }

        final Set<String> finalEnvelopeEnums = envelopeEnums;
        assertTrue(
                finalEnvelopeEnums.containsAll(telemetryCodes),
                () -> definitionName + " schema is missing telemetry reasonCodes: "
                        + new LinkedHashSet<>(difference(telemetryCodes, finalEnvelopeEnums)));
        final Set<String> finalDataEnums = dataEnums;
        assertTrue(
                finalDataEnums.containsAll(telemetryCodes),
                () -> definitionName + " data schema is missing telemetry reasonCodes: "
                        + new LinkedHashSet<>(difference(telemetryCodes, finalDataEnums)));
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> diff = new LinkedHashSet<>(left);
        diff.removeAll(right);
        return diff;
    }
}
