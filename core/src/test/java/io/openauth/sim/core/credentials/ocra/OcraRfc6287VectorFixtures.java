package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretEncoding;
import java.util.List;
import java.util.Locale;

/**
 * Test-only catalogue of RFC 6287 Appendix C vectors.
 *
 * <p>Appendix C is published under the RFC Simplified BSD License; the values are transcribed here
 * verbatim for interoperability tests. Keep the shared secrets confined to test scope.
 */
final class OcraRfc6287VectorFixtures {

  static final String STANDARD_KEY_20 = "3132333435363738393031323334353637383930";
  static final String STANDARD_KEY_32 =
      "3132333435363738393031323334353637383930313233343536373839303132";
  static final String STANDARD_KEY_64 =
      "3132333435363738393031323334353637383930"
          + "3132333435363738393031323334353637383930"
          + "3132333435363738393031323334353637383930"
          + "31323334";
  static final String SESSION_HEX_64 =
      ("00112233445566778899AABBCCDDEEFF"
              + "102132435465768798A9BACBDCEDF0EF"
              + "112233445566778899AABBCCDDEEFF00"
              + "89ABCDEF0123456789ABCDEF01234567")
          .toUpperCase(Locale.ROOT);
  static final String PIN_SHA1_HASH = "7110eda4d09e062aa5e4a390b0a572ac0d2c0220";

  private OcraRfc6287VectorFixtures() {
    // Utility class
  }

  static List<OneWayVector> standardChallengeQuestionVectors() {
    return List.of(
        new OneWayVector(
            "Standard challenge question, numeric Q",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "00000000",
            null,
            null,
            null,
            null,
            "237653"),
        new OneWayVector(
            "Standard challenge question, repeated digits",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "11111111",
            null,
            null,
            null,
            null,
            "243178"),
        new OneWayVector(
            "Standard challenge question ending with 22",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "22222222",
            null,
            null,
            null,
            null,
            "653583"),
        new OneWayVector(
            "Standard challenge question ending with 33",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "33333333",
            null,
            null,
            null,
            null,
            "740991"),
        new OneWayVector(
            "Standard challenge question ending with 44",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "44444444",
            null,
            null,
            null,
            null,
            "608993"),
        new OneWayVector(
            "Standard challenge question ending with 55",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "55555555",
            null,
            null,
            null,
            null,
            "388898"),
        new OneWayVector(
            "Standard challenge question ending with 66",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "66666666",
            null,
            null,
            null,
            null,
            "816933"),
        new OneWayVector(
            "Standard challenge question ending with 77",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "77777777",
            null,
            null,
            null,
            null,
            "224598"),
        new OneWayVector(
            "Standard challenge question ending with 88",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "88888888",
            null,
            null,
            null,
            null,
            "750600"),
        new OneWayVector(
            "Standard challenge question ending with 99",
            "OCRA-1:HOTP-SHA1-6:QN08",
            STANDARD_KEY_20,
            SecretEncoding.HEX,
            "99999999",
            null,
            null,
            null,
            null,
            "294470"));
  }

  static List<OneWayVector> counterAndPinVectors() {
    return List.of(
        new OneWayVector(
            "Counter with hashed PIN (c=0)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            0L,
            PIN_SHA1_HASH,
            null,
            null,
            "65347737"),
        new OneWayVector(
            "Counter with hashed PIN (c=1)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            1L,
            PIN_SHA1_HASH,
            null,
            null,
            "86775851"),
        new OneWayVector(
            "Counter with hashed PIN (c=2)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            2L,
            PIN_SHA1_HASH,
            null,
            null,
            "78192410"),
        new OneWayVector(
            "Counter with hashed PIN (c=3)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            3L,
            PIN_SHA1_HASH,
            null,
            null,
            "71565254"),
        new OneWayVector(
            "Counter with hashed PIN (c=4)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            4L,
            PIN_SHA1_HASH,
            null,
            null,
            "10104329"),
        new OneWayVector(
            "Counter with hashed PIN (c=5)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            5L,
            PIN_SHA1_HASH,
            null,
            null,
            "65983500"),
        new OneWayVector(
            "Counter with hashed PIN (c=6)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            6L,
            PIN_SHA1_HASH,
            null,
            null,
            "70069104"),
        new OneWayVector(
            "Counter with hashed PIN (c=7)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            7L,
            PIN_SHA1_HASH,
            null,
            null,
            "91771096"),
        new OneWayVector(
            "Counter with hashed PIN (c=8)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            8L,
            PIN_SHA1_HASH,
            null,
            null,
            "75011558"),
        new OneWayVector(
            "Counter with hashed PIN (c=9)",
            "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "12345678",
            9L,
            PIN_SHA1_HASH,
            null,
            null,
            "08522129"));
  }

  static List<OneWayVector> hashedPinWithoutCounterVectors() {
    return List.of(
        new OneWayVector(
            "Hashed PIN, question 00000000",
            "OCRA-1:HOTP-SHA256-8:QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "00000000",
            null,
            PIN_SHA1_HASH,
            null,
            null,
            "83238735"),
        new OneWayVector(
            "Hashed PIN, question 11111111",
            "OCRA-1:HOTP-SHA256-8:QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "11111111",
            null,
            PIN_SHA1_HASH,
            null,
            null,
            "01501458"),
        new OneWayVector(
            "Hashed PIN, question 22222222",
            "OCRA-1:HOTP-SHA256-8:QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "22222222",
            null,
            PIN_SHA1_HASH,
            null,
            null,
            "17957585"),
        new OneWayVector(
            "Hashed PIN, question 33333333",
            "OCRA-1:HOTP-SHA256-8:QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "33333333",
            null,
            PIN_SHA1_HASH,
            null,
            null,
            "86776967"),
        new OneWayVector(
            "Hashed PIN, question 44444444",
            "OCRA-1:HOTP-SHA256-8:QN08-PSHA1",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "44444444",
            null,
            PIN_SHA1_HASH,
            null,
            null,
            "86807031"));
  }

  static List<OneWayVector> counterOnlySha512Vectors() {
    return List.of(
        new OneWayVector(
            "Counter-only SHA512, question 00000000",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "00000000",
            0L,
            null,
            null,
            null,
            "07016083"),
        new OneWayVector(
            "Counter-only SHA512, question 11111111",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "11111111",
            1L,
            null,
            null,
            null,
            "63947962"),
        new OneWayVector(
            "Counter-only SHA512, question 22222222",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "22222222",
            2L,
            null,
            null,
            null,
            "70123924"),
        new OneWayVector(
            "Counter-only SHA512, question 33333333",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "33333333",
            3L,
            null,
            null,
            null,
            "25341727"),
        new OneWayVector(
            "Counter-only SHA512, question 44444444",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "44444444",
            4L,
            null,
            null,
            null,
            "33203315"),
        new OneWayVector(
            "Counter-only SHA512, question 55555555",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "55555555",
            5L,
            null,
            null,
            null,
            "34205738"),
        new OneWayVector(
            "Counter-only SHA512, question 66666666",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "66666666",
            6L,
            null,
            null,
            null,
            "44343969"),
        new OneWayVector(
            "Counter-only SHA512, question 77777777",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "77777777",
            7L,
            null,
            null,
            null,
            "51946085"),
        new OneWayVector(
            "Counter-only SHA512, question 88888888",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "88888888",
            8L,
            null,
            null,
            null,
            "20403879"),
        new OneWayVector(
            "Counter-only SHA512, question 99999999",
            "OCRA-1:HOTP-SHA512-8:C-QN08",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "99999999",
            9L,
            null,
            null,
            null,
            "31409299"));
  }

  static List<OneWayVector> timeBasedSha512Vectors() {
    return List.of(
        new OneWayVector(
            "Time-based SHA512, question 00000000",
            "OCRA-1:HOTP-SHA512-8:QN08-T1M",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "00000000",
            null,
            null,
            null,
            "132d0b6",
            "95209754"),
        new OneWayVector(
            "Time-based SHA512, question 11111111",
            "OCRA-1:HOTP-SHA512-8:QN08-T1M",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "11111111",
            null,
            null,
            null,
            "132d0b6",
            "55907591"),
        new OneWayVector(
            "Time-based SHA512, question 22222222",
            "OCRA-1:HOTP-SHA512-8:QN08-T1M",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "22222222",
            null,
            null,
            null,
            "132d0b6",
            "22048402"),
        new OneWayVector(
            "Time-based SHA512, question 33333333",
            "OCRA-1:HOTP-SHA512-8:QN08-T1M",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "33333333",
            null,
            null,
            null,
            "132d0b6",
            "24218844"),
        new OneWayVector(
            "Time-based SHA512, question 44444444",
            "OCRA-1:HOTP-SHA512-8:QN08-T1M",
            STANDARD_KEY_64,
            SecretEncoding.HEX,
            "44444444",
            null,
            null,
            null,
            "132d0b6",
            "36209546"));
  }

  static List<OneWayVector> sessionInformationVectors() {
    return List.of(
        new OneWayVector(
            "Session information S064 with alphanumeric challenge",
            "OCRA-1:HOTP-SHA256-8:QA08-S064",
            STANDARD_KEY_32,
            SecretEncoding.HEX,
            "SESSION01",
            null,
            null,
            SESSION_HEX_64,
            null,
            "17477202"));
  }

  static List<MutualVector> mutualSha256ServerVectors() {
    return List.of(
        new MutualVector(
            "Server computes response for CLI22220/SRV11110",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "CLI22220",
            "SRV11110",
            null,
            null,
            "28247970"),
        new MutualVector(
            "Server computes response for CLI22221/SRV11111",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "CLI22221",
            "SRV11111",
            null,
            null,
            "01984843"),
        new MutualVector(
            "Server computes response for CLI22222/SRV11112",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "CLI22222",
            "SRV11112",
            null,
            null,
            "65387857"),
        new MutualVector(
            "Server computes response for CLI22223/SRV11113",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "CLI22223",
            "SRV11113",
            null,
            null,
            "03351211"),
        new MutualVector(
            "Server computes response for CLI22224/SRV11114",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "CLI22224",
            "SRV11114",
            null,
            null,
            "83412541"));
  }

  static List<MutualVector> mutualSha256ClientVectors() {
    return List.of(
        new MutualVector(
            "Client computes response for SRV11110/CLI22220",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SRV11110",
            "CLI22220",
            null,
            null,
            "15510767"),
        new MutualVector(
            "Client computes response for SRV11111/CLI22221",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SRV11111",
            "CLI22221",
            null,
            null,
            "90175646"),
        new MutualVector(
            "Client computes response for SRV11112/CLI22222",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SRV11112",
            "CLI22222",
            null,
            null,
            "33777207"),
        new MutualVector(
            "Client computes response for SRV11113/CLI22223",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SRV11113",
            "CLI22223",
            null,
            null,
            "95285278"),
        new MutualVector(
            "Client computes response for SRV11114/CLI22224",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SRV11114",
            "CLI22224",
            null,
            null,
            "28934924"));
  }

  static List<MutualVector> mutualSha512ServerVectors() {
    return List.of(
        new MutualVector(
            "Server computes response for CLI22220/SRV11110 (SHA512)",
            "OCRA-1:HOTP-SHA512-8:QA08",
            STANDARD_KEY_64,
            "CLI22220",
            "SRV11110",
            null,
            null,
            "79496648"),
        new MutualVector(
            "Server computes response for CLI22221/SRV11111 (SHA512)",
            "OCRA-1:HOTP-SHA512-8:QA08",
            STANDARD_KEY_64,
            "CLI22221",
            "SRV11111",
            null,
            null,
            "76831980"),
        new MutualVector(
            "Server computes response for CLI22222/SRV11112 (SHA512)",
            "OCRA-1:HOTP-SHA512-8:QA08",
            STANDARD_KEY_64,
            "CLI22222",
            "SRV11112",
            null,
            null,
            "12250499"),
        new MutualVector(
            "Server computes response for CLI22223/SRV11113 (SHA512)",
            "OCRA-1:HOTP-SHA512-8:QA08",
            STANDARD_KEY_64,
            "CLI22223",
            "SRV11113",
            null,
            null,
            "90856481"),
        new MutualVector(
            "Server computes response for CLI22224/SRV11114 (SHA512)",
            "OCRA-1:HOTP-SHA512-8:QA08",
            STANDARD_KEY_64,
            "CLI22224",
            "SRV11114",
            null,
            null,
            "12761449"));
  }

  static List<MutualVector> mutualSha512ClientVectors() {
    return List.of(
        new MutualVector(
            "Client computes response for SRV11110/CLI22220 (SHA512 with PIN)",
            "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
            STANDARD_KEY_64,
            "SRV11110",
            "CLI22220",
            PIN_SHA1_HASH,
            null,
            "18806276"),
        new MutualVector(
            "Client computes response for SRV11111/CLI22221 (SHA512 with PIN)",
            "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
            STANDARD_KEY_64,
            "SRV11111",
            "CLI22221",
            PIN_SHA1_HASH,
            null,
            "70020315"),
        new MutualVector(
            "Client computes response for SRV11112/CLI22222 (SHA512 with PIN)",
            "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
            STANDARD_KEY_64,
            "SRV11112",
            "CLI22222",
            PIN_SHA1_HASH,
            null,
            "01600026"),
        new MutualVector(
            "Client computes response for SRV11113/CLI22223 (SHA512 with PIN)",
            "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
            STANDARD_KEY_64,
            "SRV11113",
            "CLI22223",
            PIN_SHA1_HASH,
            null,
            "18951020"),
        new MutualVector(
            "Client computes response for SRV11114/CLI22224 (SHA512 with PIN)",
            "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
            STANDARD_KEY_64,
            "SRV11114",
            "CLI22224",
            PIN_SHA1_HASH,
            null,
            "32528969"));
  }

  static List<PlainSignatureVector> plainSignatureVectors() {
    return List.of(
        new PlainSignatureVector(
            "Plain signature challenge SIG10000",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SIG10000",
            null,
            "53095496"),
        new PlainSignatureVector(
            "Plain signature challenge SIG11000",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SIG11000",
            null,
            "04110475"),
        new PlainSignatureVector(
            "Plain signature challenge SIG12000",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SIG12000",
            null,
            "31331128"),
        new PlainSignatureVector(
            "Plain signature challenge SIG13000",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SIG13000",
            null,
            "76028668"),
        new PlainSignatureVector(
            "Plain signature challenge SIG14000",
            "OCRA-1:HOTP-SHA256-8:QA08",
            STANDARD_KEY_32,
            "SIG14000",
            null,
            "46554205"));
  }

  static List<PlainSignatureVector> timedSignatureVectors() {
    return List.of(
        new PlainSignatureVector(
            "Timed signature SIG1000000",
            "OCRA-1:HOTP-SHA512-8:QA10-T1M",
            STANDARD_KEY_64,
            "SIG1000000",
            "132d0b6",
            "77537423"),
        new PlainSignatureVector(
            "Timed signature SIG1100000",
            "OCRA-1:HOTP-SHA512-8:QA10-T1M",
            STANDARD_KEY_64,
            "SIG1100000",
            "132d0b6",
            "31970405"),
        new PlainSignatureVector(
            "Timed signature SIG1200000",
            "OCRA-1:HOTP-SHA512-8:QA10-T1M",
            STANDARD_KEY_64,
            "SIG1200000",
            "132d0b6",
            "10235557"),
        new PlainSignatureVector(
            "Timed signature SIG1300000",
            "OCRA-1:HOTP-SHA512-8:QA10-T1M",
            STANDARD_KEY_64,
            "SIG1300000",
            "132d0b6",
            "95213541"),
        new PlainSignatureVector(
            "Timed signature SIG1400000",
            "OCRA-1:HOTP-SHA512-8:QA10-T1M",
            STANDARD_KEY_64,
            "SIG1400000",
            "132d0b6",
            "65360607"));
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
