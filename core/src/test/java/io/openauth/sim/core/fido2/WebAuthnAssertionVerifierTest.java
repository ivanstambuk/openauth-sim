package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import org.junit.jupiter.api.Test;

class WebAuthnAssertionVerifierTest {

  private static final WebAuthnFixture PACKED_ES256 = WebAuthnFixtures.loadPackedEs256();

  private final WebAuthnAssertionVerifier verifier = new WebAuthnAssertionVerifier();

  @Test
  void verifiesPackedEs256Vector() {
    WebAuthnVerificationResult result =
        verifier.verify(PACKED_ES256.storedCredential(), PACKED_ES256.request());

    assertTrue(result.success());
  }

  @Test
  void rejectsRpIdHashMismatch() {
    WebAuthnAssertionRequest mismatchedRpRequest = PACKED_ES256.requestWithRpId("evil.example.org");

    WebAuthnVerificationResult result =
        verifier.verify(PACKED_ES256.storedCredential(), mismatchedRpRequest);

    assertFalse(result.success());
    assertTrue(result.error().isPresent());
    assertEquals(WebAuthnVerificationError.RP_ID_HASH_MISMATCH, result.error().orElseThrow());
  }

  @Test
  void rejectsSignatureMismatch() {
    byte[] signature = PACKED_ES256.request().signature().clone();
    signature[0] ^= 0xFF;

    WebAuthnAssertionRequest tamperedSignatureRequest =
        PACKED_ES256.requestWithSignature(signature);

    WebAuthnVerificationResult result =
        verifier.verify(PACKED_ES256.storedCredential(), tamperedSignatureRequest);

    assertFalse(result.success());
    assertEquals(WebAuthnVerificationError.SIGNATURE_INVALID, result.error().orElseThrow());
  }
}
