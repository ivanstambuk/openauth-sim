package io.openauth.sim.application.fido2;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides deterministic WebAuthn generator presets that ship with companion private keys for CLI,
 * REST, and operator UI integrations.
 */
public final class WebAuthnGeneratorSamples {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final List<WebAuthnSignatureAlgorithm> PRESET_ORDER = List.of(
            WebAuthnSignatureAlgorithm.ES256,
            WebAuthnSignatureAlgorithm.ES384,
            WebAuthnSignatureAlgorithm.ES512,
            WebAuthnSignatureAlgorithm.RS256,
            WebAuthnSignatureAlgorithm.PS256,
            WebAuthnSignatureAlgorithm.EDDSA);

    private static final WebAuthnAssertionGenerationApplicationService GENERATOR =
            new WebAuthnAssertionGenerationApplicationService();

    private static final List<Sample> SAMPLES = buildSamples();

    private WebAuthnGeneratorSamples() {
        // utility
    }

    /** Returns the curated generator presets. */
    public static List<Sample> samples() {
        return SAMPLES;
    }

    /** Finds a preset by key. */
    public static Optional<Sample> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim();
        return SAMPLES.stream()
                .filter(sample -> sample.key().equals(normalized))
                .findFirst();
    }

    private static List<Sample> buildSamples() {
        EnumMap<WebAuthnSignatureAlgorithm, SampleBuilder> builders = new EnumMap<>(WebAuthnSignatureAlgorithm.class);

        populateFromW3cFixtures(builders);
        populateFromSyntheticVectors(builders);

        List<Sample> samples = new ArrayList<>();
        for (WebAuthnSignatureAlgorithm algorithm : PRESET_ORDER) {
            SampleBuilder builder = builders.get(algorithm);
            if (builder != null) {
                samples.add(builder.create());
            }
        }

        if (samples.isEmpty()) {
            throw new IllegalStateException("WebAuthn generator presets unavailable");
        }
        return List.copyOf(samples);
    }

    private static void populateFromW3cFixtures(EnumMap<WebAuthnSignatureAlgorithm, SampleBuilder> builders) {
        WebAuthnFixtures.w3cFixtures().stream()
                .filter(fixture -> hasText(fixture.credentialPrivateKeyJwk()))
                .sorted(Comparator.comparing((WebAuthnFixture fixture) -> !isPreferredFixture(fixture))
                        .thenComparing(WebAuthnFixture::id, String.CASE_INSENSITIVE_ORDER))
                .forEach(fixture -> builders.putIfAbsent(fixture.algorithm(), () -> createSampleFromFixture(fixture)));
    }

    private static void populateFromSyntheticVectors(EnumMap<WebAuthnSignatureAlgorithm, SampleBuilder> builders) {
        WebAuthnJsonVectorFixtures.loadAll()
                .filter(vector -> hasText(vector.privateKeyJwk()))
                .sorted(Comparator.comparing(WebAuthnJsonVector::vectorId, String.CASE_INSENSITIVE_ORDER))
                .forEach(vector -> builders.putIfAbsent(
                        vector.algorithm(), () -> createSampleFromVector(vector.algorithm(), vector)));
    }

    private static Sample createSampleFromVector(WebAuthnSignatureAlgorithm algorithm, WebAuthnJsonVector vector) {
        String privateKeyJwk = Objects.requireNonNull(vector.privateKeyJwk(), "privateKeyJwk must be present");
        String presetKey = presetKeyForVector(vector);
        String label = displayLabel(algorithm, "synthetic", null);
        String relyingPartyId = vector.storedCredential().relyingPartyId();
        String origin = vector.assertionRequest().origin();
        String expectedType = vector.assertionRequest().expectedType();
        long signatureCounter = vector.storedCredential().signatureCounter();
        boolean userVerificationRequired = vector.storedCredential().userVerificationRequired();

        GenerationResult result = GENERATOR.generate(new GenerationCommand.Inline(
                label,
                vector.storedCredential().credentialId(),
                algorithm,
                relyingPartyId,
                origin,
                expectedType,
                signatureCounter,
                userVerificationRequired,
                vector.assertionRequest().expectedChallenge(),
                privateKeyJwk));

        Map<String, String> metadata = Map.ofEntries(
                Map.entry("presetKey", presetKey),
                Map.entry("algorithm", algorithm.label()),
                Map.entry("source", "synthetic"),
                Map.entry("vectorId", vector.vectorId()),
                Map.entry("vectorRpId", vector.storedCredential().relyingPartyId()),
                Map.entry("vectorOrigin", vector.assertionRequest().origin()));

        return new Sample(
                presetKey,
                label,
                algorithm,
                relyingPartyId,
                origin,
                expectedType,
                result.credentialId(),
                result.challenge(),
                result.signatureCounter(),
                result.userVerificationRequired(),
                privateKeyJwk,
                result.publicKeyCose(),
                result.clientDataJson(),
                result.authenticatorData(),
                result.signature(),
                metadata);
    }

    private static Sample createSampleFromFixture(WebAuthnFixture fixture) {
        String privateKeyJwk = fixture.credentialPrivateKeyJwk();
        Objects.requireNonNull(privateKeyJwk, "credentialPrivateKeyJwk must be present");

        String presetKey = presetKeyForFixture(fixture);
        String label = displayLabel(fixture.algorithm(), "w3c", fixture.section());

        GenerationResult result = GENERATOR.generate(new GenerationCommand.Inline(
                label,
                fixture.storedCredential().credentialId(),
                fixture.algorithm(),
                fixture.storedCredential().relyingPartyId(),
                fixture.request().origin(),
                fixture.request().expectedType(),
                fixture.storedCredential().signatureCounter(),
                fixture.storedCredential().userVerificationRequired(),
                fixture.request().expectedChallenge(),
                privateKeyJwk));

        Map<String, String> metadata = Map.ofEntries(
                Map.entry("presetKey", presetKey),
                Map.entry("algorithm", fixture.algorithm().label()),
                Map.entry("source", "w3c"),
                Map.entry("section", fixture.section()),
                Map.entry("fixtureId", fixture.id()));

        return new Sample(
                presetKey,
                label,
                fixture.algorithm(),
                fixture.storedCredential().relyingPartyId(),
                fixture.request().origin(),
                fixture.request().expectedType(),
                result.credentialId(),
                result.challenge(),
                result.signatureCounter(),
                result.userVerificationRequired(),
                privateKeyJwk,
                result.publicKeyCose(),
                result.clientDataJson(),
                result.authenticatorData(),
                result.signature(),
                metadata);
    }

    /** Immutable sample descriptor consumed by CLI and operator UI layers. */
    public record Sample(
            String key,
            String label,
            WebAuthnSignatureAlgorithm algorithm,
            String relyingPartyId,
            String origin,
            String expectedType,
            byte[] credentialId,
            byte[] challenge,
            long signatureCounter,
            boolean userVerificationRequired,
            String privateKeyJwk,
            byte[] publicKeyCose,
            byte[] clientDataJson,
            byte[] authenticatorData,
            byte[] signature,
            Map<String, String> metadata) {

        public Sample {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(algorithm, "algorithm");
            Objects.requireNonNull(relyingPartyId, "relyingPartyId");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(expectedType, "expectedType");
            credentialId = credentialId.clone();
            challenge = challenge.clone();
            Objects.requireNonNull(privateKeyJwk, "privateKeyJwk");
            publicKeyCose = publicKeyCose.clone();
            clientDataJson = clientDataJson.clone();
            authenticatorData = authenticatorData.clone();
            signature = signature.clone();
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        public String credentialIdBase64Url() {
            return encode(credentialId);
        }

        public String challengeBase64Url() {
            return encode(challenge);
        }

        public String publicKeyCoseBase64Url() {
            return encode(publicKeyCose);
        }

        public String clientDataBase64Url() {
            return encode(clientDataJson);
        }

        public String authenticatorDataBase64Url() {
            return encode(authenticatorData);
        }

        public String signatureBase64Url() {
            return encode(signature);
        }
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static String displayLabel(WebAuthnSignatureAlgorithm algorithm, String source, String section) {
        if (hasText(source) && "w3c".equalsIgnoreCase(source) && hasText(section)) {
            return algorithm.label() + " (W3C " + section + ")";
        }
        return algorithm.label();
    }

    private static boolean isPreferredFixture(WebAuthnFixture fixture) {
        String id = fixture.id();
        return id.startsWith("packed-");
    }

    private static String presetKeyForFixture(WebAuthnFixture fixture) {
        return fixture.id();
    }

    private static String presetKeyForVector(WebAuthnJsonVector vector) {
        String normalized = vector.vectorId().toLowerCase(Locale.US).replace(':', '-');
        return "synthetic-" + normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface SampleBuilder {
        Sample create();
    }
}
