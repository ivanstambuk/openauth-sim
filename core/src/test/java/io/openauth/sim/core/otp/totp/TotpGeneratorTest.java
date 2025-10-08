package io.openauth.sim.core.otp.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {

  private static final SecretMaterial RFC_SHA1_SECRET =
      SecretMaterial.fromStringUtf8("12345678901234567890");
  private static final SecretMaterial RFC_SHA256_SECRET =
      SecretMaterial.fromStringUtf8("12345678901234567890123456789012");
  private static final SecretMaterial RFC_SHA512_SECRET =
      SecretMaterial.fromStringUtf8(
          "1234567890123456789012345678901234567890123456789012345678901234");

  private static final List<Instant> RFC_TIMESTAMPS =
      List.of(
          Instant.ofEpochSecond(59),
          Instant.ofEpochSecond(1_111_111_109L),
          Instant.ofEpochSecond(1_111_111_111L),
          Instant.ofEpochSecond(1_234_567_890L),
          Instant.ofEpochSecond(2_000_000_000L),
          Instant.ofEpochSecond(20_000_000_000L));

  @Test
  void generatesRfc6238Sha1Sequence() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-rfc-sha1", RFC_SHA1_SECRET, TotpHashAlgorithm.SHA1, 8, Duration.ofSeconds(30));

    List<String> expectedOtps =
        List.of("94287082", "07081804", "14050471", "89005924", "69279037", "65353130");

    for (int idx = 0; idx < RFC_TIMESTAMPS.size(); idx++) {
      Instant timestamp = RFC_TIMESTAMPS.get(idx);
      String otp = TotpGenerator.generate(descriptor, timestamp);
      assertEquals(expectedOtps.get(idx), otp, "timestamp=" + timestamp.getEpochSecond());
    }
  }

  @Test
  void supportsSha256AndSha512Vectors() {
    TotpDescriptor sha256Descriptor =
        TotpDescriptor.create(
            "token-rfc-sha256",
            RFC_SHA256_SECRET,
            TotpHashAlgorithm.SHA256,
            8,
            Duration.ofSeconds(30));
    TotpDescriptor sha512Descriptor =
        TotpDescriptor.create(
            "token-rfc-sha512",
            RFC_SHA512_SECRET,
            TotpHashAlgorithm.SHA512,
            8,
            Duration.ofSeconds(30));

    Instant timestamp = Instant.ofEpochSecond(59);

    String sha256Otp = TotpGenerator.generate(sha256Descriptor, timestamp);
    String sha512Otp = TotpGenerator.generate(sha512Descriptor, timestamp);

    assertEquals("46119246", sha256Otp);
    assertEquals("90693936", sha512Otp);
  }

  @Test
  void supportsSixDigitOtp() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-six-digit", RFC_SHA1_SECRET, TotpHashAlgorithm.SHA1, 6, Duration.ofSeconds(30));

    String otp = TotpGenerator.generate(descriptor, Instant.ofEpochSecond(59));
    assertEquals("287082", otp);
  }

  @Test
  void remainsStableWithinSameTimeStep() {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "token-stable", RFC_SHA1_SECRET, TotpHashAlgorithm.SHA1, 8, Duration.ofSeconds(30));

    Instant boundary = Instant.ofEpochSecond(1_234_567_890L);
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
}
