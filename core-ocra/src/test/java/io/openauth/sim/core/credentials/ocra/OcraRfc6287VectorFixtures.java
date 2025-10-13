package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraMutualVector;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraOneWayVector;
import io.openauth.sim.core.credentials.ocra.OcraJsonVectorFixtures.OcraSignatureVector;
import io.openauth.sim.core.model.SecretEncoding;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Backwards-compatible façade that exposes RFC 6287 vectors for legacy tests. */
final class OcraRfc6287VectorFixtures {

  static final String STANDARD_KEY_20 =
      uppercaseSecret(
          OcraJsonVectorFixtures.getOneWay("rfc6287_standard-challenge-question-numeric-q"));
  static final String STANDARD_KEY_32 =
      uppercaseSecret(OcraJsonVectorFixtures.getOneWay("rfc6287_counter-with-hashed-pin-c-0"));
  static final String STANDARD_KEY_64 =
      uppercaseSecret(
          OcraJsonVectorFixtures.getOneWay("rfc6287_counter-only-sha512-question-00000000"));
  static final String SESSION_HEX_64 =
      sessionOf("rfc6287_session-information-s064-with-alphanumeric-challenge");
  static final String SESSION_HEX_128 =
      sessionOf("rfc6287_session-information-s128-with-alphanumeric-challenge");
  static final String SESSION_HEX_256 =
      sessionOf("rfc6287_session-information-s256-with-alphanumeric-challenge");
  static final String SESSION_HEX_512 =
      sessionOf("rfc6287_session-information-s512-with-alphanumeric-challenge");
  static final String PIN_SHA1_HASH =
      OcraJsonVectorFixtures.getOneWay("rfc6287_counter-with-hashed-pin-c-0")
          .pinHashHex()
          .orElseThrow(() -> new IllegalStateException("Missing PIN hash in counter vector"));

  private static final Comparator<OcraOneWayVector> BY_QUESTION =
      Comparator.comparing(vector -> vector.challengeQuestion().orElse(""));
  private static final Comparator<OcraOneWayVector> BY_COUNTER =
      Comparator.comparing(vector -> vector.counter().orElse(Long.MIN_VALUE));
  private static final Comparator<OcraMutualVector> BY_CHALLENGES =
      Comparator.comparing(OcraRfc6287VectorFixtures::labelOf);
  private static final Comparator<OcraSignatureVector> BY_SIGNATURE_CHALLENGE =
      Comparator.comparing(OcraSignatureVector::challengeQuestion);

  private OcraRfc6287VectorFixtures() {
    // Utility class
  }

  static List<OneWayVector> standardChallengeQuestionVectors() {
    return oneWayBySuite("OCRA-1:HOTP-SHA1-6:QN08", BY_QUESTION);
  }

  static List<OneWayVector> counterAndPinVectors() {
    return oneWayBySuite("OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1", BY_COUNTER);
  }

  static List<OneWayVector> hashedPinWithoutCounterVectors() {
    return oneWayBySuite("OCRA-1:HOTP-SHA256-8:QN08-PSHA1", BY_QUESTION);
  }

  static List<OneWayVector> counterOnlySha512Vectors() {
    return oneWayBySuite("OCRA-1:HOTP-SHA512-8:C-QN08", BY_COUNTER);
  }

  static List<OneWayVector> timeBasedSha512Vectors() {
    return oneWayBySuite("OCRA-1:HOTP-SHA512-8:QN08-T1M", BY_QUESTION);
  }

  static List<OneWayVector> sessionInformationVectors() {
    return OcraJsonVectorFixtures.loadOneWayVectors()
        .filter(vector -> vector.suite().startsWith("OCRA-1:HOTP-SHA256-8:QA08-S"))
        .sorted(Comparator.comparing(OcraOneWayVector::suite))
        .map(OcraRfc6287VectorFixtures::adapt)
        .toList();
  }

  static List<MutualVector> mutualSha256ServerVectors() {
    return mutualBySuite(
        "OCRA-1:HOTP-SHA256-8:QA08",
        vector -> labelOf(vector).toLowerCase(Locale.ROOT).startsWith("server"),
        BY_CHALLENGES);
  }

  static List<MutualVector> mutualSha256ClientVectors() {
    return mutualBySuite(
        "OCRA-1:HOTP-SHA256-8:QA08",
        vector -> labelOf(vector).toLowerCase(Locale.ROOT).startsWith("client"),
        BY_CHALLENGES);
  }

  static List<MutualVector> mutualSha512ServerVectors() {
    return mutualBySuite(
        "OCRA-1:HOTP-SHA512-8:QA08",
        vector -> labelOf(vector).toLowerCase(Locale.ROOT).startsWith("server"),
        BY_CHALLENGES);
  }

  static List<MutualVector> mutualSha512ClientVectors() {
    return mutualBySuite(
        "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
        vector -> labelOf(vector).toLowerCase(Locale.ROOT).startsWith("client"),
        BY_CHALLENGES);
  }

  static List<PlainSignatureVector> plainSignatureVectors() {
    return signatureBySuite("OCRA-1:HOTP-SHA256-8:QA08", BY_SIGNATURE_CHALLENGE);
  }

  static List<PlainSignatureVector> timedSignatureVectors() {
    return signatureBySuite("OCRA-1:HOTP-SHA512-8:QA10-T1M", BY_SIGNATURE_CHALLENGE);
  }

  private static List<OneWayVector> oneWayBySuite(
      String suite, Comparator<OcraOneWayVector> comparator) {
    return OcraJsonVectorFixtures.loadOneWayVectors()
        .filter(vector -> suite.equals(vector.suite()))
        .sorted(comparator)
        .map(OcraRfc6287VectorFixtures::adapt)
        .toList();
  }

  private static List<MutualVector> mutualBySuite(
      String suite, Predicate<OcraMutualVector> filter, Comparator<OcraMutualVector> comparator) {
    return OcraJsonVectorFixtures.loadMutualVectors()
        .filter(vector -> suite.equals(vector.suite()))
        .filter(filter)
        .sorted(comparator)
        .map(OcraRfc6287VectorFixtures::adapt)
        .toList();
  }

  private static List<PlainSignatureVector> signatureBySuite(
      String suite, Comparator<OcraSignatureVector> comparator) {
    return OcraJsonVectorFixtures.loadSignatureVectors()
        .filter(vector -> suite.equals(vector.suite()))
        .sorted(comparator)
        .map(OcraRfc6287VectorFixtures::adapt)
        .toList();
  }

  private static OneWayVector adapt(OcraOneWayVector vector) {
    return new OneWayVector(
        labelOf(vector),
        vector.suite(),
        vector.secret().asHex().toUpperCase(Locale.ROOT),
        SecretEncoding.HEX,
        vector.challengeQuestion().orElse(null),
        vector.counter().orElse(null),
        vector.pinHashHex().orElse(null),
        vector.sessionInformationHex().orElse(null),
        vector.timestampHex().orElse(null),
        vector.expectedOtp());
  }

  private static MutualVector adapt(OcraMutualVector vector) {
    return new MutualVector(
        labelOf(vector),
        vector.suite(),
        vector.secret().asHex().toUpperCase(Locale.ROOT),
        vector.challengeA(),
        vector.challengeB(),
        vector.pinHashHex().orElse(null),
        vector.timestampHex().orElse(null),
        vector.expectedOtp());
  }

  private static PlainSignatureVector adapt(OcraSignatureVector vector) {
    return new PlainSignatureVector(
        labelOf(vector),
        vector.suite(),
        vector.secret().asHex().toUpperCase(Locale.ROOT),
        vector.challengeQuestion(),
        vector.timestampHex().orElse(null),
        vector.expectedOtp());
  }

  private static String uppercaseSecret(OcraOneWayVector vector) {
    return vector.secret().asHex().toUpperCase(Locale.ROOT);
  }

  private static String sessionOf(String vectorId) {
    return OcraJsonVectorFixtures.getOneWay(vectorId)
        .sessionInformationHex()
        .orElseThrow(
            () -> new IllegalStateException("Vector " + vectorId + " missing session info"));
  }

  private static String labelOf(OcraJsonVectorFixtures.OcraVectorEntry vector) {
    return vector.label().orElse(vector.vectorId());
  }

  static record OneWayVector(
      String description,
      String ocraSuite,
      String sharedSecretHex,
      SecretEncoding secretEncoding,
      String question,
      Long counter,
      String pinHashHex,
      String sessionInformation,
      String timestampHex,
      String expectedOtp) {

    @Override
    public String toString() {
      return description;
    }
  }

  static record MutualVector(
      String description,
      String ocraSuite,
      String sharedSecretHex,
      String challengeA,
      String challengeB,
      String pinHashHex,
      String timestampHex,
      String expectedOtp) {

    @Override
    public String toString() {
      return description;
    }
  }

  static record PlainSignatureVector(
      String description,
      String ocraSuite,
      String sharedSecretHex,
      String question,
      String timestampHex,
      String expectedOtp) {

    @Override
    public String toString() {
      return description;
    }
  }
}
