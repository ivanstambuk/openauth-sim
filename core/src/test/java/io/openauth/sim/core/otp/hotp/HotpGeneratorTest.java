package io.openauth.sim.core.otp.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.SecretMaterial;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotpGeneratorTest {

  private static final SecretMaterial RFC_SECRET =
      SecretMaterial.fromStringUtf8("12345678901234567890");

  @Test
  void generatesRfc4226Sequence() {
    HotpDescriptor descriptor =
        HotpDescriptor.create("token-rfc", RFC_SECRET, HotpHashAlgorithm.SHA1, 6);

    List<String> expectedOtps =
        List.of(
            "755224", "287082", "359152", "969429", "338314", "254676", "287922", "162583",
            "399871", "520489");

    for (int counter = 0; counter < expectedOtps.size(); counter++) {
      String otp = HotpGenerator.generate(descriptor, counter);
      assertEquals(expectedOtps.get(counter), otp, "counter=" + counter);
    }
  }

  @Test
  void supportsEightDigitOtp() {
    HotpDescriptor descriptor =
        HotpDescriptor.create("token-eight", RFC_SECRET, HotpHashAlgorithm.SHA1, 8);

    String otp = HotpGenerator.generate(descriptor, 0);
    assertEquals("84755224", otp);
  }

  @Test
  void rejectsSecretsBelowMinimumLength() {
    SecretMaterial shortSecret = SecretMaterial.fromStringUtf8("short");
    HotpDescriptor descriptor =
        HotpDescriptor.create("token-short", shortSecret, HotpHashAlgorithm.SHA1, 6);

    assertThrows(IllegalArgumentException.class, () -> HotpGenerator.generate(descriptor, 0));
  }
}
