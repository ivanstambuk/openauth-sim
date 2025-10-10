package io.openauth.sim.rest.ui;

import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared sample data for the FIDO2/WebAuthn operator console. */
public final class Fido2OperatorSampleData {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");

  private static final WebAuthnFixture PACKED_ES256_FIXTURE = WebAuthnFixtures.loadPackedEs256();

  private static final List<SeedDefinition> SEED_DEFINITIONS =
      List.of(
          new SeedDefinition(
              "fido2-packed-es256",
              "Packed ES256 demo credential",
              PACKED_ES256_FIXTURE.storedCredential().relyingPartyId(),
              urlEncode(PACKED_ES256_FIXTURE.storedCredential().credentialId()),
              urlEncode(PACKED_ES256_FIXTURE.storedCredential().publicKeyCose()),
              PACKED_ES256_FIXTURE.storedCredential().signatureCounter(),
              PACKED_ES256_FIXTURE.storedCredential().userVerificationRequired(),
              WebAuthnSignatureAlgorithm.ES256,
              metadata(
                  "packed-es256",
                  "Packed ES256 demo credential",
                  "Seeded WebAuthn credential derived from W3C §16.1.6 fixture.")));

  private static final List<InlineVector> INLINE_VECTORS =
      List.of(
          new InlineVector(
              "packed-es256-inline",
              "Packed ES256 inline sample",
              PACKED_ES256_FIXTURE.request().relyingPartyId(),
              PACKED_ES256_FIXTURE.request().origin(),
              PACKED_ES256_FIXTURE.request().expectedType(),
              urlEncode(PACKED_ES256_FIXTURE.storedCredential().credentialId()),
              urlEncode(PACKED_ES256_FIXTURE.storedCredential().publicKeyCose()),
              PACKED_ES256_FIXTURE.storedCredential().signatureCounter(),
              PACKED_ES256_FIXTURE.storedCredential().userVerificationRequired(),
              WebAuthnSignatureAlgorithm.ES256.label(),
              urlEncode(PACKED_ES256_FIXTURE.request().expectedChallenge()),
              urlEncode(PACKED_ES256_FIXTURE.request().clientDataJson()),
              urlEncode(PACKED_ES256_FIXTURE.request().authenticatorData()),
              urlEncode(PACKED_ES256_FIXTURE.request().signature()),
              metadata(
                  "packed-es256-inline",
                  "Packed ES256 inline sample",
                  "Inline evaluation payload derived from W3C §16.1.6 fixture.")));

  private Fido2OperatorSampleData() {
    // utility class
  }

  /** Canonical WebAuthn credential definitions available for seeding. */
  public static List<SeedDefinition> seedDefinitions() {
    return SEED_DEFINITIONS;
  }

  /** Inline WebAuthn assertion vectors for populating operator console presets. */
  public static List<InlineVector> inlineVectors() {
    return INLINE_VECTORS;
  }

  private static String urlEncode(byte[] value) {
    return URL_ENCODER.encodeToString(Objects.requireNonNull(value, "value"));
  }

  private static Map<String, String> metadata(String key, String label, String notes) {
    Map<String, String> result = new java.util.LinkedHashMap<>(BASE_METADATA);
    result.put("presetKey", Objects.requireNonNull(key, "key"));
    result.put("label", Objects.requireNonNull(label, "label"));
    result.put("notes", Objects.requireNonNull(notes, "notes"));
    return Map.copyOf(result);
  }

  /** Descriptor used to seed canonical FIDO2/WebAuthn credentials. */
  public record SeedDefinition(
      String credentialId,
      String label,
      String relyingPartyId,
      String credentialIdBase64Url,
      String publicKeyCoseBase64Url,
      long signatureCounter,
      boolean userVerificationRequired,
      WebAuthnSignatureAlgorithm algorithm,
      Map<String, String> metadata) {

    public SeedDefinition {
      credentialId = Objects.requireNonNull(credentialId, "credentialId");
      label = Objects.requireNonNull(label, "label");
      relyingPartyId = Objects.requireNonNull(relyingPartyId, "relyingPartyId");
      credentialIdBase64Url =
          Objects.requireNonNull(credentialIdBase64Url, "credentialIdBase64Url");
      publicKeyCoseBase64Url =
          Objects.requireNonNull(publicKeyCoseBase64Url, "publicKeyCoseBase64Url");
      algorithm = Objects.requireNonNull(algorithm, "algorithm");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  /** Inline preset describing a WebAuthn assertion vector. */
  public record InlineVector(
      String key,
      String label,
      String relyingPartyId,
      String origin,
      String expectedType,
      String credentialIdBase64Url,
      String publicKeyCoseBase64Url,
      long signatureCounter,
      boolean userVerificationRequired,
      String algorithm,
      String expectedChallengeBase64Url,
      String clientDataBase64Url,
      String authenticatorDataBase64Url,
      String signatureBase64Url,
      Map<String, String> metadata) {

    public InlineVector {
      key = Objects.requireNonNull(key, "key");
      label = Objects.requireNonNull(label, "label");
      relyingPartyId = Objects.requireNonNull(relyingPartyId, "relyingPartyId");
      origin = Objects.requireNonNull(origin, "origin");
      expectedType = Objects.requireNonNull(expectedType, "expectedType");
      credentialIdBase64Url =
          Objects.requireNonNull(credentialIdBase64Url, "credentialIdBase64Url");
      publicKeyCoseBase64Url =
          Objects.requireNonNull(publicKeyCoseBase64Url, "publicKeyCoseBase64Url");
      algorithm = Objects.requireNonNull(algorithm, "algorithm");
      expectedChallengeBase64Url =
          Objects.requireNonNull(expectedChallengeBase64Url, "expectedChallengeBase64Url");
      clientDataBase64Url = Objects.requireNonNull(clientDataBase64Url, "clientDataBase64Url");
      authenticatorDataBase64Url =
          Objects.requireNonNull(authenticatorDataBase64Url, "authenticatorDataBase64Url");
      signatureBase64Url = Objects.requireNonNull(signatureBase64Url, "signatureBase64Url");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }
}
