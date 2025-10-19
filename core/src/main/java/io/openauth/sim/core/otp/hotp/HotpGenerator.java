package io.openauth.sim.core.otp.hotp;

import io.openauth.sim.core.model.SecretMaterial;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Responsible for generating HOTP values. */
public final class HotpGenerator {

    private HotpGenerator() {
        throw new AssertionError("No instances");
    }

    /** RFC 4226 recommends at least 128-bit secrets; enforce 80-bit minimum for SHA-1. */
    private static final int MIN_SECRET_LENGTH_BYTES = 10;

    public static String generate(HotpDescriptor descriptor, long counter) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (counter < 0) {
            throw new IllegalArgumentException("counter must be non-negative");
        }

        SecretMaterial secretMaterial = descriptor.secret();
        byte[] secret = secretMaterial.value();
        if (secret.length
                < Math.max(MIN_SECRET_LENGTH_BYTES, descriptor.algorithm().minimumSecretLengthBytes())) {
            throw new IllegalArgumentException("secret length below minimum for HOTP");
        }

        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();

        byte[] hmac = hmac(descriptor.algorithm(), secret, counterBytes);

        int offset = hmac[hmac.length - 1] & 0x0F;
        int binary = ((hmac[offset] & 0x7F) << 24)
                | ((hmac[offset + 1] & 0xFF) << 16)
                | ((hmac[offset + 2] & 0xFF) << 8)
                | (hmac[offset + 3] & 0xFF);

        int modulo = decimalModulo(descriptor.digits());
        int otp = binary % modulo;
        return String.format("%0" + descriptor.digits() + "d", otp);
    }

    private static byte[] hmac(HotpHashAlgorithm algorithm, byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithm.macAlgorithm());
            mac.init(new SecretKeySpec(secret, algorithm.macAlgorithm()));
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute HOTP HMAC", ex);
        }
    }

    private static int decimalModulo(int digits) {
        int modulo = 1;
        for (int i = 0; i < digits; i++) {
            modulo *= 10;
        }
        return modulo;
    }
}
