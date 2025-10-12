package io.openauth.sim.rest.ui;

import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared sample data for TOTP operator console seeding and presets. */
public final class TotpOperatorSampleData {

  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");

  private static final List<SampleDefinition> DEFINITIONS =
      List.of(
          sample(
              "ui-totp-demo",
              "ui-totp-demo (SHA1, 6 digits, 30s step)",
              "31323334353637383930313233343536373839303132",
              TotpHashAlgorithm.SHA1,
              6,
              30,
              1,
              1,
              1_111_111_111L,
              metadata(
                  "ui-totp-demo",
                  "ui-totp-demo (SHA1, 6 digits, 30s step)",
                  "Seeded TOTP credential (SHA-1, 6 digits, 30s step).")),
          sample(
              "ui-totp-demo-sha512",
              "ui-totp-demo-sha512 (SHA512, 8 digits, 60s step)",
              "3132333435363738393031323334353637383930313233343536373839303132",
              TotpHashAlgorithm.SHA512,
              8,
              60,
              2,
              2,
              2_222_222_222L,
              metadata(
                  "ui-totp-demo-sha512",
                  "ui-totp-demo-sha512 (SHA512, 8 digits, 60s step)",
                  "Seeded TOTP credential (SHA-512, 8 digits, 60s step).")));

  private static final List<InlinePreset> INLINE_PRESETS =
      List.of(
          inlinePreset(
              "inline-rfc6238-sha1",
              "SHA-1, 8 digits, 30s (RFC 6238)",
              "3132333435363738393031323334353637383930",
              TotpHashAlgorithm.SHA1,
              8,
              30,
              1,
              1,
              59L,
              metadata(
                  "inline-rfc6238-sha1",
                  "SHA-1, 8 digits, 30s (RFC 6238)",
                  "Inline preset based on RFC 6238 sample.")),
          inlinePreset(
              "inline-ui-totp-demo",
              "SHA-1, 6 digits, 30s (seeded demo)",
              "31323334353637383930313233343536373839303132",
              TotpHashAlgorithm.SHA1,
              6,
              30,
              1,
              1,
              1_111_111_111L,
              metadata(
                  "inline-ui-totp-demo",
                  "SHA-1, 6 digits, 30s (seeded demo)",
                  "Inline preset mirroring the seeded demo credential.")));

  private TotpOperatorSampleData() {
    // utility class
  }

  /** Returns the canonical TOTP credential definitions available for seeding. */
  public static List<SampleDefinition> seedDefinitions() {
    return DEFINITIONS;
  }

  /** Returns inline sample presets for populating the TOTP inline forms. */
  public static List<InlinePreset> inlinePresets() {
    return INLINE_PRESETS;
  }

  private static SampleDefinition sample(
      String credentialId,
      String optionLabel,
      String sharedSecretHex,
      TotpHashAlgorithm algorithm,
      int digits,
      int stepSeconds,
      int driftBackward,
      int driftForward,
      long sampleTimestamp,
      Map<String, String> metadata) {
    Map<String, String> enrichedMetadata = new java.util.LinkedHashMap<>(metadata);
    enrichedMetadata.putIfAbsent("sampleTimestamp", Long.toString(sampleTimestamp));
    return new SampleDefinition(
        credentialId,
        optionLabel,
        sharedSecretHex,
        algorithm,
        digits,
        stepSeconds,
        driftBackward,
        driftForward,
        Map.copyOf(enrichedMetadata));
  }

  private static Map<String, String> metadata(String presetKey, String label, String notes) {
    Map<String, String> metadata = new java.util.LinkedHashMap<>();
    metadata.put("seedSource", BASE_METADATA.get("seedSource"));
    metadata.put("presetKey", Objects.requireNonNull(presetKey, "presetKey"));
    metadata.put("label", Objects.requireNonNull(label, "label"));
    metadata.put("notes", Objects.requireNonNull(notes, "notes"));
    return Map.copyOf(metadata);
  }

  private static InlinePreset inlinePreset(
      String key,
      String label,
      String sharedSecretHex,
      TotpHashAlgorithm algorithm,
      int digits,
      int stepSeconds,
      int driftBackward,
      int driftForward,
      long timestampEpochSeconds,
      Map<String, String> metadata) {
    SecretMaterial secret = SecretMaterial.fromHex(sharedSecretHex);
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            key,
            secret,
            algorithm,
            digits,
            Duration.ofSeconds(stepSeconds),
            TotpDriftWindow.of(driftBackward, driftForward));
    String otp = TotpGenerator.generate(descriptor, Instant.ofEpochSecond(timestampEpochSeconds));
    return new InlinePreset(
        key,
        label,
        sharedSecretHex,
        algorithm,
        digits,
        stepSeconds,
        driftBackward,
        driftForward,
        timestampEpochSeconds,
        otp,
        metadata);
  }

  /** Canonical TOTP sample definition used for seeding and dropdown labels. */
  public record SampleDefinition(
      String credentialId,
      String optionLabel,
      String sharedSecretHex,
      TotpHashAlgorithm algorithm,
      int digits,
      int stepSeconds,
      int driftBackwardSteps,
      int driftForwardSteps,
      Map<String, String> metadata) {

    public SampleDefinition {
      Objects.requireNonNull(credentialId, "credentialId");
      Objects.requireNonNull(optionLabel, "optionLabel");
      Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
      Objects.requireNonNull(algorithm, "algorithm");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  /** Canonical inline preset definition for operator console dropdowns. */
  public record InlinePreset(
      String key,
      String label,
      String sharedSecretHex,
      TotpHashAlgorithm algorithm,
      int digits,
      int stepSeconds,
      int driftBackwardSteps,
      int driftForwardSteps,
      long timestamp,
      String otp,
      Map<String, String> metadata) {

    public InlinePreset {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
      Objects.requireNonNull(algorithm, "algorithm");
      Objects.requireNonNull(metadata, "metadata");
      metadata = Map.copyOf(metadata);
    }
  }
}
