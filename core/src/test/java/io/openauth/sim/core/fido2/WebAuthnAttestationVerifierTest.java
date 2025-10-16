package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
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

    assertTrue(
        verification.result().success(), () -> "Unexpected failure for " + vector.vectorId());
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

    WebAuthnAttestationRequest tamperedRequest =
        withAttestationObject(buildRequest(packedVector), tamperedObject);

    WebAuthnAttestationVerification verification = verifier.verify(tamperedRequest);

    assertFalse(verification.result().success());
    assertEquals(
        WebAuthnVerificationError.SIGNATURE_INVALID, verification.result().error().orElseThrow());
  }

  @Test
  void mismatchedFormatFails() {
    WebAuthnAttestationVector packedVector =
        WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
            .findFirst()
            .orElseThrow();

    WebAuthnAttestationRequest incorrectFormatRequest =
        new WebAuthnAttestationRequest(
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
}
