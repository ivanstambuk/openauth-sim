package io.openauth.sim.core.otp.totp;

import java.time.Instant;
import java.util.Objects;

/** Validates TOTP codes against a descriptor and configurable drift window. */
public final class TotpValidator {

    private TotpValidator() {
        throw new AssertionError("No instances");
    }

    public static TotpVerificationResult verify(
            TotpDescriptor descriptor,
            String candidateOtp,
            Instant evaluationTime,
            TotpDriftWindow driftWindow,
            Instant timestampOverride) {

        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(candidateOtp, "candidateOtp");
        Objects.requireNonNull(evaluationTime, "evaluationTime");
        Objects.requireNonNull(driftWindow, "driftWindow");

        String normalized = candidateOtp.trim();
        if (normalized.length() != descriptor.digits() || !normalized.chars().allMatch(Character::isDigit)) {
            return TotpVerificationResult.failure();
        }

        Instant effectiveTimestamp = timestampOverride != null ? timestampOverride : evaluationTime;

        long stepSeconds = descriptor.stepSeconds();
        long baseTimeStep = Math.floorDiv(effectiveTimestamp.getEpochSecond(), stepSeconds);

        int backward = driftWindow.backwardSteps();
        int forward = driftWindow.forwardSteps();

        for (int offset = -backward; offset <= forward; offset++) {
            long candidateStep;
            try {
                candidateStep = Math.addExact(baseTimeStep, offset);
            } catch (ArithmeticException ex) {
                continue;
            }

            String expected = TotpGenerator.generate(descriptor, candidateStep);
            if (expected.equals(normalized)) {
                return TotpVerificationResult.success(offset);
            }
        }

        return TotpVerificationResult.failure();
    }
}
