package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretMaterial;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Factory for normalising raw inputs into {@link OcraCredentialDescriptor} instances. */
public final class OcraCredentialDescriptorFactory {

  public OcraCredentialDescriptor create(
      String name,
      String ocraSuite,
      String sharedSecretHex,
      Long counterValue,
      String pinHashHex,
      Duration allowedTimestampDrift,
      Map<String, String> metadata) {

    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (ocraSuite == null || ocraSuite.isBlank()) {
      throw new IllegalArgumentException("ocraSuite must not be blank");
    }
    if (sharedSecretHex == null || sharedSecretHex.isBlank()) {
      throw new IllegalArgumentException("sharedSecretKey must not be blank");
    }

    OcraSuite suite = OcraSuiteParser.parse(ocraSuite);
    SecretMaterial sharedSecret = SecretMaterial.fromHex(normaliseHex(sharedSecretHex));

    Optional<Long> counter = Optional.ofNullable(counterValue);
    validateCounter(counter, suite);

    Optional<SecretMaterial> pinHash = normalisePinHash(pinHashHex, suite);

    Optional<Duration> drift = Optional.ofNullable(allowedTimestampDrift);
    drift.ifPresent(OcraCredentialDescriptorFactory::validateDriftWindow);

    return new OcraCredentialDescriptor(
        name.trim(), suite, sharedSecret, counter, pinHash, drift, metadataOrEmpty(metadata));
  }

  private static Map<String, String> metadataOrEmpty(Map<String, String> metadata) {
    return metadata == null ? Map.of() : metadata;
  }

  private static void validateCounter(Optional<Long> counter, OcraSuite suite) {
    if (suite.dataInput().counter()) {
      if (counter.isEmpty()) {
        throw new IllegalArgumentException("counterValue required for suite: " + suite.value());
      }
      if (counter.orElseThrow() < 0) {
        throw new IllegalArgumentException("counterValue must be >= 0");
      }
    } else if (counter.isPresent()) {
      throw new IllegalArgumentException("counterValue not permitted for suite: " + suite.value());
    }
  }

  private static Optional<SecretMaterial> normalisePinHash(String pinHashHex, OcraSuite suite) {
    boolean pinRequired = suite.dataInput().pin().isPresent();
    if (pinHashHex == null || pinHashHex.isBlank()) {
      if (pinRequired) {
        throw new IllegalArgumentException("pinHash required for suite: " + suite.value());
      }
      return Optional.empty();
    }

    if (!pinRequired) {
      throw new IllegalArgumentException("pinHash not permitted for suite: " + suite.value());
    }

    String normalised = normaliseHex(pinHashHex);
    OcraHashAlgorithm hashAlgorithm = suite.dataInput().pin().orElseThrow().hashAlgorithm();
    int expectedHexLength = hashAlgorithm.hexLength();
    if (normalised.length() != expectedHexLength) {
      throw new IllegalArgumentException(
          "pinHash must use "
              + hashAlgorithm.token()
              + " and contain "
              + expectedHexLength
              + " hex characters");
    }

    return Optional.of(SecretMaterial.fromHex(normalised));
  }

  private static void validateDriftWindow(Duration drift) {
    if (drift.isNegative() || drift.isZero()) {
      throw new IllegalArgumentException("allowedTimestampDrift must be positive");
    }
  }

  private static String normaliseHex(String value) {
    String trimmed = value.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      trimmed = trimmed.substring(2);
    }
    String normalised = trimmed.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    if (normalised.isEmpty()) {
      throw new IllegalArgumentException("Hex value must not be empty");
    }
    if ((normalised.length() & 1) != 0) {
      throw new IllegalArgumentException("Hex value must contain an even number of characters");
    }
    if (!normalised.chars().allMatch(OcraCredentialDescriptorFactory::isHexCharacter)) {
      throw new IllegalArgumentException("Value must be hexadecimal");
    }
    return normalised;
  }

  private static boolean isHexCharacter(int ch) {
    return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
  }
}
