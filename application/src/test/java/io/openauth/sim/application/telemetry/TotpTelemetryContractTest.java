package io.openauth.sim.application.telemetry;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("telemetry")
final class TotpTelemetryContractTest {

    @Test
    void totpEvaluationSchemaContainsTelemetryReasonCodes() {
        // Mirror TOTP evaluate telemetry reason codes against the CLI schema enums to prevent drift.
        @SuppressWarnings("unchecked")
        var definitions = (java.util.Map<String, Object>) loadCliSchema().get("definitions");
        @SuppressWarnings("unchecked")
        var schema = (java.util.Map<String, Object>) definitions.get("cli.totp.evaluate");
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        var envelope = (java.util.Map<String, Object>) properties.get("reasonCode");
        @SuppressWarnings("unchecked")
        var dataProperties = (java.util.Map<String, Object>)
                ((java.util.Map<String, Object>) properties.get("data")).get("properties");
        @SuppressWarnings("unchecked")
        var dataReason = (java.util.Map<String, Object>) dataProperties.get("reasonCode");

        @SuppressWarnings("unchecked")
        java.util.Set<String> envelopeEnums =
                new java.util.LinkedHashSet<>((java.util.List<String>) envelope.get("enum"));
        @SuppressWarnings("unchecked")
        java.util.Set<String> dataEnums =
                new java.util.LinkedHashSet<>((java.util.List<String>) dataReason.get("enum"));

        // Telemetry emits these codes (success + validation/error branches).
        java.util.Set<String> telemetryCodes = java.util.Set.of(
                "generated",
                "validated",
                "credential_not_found",
                "otp_invalid_format",
                "otp_out_of_window",
                "shared_secret_invalid",
                "validation_error",
                "unexpected_error");

        org.junit.jupiter.api.Assertions.assertEquals(
                telemetryCodes, envelopeEnums, "Envelope reasonCode enum should match telemetry codes");
        org.junit.jupiter.api.Assertions.assertEquals(
                telemetryCodes, dataEnums, "data.reasonCode enum should match telemetry codes");
    }

    private static java.util.Map<String, Object> loadCliSchema() {
        java.nio.file.Path path = java.nio.file.Path.of("docs/3-reference/cli/cli.schema.json");
        if (!java.nio.file.Files.exists(path)) {
            path = java.nio.file.Path.of("..", "docs/3-reference/cli/cli.schema.json");
        }
        try {
            Object parsed = io.openauth.sim.core.json.SimpleJson.parse(java.nio.file.Files.readString(path));
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> root = (java.util.Map<String, Object>) parsed;
            return root;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to read CLI schema at " + path, ex);
        }
    }

    @Test
    void totpSeedingAdapterProducesSeedFrame() {
        TotpTelemetryAdapter adapter = TelemetryContracts.totpSeedingAdapter();

        TelemetryFrame frame = adapter.status(
                "seeded",
                TelemetryContractTestSupport.telemetryId(),
                "seeded",
                true,
                null,
                TelemetryContractTestSupport.totpSeedFields());

        TelemetryContractTestSupport.assertTotpSeedFrame(frame);
    }

    @Test
    void totpSampleAdapterProducesSampleFrame() {
        TotpTelemetryAdapter adapter = TelemetryContracts.totpSampleAdapter();

        TelemetryFrame frame = adapter.status(
                "sampled",
                TelemetryContractTestSupport.telemetryId(),
                "sampled",
                true,
                null,
                TelemetryContractTestSupport.totpSampleFields());

        TelemetryContractTestSupport.assertTotpSampleFrame(frame);
    }
}
