package io.openauth.sim.rest.ui;

import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared sample data for HOTP operator console seeding and presets. */
public final class HotpOperatorSampleData {

  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");

  private static final List<SampleDefinition> DEFINITIONS =
      List.of(
          sample(
              "ui-hotp-demo",
              "ui-hotp-demo (SHA1, 6 digits)",
              "3132333435363738393031323334353637383930",
              HotpHashAlgorithm.SHA1,
              6,
              0L,
              metadata("ui-hotp-demo", "stored-demo", "Seeded HOTP SHA-1 demo credential.")),
          sample(
              "ui-hotp-demo-sha256",
              "ui-hotp-demo-sha256 (SHA256, 8 digits)",
              "3132333435363738393031323334353637383930",
              HotpHashAlgorithm.SHA256,
              8,
              5L,
              metadata(
                  "ui-hotp-demo-sha256",
                  "stored-demo-sha256",
                  "Seeded HOTP SHA-256 demo credential.")));

  private HotpOperatorSampleData() {
    // utility class
  }

  /** Returns the canonical HOTP credential definitions available for seeding. */
  public static List<SampleDefinition> seedDefinitions() {
    return DEFINITIONS;
  }

  private static SampleDefinition sample(
      String credentialId,
      String optionLabel,
      String sharedSecretHex,
      HotpHashAlgorithm algorithm,
      int digits,
      long counter,
      Map<String, String> metadata) {
    return new SampleDefinition(
        credentialId, optionLabel, sharedSecretHex, algorithm, digits, counter, metadata);
  }

  private static Map<String, String> metadata(String presetKey, String label, String notes) {
    return Map.of(
        "seedSource", BASE_METADATA.get("seedSource"),
        "presetKey", Objects.requireNonNull(presetKey, "presetKey"),
        "label", Objects.requireNonNull(label, "label"),
        "notes", Objects.requireNonNull(notes, "notes"));
  }

  /** Canonical HOTP sample definition used for seeding and dropdown labels. */
  public record SampleDefinition(
      String credentialId,
      String optionLabel,
      String sharedSecretHex,
      HotpHashAlgorithm algorithm,
      int digits,
      long counter,
      Map<String, String> metadata) {

    public SampleDefinition {
      Objects.requireNonNull(credentialId, "credentialId");
      Objects.requireNonNull(optionLabel, "optionLabel");
      Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
      Objects.requireNonNull(algorithm, "algorithm");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }
}
