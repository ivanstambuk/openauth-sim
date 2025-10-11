package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class WebAuthnJsonVectorVerificationTest {

  private final WebAuthnAssertionVerifier verifier = new WebAuthnAssertionVerifier();

  private static Stream<WebAuthnJsonVector> vectors() {
    return WebAuthnJsonVectorFixtures.loadAll();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("vectors")
  void verifiesSyntheticJsonVectors(WebAuthnJsonVector vector) {
    WebAuthnVerificationResult result =
        verifier.verify(vector.storedCredential(), vector.assertionRequest());

    assertTrue(result.success(), "Expected JSON vector " + vector.vectorId() + " to verify");
    assertTrue(
        result.error().isEmpty(), "Expected no verification error for vector " + vector.vectorId());
  }
}
