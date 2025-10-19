package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationSamplesTest {

    @Test
    void vectorsExposeFixturesForAllFormats() {
        var vectors = WebAuthnAttestationSamples.vectors();
        assertFalse(vectors.isEmpty(), "Expected attestation vectors to be available");
        for (WebAuthnAttestationFormat format : WebAuthnAttestationFormat.values()) {
            boolean present = vectors.stream()
                    .anyMatch(vector ->
                            vector.format() == format && !vector.vectorId().isBlank());
            assertTrue(present, () -> "Expected attestation catalogue to expose format " + format);
        }
    }
}
