package io.openauth.sim.rest.ui;

import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures.HotpJsonVector;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared sample data for HOTP operator console seeding and presets. */
public final class HotpOperatorSampleData {

    private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");

    private static final HotpJsonVector RFC_SHA1_DIGITS6_COUNTER0 =
            HotpJsonVectorFixtures.getById("rfc4226_sha1_digits6_counter0");
    private static final HotpJsonVector RFC_SHA1_DIGITS6_COUNTER5 =
            HotpJsonVectorFixtures.getById("rfc4226_sha1_digits6_counter5");
    private static final HotpJsonVector RFC_SHA1_DIGITS8_COUNTER5 =
            HotpJsonVectorFixtures.getById("rfc4226_sha1_digits8_counter5");

    private static final String SHA512_SECRET_HEX = "3132333435363738393031323334353637383930313233343536373839303132";

    private static final List<SampleDefinition> DEFINITIONS = List.of(
            sample(
                    "ui-hotp-demo",
                    "ui-hotp-demo (SHA1, 6 digits, RFC 4226)",
                    RFC_SHA1_DIGITS6_COUNTER0.secret().asHex(),
                    RFC_SHA1_DIGITS6_COUNTER0.algorithm(),
                    RFC_SHA1_DIGITS6_COUNTER0.digits(),
                    RFC_SHA1_DIGITS6_COUNTER0.counter(),
                    metadata("ui-hotp-demo", "stored-demo", "Seeded HOTP SHA-1 demo credential.")),
            sample(
                    "ui-hotp-demo-sha256",
                    "ui-hotp-demo-sha256 (SHA256, 8 digits)",
                    RFC_SHA1_DIGITS6_COUNTER0.secret().asHex(),
                    HotpHashAlgorithm.SHA256,
                    8,
                    5L,
                    metadata("ui-hotp-demo-sha256", "stored-demo-sha256", "Seeded HOTP SHA-256 demo credential.")),
            sample(
                    "ui-hotp-demo-sha1-8",
                    "ui-hotp-demo-sha1-8 (SHA1, 8 digits)",
                    RFC_SHA1_DIGITS8_COUNTER5.secret().asHex(),
                    RFC_SHA1_DIGITS8_COUNTER5.algorithm(),
                    RFC_SHA1_DIGITS8_COUNTER5.digits(),
                    RFC_SHA1_DIGITS8_COUNTER5.counter(),
                    metadata(
                            "ui-hotp-demo-sha1-8",
                            "stored-demo-sha1-8",
                            "Seeded HOTP SHA-1 demo credential (8 digits).")),
            sample(
                    "ui-hotp-demo-sha256-6",
                    "ui-hotp-demo-sha256-6 (SHA256, 6 digits)",
                    RFC_SHA1_DIGITS6_COUNTER0.secret().asHex(),
                    HotpHashAlgorithm.SHA256,
                    6,
                    5L,
                    metadata(
                            "ui-hotp-demo-sha256-6",
                            "stored-demo-sha256-6",
                            "Seeded HOTP SHA-256 demo credential (6 digits).")),
            sample(
                    "ui-hotp-demo-sha512",
                    "ui-hotp-demo-sha512 (SHA512, 8 digits)",
                    SHA512_SECRET_HEX,
                    HotpHashAlgorithm.SHA512,
                    8,
                    5L,
                    metadata("ui-hotp-demo-sha512", "stored-demo-sha512", "Seeded HOTP SHA-512 demo credential.")),
            sample(
                    "ui-hotp-demo-sha512-6",
                    "ui-hotp-demo-sha512-6 (SHA512, 6 digits)",
                    SHA512_SECRET_HEX,
                    HotpHashAlgorithm.SHA512,
                    6,
                    5L,
                    metadata(
                            "ui-hotp-demo-sha512-6",
                            "stored-demo-sha512-6",
                            "Seeded HOTP SHA-512 demo credential (6 digits).")));

    private static final List<InlinePreset> INLINE_PRESETS = List.of(
            inlinePresetFromVector(
                    "seeded-demo-sha1",
                    "SHA-1, 6 digits (RFC 4226)",
                    RFC_SHA1_DIGITS6_COUNTER5,
                    metadata(
                            "seeded-demo-sha1",
                            "SHA-1, 6 digits (RFC 4226)",
                            "Seeded HOTP SHA-1 credential mirrored for inline replay.")),
            inlinePresetFromVector(
                    "seeded-demo-sha1-8",
                    "SHA-1, 8 digits",
                    RFC_SHA1_DIGITS8_COUNTER5,
                    metadata(
                            "seeded-demo-sha1-8",
                            "SHA-1, 8 digits",
                            "Seeded HOTP SHA-1 credential (8 digits) mirrored for inline replay.")),
            inlinePreset(
                    "seeded-demo-sha256",
                    "SHA-256, 8 digits",
                    RFC_SHA1_DIGITS6_COUNTER0.secret().asHex(),
                    HotpHashAlgorithm.SHA256,
                    8,
                    5L,
                    "89697997",
                    metadata(
                            "seeded-demo-sha256",
                            "SHA-256, 8 digits",
                            "Seeded HOTP SHA-256 credential (8 digits) mirrored for inline replay.")),
            inlinePreset(
                    "seeded-demo-sha256-6",
                    "SHA-256, 6 digits",
                    RFC_SHA1_DIGITS6_COUNTER0.secret().asHex(),
                    HotpHashAlgorithm.SHA256,
                    6,
                    5L,
                    "697997",
                    metadata(
                            "seeded-demo-sha256-6",
                            "SHA-256, 6 digits",
                            "Seeded HOTP SHA-256 credential (6 digits) mirrored for inline replay.")),
            inlinePreset(
                    "seeded-demo-sha512",
                    "SHA-512, 8 digits",
                    SHA512_SECRET_HEX,
                    HotpHashAlgorithm.SHA512,
                    8,
                    5L,
                    "77873376",
                    metadata(
                            "seeded-demo-sha512",
                            "SHA-512, 8 digits",
                            "Seeded HOTP SHA-512 credential (8 digits) mirrored for inline replay.")),
            inlinePreset(
                    "seeded-demo-sha512-6",
                    "SHA-512, 6 digits",
                    SHA512_SECRET_HEX,
                    HotpHashAlgorithm.SHA512,
                    6,
                    5L,
                    "873376",
                    metadata(
                            "seeded-demo-sha512-6",
                            "SHA-512, 6 digits",
                            "Seeded HOTP SHA-512 credential (6 digits) mirrored for inline replay.")));

    private HotpOperatorSampleData() {
        // utility class
    }

    /** Returns the canonical HOTP credential definitions available for seeding. */
    public static List<SampleDefinition> seedDefinitions() {
        return DEFINITIONS;
    }

    /** Returns inline preset definitions for the operator console. */
    public static List<InlinePreset> inlinePresets() {
        return INLINE_PRESETS;
    }

    private static SampleDefinition sample(
            String credentialId,
            String optionLabel,
            String sharedSecretHex,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter,
            Map<String, String> metadata) {
        return new SampleDefinition(credentialId, optionLabel, sharedSecretHex, algorithm, digits, counter, metadata);
    }

    private static InlinePreset inlinePresetFromVector(
            String presetKey, String label, HotpJsonVector vector, Map<String, String> metadata) {
        return inlinePreset(
                presetKey,
                label,
                vector.secret().asHex(),
                vector.algorithm(),
                vector.digits(),
                vector.counter(),
                vector.otp(),
                metadata);
    }

    private static InlinePreset inlinePreset(
            String presetKey,
            String label,
            String sharedSecretHex,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter,
            String otp,
            Map<String, String> metadata) {
        return new InlinePreset(presetKey, label, sharedSecretHex, algorithm, digits, counter, otp, metadata);
    }

    private static Map<String, String> metadata(String presetKey, String label, String notes) {
        return Map.of(
                "seedSource", BASE_METADATA.get("seedSource"),
                "presetKey", Objects.requireNonNull(presetKey, "presetKey"),
                "label", Objects.requireNonNull(label, "label"),
                "notes", Objects.requireNonNull(notes, "notes"));
    }

    /** Canonical HOTP sample definition used for seeding and dropdown labels. */
    public record SampleDefinition(
            String credentialId,
            String optionLabel,
            String sharedSecretHex,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter,
            Map<String, String> metadata) {

        public SampleDefinition {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(optionLabel, "optionLabel");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            Objects.requireNonNull(algorithm, "algorithm");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** Inline preset definition surfaced to the operator console. */
    public record InlinePreset(
            String presetKey,
            String label,
            String sharedSecretHex,
            HotpHashAlgorithm algorithm,
            int digits,
            long counter,
            String otp,
            Map<String, String> metadata) {

        public InlinePreset {
            Objects.requireNonNull(presetKey, "presetKey");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            Objects.requireNonNull(algorithm, "algorithm");
            Objects.requireNonNull(otp, "otp");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
