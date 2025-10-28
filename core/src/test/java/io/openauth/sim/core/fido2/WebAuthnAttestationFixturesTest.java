package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WebAuthnAttestationFixturesTest {

    @Test
    void loadsVectorsForEveryFormat() {
        Set<WebAuthnAttestationFormat> missing = EnumSet.noneOf(WebAuthnAttestationFormat.class);

        for (WebAuthnAttestationFormat format : WebAuthnAttestationFormat.values()) {
            if (WebAuthnAttestationFixtures.vectorsFor(format).isEmpty()) {
                missing.add(format);
            }
        }

        assertTrue(missing.isEmpty(), () -> "Missing attestation fixtures for: " + missing);
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void vectorContainsRequiredFields(WebAuthnAttestationVector vector) {
        assertAll(
                () -> assertNotNull(vector.vectorId(), "vector id"),
                () -> assertNotNull(vector.registration(), "registration payload"),
                () -> assertFalse(vector.registration().attestationObject().length == 0, "attestation object"),
                () -> assertFalse(vector.registration().clientDataJson().length == 0, "clientDataJSON"),
                () -> assertFalse(vector.registration().challenge().length == 0, "challenge"),
                () -> assertNotNull(vector.keyMaterial(), "key material"));
    }

    @Test
    void exposesSyntheticPackedPs256Fixture() {
        WebAuthnAttestationVector vector = WebAuthnAttestationFixtures.findById("synthetic-packed-ps256")
                .orElseThrow(() -> new AssertionError("Expected synthetic-packed-ps256 fixture to be present"));

        assertEquals(
                WebAuthnSignatureAlgorithm.PS256,
                vector.algorithm(),
                "synthetic-packed-ps256 should advertise PS256 algorithm");
        assertEquals(WebAuthnAttestationFormat.PACKED, vector.format(), "fixture format");
    }

    private static Stream<WebAuthnAttestationVector> vectors() {
        return WebAuthnAttestationFixtures.allVectors();
    }
}
