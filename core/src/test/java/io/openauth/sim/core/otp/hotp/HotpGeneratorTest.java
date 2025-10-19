package io.openauth.sim.core.otp.hotp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.model.SecretMaterial;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HotpGeneratorTest {

    @Test
    void generatesRfc4226Sequence() {
        List<HotpJsonVectorFixtures.HotpJsonVector> vectors = HotpJsonVectorFixtures.loadAll()
                .filter(vector -> vector.digits() == 6)
                .sorted(Comparator.comparingLong(HotpJsonVectorFixtures.HotpJsonVector::counter))
                .collect(Collectors.toList());

        for (HotpJsonVectorFixtures.HotpJsonVector vector : vectors) {
            HotpDescriptor descriptor = HotpDescriptor.create(
                    "vector-" + vector.vectorId(), vector.secret(), vector.algorithm(), vector.digits());
            String otp = HotpGenerator.generate(descriptor, vector.counter());
            assertEquals(vector.otp(), otp, () -> "vectorId=" + vector.vectorId());
        }
    }

    @Test
    void supportsEightDigitOtp() {
        HotpJsonVectorFixtures.HotpJsonVector vector = HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == 8 && v.counter() == 0L)
                .findFirst()
                .orElseThrow();

        HotpDescriptor descriptor = HotpDescriptor.create(
                "vector-" + vector.vectorId(), vector.secret(), vector.algorithm(), vector.digits());

        String otp = HotpGenerator.generate(descriptor, vector.counter());
        assertEquals(vector.otp(), otp, () -> "vectorId=" + vector.vectorId());
    }

    @Test
    void rejectsSecretsBelowMinimumLength() {
        SecretMaterial shortSecret = SecretMaterial.fromStringUtf8("short");
        HotpDescriptor descriptor = HotpDescriptor.create("token-short", shortSecret, HotpHashAlgorithm.SHA1, 6);

        assertThrows(IllegalArgumentException.class, () -> HotpGenerator.generate(descriptor, 0));
    }
}
