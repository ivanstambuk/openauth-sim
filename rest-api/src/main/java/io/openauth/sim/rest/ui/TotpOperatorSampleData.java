package io.openauth.sim.rest.ui;

import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures.TotpJsonVector;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared sample data for TOTP operator console seeding and presets. */
public final class TotpOperatorSampleData {

    private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");
    private static final String SECRET_SHA1_DEMO_HEX = "31323334353637383930313233343536373839303132";
    private static final TotpJsonVector VECTOR_SHA1_DIGITS8_T59 = vector("rfc6238_sha1_digits8_t59");
    private static final TotpJsonVector VECTOR_SHA256_DIGITS6_T59 = vector("rfc6238_sha256_digits6_t59");
    private static final TotpJsonVector VECTOR_SHA256_DIGITS8_T59 = vector("rfc6238_sha256_digits8_t59");
    private static final TotpJsonVector VECTOR_SHA512_DIGITS6_T59 = vector("rfc6238_sha512_digits6_t59");
    private static final TotpJsonVector VECTOR_SHA512_DIGITS8_T59 = vector("rfc6238_sha512_digits8_t59");

    private static final List<SampleDefinition> DEFINITIONS = List.of(
            sample(
                    "ui-totp-sample-sha1-6",
                    "SHA-1, 6 digits, 30s",
                    SECRET_SHA1_DEMO_HEX,
                    TotpHashAlgorithm.SHA1,
                    6,
                    30,
                    1,
                    1,
                    1_111_111_111L,
                    metadata(
                            "inline-ui-totp-demo",
                            "SHA-1, 6 digits, 30s",
                            "Seeded TOTP credential (inline demo preset).")),
            sample(
                    "ui-totp-sample-sha1-8",
                    "SHA-1, 8 digits, 30s (RFC 6238)",
                    VECTOR_SHA1_DIGITS8_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA1,
                    8,
                    (int) VECTOR_SHA1_DIGITS8_T59.stepSeconds(),
                    VECTOR_SHA1_DIGITS8_T59.driftBackwardSteps(),
                    VECTOR_SHA1_DIGITS8_T59.driftForwardSteps(),
                    VECTOR_SHA1_DIGITS8_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha1",
                            "SHA-1, 8 digits, 30s (RFC 6238)",
                            "Seeded TOTP credential based on RFC 6238 sample.")),
            sample(
                    "ui-totp-sample-sha256-6",
                    "SHA-256, 6 digits, 30s",
                    VECTOR_SHA256_DIGITS6_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA256,
                    6,
                    (int) VECTOR_SHA256_DIGITS6_T59.stepSeconds(),
                    VECTOR_SHA256_DIGITS6_T59.driftBackwardSteps(),
                    VECTOR_SHA256_DIGITS6_T59.driftForwardSteps(),
                    VECTOR_SHA256_DIGITS6_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha256-6",
                            "SHA-256, 6 digits, 30s",
                            "Seeded TOTP credential truncated to six digits from RFC 6238 sample.")),
            sample(
                    "ui-totp-sample-sha256-8",
                    "SHA-256, 8 digits, 30s (RFC 6238)",
                    VECTOR_SHA256_DIGITS8_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA256,
                    8,
                    (int) VECTOR_SHA256_DIGITS8_T59.stepSeconds(),
                    VECTOR_SHA256_DIGITS8_T59.driftBackwardSteps(),
                    VECTOR_SHA256_DIGITS8_T59.driftForwardSteps(),
                    VECTOR_SHA256_DIGITS8_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha256-8",
                            "SHA-256, 8 digits, 30s (RFC 6238)",
                            "Seeded TOTP credential based on RFC 6238 sample.")),
            sample(
                    "ui-totp-sample-sha512-6",
                    "SHA-512, 6 digits, 30s",
                    VECTOR_SHA512_DIGITS6_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA512,
                    6,
                    (int) VECTOR_SHA512_DIGITS6_T59.stepSeconds(),
                    VECTOR_SHA512_DIGITS6_T59.driftBackwardSteps(),
                    VECTOR_SHA512_DIGITS6_T59.driftForwardSteps(),
                    VECTOR_SHA512_DIGITS6_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha512-6",
                            "SHA-512, 6 digits, 30s",
                            "Seeded TOTP credential truncated to six digits from RFC 6238 sample.")),
            sample(
                    "ui-totp-sample-sha512-8",
                    "SHA-512, 8 digits, 30s (RFC 6238)",
                    VECTOR_SHA512_DIGITS8_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA512,
                    8,
                    (int) VECTOR_SHA512_DIGITS8_T59.stepSeconds(),
                    VECTOR_SHA512_DIGITS8_T59.driftBackwardSteps(),
                    VECTOR_SHA512_DIGITS8_T59.driftForwardSteps(),
                    VECTOR_SHA512_DIGITS8_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha512-8",
                            "SHA-512, 8 digits, 30s (RFC 6238)",
                            "Seeded TOTP credential based on RFC 6238 sample.")));

    private static final List<InlinePreset> INLINE_PRESETS = List.of(
            inlinePreset(
                    "inline-rfc6238-sha1",
                    "SHA-1, 8 digits, 30s (RFC 6238)",
                    VECTOR_SHA1_DIGITS8_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA1,
                    8,
                    (int) VECTOR_SHA1_DIGITS8_T59.stepSeconds(),
                    VECTOR_SHA1_DIGITS8_T59.driftBackwardSteps(),
                    VECTOR_SHA1_DIGITS8_T59.driftForwardSteps(),
                    VECTOR_SHA1_DIGITS8_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha1",
                            "SHA-1, 8 digits, 30s (RFC 6238)",
                            "Inline preset based on RFC 6238 sample.")),
            inlinePreset(
                    "inline-rfc6238-sha256-6",
                    "SHA-256, 6 digits, 30s",
                    VECTOR_SHA256_DIGITS6_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA256,
                    6,
                    (int) VECTOR_SHA256_DIGITS6_T59.stepSeconds(),
                    VECTOR_SHA256_DIGITS6_T59.driftBackwardSteps(),
                    VECTOR_SHA256_DIGITS6_T59.driftForwardSteps(),
                    VECTOR_SHA256_DIGITS6_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha256-6",
                            "SHA-256, 6 digits, 30s",
                            "Inline preset derived from RFC 6238 sample (truncated to 6 digits).")),
            inlinePreset(
                    "inline-rfc6238-sha256-8",
                    "SHA-256, 8 digits, 30s (RFC 6238)",
                    VECTOR_SHA256_DIGITS8_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA256,
                    8,
                    (int) VECTOR_SHA256_DIGITS8_T59.stepSeconds(),
                    VECTOR_SHA256_DIGITS8_T59.driftBackwardSteps(),
                    VECTOR_SHA256_DIGITS8_T59.driftForwardSteps(),
                    VECTOR_SHA256_DIGITS8_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha256-8",
                            "SHA-256, 8 digits, 30s (RFC 6238)",
                            "Inline preset based on RFC 6238 sample.")),
            inlinePreset(
                    "inline-rfc6238-sha512-6",
                    "SHA-512, 6 digits, 30s",
                    VECTOR_SHA512_DIGITS6_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA512,
                    6,
                    (int) VECTOR_SHA512_DIGITS6_T59.stepSeconds(),
                    VECTOR_SHA512_DIGITS6_T59.driftBackwardSteps(),
                    VECTOR_SHA512_DIGITS6_T59.driftForwardSteps(),
                    VECTOR_SHA512_DIGITS6_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha512-6",
                            "SHA-512, 6 digits, 30s",
                            "Inline preset derived from RFC 6238 sample (truncated to 6 digits).")),
            inlinePreset(
                    "inline-rfc6238-sha512-8",
                    "SHA-512, 8 digits, 30s (RFC 6238)",
                    VECTOR_SHA512_DIGITS8_T59.secret().asHex(),
                    TotpHashAlgorithm.SHA512,
                    8,
                    (int) VECTOR_SHA512_DIGITS8_T59.stepSeconds(),
                    VECTOR_SHA512_DIGITS8_T59.driftBackwardSteps(),
                    VECTOR_SHA512_DIGITS8_T59.driftForwardSteps(),
                    VECTOR_SHA512_DIGITS8_T59.timestampEpochSeconds(),
                    metadata(
                            "inline-rfc6238-sha512-8",
                            "SHA-512, 8 digits, 30s (RFC 6238)",
                            "Inline preset based on RFC 6238 sample.")),
            inlinePreset(
                    "inline-ui-totp-demo",
                    "SHA-1, 6 digits, 30s",
                    SECRET_SHA1_DEMO_HEX,
                    TotpHashAlgorithm.SHA1,
                    6,
                    30,
                    1,
                    1,
                    1_111_111_111L,
                    metadata(
                            "inline-ui-totp-demo",
                            "SHA-1, 6 digits, 30s",
                            "Inline preset mirroring the seeded demo credential.")));

    private TotpOperatorSampleData() {
        // utility class
    }

    /** Returns the canonical TOTP credential definitions available for seeding. */
    public static List<SampleDefinition> seedDefinitions() {
        return DEFINITIONS;
    }

    /** Returns inline sample presets for populating the TOTP inline forms. */
    public static List<InlinePreset> inlinePresets() {
        return INLINE_PRESETS;
    }

    private static SampleDefinition sample(
            String credentialId,
            String optionLabel,
            String sharedSecretHex,
            TotpHashAlgorithm algorithm,
            int digits,
            int stepSeconds,
            int driftBackward,
            int driftForward,
            long sampleTimestamp,
            Map<String, String> metadata) {
        Map<String, String> enrichedMetadata = new java.util.LinkedHashMap<>(metadata);
        enrichedMetadata.putIfAbsent("sampleTimestamp", Long.toString(sampleTimestamp));
        return new SampleDefinition(
                credentialId,
                optionLabel,
                sharedSecretHex,
                algorithm,
                digits,
                stepSeconds,
                driftBackward,
                driftForward,
                Map.copyOf(enrichedMetadata));
    }

    private static Map<String, String> metadata(String presetKey, String label, String notes) {
        Map<String, String> metadata = new java.util.LinkedHashMap<>();
        String safeLabel = Objects.requireNonNull(label, "label");
        metadata.put("seedSource", BASE_METADATA.get("seedSource"));
        metadata.put("presetKey", Objects.requireNonNull(presetKey, "presetKey"));
        metadata.put("label", safeLabel);
        metadata.put("presetLabel", safeLabel);
        metadata.put("notes", Objects.requireNonNull(notes, "notes"));
        return Map.copyOf(metadata);
    }

    private static InlinePreset inlinePreset(
            String key,
            String label,
            String sharedSecretHex,
            TotpHashAlgorithm algorithm,
            int digits,
            int stepSeconds,
            int driftBackward,
            int driftForward,
            long timestampEpochSeconds,
            Map<String, String> metadata) {
        SecretMaterial secret = SecretMaterial.fromHex(sharedSecretHex);
        TotpDescriptor descriptor = TotpDescriptor.create(
                key,
                secret,
                algorithm,
                digits,
                Duration.ofSeconds(stepSeconds),
                TotpDriftWindow.of(driftBackward, driftForward));
        String otp = TotpGenerator.generate(descriptor, Instant.ofEpochSecond(timestampEpochSeconds));
        return new InlinePreset(
                key,
                label,
                sharedSecretHex,
                algorithm,
                digits,
                stepSeconds,
                driftBackward,
                driftForward,
                timestampEpochSeconds,
                otp,
                metadata);
    }

    /** Canonical TOTP sample definition used for seeding and dropdown labels. */
    public record SampleDefinition(
            String credentialId,
            String optionLabel,
            String sharedSecretHex,
            TotpHashAlgorithm algorithm,
            int digits,
            int stepSeconds,
            int driftBackwardSteps,
            int driftForwardSteps,
            Map<String, String> metadata) {

        public SampleDefinition {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(optionLabel, "optionLabel");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            Objects.requireNonNull(algorithm, "algorithm");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** Canonical inline preset definition for operator console dropdowns. */
    public record InlinePreset(
            String key,
            String label,
            String sharedSecretHex,
            TotpHashAlgorithm algorithm,
            int digits,
            int stepSeconds,
            int driftBackwardSteps,
            int driftForwardSteps,
            long timestamp,
            String otp,
            Map<String, String> metadata) {

        public InlinePreset {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            Objects.requireNonNull(algorithm, "algorithm");
            Objects.requireNonNull(metadata, "metadata");
            metadata = Map.copyOf(metadata);
        }
    }

    private static TotpJsonVector vector(String id) {
        return TotpJsonVectorFixtures.getById(id);
    }
}
