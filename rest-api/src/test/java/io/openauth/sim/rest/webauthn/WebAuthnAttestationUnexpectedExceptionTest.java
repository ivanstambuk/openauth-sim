package io.openauth.sim.rest.webauthn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationUnexpectedExceptionTest {

    @Test
    @DisplayName("Unexpected exception copies metadata and preserves trace reference")
    void copiesMetadataAndTrace() {
        VerboseTrace trace = VerboseTrace.builder("fido2.attestation.verify")
                .withMetadata("mode", "manual")
                .addStep(step -> step.id("parse.attestation").summary("Parse payload"))
                .build();

        WebAuthnAttestationUnexpectedException exception = new WebAuthnAttestationUnexpectedException(
                "unexpected_error", "Verification failed", Map.of("status", "error", "format", "packed"), trace);

        assertThat(exception.reasonCode()).isEqualTo("unexpected_error");
        assertThat(exception.getMessage()).isEqualTo("Verification failed");
        assertThat(exception.metadata()).containsEntry("status", "error").containsEntry("format", "packed");
        assertThatThrownBy(() -> exception.metadata().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(exception.trace()).isSameAs(trace);
    }

    @Test
    @DisplayName("Unexpected exception normalises metadata when null")
    void defaultsMetadataWhenNull() {
        WebAuthnAttestationUnexpectedException exception =
                new WebAuthnAttestationUnexpectedException("unexpected_error", "Failure", null, null);

        assertThat(exception.metadata()).isEmpty();
        assertThat(exception.trace()).isNull();
    }
}
