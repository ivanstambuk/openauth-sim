package io.openauth.sim.application.contract;

import java.util.Objects;

public record CanonicalFacadeResult(
        boolean success,
        String reasonCode,
        String otp,
        Long previousCounter,
        Long nextCounter,
        Long epochSeconds,
        String suite,
        boolean includeTrace,
        boolean telemetryIdPresent) {

    public CanonicalFacadeResult {
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }
}
