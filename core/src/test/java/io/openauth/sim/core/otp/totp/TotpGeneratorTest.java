package io.openauth.sim.core.otp.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures.TotpJsonVector;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {

  private static final List<TotpJsonVector> SHA1_SEQUENCE =
      TotpJsonVectorFixtures.loadAll()
          .filter(
              vector ->
                  vector.algorithm() == TotpHashAlgorithm.SHA1
                      && vector.digits() == 8
                      && vector.stepSeconds() == 30)
          .sorted(Comparator.comparingLong(TotpJsonVector::timestampEpochSeconds))
          .toList();
  private static final TotpJsonVector SHA1_T59 = vector("rfc6238_sha1_digits8_t59");
  private static final TotpJsonVector SHA1_T1234567890 = vector("rfc6238_sha1_digits8_t1234567890");
  private static final TotpJsonVector SHA1_DIGITS6_T59 = vector("rfc6238_sha1_digits6_t59");
  private static final TotpJsonVector SHA256_T59 = vector("rfc6238_sha256_digits8_t59");
  private static final TotpJsonVector SHA512_T59 = vector("rfc6238_sha512_digits8_t59");

  @Test
  void generatesRfc6238Sha1Sequence() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-rfc-sha1",
            SHA1_T59.secret(),
            TotpHashAlgorithm.SHA1,
            8,
            SHA1_T59.stepDuration());
    List<String> expectedOtps =
        List.of("94287082", "07081804", "14050471", "89005924", "69279037", "65353130");

    assertEquals(
        expectedOtps,
        SHA1_SEQUENCE.stream().map(TotpJsonVector::otp).toList(),
        "Fixture OTPs must match RFC 6238 Appendix B");

    for (int idx = 0; idx < SHA1_SEQUENCE.size(); idx++) {
      TotpJsonVector vector = SHA1_SEQUENCE.get(idx);
      String actual = TotpGenerator.generate(descriptor, vector.timestamp());
      assertEquals(vector.otp(), actual, "timestamp=" + vector.timestampEpochSeconds());
    }
  }

  @Test
  void supportsSha256AndSha512Vectors() {
    TotpDescriptor sha256Descriptor =
        TotpDescriptor.create(
            "token-rfc-sha256",
            SHA256_T59.secret(),
            TotpHashAlgorithm.SHA256,
            8,
            SHA256_T59.stepDuration());
    TotpDescriptor sha512Descriptor =
        TotpDescriptor.create(
            "token-rfc-sha512",
            SHA512_T59.secret(),
            TotpHashAlgorithm.SHA512,
            8,
            SHA512_T59.stepDuration());

    Instant timestamp = Instant.ofEpochSecond(59);

    String sha256Otp = TotpGenerator.generate(sha256Descriptor, timestamp);
    String sha512Otp = TotpGenerator.generate(sha512Descriptor, timestamp);

    assertEquals(SHA256_T59.otp(), sha256Otp);
    assertEquals(SHA512_T59.otp(), sha512Otp);
  }

  @Test
  void supportsSixDigitOtp() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-six-digit",
            SHA1_DIGITS6_T59.secret(),
            TotpHashAlgorithm.SHA1,
            6,
            SHA1_DIGITS6_T59.stepDuration());

    String otp = TotpGenerator.generate(descriptor, SHA1_DIGITS6_T59.timestamp());
    assertEquals(SHA1_DIGITS6_T59.otp(), otp);
  }

  @Test
  void remainsStableWithinSameTimeStep() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-stable",
            SHA1_T1234567890.secret(),
            TotpHashAlgorithm.SHA1,
            8,
            SHA1_T1234567890.stepDuration());

    Instant boundary = SHA1_T1234567890.timestamp();
    Instant withinStep = boundary.plusSeconds(15);

    String boundaryOtp = TotpGenerator.generate(descriptor, boundary);
    String withinStepOtp = TotpGenerator.generate(descriptor, withinStep);

    assertEquals(boundaryOtp, withinStepOtp);
  }

  @Test
  void rejectsSecretsBelowMinimumLength() {
    SecretMaterial shortSecret = SecretMaterial.fromStringUtf8("short");
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-short", shortSecret, TotpHashAlgorithm.SHA1, 6, Duration.ofSeconds(30));

    assertThrows(
        IllegalArgumentException.class,
        () -> TotpGenerator.generate(descriptor, Instant.ofEpochSecond(59)));
  }

  private static TotpJsonVector vector(String id) {
    return TotpJsonVectorFixtures.getById(id);
  }
}
