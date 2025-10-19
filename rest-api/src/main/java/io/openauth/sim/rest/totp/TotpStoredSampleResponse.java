package io.openauth.sim.rest.totp;

import java.util.Map;
import java.util.Objects;

record TotpStoredSampleResponse(
        String credentialId,
        String algorithm,
        int digits,
        long stepSeconds,
        int driftBackward,
        int driftForward,
        long timestamp,
        String otp,
        Map<String, String> metadata) {

    TotpStoredSampleResponse {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(otp, "otp");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
