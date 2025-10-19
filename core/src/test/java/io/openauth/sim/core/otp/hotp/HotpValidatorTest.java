package io.openauth.sim.core.otp.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HotpValidatorTest {

    @Test
    void acceptsMatchingOtpAndAdvancesCounter() {
        HotpJsonVectorFixtures.HotpJsonVector vector = HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == 6 && v.counter() == 0L)
                .findFirst()
                .orElseThrow();

        HotpDescriptor descriptor = HotpDescriptor.create(
                "vector-" + vector.vectorId(), vector.secret(), vector.algorithm(), vector.digits());

        HotpVerificationResult result = HotpValidator.verify(descriptor, vector.counter(), vector.otp());

        assertTrue(result.valid(), "OTP should validate");
        assertEquals(vector.counter() + 1, result.nextCounter());
    }

    @Test
    void rejectsInvalidOtpWithoutAdvancingCounter() {
        HotpJsonVectorFixtures.HotpJsonVector vector = HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == 6 && v.counter() == 0L)
                .findFirst()
                .orElseThrow();

        HotpDescriptor descriptor = HotpDescriptor.create(
                "vector-" + vector.vectorId(), vector.secret(), vector.algorithm(), vector.digits());

        HotpVerificationResult result = HotpValidator.verify(descriptor, vector.counter(), "000000");

        assertFalse(result.valid(), "OTP should be rejected");
        assertEquals(vector.counter(), result.nextCounter());
    }

    @Test
    void preventsCounterOverflow() {
        HotpJsonVectorFixtures.HotpJsonVector vector = HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == 6)
                .findFirst()
                .orElseThrow();

        HotpDescriptor descriptor = HotpDescriptor.create(
                "vector-" + vector.vectorId(), vector.secret(), vector.algorithm(), vector.digits());

        assertThrows(IllegalStateException.class, () -> HotpValidator.verify(descriptor, Long.MAX_VALUE, vector.otp()));
    }
}
