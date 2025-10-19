package io.openauth.sim.core.otp.totp;

import io.openauth.sim.core.model.SecretMaterial;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Responsible for generating TOTP values. */
public final class TotpGenerator {

    private TotpGenerator() {
        throw new AssertionError("No instances");
    }

    private static final int MIN_SECRET_LENGTH_BYTES = 10;

    public static String generate(TotpDescriptor descriptor, Instant timestamp) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(timestamp, "timestamp");

        long timeStep = toTimeStep(timestamp, descriptor.stepSeconds());
        return generate(descriptor, timeStep);
    }

    static String generate(TotpDescriptor descriptor, long timeStep) {
        Objects.requireNonNull(descriptor, "descriptor");

        SecretMaterial secretMaterial = descriptor.secret();
        byte[] secret = secretMaterial.value();
        int minimumLength =
                Math.max(MIN_SECRET_LENGTH_BYTES, descriptor.algorithm().minimumSecretLengthBytes());
        if (secret.length < minimumLength) {
            throw new IllegalArgumentException("secret length below minimum for TOTP");
        }

        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array();
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

    private static long toTimeStep(Instant instant, long stepSeconds) {
        long epochSeconds = instant.getEpochSecond();
        return Math.floorDiv(epochSeconds, stepSeconds);
    }

    private static byte[] hmac(TotpHashAlgorithm algorithm, byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithm.macAlgorithm());
            mac.init(new SecretKeySpec(secret, algorithm.macAlgorithm()));
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute TOTP HMAC", ex);
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
