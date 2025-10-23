package io.openauth.sim.rest.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openauth.sim.core.trace.VerboseTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TotpReplayExceptionsTest {

    @Test
    @DisplayName("Validation exception preserves metadata and exposes sanitized flag")
    void validationExceptionPreservesMetadata() {
        VerboseTrace trace = sampleTrace();
        TotpReplayValidationException exception = new TotpReplayValidationException(
                "rest-totp-validate",
                "inline",
                "otp_required",
                "OTP required",
                false,
                java.util.Map.of("field", "otp"),
                trace);

        assertThat(exception.telemetryId()).isEqualTo("rest-totp-validate");
        assertThat(exception.credentialSource()).isEqualTo("inline");
        assertThat(exception.reasonCode()).isEqualTo("otp_required");
        assertThat(exception.sanitized()).isFalse();
        assertThat(exception.details()).containsEntry("field", "otp");
        assertThatThrownBy(() -> exception.details().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(exception.trace()).isSameAs(trace);
    }

    @Test
    @DisplayName("Validation exception defaults details map when missing")
    void validationExceptionDefaultsDetails() {
        TotpReplayValidationException exception = new TotpReplayValidationException(
                "rest-totp-empty", "stored", "otp_required", "OTP required", true, null, null);

        assertThat(exception.details()).isEmpty();
        assertThat(exception.trace()).isNull();
    }

    @Test
    @DisplayName("Unexpected exception provides defensive copies and trace")
    void unexpectedExceptionProvidesDefensiveCopies() {
        VerboseTrace trace = sampleTrace();
        TotpReplayUnexpectedException exception = new TotpReplayUnexpectedException(
                "rest-totp-error",
                "stored",
                "Failure",
                java.util.Map.of("status", "error"),
                new IllegalStateException("boom"),
                trace);

        assertThat(exception.telemetryId()).isEqualTo("rest-totp-error");
        assertThat(exception.credentialSource()).isEqualTo("stored");
        assertThat(exception.details()).containsEntry("status", "error");
        assertThatThrownBy(() -> exception.details().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(exception.trace()).isSameAs(trace);
    }

    @Test
    @DisplayName("Unexpected exception normalises details when null")
    void unexpectedExceptionDefaultsDetails() {
        TotpReplayUnexpectedException exception =
                new TotpReplayUnexpectedException("rest-totp-error", "inline", "Error", null, null, null);

        assertThat(exception.details()).isEmpty();
        assertThat(exception.trace()).isNull();
    }

    private static VerboseTrace sampleTrace() {
        return VerboseTrace.builder("totp.replay.test")
                .withMetadata("mode", "test")
                .addStep(step -> step.id("normalize").summary("Normalize replay request"))
                .build();
    }
}
