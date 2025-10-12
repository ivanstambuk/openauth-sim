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

  private static final String STANDARD_KEY_20 = "3132333435363738393031323334353637383930";
  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String STANDARD_KEY_64 =
      "3132333435363738393031323334353637383930"
          + "3132333435363738393031323334353637383930"
          + "3132333435363738393031323334353637383930"
          + "31323334";
  private static final String PIN_SHA1_HASH = "7110eda4d09e062aa5e4a390b0a572ac0d2c0220";

  private static final Map<String, String> BASE_METADATA = Map.of("seedSource", "operator-ui");
  private static final Map<String, String> ALIASES = Map.of("operator-demo", "qa08-s064");

  private static final List<SampleDefinition> DEFINITIONS =
      List.of(
          seedDefinition(
              "qn08-sha1",
              "QN08 numeric - HOTP-SHA1, 6 digits (RFC 6287)",
              "sample-qn08-sha1",
              "OCRA-1:HOTP-SHA1-6:QN08",
              STANDARD_KEY_20,
              "11111111",
              null,
              null,
              null,
              null,
              null,
              null,
              "qn08-sha1",
              null,
              "243178"),
          seedDefinition(
              "c-qn08-psha1",
              "C-QN08 PIN - HOTP-SHA256, 8 digits (RFC 6287)",
              "sample-c-qn08-psha1",
              "OCRA-1:HOTP-SHA256-8:C-QN08-PSHA1",
              SHARED_SECRET_HEX,
              "12345678",
              null,
              null,
              null,
              PIN_SHA1_HASH,
              null,
              0L,
              "c-qn08-psha1",
              null,
              "65347737"),
          seedDefinition(
              "qn08-psha1",
              "QN08 PIN - HOTP-SHA256, 8 digits (RFC 6287)",
              "sample-qn08-psha1",
              "OCRA-1:HOTP-SHA256-8:QN08-PSHA1",
              SHARED_SECRET_HEX,
              "00000000",
              null,
              null,
              null,
              PIN_SHA1_HASH,
              null,
              null,
              "qn08-psha1",
              null,
              "83238735"),
          seedDefinition(
              "c-qn08-sha512",
              "C-QN08 - HOTP-SHA512, 8 digits (RFC 6287)",
              "sample-c-qn08-sha512",
              "OCRA-1:HOTP-SHA512-8:C-QN08",
              STANDARD_KEY_64,
              "00000000",
              null,
              null,
              null,
              null,
              null,
              0L,
              "c-qn08-sha512",
              null,
              "07016083"),
          seedDefinition(
              "qn08-t1m",
              "QN08 T1M - HOTP-SHA512, 8 digits (RFC 6287)",
              "sample-qn08-t1m",
              "OCRA-1:HOTP-SHA512-8:QN08-T1M",
              STANDARD_KEY_64,
              "00000000",
              null,
              null,
              null,
              null,
              "0132D0B6",
              null,
              "qn08-t1m",
              Duration.ofMinutes(1),
              "95209754"),
          seedDefinition(
              "qa08-s064",
              "QA08 S064 - session 64 (RFC 6287)",
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
              "QA08 S128 - session 128 (RFC 6287)",
              "sample-qa08-s128",
              "OCRA-1:HOTP-SHA256-8:QA08-S128",
              SHARED_SECRET_HEX,
              "SESSION01",
              repeatHex(
                  "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
                      + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
                  2),
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
              "QA08 S256 - session 256 (RFC 6287)",
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
              "QA08 S512 - session 512 (RFC 6287)",
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
              "qa08-mutual-sha256",
              "QA08 mutual - HOTP-SHA256, 8 digits (RFC 6287)",
              "sample-qa08-mutual-sha256",
              "OCRA-1:HOTP-SHA256-8:QA08",
              SHARED_SECRET_HEX,
              "CLI22220SRV11110",
              null,
              "CLI22220",
              "SRV11110",
              null,
              null,
              null,
              "qa08-mutual-sha256",
              null,
              "28247970"),
          seedDefinition(
              "qa08-mutual-sha512",
              "QA08 mutual SHA512 - HOTP-SHA512, 8 digits (RFC 6287)",
              "sample-qa08-mutual-sha512",
              "OCRA-1:HOTP-SHA512-8:QA08",
              STANDARD_KEY_64,
              "CLI22220SRV11110",
              null,
              "CLI22220",
              "SRV11110",
              null,
              null,
              null,
              "qa08-mutual-sha512",
              null,
              "79496648"),
          seedDefinition(
              "qa08-pin-sha512",
              "QA08 PIN SHA512 - HOTP-SHA512, 8 digits (RFC 6287)",
              "sample-qa08-pin-sha512",
              "OCRA-1:HOTP-SHA512-8:QA08-PSHA1",
              STANDARD_KEY_64,
              "SRV11110CLI22220",
              null,
              "CLI22220",
              "SRV11110",
              PIN_SHA1_HASH,
              null,
              null,
              "qa08-pin-sha512",
              null,
              "18806276"),
          seedDefinition(
              "qa10-t1m",
              "QA10 T1M signature - HOTP-SHA512, 8 digits (RFC 6287)",
              "sample-qa10-t1m",
              "OCRA-1:HOTP-SHA512-8:QA10-T1M",
              STANDARD_KEY_64,
              "SIG1000000",
              null,
              null,
              null,
              null,
              "0132D0B6",
              null,
              "qa10-t1m",
              Duration.ofMinutes(1),
              "77537423"),
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
