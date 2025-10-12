package io.openauth.sim.rest.ui;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Shared sample data for inline presets and credential seeding. */
public final class OcraOperatorSampleData {

  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";

  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");
  private static final Map<String, String> ALIASES = Map.of("operator-demo", "qa08-s064");

  private static final List<SampleDefinition> DEFINITIONS =
      List.of(
          seedDefinition(
              "qa08-s064",
              "QA08 S064 - session 64",
              "sample-qa08-s064",
              "OCRA-1:HOTP-SHA256-8:QA08-S064",
              SHARED_SECRET_HEX,
              "SESSION01",
              "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
                  + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
              null,
              null,
              null,
              null,
              null,
              "qa08-s064",
              null,
              "17477202"),
          seedDefinition(
              "qa08-s128",
              "QA08 S128 - session 128",
              "sample-qa08-s128",
              "OCRA-1:HOTP-SHA256-8:QA08-S128",
              SHARED_SECRET_HEX,
              "SESSION01",
              "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
                  + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                  + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
                  + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
              null,
              null,
              null,
              null,
              null,
              "qa08-s128",
              null,
              "18468077"),
          seedDefinition(
              "qa08-s256",
              "QA08 S256 - session 256",
              "sample-qa08-s256",
              "OCRA-1:HOTP-SHA256-8:QA08-S256",
              SHARED_SECRET_HEX,
              "SESSION01",
              repeatHex(
                  "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
                      + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
                  4),
              null,
              null,
              null,
              null,
              null,
              "qa08-s256",
              null,
              "77715695"),
          seedDefinition(
              "qa08-s512",
              "QA08 S512 - session 512",
              "sample-qa08-s512",
              "OCRA-1:HOTP-SHA256-8:QA08-S512",
              SHARED_SECRET_HEX,
              "SESSION01",
              repeatHex(
                  "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
                      + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
                  8),
              null,
              null,
              null,
              null,
              null,
              "qa08-s512",
              null,
              "05806151"),
          seedDefinition(
              "c-qh64",
              "C-QH64 - HOTP-SHA256, 6 digits",
              "sample-c-qh64",
              "OCRA-1:HOTP-SHA256-6:C-QH64",
              SHARED_SECRET_HEX,
              "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF",
              null,
              null,
              null,
              null,
              null,
              Long.valueOf(1),
              "c-qh64",
              null,
              "429968"));

  private OcraOperatorSampleData() {
    // utility class
  }

  static List<OcraOperatorUiController.PolicyPreset> policyPresets() {
    return DEFINITIONS.stream()
        .map(
            definition ->
                new OcraOperatorUiController.PolicyPreset(
                    definition.key(),
                    definition.label(),
                    new OcraOperatorUiController.InlineSample(
                        definition.suite(),
                        definition.sharedSecretHex(),
                        definition.challenge(),
                        definition.sessionHex(),
                        definition.clientChallenge(),
                        definition.serverChallenge(),
                        definition.pinHashHex(),
                        definition.timestampHex(),
                        definition.counter(),
                        definition.expectedOtp())))
        .collect(Collectors.toUnmodifiableList());
  }

  public static List<SampleDefinition> seedDefinitions() {
    return DEFINITIONS;
  }

  public static Optional<SampleDefinition> findByCredentialName(String credentialName) {
    if (credentialName == null || credentialName.isBlank()) {
      return Optional.empty();
    }
    String normalized = credentialName.trim();
    for (SampleDefinition definition : DEFINITIONS) {
      if (definition.credentialName().equalsIgnoreCase(normalized)) {
        return Optional.of(definition);
      }
    }
    String aliasKey = ALIASES.get(normalized.toLowerCase(Locale.ROOT));
    if (aliasKey != null) {
      return findByPresetKey(aliasKey);
    }
    return Optional.empty();
  }

  public static Optional<SampleDefinition> findByPresetKey(String presetKey) {
    if (presetKey == null || presetKey.isBlank()) {
      return Optional.empty();
    }
    String normalized = presetKey.trim();
    for (SampleDefinition definition : DEFINITIONS) {
      String value = definition.metadata().get("presetKey");
      if (value != null && value.equalsIgnoreCase(normalized)) {
        return Optional.of(definition);
      }
    }
    return Optional.empty();
  }

  public static Optional<SampleDefinition> findBySuite(String suite) {
    if (suite == null || suite.isBlank()) {
      return Optional.empty();
    }
    String normalized = suite.trim();
    for (SampleDefinition definition : DEFINITIONS) {
      if (definition.suite().equalsIgnoreCase(normalized)) {
        return Optional.of(definition);
      }
    }
    return Optional.empty();
  }

  private static SampleDefinition seedDefinition(
      String key,
      String label,
      String credentialName,
      String suite,
      String sharedSecretHex,
      String challenge,
      String sessionHex,
      String clientChallenge,
      String serverChallenge,
      String pinHashHex,
      String timestampHex,
      Long counter,
      String presetKey,
      Duration allowedTimestampDrift,
      String expectedOtp) {
    return new SampleDefinition(
        key,
        label,
        credentialName,
        suite,
        sharedSecretHex,
        challenge,
        sessionHex,
        clientChallenge,
        serverChallenge,
        pinHashHex,
        timestampHex,
        counter,
        metadata(presetKey),
        allowedTimestampDrift,
        expectedOtp);
  }

  private static Map<String, String> metadata(String presetKey) {
    return Map.of(
        "seedSource", BASE_METADATA.get("seedSource"),
        "presetKey", Objects.requireNonNull(presetKey, "presetKey"));
  }

  private static String repeatHex(String chunk, int times) {
    Objects.requireNonNull(chunk, "chunk");
    if (times <= 0) {
      return "";
    }
    return chunk.repeat(times);
  }

  public record SampleDefinition(
      String key,
      String label,
      String credentialName,
      String suite,
      String sharedSecretHex,
      String challenge,
      String sessionHex,
      String clientChallenge,
      String serverChallenge,
      String pinHashHex,
      String timestampHex,
      Long counter,
      Map<String, String> metadata,
      Duration allowedTimestampDrift,
      String expectedOtp) {

    public SampleDefinition {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(credentialName, "credentialName");
      Objects.requireNonNull(suite, "suite");
      Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }
}
