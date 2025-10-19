package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TotpOperatorSampleDataTest {

    @Test
    @DisplayName("Seed definitions generate valid sample OTPs for all algorithms")
    void seedDefinitionsGenerateValidOtps() {
        for (TotpOperatorSampleData.SampleDefinition definition : TotpOperatorSampleData.seedDefinitions()) {
            String timestampValue = definition.metadata().get("sampleTimestamp");
            assertFalse(
                    timestampValue == null || timestampValue.isBlank(),
                    () -> "sampleTimestamp metadata missing for " + definition.credentialId());
            long timestamp = Long.parseLong(timestampValue.trim());
            TotpDescriptor descriptor = TotpDescriptor.create(
                    definition.credentialId(),
                    SecretMaterial.fromHex(definition.sharedSecretHex()),
                    definition.algorithm(),
                    definition.digits(),
                    Duration.ofSeconds(definition.stepSeconds()),
                    TotpDriftWindow.of(definition.driftBackwardSteps(), definition.driftForwardSteps()));

            assertDoesNotThrow(
                    () -> TotpGenerator.generate(descriptor, Instant.ofEpochSecond(timestamp)),
                    () -> "Unable to generate TOTP for " + definition.credentialId());
        }
    }

    @Test
    @DisplayName("Inline presets include RFC 6238 SHA-256/512 samples with correct OTPs")
    void inlinePresetsIncludeRfcVectors() {
        Map<String, TotpOperatorSampleData.InlinePreset> presetsByKey = TotpOperatorSampleData.inlinePresets().stream()
                .collect(Collectors.toMap(TotpOperatorSampleData.InlinePreset::key, preset -> preset));

        assertTrue(
                presetsByKey.containsKey("inline-rfc6238-sha256-6"), "Expected SHA-256 6-digit preset to be available");
        assertTrue(
                presetsByKey.containsKey("inline-rfc6238-sha256-8"), "Expected SHA-256 8-digit preset to be available");
        assertTrue(
                presetsByKey.containsKey("inline-rfc6238-sha512-6"), "Expected SHA-512 6-digit preset to be available");
        assertTrue(
                presetsByKey.containsKey("inline-rfc6238-sha512-8"), "Expected SHA-512 8-digit preset to be available");

        assertEquals("119246", presetsByKey.get("inline-rfc6238-sha256-6").otp());
        assertEquals("46119246", presetsByKey.get("inline-rfc6238-sha256-8").otp());
        assertEquals("693936", presetsByKey.get("inline-rfc6238-sha512-6").otp());
        assertEquals("90693936", presetsByKey.get("inline-rfc6238-sha512-8").otp());
    }
}
