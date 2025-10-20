package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Ensures attestation fixture key material surfaces pretty-printed JWK payloads in a predictable
 * field ordering so downstream facades can render them directly in operator tooling.
 */
final class WebAuthnAttestationFixturesJwkFormattingTest {

    private static Stream<WebAuthnAttestationVector> vectorsWithCredentialKeys() {
        return WebAuthnAttestationFixtures.allVectors()
                .filter(vector -> vector.keyMaterial().credentialPrivateKeyJwk() != null);
    }

    private static Stream<WebAuthnAttestationVector> vectorsWithAttestationKeys() {
        return WebAuthnAttestationFixtures.allVectors()
                .filter(vector -> vector.keyMaterial().attestationPrivateKeyJwk() != null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorsWithCredentialKeys")
    void credentialPrivateKeyJwkListsKtyFirst(WebAuthnAttestationVector vector) {
        assertKtyFirst(
                vector.keyMaterial().credentialPrivateKeyJwk(),
                "Attestation fixture " + vector.vectorId() + " credential key ordering");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorsWithAttestationKeys")
    void attestationPrivateKeyJwkListsKtyFirst(WebAuthnAttestationVector vector) {
        assertKtyFirst(
                vector.keyMaterial().attestationPrivateKeyJwk(),
                "Attestation fixture " + vector.vectorId() + " attestation key ordering");
    }

    private static void assertKtyFirst(String jwk, String context) {
        assertNotNull(jwk, context + " must provide a JWK payload");
        String trimmed = jwk.stripLeading();
        assertTrue(trimmed.startsWith("{"), context + " should start with '{'");
        int firstQuote = trimmed.indexOf('"');
        assertTrue(firstQuote >= 0, context + " should contain a JSON key");
        int secondQuote = trimmed.indexOf('"', firstQuote + 1);
        assertTrue(secondQuote > firstQuote, context + " should contain a JSON key");
        String firstKey = trimmed.substring(firstQuote + 1, secondQuote);
        assertEquals("kty", firstKey, context + " should list kty as the first property");
    }
}
