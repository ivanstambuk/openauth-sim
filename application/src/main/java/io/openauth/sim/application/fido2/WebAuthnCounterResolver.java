package io.openauth.sim.application.fido2;

import java.time.Instant;

/**
 * Derives WebAuthn signature counters with a Unix-seconds default when callers omit a value.
 * Values are clamped to the unsigned 32-bit range per WebAuthn signCount semantics.
 */
public final class WebAuthnCounterResolver {

    private static final long UINT32_MAX = 0xFFFFFFFFL;

    private WebAuthnCounterResolver() {
        // utility
    }

    public static SignatureCounter resolve(Long provided) {
        if (provided == null) {
            return new SignatureCounter(clamp(Instant.now().getEpochSecond()), true);
        }
        return new SignatureCounter(clamp(provided), false);
    }

    private static long clamp(long value) {
        if (value < 0) {
            return 0L;
        }
        return Math.min(value, UINT32_MAX);
    }

    public record SignatureCounter(long value, boolean derived) {}
}
