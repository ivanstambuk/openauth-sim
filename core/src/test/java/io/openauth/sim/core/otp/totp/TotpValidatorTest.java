package io.openauth.sim.core.otp.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpValidatorTest {

    private static final SecretMaterial RFC_SHA1_SECRET = SecretMaterial.fromStringUtf8("12345678901234567890");

    private static TotpDescriptor descriptorEightDigits() {
        return TotpDescriptor.create(
                "token-rfc-sha1", RFC_SHA1_SECRET, TotpHashAlgorithm.SHA1, 8, Duration.ofSeconds(30));
    }

    private static TotpDescriptor descriptorSixDigits() {
        return TotpDescriptor.create(
                "token-rfc-sha1-six", RFC_SHA1_SECRET, TotpHashAlgorithm.SHA1, 6, Duration.ofSeconds(30));
    }

    @Test
    void acceptsMatchingOtpWithoutDrift() {
        TotpDescriptor descriptor = descriptorEightDigits();
        Instant timestamp = Instant.ofEpochSecond(59);

        TotpVerificationResult result =
                TotpValidator.verify(descriptor, "94287082", timestamp, TotpDriftWindow.of(0, 0), timestamp);

        assertTrue(result.valid(), "OTP should validate");
        assertEquals(0, result.matchedSkewSteps());
    }

    @Test
    void acceptsOtpWithinBackwardDrift() {
        TotpDescriptor descriptor = descriptorEightDigits();
        Instant otpIssuedAt = Instant.ofEpochSecond(59);
        Instant validationTime = otpIssuedAt.plusSeconds(30);

        TotpVerificationResult result =
                TotpValidator.verify(descriptor, "94287082", validationTime, TotpDriftWindow.of(1, 0), validationTime);

        assertTrue(result.valid(), "OTP should validate within backward drift");
        assertEquals(-1, result.matchedSkewSteps());
    }

    @Test
    void acceptsOtpWithinForwardDrift() {
        TotpDescriptor descriptor = descriptorEightDigits();
        Instant futureTimestamp = Instant.ofEpochSecond(89);
        String futureOtp = TotpGenerator.generate(descriptor, futureTimestamp);
        Instant validationTime = futureTimestamp.minusSeconds(30);

        TotpVerificationResult result =
                TotpValidator.verify(descriptor, futureOtp, validationTime, TotpDriftWindow.of(0, 1), validationTime);

        assertTrue(result.valid(), "OTP should validate within forward drift");
        assertEquals(1, result.matchedSkewSteps());
    }

    @Test
    void rejectsOtpOutsideDriftWindow() {
        TotpDescriptor descriptor = descriptorEightDigits();
        Instant otpIssuedAt = Instant.ofEpochSecond(59);
        Instant validationTime = otpIssuedAt.plusSeconds(90);

        TotpVerificationResult result =
                TotpValidator.verify(descriptor, "94287082", validationTime, TotpDriftWindow.of(1, 1), validationTime);

        assertFalse(result.valid(), "OTP should be rejected when drift exceeds window");
    }

    @Test
    void rejectsOtpWithMismatchedDigits() {
        TotpDescriptor descriptor = descriptorSixDigits();
        Instant timestamp = Instant.ofEpochSecond(59);

        TotpVerificationResult result =
                TotpValidator.verify(descriptor, "94287082", timestamp, TotpDriftWindow.of(0, 0), timestamp);

        assertFalse(result.valid(), "OTP with incorrect length should be rejected");
    }

    @Test
    void rejectsSecretsBelowMinimumLength() {
        SecretMaterial shortSecret = SecretMaterial.fromStringUtf8("short");
        TotpDescriptor descriptor =
                TotpDescriptor.create("token-short", shortSecret, TotpHashAlgorithm.SHA1, 6, Duration.ofSeconds(30));

        assertThrows(
                IllegalArgumentException.class,
                () -> TotpValidator.verify(
                        descriptor,
                        "123456",
                        Instant.ofEpochSecond(59),
                        TotpDriftWindow.of(0, 0),
                        Instant.ofEpochSecond(59)));
    }

    @Test
    void acceptsOtpWhenTimestampOverrideProvided() {
        TotpDescriptor descriptor = descriptorEightDigits();
        Instant suppliedTimestamp = Instant.ofEpochSecond(1_234_567_890L);
        String otp = TotpGenerator.generate(descriptor, suppliedTimestamp);

        TotpVerificationResult result = TotpValidator.verify(
                descriptor, otp, Instant.ofEpochSecond(0), TotpDriftWindow.of(0, 0), suppliedTimestamp);

        assertTrue(result.valid(), "OTP should validate using the supplied timestamp override");
        assertEquals(0, result.matchedSkewSteps());
    }
}
