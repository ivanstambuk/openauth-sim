package io.openauth.sim.core.credentials.ocra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.model.SecretEncoding;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Placeholder parameterised tests covering the RFC 6287 vectors.
 *
 * <p>TODO(#T018): Replace the UnsupportedOperationException assertions with real OTP comparisons
 * once the execution helper is implemented.
 */
@Tag("ocra")
@TestInstance(Lifecycle.PER_CLASS)
final class OcraRfc6287PlaceholderTest {

  private final OcraCredentialFactory factory = new OcraCredentialFactory();

  @DisplayName("Standard OCRA challenge vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("standardChallengeVectors")
  void standardChallengeVectorsThrowUntilHelperArrives(
      OcraRfc6287VectorFixtures.OneWayVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Counter + PIN vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("counterAndPinVectors")
  void counterAndPinVectorsThrowUntilHelperArrives(OcraRfc6287VectorFixtures.OneWayVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Hashed PIN question vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("hashedPinVectors")
  void hashedPinVectorsThrowUntilHelperArrives(OcraRfc6287VectorFixtures.OneWayVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Counter-only SHA512 vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("counterOnlyVectors")
  void counterOnlyVectorsThrowUntilHelperArrives(OcraRfc6287VectorFixtures.OneWayVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Time-based SHA512 vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("timeBasedVectors")
  void timeBasedVectorsThrowUntilHelperArrives(OcraRfc6287VectorFixtures.OneWayVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Mutual challenge-response server vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("mutualServerVectors")
  void mutualServerVectorsThrowUntilHelperArrives(OcraRfc6287VectorFixtures.MutualVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Mutual challenge-response client vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("mutualClientVectors")
  void mutualClientVectorsThrowUntilHelperArrives(OcraRfc6287VectorFixtures.MutualVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Plain signature vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("plainSignatureVectors")
  void plainSignatureVectorsThrowUntilHelperArrives(
      OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  @DisplayName("Timed signature vectors await execution helper")
  @ParameterizedTest(name = "{index} ⇒ {0}")
  @MethodSource("timedSignatureVectors")
  void timedSignatureVectorsThrowUntilHelperArrives(
      OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
    assertUnsupported(vector, contextFor(vector));
  }

  private void assertUnsupported(
      OcraRfc6287VectorFixtures.OneWayVector vector,
      OcraResponseCalculator.OcraExecutionContext context) {
    OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
    UnsupportedOperationException ex =
        assertThrows(
            UnsupportedOperationException.class,
            () -> OcraResponseCalculator.generate(descriptor, context));
    assertNoSecretLeak(vector.sharedSecretHex(), ex);
  }

  private void assertUnsupported(
      OcraRfc6287VectorFixtures.MutualVector vector,
      OcraResponseCalculator.OcraExecutionContext context) {
    OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
    UnsupportedOperationException ex =
        assertThrows(
            UnsupportedOperationException.class,
            () -> OcraResponseCalculator.generate(descriptor, context));
    assertNoSecretLeak(vector.sharedSecretHex(), ex);
  }

  private void assertUnsupported(
      OcraRfc6287VectorFixtures.PlainSignatureVector vector,
      OcraResponseCalculator.OcraExecutionContext context) {
    OcraCredentialDescriptor descriptor = descriptorFor(vector.description(), vector);
    UnsupportedOperationException ex =
        assertThrows(
            UnsupportedOperationException.class,
            () -> OcraResponseCalculator.generate(descriptor, context));
    assertNoSecretLeak(vector.sharedSecretHex(), ex);
  }

  private OcraResponseCalculator.OcraExecutionContext contextFor(
      OcraRfc6287VectorFixtures.OneWayVector vector) {
    return new OcraResponseCalculator.OcraExecutionContext(
        vector.counter(),
        vector.question(),
        vector.sessionInformation(),
        null,
        null,
        vector.pinHashHex(),
        vector.timestampHex());
  }

  private OcraResponseCalculator.OcraExecutionContext contextFor(
      OcraRfc6287VectorFixtures.MutualVector vector) {
    return new OcraResponseCalculator.OcraExecutionContext(
        null,
        null,
        null,
        vector.challengeA(),
        vector.challengeB(),
        vector.pinHashHex(),
        vector.timestampHex());
  }

  private OcraResponseCalculator.OcraExecutionContext contextFor(
      OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
    return new OcraResponseCalculator.OcraExecutionContext(
        null, vector.question(), null, null, null, null, vector.timestampHex());
  }

  private OcraCredentialDescriptor descriptorFor(
      String prefix, OcraRfc6287VectorFixtures.OneWayVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            nameFor(prefix, vector.ocraSuite()),
            vector.ocraSuite(),
            vector.sharedSecretHex(),
            vector.secretEncoding(),
            vector.counter(),
            vector.pinHashHex(),
            null,
            Map.of("source", "rfc6287"));
    return assertDoesNotThrow(() -> factory.createDescriptor(request));
  }

  private OcraCredentialDescriptor descriptorFor(
      String prefix, OcraRfc6287VectorFixtures.MutualVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            nameFor(prefix, vector.ocraSuite()),
            vector.ocraSuite(),
            vector.sharedSecretHex(),
            SecretEncoding.HEX,
            null,
            vector.pinHashHex(),
            null,
            Map.of("source", "rfc6287"));
    return assertDoesNotThrow(() -> factory.createDescriptor(request));
  }

  private OcraCredentialDescriptor descriptorFor(
      String prefix, OcraRfc6287VectorFixtures.PlainSignatureVector vector) {
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            nameFor(prefix, vector.ocraSuite()),
            vector.ocraSuite(),
            vector.sharedSecretHex(),
            SecretEncoding.HEX,
            null,
            null,
            null,
            Map.of("source", "rfc6287"));
    return assertDoesNotThrow(() -> factory.createDescriptor(request));
  }

  private static void assertNoSecretLeak(String sharedSecretHex, UnsupportedOperationException ex) {
    assertTrue(
        OcraResponseCalculator.UNIMPLEMENTED_MESSAGE.equals(ex.getMessage()),
        "Exception message should reflect placeholder state");
    if (sharedSecretHex == null) {
      return;
    }
    String prefix =
        sharedSecretHex
            .substring(0, Math.min(sharedSecretHex.length(), 8))
            .toLowerCase(Locale.ROOT);
    assertTrue(
        !ex.getMessage().toLowerCase(Locale.ROOT).contains(prefix),
        "Exception message must not leak secret material");
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
