package io.openauth.sim.application.preview;

import java.util.Objects;

/** Simple value object describing an OTP preview entry for evaluation windows. */
public record OtpPreview(String counter, int delta, String otp) {

    public OtpPreview {
        Objects.requireNonNull(otp, "otp");
    }

    public static OtpPreview forCounter(long counter, int delta, String otp) {
        return new OtpPreview(Long.toString(counter), delta, otp);
    }

    public static OtpPreview forCounter(String counterLabel, int delta, String otp) {
        return new OtpPreview(counterLabel, delta, otp);
    }

    public static OtpPreview centerOnly(String otp) {
        return new OtpPreview(null, 0, otp);
    }
}
