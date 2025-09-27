package io.openauth.sim.core.credentials.ocra;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

final class OcraSuiteParser {

  private static final String PREFIX = "OCRA-1";

  private OcraSuiteParser() {
    // Utility class
  }

  static OcraSuite parse(String suite) {
    if (suite == null || suite.isBlank()) {
      throw new IllegalArgumentException("OCRA suite must not be blank");
    }

    String trimmed = suite.trim();
    String[] parts = trimmed.split(":");
    if (parts.length != 3 || !PREFIX.equalsIgnoreCase(parts[0])) {
      throw new IllegalArgumentException("Unsupported OCRA suite prefix: " + suite);
    }

    OcraCryptoFunction cryptoFunction = parseCryptoFunction(parts[1]);
    OcraDataInput dataInput = parseDataInput(parts[2], cryptoFunction);

    return new OcraSuite(trimmed, cryptoFunction, dataInput);
  }

  private static OcraCryptoFunction parseCryptoFunction(String rawCrypto) {
    if (rawCrypto == null || rawCrypto.isBlank()) {
      throw new IllegalArgumentException("Crypto function segment must not be blank");
    }

    String cursor = rawCrypto.trim().toUpperCase(Locale.ROOT);
    if (!cursor.startsWith("HOTP")) {
      throw new IllegalArgumentException(
          "Only HOTP-derived OCRA suites are supported: " + rawCrypto);
    }

    cursor = cursor.substring(4); // drop HOTP
    if (cursor.startsWith("-")) {
      cursor = cursor.substring(1);
    }

    Optional<Duration> timeStep = Optional.empty();
    if (cursor.startsWith("T")) {
      int idx = 1;
      while (idx < cursor.length() && Character.isDigit(cursor.charAt(idx))) {
        idx++;
      }
      if (idx == 1) {
        throw new IllegalArgumentException("Time step indicator requires digits: " + rawCrypto);
      }
      int seconds = Integer.parseInt(cursor.substring(1, idx));
      timeStep = Optional.of(Duration.ofSeconds(seconds));
      cursor = cursor.substring(idx);
      if (cursor.startsWith("-")) {
        cursor = cursor.substring(1);
      }
    }

    if (!cursor.startsWith("SHA")) {
      throw new IllegalArgumentException("Missing SHA algorithm token: " + rawCrypto);
    }

    int hashEnd = 3;
    while (hashEnd < cursor.length() && Character.isDigit(cursor.charAt(hashEnd))) {
      hashEnd++;
    }
    if (hashEnd == 3) {
      throw new IllegalArgumentException("Hash algorithm digits missing: " + rawCrypto);
    }

    OcraHashAlgorithm hashAlgorithm = OcraHashAlgorithm.fromToken(cursor.substring(0, hashEnd));

    if (cursor.charAt(hashEnd) == '-') {
      hashEnd++;
    }
    String digitsToken = cursor.substring(hashEnd);
    if (digitsToken.isBlank() || !digitsToken.chars().allMatch(Character::isDigit)) {
      throw new IllegalArgumentException("Response length must be numeric: " + rawCrypto);
    }
    int responseDigits = Integer.parseInt(digitsToken);

    return new OcraCryptoFunction(hashAlgorithm, responseDigits, timeStep);
  }

  private static OcraDataInput parseDataInput(
      String rawDataInput, OcraCryptoFunction cryptoFunction) {
    String[] tokens = rawDataInput.split("-");
    boolean counter = false;
    OcraChallengeQuestion challengeQuestion = null;
    OcraPinSpecification pinSpecification = null;
    OcraSessionSpecification sessionSpecification = null;
    OcraTimestampSpecification timestampSpecification = null;

    for (String token : tokens) {
      String trimmed = token.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      if ("C".equalsIgnoreCase(trimmed)) {
        counter = true;
        continue;
      }

      if (trimmed.startsWith("Q") && trimmed.length() >= 3) {
        char formatToken = trimmed.charAt(1);
        OcraChallengeFormat format = OcraChallengeFormat.fromToken(formatToken);
        String lengthToken = trimmed.substring(2);
        if (!lengthToken.chars().allMatch(Character::isDigit)) {
          throw new IllegalArgumentException("Challenge length must be numeric: " + trimmed);
        }
        int length = Integer.parseInt(lengthToken);
        challengeQuestion = new OcraChallengeQuestion(format, length);
        continue;
      }

      if (trimmed.startsWith("P")) {
        if (pinSpecification != null) {
          throw new IllegalArgumentException(
              "Multiple PIN declarations not supported: " + rawDataInput);
        }
        String algorithmToken = trimmed.substring(1);
        pinSpecification = new OcraPinSpecification(OcraHashAlgorithm.fromToken(algorithmToken));
        continue;
      }

      if (trimmed.startsWith("S")) {
        String algorithmToken = trimmed.substring(1);
        if (algorithmToken.isEmpty()) {
          sessionSpecification = new OcraSessionSpecification(OcraHashAlgorithm.SHA1);
        } else {
          sessionSpecification =
              new OcraSessionSpecification(OcraHashAlgorithm.fromToken(algorithmToken));
        }
        continue;
      }

      if (trimmed.startsWith("T")) {
        int idx = 1;
        while (idx < trimmed.length() && Character.isDigit(trimmed.charAt(idx))) {
          idx++;
        }
        if (idx == 1) {
          throw new IllegalArgumentException("Timestamp token requires step digits: " + trimmed);
        }
        int stepSeconds = Integer.parseInt(trimmed.substring(1, idx));
        timestampSpecification = new OcraTimestampSpecification(Duration.ofSeconds(stepSeconds));
        continue;
      }

      throw new IllegalArgumentException("Unsupported OCRA data input token: " + trimmed);
    }

    if (timestampSpecification == null && cryptoFunction.timeStep().isPresent()) {
      timestampSpecification =
          new OcraTimestampSpecification(cryptoFunction.timeStep().orElseThrow());
    }

    return new OcraDataInput(
        counter,
        Optional.ofNullable(challengeQuestion),
        Optional.ofNullable(pinSpecification),
        Optional.ofNullable(sessionSpecification),
        Optional.ofNullable(timestampSpecification));
  }
}
