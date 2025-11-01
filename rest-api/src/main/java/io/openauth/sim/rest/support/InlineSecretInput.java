package io.openauth.sim.rest.support;

import io.openauth.sim.core.encoding.Base32SecretCodec;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared helper for validating inline secret payloads across HOTP, TOTP, and OCRA REST
 * endpoints. Ensures exactly one encoding is provided and converts Base32 values into
 * canonical hexadecimal strings for downstream services.
 */
public final class InlineSecretInput {

    private InlineSecretInput() {
        // Utility class
    }

    public static boolean hasSecret(String hex, String base32) {
        return hasText(hex) || hasText(base32);
    }

    public static String resolveHex(
            String hex,
            String base32,
            Supplier<? extends RuntimeException> missingSupplier,
            Supplier<? extends RuntimeException> conflictSupplier,
            Function<String, ? extends RuntimeException> invalidBase32Mapper) {
        boolean hasHex = hasText(hex);
        boolean hasBase32 = hasText(base32);

        if (hasHex && hasBase32) {
            throw Objects.requireNonNull(conflictSupplier, "conflictSupplier").get();
        }
        if (!hasHex && !hasBase32) {
            throw Objects.requireNonNull(missingSupplier, "missingSupplier").get();
        }
        if (hasBase32) {
            try {
                return Base32SecretCodec.toUpperHex(base32);
            } catch (IllegalArgumentException ex) {
                throw Objects.requireNonNull(invalidBase32Mapper, "invalidBase32Mapper")
                        .apply(ex.getMessage());
            }
        }
        return hex.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
