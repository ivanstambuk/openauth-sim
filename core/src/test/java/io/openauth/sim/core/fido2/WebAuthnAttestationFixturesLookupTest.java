package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WebAuthnAttestationFixturesLookupTest {

  @Test
  void findByIdReturnsVectorsFromCatalogue() {
    for (WebAuthnAttestationFixtures.WebAuthnAttestationVector vector :
        WebAuthnAttestationFixtures.allVectors().toList()) {
      assertTrue(
          WebAuthnAttestationFixtures.findById(vector.vectorId()).isPresent(),
          () -> "Expected vector lookup to return " + vector.vectorId());
    }
  }
}
