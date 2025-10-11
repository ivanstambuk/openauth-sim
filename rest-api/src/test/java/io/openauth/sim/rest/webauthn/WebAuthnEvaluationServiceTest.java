package io.openauth.sim.rest.webauthn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class WebAuthnEvaluationServiceTest {

  private WebAuthnEvaluationService service;

  @BeforeEach
  void setUp() {
    service = new WebAuthnEvaluationService(new WebAuthnAssertionGenerationApplicationService());
  }

  @Test
  @DisplayName("Inline generation surfaces private_key_invalid when private key parsing fails")
  void inlineGenerationInvalidPrivateKeyMapsReasonCode() {
    String credentialId = base64Url("inline-credential");
    String challenge = base64Url("challenge");

    WebAuthnInlineEvaluationRequest request =
        new WebAuthnInlineEvaluationRequest(
            "inline",
            credentialId,
            "example.org",
            "https://example.org",
            "webauthn.get",
            "ES256",
            1L,
            Boolean.FALSE,
            challenge,
            "{\"kty\":\"EC\",\"crv\":\"P-256\",\"d\":\"invalid\"}");

    assertThatThrownBy(() -> service.evaluateInline(request))
        .isInstanceOf(WebAuthnEvaluationValidationException.class)
        .satisfies(
            throwable ->
                assertThat(((WebAuthnEvaluationValidationException) throwable).reasonCode())
                    .isEqualTo("private_key_invalid"));
  }

  private static String base64Url(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
