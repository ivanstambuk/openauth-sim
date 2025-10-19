package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class WebAuthnJsonVectorJwkFormattingTest {

    private static Stream<WebAuthnJsonVector> vectorsWithPrivateKeys() {
        return WebAuthnJsonVectorFixtures.loadAll().filter(vector -> vector.privateKeyJwk() != null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectorsWithPrivateKeys")
    void privateKeyJwkListsKtyFirst(WebAuthnJsonVector vector) {
        assertKtyFirst(vector.privateKeyJwk(), "JSON vector " + vector.vectorId());
    }

    private static void assertKtyFirst(String jwk, String context) {
        assertNotNull(jwk, context + " must provide private key JWK");
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
