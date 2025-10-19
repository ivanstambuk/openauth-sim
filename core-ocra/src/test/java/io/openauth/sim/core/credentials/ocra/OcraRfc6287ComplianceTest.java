package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.model.SecretEncoding;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Proves the built-in execution helper reproduces RFC 6287 Appendix C reference vectors. */
@Tag("ocra")
@TestInstance(Lifecycle.PER_CLASS)
final class OcraRfc6287ComplianceTest {

    private final OcraCredentialFactory factory = new OcraCredentialFactory();

    @DisplayName("Standard numeric challenge vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("standardChallengeVectors")
    void standardChallengeVectors(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Counter + PIN vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("counterAndPinVectors")
    void counterPinVectors(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Hashed PIN vectors without counter match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("hashedPinVectors")
    void hashedPinVectors(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Counter-only SHA512 vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("counterOnlyVectors")
    void counterOnlyVectors(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Time-based SHA512 vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("timeBasedVectors")
    void timeBasedVectors(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Session-information vectors match expected OCRA outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("sessionInformationVectors")
    void sessionInformationVectors(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Draft HOTP SHA256-6/QH64 vectors align with Appendix B generator")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("draftHotpVariants")
    void draftHotpVariants(OcraRfc6287VectorFixtures.OneWayVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Mutual challenge-response server vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("mutualServerVectors")
    void mutualServerVectors(OcraRfc6287VectorFixtures.MutualVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Mutual challenge-response client vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("mutualClientVectors")
    void mutualClientVectors(OcraRfc6287VectorFixtures.MutualVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Plain signature vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("plainSignatureVectors")
    void plainSignatureVectors(OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @DisplayName("Timed signature vectors match RFC 6287 Appendix C outputs")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("timedSignatureVectors")
    void timedSignatureVectors(OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
        assertMatchesPublishedOtp(vector);
    }

    @Test
    @DisplayName("Missing counter input triggers descriptive error without leaking secrets")
    void missingCounterThrowsDescriptiveError() {
        OcraRfc6287VectorFixtures.OneWayVector vector =
                OcraRfc6287VectorFixtures.counterAndPinVectors().get(0);

        OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
        OcraResponseCalculator.OcraExecutionContext context = new OcraResponseCalculator.OcraExecutionContext(
                null, vector.question(), null, null, null, vector.pinHashHex(), null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> OcraResponseCalculator.generate(descriptor, context));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("counter"));
        assertTrue(!ex.getMessage()
                .toLowerCase(Locale.ROOT)
                .contains(vector.sharedSecretHex().substring(0, 6).toLowerCase(Locale.ROOT)));
    }

    @Test
    @DisplayName("Missing session information triggers descriptive error")
    void missingSessionInformationThrowsDescriptiveError() {
        OcraRfc6287VectorFixtures.OneWayVector vector =
                OcraRfc6287VectorFixtures.sessionInformationVectors().get(0);

        OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
        OcraResponseCalculator.OcraExecutionContext context = new OcraResponseCalculator.OcraExecutionContext(
                null, vector.question(), null, null, null, vector.pinHashHex(), null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> OcraResponseCalculator.generate(descriptor, context));
        assertTrue(ex.getMessage().toLowerCase(Locale.ROOT).contains("session"));
        assertTrue(!ex.getMessage()
                .toLowerCase(Locale.ROOT)
                .contains(vector.sessionInformation().substring(0, 6).toLowerCase(Locale.ROOT)));
    }

    @DisplayName("Session-enabled suites expose expected byte lengths")
    @ParameterizedTest(name = "{index} ⇒ {0}")
    @MethodSource("sessionInformationVectors")
    void sessionInformationSuitesExposeExpectedLengths(OcraRfc6287VectorFixtures.OneWayVector vector) {
        OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
        int expectedLengthBytes = vector.sessionInformation().length() / 2;
        int actualLengthBytes = descriptor
                .suite()
                .dataInput()
                .sessionInformation()
                .orElseThrow(() -> new AssertionError("suite missing session specification"))
                .lengthBytes();
        assertEquals(expectedLengthBytes, actualLengthBytes);
    }

    private void assertMatchesPublishedOtp(OcraRfc6287VectorFixtures.OneWayVector vector) {
        OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
        OcraResponseCalculator.OcraExecutionContext context = new OcraResponseCalculator.OcraExecutionContext(
                vector.counter(),
                vector.question(),
                vector.sessionInformation(),
                null,
                null,
                vector.pinHashHex(),
                vector.timestampHex());
        String otp = OcraResponseCalculator.generate(descriptor, context);
        assertEquals(vector.expectedOtp(), otp);
    }

    private void assertMatchesPublishedOtp(OcraRfc6287VectorFixtures.MutualVector vector) {
        OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
        OcraResponseCalculator.OcraExecutionContext context = new OcraResponseCalculator.OcraExecutionContext(
                null, null, null, vector.challengeA(), vector.challengeB(), vector.pinHashHex(), vector.timestampHex());
        String otp = OcraResponseCalculator.generate(descriptor, context);
        assertEquals(vector.expectedOtp(), otp);
    }

    private void assertMatchesPublishedOtp(OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
        OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
        OcraResponseCalculator.OcraExecutionContext context = new OcraResponseCalculator.OcraExecutionContext(
                null, vector.question(), null, null, null, null, vector.timestampHex());
        String otp = OcraResponseCalculator.generate(descriptor, context);
        assertEquals(vector.expectedOtp(), otp);
    }

    private OcraCredentialDescriptor descriptorFor(String prefix, OcraRfc6287VectorFixtures.OneWayVector vector) {
        OcraCredentialRequest request = new OcraCredentialRequest(
                nameFor(prefix, vector.ocraSuite()),
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                vector.secretEncoding(),
                vector.counter(),
                vector.pinHashHex(),
                null,
                Map.of("source", "rfc6287"));
        return factory.createDescriptor(request);
    }

    private OcraCredentialDescriptor descriptorFor(String prefix, OcraRfc6287VectorFixtures.MutualVector vector) {
        OcraCredentialRequest request = new OcraCredentialRequest(
                nameFor(prefix, vector.ocraSuite()),
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                SecretEncoding.HEX,
                null,
                vector.pinHashHex(),
                null,
                Map.of("source", "rfc6287"));
        return factory.createDescriptor(request);
    }

    private OcraCredentialDescriptor descriptorFor(
            String prefix, OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
        OcraCredentialRequest request = new OcraCredentialRequest(
                nameFor(prefix, vector.ocraSuite()),
                vector.ocraSuite(),
                vector.sharedSecretHex(),
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "rfc6287"));
        return factory.createDescriptor(request);
    }

    private static String nameFor(String prefix, String suite) {
        String base = prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("^-+", "").replaceAll("-+$", "");
        String suffix = Integer.toHexString(suite.hashCode());
        String candidate = (base.isEmpty() ? "ocra" : base) + "-" + suffix;
        if (candidate.length() > 63) {
            candidate = candidate.substring(0, 63);
        }
        if (candidate.endsWith("-")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate;
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> standardChallengeVectors() {
        return OcraRfc6287VectorFixtures.standardChallengeQuestionVectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> counterAndPinVectors() {
        return OcraRfc6287VectorFixtures.counterAndPinVectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> hashedPinVectors() {
        return OcraRfc6287VectorFixtures.hashedPinWithoutCounterVectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> counterOnlyVectors() {
        return OcraRfc6287VectorFixtures.counterOnlySha512Vectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> timeBasedVectors() {
        return OcraRfc6287VectorFixtures.timeBasedSha512Vectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> sessionInformationVectors() {
        return OcraRfc6287VectorFixtures.sessionInformationVectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.OneWayVector> draftHotpVariants() {
        return OcraDraftHotpVariantsVectorFixtures.counterHexVectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.MutualVector> mutualServerVectors() {
        return Stream.concat(
                OcraRfc6287VectorFixtures.mutualSha256ServerVectors().stream(),
                OcraRfc6287VectorFixtures.mutualSha512ServerVectors().stream());
    }

    private Stream<OcraRfc6287VectorFixtures.MutualVector> mutualClientVectors() {
        return Stream.concat(
                OcraRfc6287VectorFixtures.mutualSha256ClientVectors().stream(),
                OcraRfc6287VectorFixtures.mutualSha512ClientVectors().stream());
    }

    private Stream<OcraRfc6287VectorFixtures.PlainSignatureVector> plainSignatureVectors() {
        return OcraRfc6287VectorFixtures.plainSignatureVectors().stream();
    }

    private Stream<OcraRfc6287VectorFixtures.PlainSignatureVector> timedSignatureVectors() {
        return OcraRfc6287VectorFixtures.timedSignatureVectors().stream();
    }
}
