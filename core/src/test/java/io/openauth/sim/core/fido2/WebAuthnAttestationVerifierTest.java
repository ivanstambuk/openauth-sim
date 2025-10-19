package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WebAuthnAttestationVerifierTest {

    private final WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();

    @ParameterizedTest
    @MethodSource("attestationVectors")
    void verifiesAttestationVectors(WebAuthnAttestationVector vector) {
        WebAuthnAttestationVerification verification = verifier.verify(buildRequest(vector));

        assertTrue(verification.result().success(), () -> "Unexpected failure for " + vector.vectorId());
        assertTrue(verification.attestedCredential().isPresent(), "Attested credential missing");
    }

    @Test
    void tamperedPackedSignatureFails() {
        WebAuthnAttestationVector packedVector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        byte[] tamperedObject = packedVector.registration().attestationObject().clone();
        tamperedObject[tamperedObject.length - 1] ^= 0x01;

        WebAuthnAttestationRequest tamperedRequest = withAttestationObject(buildRequest(packedVector), tamperedObject);

        WebAuthnAttestationVerification verification = verifier.verify(tamperedRequest);

        assertFalse(verification.result().success());
        assertEquals(
                WebAuthnVerificationError.SIGNATURE_INVALID,
                verification.result().error().orElseThrow());
    }

    @Test
    void mismatchedFormatFails() {
        WebAuthnAttestationVector packedVector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        WebAuthnAttestationRequest incorrectFormatRequest = new WebAuthnAttestationRequest(
                WebAuthnAttestationFormat.FIDO_U2F,
                packedVector.registration().attestationObject(),
                packedVector.registration().clientDataJson(),
                packedVector.registration().challenge(),
                packedVector.relyingPartyId(),
                packedVector.origin());

        WebAuthnAttestationVerification verification = verifier.verify(incorrectFormatRequest);

        assertFalse(verification.result().success());
        assertEquals(
                WebAuthnVerificationError.ATTESTATION_FORMAT_MISMATCH,
                verification.result().error().orElseThrow());
    }

    @Test
    void incorrectClientDataTypeFails() {
        WebAuthnAttestationVector packedVector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        byte[] tamperedClientData = mutateClientData(
                packedVector,
                original -> original.replace("\"type\":\"webauthn.create\"", "\"type\":\"webauthn.get\""));

        WebAuthnAttestationRequest invalidRequest = new WebAuthnAttestationRequest(
                packedVector.format(),
                packedVector.registration().attestationObject(),
                tamperedClientData,
                packedVector.registration().challenge(),
                packedVector.relyingPartyId(),
                packedVector.origin());

        WebAuthnAttestationVerification verification = verifier.verify(invalidRequest);

        assertFalse(verification.result().success());
        assertEquals(
                WebAuthnVerificationError.CLIENT_DATA_TYPE_MISMATCH,
                verification.result().error().orElseThrow());
    }

    @Test
    void emptyClientDataChallengeFails() {
        WebAuthnAttestationVector packedVector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        byte[] tamperedClientData = mutateClientData(
                packedVector, original -> original.replaceFirst("\"challenge\":\"[^\"]+\"", "\"challenge\":\"\""));

        WebAuthnAttestationRequest invalidRequest = new WebAuthnAttestationRequest(
                packedVector.format(),
                packedVector.registration().attestationObject(),
                tamperedClientData,
                packedVector.registration().challenge(),
                packedVector.relyingPartyId(),
                packedVector.origin());

        WebAuthnAttestationVerification verification = verifier.verify(invalidRequest);

        assertFalse(verification.result().success());
        assertEquals(
                WebAuthnVerificationError.CLIENT_DATA_CHALLENGE_MISMATCH,
                verification.result().error().orElseThrow());
    }

    @Test
    void mismatchedClientDataOriginFails() {
        WebAuthnAttestationVector packedVector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        byte[] tamperedClientData = mutateClientData(
                packedVector,
                original -> original.replace(
                        "\"origin\":\"" + packedVector.origin() + "\"", "\"origin\":\"https://evil.example\""));

        WebAuthnAttestationRequest invalidRequest = new WebAuthnAttestationRequest(
                packedVector.format(),
                packedVector.registration().attestationObject(),
                tamperedClientData,
                packedVector.registration().challenge(),
                packedVector.relyingPartyId(),
                packedVector.origin());

        WebAuthnAttestationVerification verification = verifier.verify(invalidRequest);

        assertFalse(verification.result().success());
        assertEquals(
                WebAuthnVerificationError.ORIGIN_MISMATCH,
                verification.result().error().orElseThrow());
    }

    private static Stream<WebAuthnAttestationVector> attestationVectors() {
        return WebAuthnAttestationFixtures.allVectors();
    }

    private static WebAuthnAttestationRequest buildRequest(WebAuthnAttestationVector vector) {
        return new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin());
    }

    private static WebAuthnAttestationRequest withAttestationObject(
            WebAuthnAttestationRequest request, byte[] attestationObject) {
        return new WebAuthnAttestationRequest(
                request.format(),
                attestationObject,
                request.clientDataJson(),
                request.expectedChallenge(),
                request.relyingPartyId(),
                request.origin());
    }

    private static byte[] mutateClientData(
            WebAuthnAttestationVector vector, java.util.function.UnaryOperator<String> mutator) {
        String original = new String(vector.registration().clientDataJson(), StandardCharsets.UTF_8);
        String mutated = mutator.apply(original);
        return mutated.getBytes(StandardCharsets.UTF_8);
    }
}
