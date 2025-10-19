package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WebAuthnAssertionVerifierTest {

    private static final WebAuthnFixture PACKED_ES256 = WebAuthnFixtures.loadPackedEs256();

    private final WebAuthnAssertionVerifier verifier = new WebAuthnAssertionVerifier();

    @ParameterizedTest
    @MethodSource("w3cFixtures")
    void verifiesSpecFixtures(WebAuthnFixture fixture) {
        WebAuthnVerificationResult result = verifier.verify(fixture.storedCredential(), fixture.request());

        assertTrue(
                result.success(),
                () -> "Fixture should verify: "
                        + fixture.id()
                        + " error="
                        + result.error().map(Enum::name).orElse("none"));
    }

    private static Stream<WebAuthnFixture> w3cFixtures() {
        return WebAuthnFixtures.w3cFixtures().stream();
    }

    @Test
    void rejectsRpIdHashMismatch() {
        WebAuthnAssertionRequest mismatchedRpRequest = PACKED_ES256.requestWithRpId("evil.example.org");

        WebAuthnVerificationResult result = verifier.verify(PACKED_ES256.storedCredential(), mismatchedRpRequest);

        assertFalse(result.success());
        assertTrue(result.error().isPresent());
        assertEquals(
                WebAuthnVerificationError.RP_ID_HASH_MISMATCH, result.error().orElseThrow());
    }

    @Test
    void rejectsSignatureMismatch() {
        byte[] signature = PACKED_ES256.request().signature().clone();
        signature[0] = (byte) (signature[0] ^ 0xFF);

        WebAuthnAssertionRequest tamperedSignatureRequest = PACKED_ES256.requestWithSignature(signature);

        WebAuthnVerificationResult result = verifier.verify(PACKED_ES256.storedCredential(), tamperedSignatureRequest);

        assertFalse(result.success());
        assertEquals(WebAuthnVerificationError.SIGNATURE_INVALID, result.error().orElseThrow());
    }
}
