package io.openauth.sim.rest.ui;

import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraMutualVector;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraOneWayVector;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraSignatureVector;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Shared sample data for inline presets and credential seeding. */
public final class OcraOperatorSampleData {

    private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");
    private static final Map<String, String> ALIASES = Map.of("operator-demo", "qa08-s064");

    private static final OcraOneWayVector COUNTER_PIN_VECTOR =
            OcraJsonVectorFixtures.getOneWay("rfc6287_counter-with-hashed-pin-c-0");

    private static final List<SampleDefinition> DEFINITIONS = List.of(
            oneWaySample(
                    "qn08-sha1",
                    "QN08 numeric - HOTP-SHA1, 6 digits (RFC 6287)",
                    "sample-qn08-sha1",
                    "rfc6287_standard-challenge-question-repeated-digits",
                    "qn08-sha1",
                    null),
            oneWaySample(
                    "c-qn08-psha1",
                    "C-QN08 PIN - HOTP-SHA256, 8 digits (RFC 6287)",
                    "sample-c-qn08-psha1",
                    "rfc6287_counter-with-hashed-pin-c-0",
                    "c-qn08-psha1",
                    null),
            oneWaySample(
                    "qn08-psha1",
                    "QN08 PIN - HOTP-SHA256, 8 digits (RFC 6287)",
                    "sample-qn08-psha1",
                    "rfc6287_hashed-pin-question-00000000",
                    "qn08-psha1",
                    null),
            oneWaySample(
                    "c-qn08-sha512",
                    "C-QN08 - HOTP-SHA512, 8 digits (RFC 6287)",
                    "sample-c-qn08-sha512",
                    "rfc6287_counter-only-sha512-question-00000000",
                    "c-qn08-sha512",
                    null),
            oneWaySample(
                    "qn08-t1m",
                    "QN08 T1M - HOTP-SHA512, 8 digits (RFC 6287)",
                    "sample-qn08-t1m",
                    "rfc6287_time-based-sha512-question-00000000",
                    "qn08-t1m",
                    Duration.ofMinutes(1)),
            oneWaySample(
                    "qa08-s064",
                    "QA08 S064 - session 64 (RFC 6287)",
                    "sample-qa08-s064",
                    "rfc6287_session-information-s064-with-alphanumeric-challenge",
                    "qa08-s064",
                    null),
            oneWaySample(
                    "qa08-s128",
                    "QA08 S128 - session 128 (RFC 6287)",
                    "sample-qa08-s128",
                    "rfc6287_session-information-s128-with-alphanumeric-challenge",
                    "qa08-s128",
                    null),
            oneWaySample(
                    "qa08-s256",
                    "QA08 S256 - session 256 (RFC 6287)",
                    "sample-qa08-s256",
                    "rfc6287_session-information-s256-with-alphanumeric-challenge",
                    "qa08-s256",
                    null),
            oneWaySample(
                    "qa08-s512",
                    "QA08 S512 - session 512 (RFC 6287)",
                    "sample-qa08-s512",
                    "rfc6287_session-information-s512-with-alphanumeric-challenge",
                    "qa08-s512",
                    null),
            mutualServerSample(
                    "qa08-mutual-sha256",
                    "QA08 mutual - HOTP-SHA256, 8 digits (RFC 6287)",
                    "sample-qa08-mutual-sha256",
                    "rfc6287_server-computes-response-for-cli22220-srv11110",
                    "qa08-mutual-sha256"),
            mutualServerSample(
                    "qa08-mutual-sha512",
                    "QA08 mutual SHA512 - HOTP-SHA512, 8 digits (RFC 6287)",
                    "sample-qa08-mutual-sha512",
                    "rfc6287_server-computes-response-for-cli22220-srv11110-sha512",
                    "qa08-mutual-sha512"),
            mutualClientSample(
                    "qa08-pin-sha512",
                    "QA08 PIN SHA512 - HOTP-SHA512, 8 digits (RFC 6287)",
                    "sample-qa08-pin-sha512",
                    "rfc6287_client-computes-response-for-srv11110-cli22220-sha512-with-pin",
                    "qa08-pin-sha512"),
            signatureSample(
                    "qa10-t1m",
                    "QA10 T1M signature - HOTP-SHA512, 8 digits (RFC 6287)",
                    "sample-qa10-t1m",
                    "rfc6287_timed-signature-sig1000000",
                    "qa10-t1m",
                    Duration.ofMinutes(1)),
            draftHotpQh64Sample());

    private OcraOperatorSampleData() {
        // utility class
    }

    static List<OperatorConsoleController.PolicyPreset> policyPresets() {
        return DEFINITIONS.stream()
                .map(definition -> new OperatorConsoleController.PolicyPreset(
                        definition.key(),
                        definition.label(),
                        new OperatorConsoleController.InlineSample(
                                definition.suite(),
                                definition.sharedSecretHex(),
                                definition.challenge(),
                                definition.sessionHex(),
                                definition.clientChallenge(),
                                definition.serverChallenge(),
                                definition.pinHashHex(),
                                definition.timestampHex(),
                                definition.counter(),
                                definition.expectedOtp())))
                .collect(Collectors.toUnmodifiableList());
    }

    public static List<SampleDefinition> seedDefinitions() {
        return DEFINITIONS;
    }

    public static Optional<SampleDefinition> findByCredentialName(String credentialName) {
        if (credentialName == null || credentialName.isBlank()) {
            return Optional.empty();
        }
        String normalized = credentialName.trim();
        for (SampleDefinition definition : DEFINITIONS) {
            if (definition.credentialName().equalsIgnoreCase(normalized)) {
                return Optional.of(definition);
            }
        }
        String aliasKey = ALIASES.get(normalized.toLowerCase(Locale.ROOT));
        if (aliasKey != null) {
            return findByPresetKey(aliasKey);
        }
        return Optional.empty();
    }

    public static Optional<SampleDefinition> findByPresetKey(String presetKey) {
        if (presetKey == null || presetKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = presetKey.trim();
        for (SampleDefinition definition : DEFINITIONS) {
            String value = definition.metadata().get("presetKey");
            if (value != null && value.equalsIgnoreCase(normalized)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    public static Optional<SampleDefinition> findBySuite(String suite) {
        if (suite == null || suite.isBlank()) {
            return Optional.empty();
        }
        String normalized = suite.trim();
        for (SampleDefinition definition : DEFINITIONS) {
            if (definition.suite().equalsIgnoreCase(normalized)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    public static Optional<SampleDefinition> findByDescriptor(
            io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor descriptor) {
        if (descriptor == null) {
            return Optional.empty();
        }
        String presetKey = descriptor.metadata().get("presetKey");
        if (presetKey != null && !presetKey.isBlank()) {
            Optional<SampleDefinition> byPreset = findByPresetKey(presetKey);
            if (byPreset.isPresent()) {
                return byPreset;
            }
        }
        Optional<SampleDefinition> byName = findByCredentialName(descriptor.name());
        if (byName.isPresent()) {
            return byName;
        }
        return findBySuite(descriptor.suite().value());
    }

    private static SampleDefinition oneWaySample(
            String key,
            String label,
            String credentialName,
            String vectorId,
            String presetKey,
            Duration allowedTimestampDrift) {
        OcraOneWayVector vector = OcraJsonVectorFixtures.getOneWay(vectorId);
        return seedDefinition(
                key,
                label,
                credentialName,
                vector.suite(),
                vector.secret().asHex().toUpperCase(Locale.ROOT),
                vector.challengeQuestion().orElse(null),
                vector.sessionInformationHex().orElse(null),
                null,
                null,
                vector.pinHashHex().orElse(null),
                vector.timestampHex().orElse(null),
                vector.counter().orElse(null),
                presetKey,
                allowedTimestampDrift,
                vector.expectedOtp());
    }

    private static SampleDefinition mutualServerSample(
            String key, String label, String credentialName, String vectorId, String presetKey) {
        OcraMutualVector vector = OcraJsonVectorFixtures.getMutual(vectorId);
        String clientChallenge = vector.challengeA();
        String serverChallenge = vector.challengeB();
        return seedDefinition(
                key,
                label,
                credentialName,
                vector.suite(),
                vector.secret().asHex().toUpperCase(Locale.ROOT),
                clientChallenge + serverChallenge,
                null,
                clientChallenge,
                serverChallenge,
                vector.pinHashHex().orElse(null),
                vector.timestampHex().orElse(null),
                null,
                presetKey,
                null,
                vector.expectedOtp());
    }

    private static SampleDefinition mutualClientSample(
            String key, String label, String credentialName, String vectorId, String presetKey) {
        OcraMutualVector vector = OcraJsonVectorFixtures.getMutual(vectorId);
        String clientChallenge = vector.challengeB();
        String serverChallenge = vector.challengeA();
        return seedDefinition(
                key,
                label,
                credentialName,
                vector.suite(),
                vector.secret().asHex().toUpperCase(Locale.ROOT),
                serverChallenge + clientChallenge,
                null,
                clientChallenge,
                serverChallenge,
                vector.pinHashHex().orElse(null),
                vector.timestampHex().orElse(null),
                null,
                presetKey,
                null,
                vector.expectedOtp());
    }

    private static SampleDefinition signatureSample(
            String key,
            String label,
            String credentialName,
            String vectorId,
            String presetKey,
            Duration allowedTimestampDrift) {
        OcraSignatureVector vector = OcraJsonVectorFixtures.getSignature(vectorId);
        return seedDefinition(
                key,
                label,
                credentialName,
                vector.suite(),
                vector.secret().asHex().toUpperCase(Locale.ROOT),
                vector.challengeQuestion(),
                null,
                null,
                null,
                null,
                vector.timestampHex().orElse(null),
                null,
                presetKey,
                allowedTimestampDrift,
                vector.expectedOtp());
    }

    private static SampleDefinition draftHotpQh64Sample() {
        return seedDefinition(
                "c-qh64",
                "C-QH64 - HOTP-SHA256, 6 digits",
                "sample-c-qh64",
                "OCRA-1:HOTP-SHA256-6:C-QH64",
                COUNTER_PIN_VECTOR.secret().asHex().toUpperCase(Locale.ROOT),
                "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF",
                null,
                null,
                null,
                null,
                null,
                1L,
                "c-qh64",
                null,
                "429968");
    }

    private static SampleDefinition seedDefinition(
            String key,
            String label,
            String credentialName,
            String suite,
            String sharedSecretHex,
            String challenge,
            String sessionHex,
            String clientChallenge,
            String serverChallenge,
            String pinHashHex,
            String timestampHex,
            Long counter,
            String presetKey,
            Duration allowedTimestampDrift,
            String expectedOtp) {
        return new SampleDefinition(
                key,
                label,
                credentialName,
                suite,
                sharedSecretHex,
                challenge,
                sessionHex,
                clientChallenge,
                serverChallenge,
                pinHashHex,
                timestampHex,
                counter,
                metadata(presetKey),
                allowedTimestampDrift,
                expectedOtp);
    }

    private static Map<String, String> metadata(String presetKey) {
        return Map.of(
                "seedSource", BASE_METADATA.get("seedSource"),
                "presetKey", Objects.requireNonNull(presetKey, "presetKey"));
    }

    public record SampleDefinition(
            String key,
            String label,
            String credentialName,
            String suite,
            String sharedSecretHex,
            String challenge,
            String sessionHex,
            String clientChallenge,
            String serverChallenge,
            String pinHashHex,
            String timestampHex,
            Long counter,
            Map<String, String> metadata,
            Duration allowedTimestampDrift,
            String expectedOtp) {

        public SampleDefinition {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(credentialName, "credentialName");
            Objects.requireNonNull(suite, "suite");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
