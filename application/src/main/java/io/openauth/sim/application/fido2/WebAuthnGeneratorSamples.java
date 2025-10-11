package io.openauth.sim.application.fido2;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides deterministic WebAuthn generator presets that ship with companion private keys for CLI,
 * REST, and operator UI integrations.
 */
public final class WebAuthnGeneratorSamples {

  private static final String EXPECTED_TYPE = "webauthn.get";
  private static final String DEFAULT_RELYING_PARTY = "example.org";
  private static final String DEFAULT_ORIGIN = "https://example.org";
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private static final WebAuthnAssertionGenerationApplicationService GENERATOR =
      new WebAuthnAssertionGenerationApplicationService();

  private static final List<Sample> SAMPLES = buildSamples();

  private WebAuthnGeneratorSamples() {
    // utility
  }

  /** Returns the curated generator presets. */
  public static List<Sample> samples() {
    return SAMPLES;
  }

  /** Finds a preset by key. */
  public static Optional<Sample> findByKey(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    String normalized = key.trim();
    return SAMPLES.stream().filter(sample -> sample.key().equals(normalized)).findFirst();
  }

  private static List<Sample> buildSamples() {
    return List.of(
        createSample(
            "generator-es256",
            "ES256 generator preset",
            WebAuthnSignatureAlgorithm.ES256,
            "Z2VuZXJhdG9yLWVzMjU2LWNyZWRlbnRpYWw",
            "c3RvcmVkLWNoYWxsZW5nZQ",
            0L,
            false,
            """
            {
              "kty":"EC",
              "crv":"P-256",
              "x":"qdZggyTjMpAsFSTkjMWSwuBQuB3T-w6bDAphr8rHSVk",
              "y":"cNVi6TQ6udwSbuwQ9JCt0dAxM5LgpenvK6jQPZ2_GTs",
              "d":"GV7Q6vqPvJNmr1Lu2swyafBOzG9hvrtqs-vronAeZv8"
            }
            """));
  }

  private static Sample createSample(
      String key,
      String label,
      WebAuthnSignatureAlgorithm algorithm,
      String credentialIdBase64Url,
      String challengeBase64Url,
      long signatureCounter,
      boolean userVerificationRequired,
      String privateKeyJwk) {

    byte[] credentialId = decodeBase64Url(credentialIdBase64Url, "credentialId");
    byte[] challenge = decodeBase64Url(challengeBase64Url, "challenge");

    GenerationResult result =
        GENERATOR.generate(
            new GenerationCommand.Inline(
                label,
                credentialId,
                algorithm,
                DEFAULT_RELYING_PARTY,
                DEFAULT_ORIGIN,
                EXPECTED_TYPE,
                signatureCounter,
                userVerificationRequired,
                challenge,
                privateKeyJwk));

    Map<String, String> metadata =
        Map.of("presetKey", key, "algorithm", algorithm.label(), "source", "generator-sample");

    return new Sample(
        key,
        label,
        algorithm,
        DEFAULT_RELYING_PARTY,
        DEFAULT_ORIGIN,
        EXPECTED_TYPE,
        result.credentialId(),
        result.challenge(),
        result.signatureCounter(),
        result.userVerificationRequired(),
        privateKeyJwk,
        result.publicKeyCose(),
        result.clientDataJson(),
        result.authenticatorData(),
        result.signature(),
        metadata);
  }

  /** Immutable sample descriptor consumed by CLI and operator UI layers. */
  public record Sample(
      String key,
      String label,
      WebAuthnSignatureAlgorithm algorithm,
      String relyingPartyId,
      String origin,
      String expectedType,
      byte[] credentialId,
      byte[] challenge,
      long signatureCounter,
      boolean userVerificationRequired,
      String privateKeyJwk,
      byte[] publicKeyCose,
      byte[] clientDataJson,
      byte[] authenticatorData,
      byte[] signature,
      Map<String, String> metadata) {

    public Sample {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(algorithm, "algorithm");
      Objects.requireNonNull(relyingPartyId, "relyingPartyId");
      Objects.requireNonNull(origin, "origin");
      Objects.requireNonNull(expectedType, "expectedType");
      credentialId = credentialId.clone();
      challenge = challenge.clone();
      Objects.requireNonNull(privateKeyJwk, "privateKeyJwk");
      publicKeyCose = publicKeyCose.clone();
      clientDataJson = clientDataJson.clone();
      authenticatorData = authenticatorData.clone();
      signature = signature.clone();
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String credentialIdBase64Url() {
      return encode(credentialId);
    }

    public String challengeBase64Url() {
      return encode(challenge);
    }

    public String publicKeyCoseBase64Url() {
      return encode(publicKeyCose);
    }

    public String clientDataBase64Url() {
      return encode(clientDataJson);
    }

    public String authenticatorDataBase64Url() {
      return encode(authenticatorData);
    }

    public String signatureBase64Url() {
      return encode(signature);
    }
  }

  private static byte[] decodeBase64Url(String value, String field) {
    try {
      return URL_DECODER.decode(value.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException(
          "Unable to decode Base64URL value for " + field.toLowerCase(Locale.ROOT), ex);
    }
  }

  private static String encode(byte[] value) {
    return URL_ENCODER.encodeToString(value);
  }
}
