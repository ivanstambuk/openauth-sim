package io.openauth.sim.rest.ui;

import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Shared sample data for the FIDO2/WebAuthn operator console. */
public final class Fido2OperatorSampleData {

  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");
  private static final List<SeedDefinition> SEED_DEFINITIONS;
  private static final List<InlineVector> INLINE_VECTORS;

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

  private static List<InlineVector> buildInlineVectors(List<Sample> samples) {
    return samples.stream()
        .sorted(
            Comparator.comparing((Sample sample) -> sample.algorithm().name())
                .thenComparing(Sample::key))
        .map(Fido2OperatorSampleData::toInlineVector)
        .collect(Collectors.toUnmodifiableList());
  }

  private static InlineVector toInlineVector(Sample sample) {
    return new InlineVector(
        sample.key(),
        sample.label(),
        sample.relyingPartyId(),
        sample.origin(),
        sample.expectedType(),
        sample.credentialIdBase64Url(),
        sample.publicKeyCoseBase64Url(),
        sample.signatureCounter(),
        sample.userVerificationRequired(),
        sample.algorithm().label(),
        sample.challengeBase64Url(),
        sample.clientDataBase64Url(),
        sample.authenticatorDataBase64Url(),
        sample.signatureBase64Url(),
        sample.privateKeyJwk(),
        metadata(
            sample.key(),
            sample.label(),
            "Generator preset produced via WebAuthnAssertionGenerationApplicationService",
            sample));
  }

  private static List<SeedDefinition> buildSeedDefinitions(List<Sample> samples) {
    return samples.stream().map(Fido2OperatorSampleData::toSeedDefinition).toList();
  }

  private static SeedDefinition toSeedDefinition(Sample sample) {
    return new SeedDefinition(
        sample.key(),
        seedLabel(sample),
        sample.relyingPartyId(),
        sample.credentialIdBase64Url(),
        sample.publicKeyCoseBase64Url(),
        sample.signatureCounter(),
        sample.userVerificationRequired(),
        sample.algorithm(),
        sample.privateKeyJwk(),
        metadata(
            sample.key(),
            seedLabel(sample),
            "Seeded WebAuthn credential produced by generator preset",
            sample));
  }

  private static Map<String, String> metadata(
      String key, String label, String notes, Sample sample) {
    Map<String, String> result = new LinkedHashMap<>(BASE_METADATA);
    result.put("presetKey", Objects.requireNonNull(key, "key"));
    result.put("label", Objects.requireNonNull(label, "label"));
    result.put("notes", Objects.requireNonNull(notes, "notes"));
    if (sample != null) {
      result.put("algorithm", sample.algorithm().label());
      result.put("userVerificationRequired", Boolean.toString(sample.userVerificationRequired()));
      result.put("source", "generator-preset");
    }
    return Map.copyOf(result);
  }

  private static String seedLabel(Sample sample) {
    return "Seed " + sample.label();
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
      String privateKeyJwk,
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
      privateKeyJwk = Objects.requireNonNull(privateKeyJwk, "privateKeyJwk");
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
      String privateKeyJwk,
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
      privateKeyJwk = Objects.requireNonNull(privateKeyJwk, "privateKeyJwk");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  static {
    List<Sample> samples = WebAuthnGeneratorSamples.samples();
    INLINE_VECTORS = buildInlineVectors(samples);
    SEED_DEFINITIONS = buildSeedDefinitions(samples);
  }
}
