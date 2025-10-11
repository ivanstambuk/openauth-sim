package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAssertionRequest;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAssertionGenerationApplicationServiceTest {

  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private WebAuthnAssertionGenerationApplicationService service;
  private WebAuthnAssertionVerifier verifier;

  @BeforeEach
  void setUp() {
    service = new WebAuthnAssertionGenerationApplicationService();
    verifier = new WebAuthnAssertionVerifier();
  }

  @Test
  void inlineGenerationProducesVerifiableAssertionFromJwk() {
    byte[] challenge =
        URL_DECODER.decode(
            "sRBvpGpXvvF4FRHAVX3ImKA0E9Xw8X0kRjDBlMfhrbU".getBytes(StandardCharsets.UTF_8));
    byte[] credentialId =
        URL_DECODER.decode("bW9jay1jcmVkZW50aWFsLWlk".getBytes(StandardCharsets.UTF_8));
    String privateKeyJwk =
        """
        {
          "kty":"EC",
          "crv":"P-256",
          "x":"qdZggyTjMpAsFSTkjMWSwuBQuB3T-w6bDAphr8rHSVk",
          "y":"cNVi6TQ6udwSbuwQ9JCt0dAxM5LgpenvK6jQPZ2_GTs",
          "d":"GV7Q6vqPvJNmr1Lu2swyafBOzG9hvrtqs-vronAeZv8"
        }
        """;

    WebAuthnAssertionGenerationApplicationService.GenerationCommand.Inline command =
        new WebAuthnAssertionGenerationApplicationService.GenerationCommand.Inline(
            "test-inline",
            credentialId,
            WebAuthnSignatureAlgorithm.ES256,
            "example.org",
            "https://example.org",
            "webauthn.get",
            0L,
            false,
            challenge,
            privateKeyJwk);

    WebAuthnAssertionGenerationApplicationService.GenerationResult result;
    result = service.generate(command);

    assertNotNull(result);
    assertEquals("test-inline", result.credentialName());
    assertEquals(WebAuthnSignatureAlgorithm.ES256, result.algorithm());

    WebAuthnStoredCredential storedCredential =
        new WebAuthnStoredCredential(
            command.relyingPartyId(),
            result.credentialId(),
            result.publicKeyCose(),
            result.signatureCounter(),
            command.userVerificationRequired(),
            command.algorithm());

    WebAuthnAssertionRequest assertionRequest =
        new WebAuthnAssertionRequest(
            command.relyingPartyId(),
            command.origin(),
            result.challenge(),
            result.clientDataJson(),
            result.authenticatorData(),
            result.signature(),
            command.expectedType());

    assertTrue(verifier.verify(storedCredential, assertionRequest).success());
  }
}
