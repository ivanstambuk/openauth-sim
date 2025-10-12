package io.openauth.sim.rest.ui;

import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared sample data for HOTP operator console seeding and presets. */
public final class HotpOperatorSampleData {

  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");
  private static final String SHA1_SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final String SHA512_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";

  private static final List<SampleDefinition> DEFINITIONS =
      List.of(
          sample(
              "ui-hotp-demo",
              "ui-hotp-demo (SHA1, 6 digits, RFC 4226)",
              SHA1_SECRET_HEX,
              HotpHashAlgorithm.SHA1,
              6,
              0L,
              metadata("ui-hotp-demo", "stored-demo", "Seeded HOTP SHA-1 demo credential.")),
          sample(
              "ui-hotp-demo-sha256",
              "ui-hotp-demo-sha256 (SHA256, 8 digits)",
              SHA1_SECRET_HEX,
              HotpHashAlgorithm.SHA256,
              8,
              5L,
              metadata(
                  "ui-hotp-demo-sha256",
                  "stored-demo-sha256",
                  "Seeded HOTP SHA-256 demo credential.")),
          sample(
              "ui-hotp-demo-sha1-8",
              "ui-hotp-demo-sha1-8 (SHA1, 8 digits)",
              SHA1_SECRET_HEX,
              HotpHashAlgorithm.SHA1,
              8,
              5L,
              metadata(
                  "ui-hotp-demo-sha1-8",
                  "stored-demo-sha1-8",
                  "Seeded HOTP SHA-1 demo credential (8 digits).")),
          sample(
              "ui-hotp-demo-sha256-6",
              "ui-hotp-demo-sha256-6 (SHA256, 6 digits)",
              SHA1_SECRET_HEX,
              HotpHashAlgorithm.SHA256,
              6,
              5L,
              metadata(
                  "ui-hotp-demo-sha256-6",
                  "stored-demo-sha256-6",
                  "Seeded HOTP SHA-256 demo credential (6 digits).")),
          sample(
              "ui-hotp-demo-sha512",
              "ui-hotp-demo-sha512 (SHA512, 8 digits)",
              SHA512_SECRET_HEX,
              HotpHashAlgorithm.SHA512,
              8,
              5L,
              metadata(
                  "ui-hotp-demo-sha512",
                  "stored-demo-sha512",
                  "Seeded HOTP SHA-512 demo credential.")),
          sample(
              "ui-hotp-demo-sha512-6",
              "ui-hotp-demo-sha512-6 (SHA512, 6 digits)",
              SHA512_SECRET_HEX,
              HotpHashAlgorithm.SHA512,
              6,
              5L,
              metadata(
                  "ui-hotp-demo-sha512-6",
                  "stored-demo-sha512-6",
                  "Seeded HOTP SHA-512 demo credential (6 digits).")));

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
