package io.openauth.sim.core.otp.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.SecretMaterial;
import org.junit.jupiter.api.Test;

class HotpValidatorTest {

  private static final SecretMaterial RFC_SECRET =
      SecretMaterial.fromStringUtf8("12345678901234567890");

  @Test
  void acceptsMatchingOtpAndAdvancesCounter() {
    HotpDescriptor descriptor =
        HotpDescriptor.create("token-rfc", RFC_SECRET, HotpHashAlgorithm.SHA1, 6);

    HotpVerificationResult result = HotpValidator.verify(descriptor, 0, "755224");

    assertTrue(result.valid(), "OTP should validate");
    assertEquals(1L, result.nextCounter());
  }

  @Test
  void rejectsInvalidOtpWithoutAdvancingCounter() {
    HotpDescriptor descriptor =
        HotpDescriptor.create("token-rfc", RFC_SECRET, HotpHashAlgorithm.SHA1, 6);

    HotpVerificationResult result = HotpValidator.verify(descriptor, 0, "123456");

    assertFalse(result.valid(), "OTP should be rejected");
    assertEquals(0L, result.nextCounter());
  }

  @Test
  void preventsCounterOverflow() {
    HotpDescriptor descriptor =
        HotpDescriptor.create("token-rfc", RFC_SECRET, HotpHashAlgorithm.SHA1, 6);

    assertThrows(
        IllegalStateException.class,
        () -> HotpValidator.verify(descriptor, Long.MAX_VALUE, "000000"));
  }
}
