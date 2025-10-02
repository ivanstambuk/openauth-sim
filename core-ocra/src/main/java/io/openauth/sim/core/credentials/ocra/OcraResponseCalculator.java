package io.openauth.sim.core.credentials.ocra;

import io.openauth.sim.core.model.SecretMaterial;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Execution helper that evaluates an OCRA response using descriptor metadata and runtime inputs.
 */
public final class OcraResponseCalculator {

  private static final HexFormat HEX = HexFormat.of();

  private OcraResponseCalculator() {
    // Utility class
  }

  /**
   * Generates an OCRA response for the supplied descriptor and execution context.
   *
   * @throws IllegalArgumentException when required runtime inputs are missing or malformed
   * @throws IllegalStateException when the crypto engine is unavailable
   */
  public static String generate(OcraCredentialDescriptor descriptor, OcraExecutionContext context) {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(context, "context");

    OcraSuite suite = descriptor.suite();
    OcraDataInput dataInput = suite.dataInput();

    String keyHex = HEX.formatHex(descriptor.sharedSecret().value()).toUpperCase(Locale.ROOT);

    String counterHex = "";
    if (dataInput.counter()) {
      if (context.counter() == null) {
        throw new IllegalArgumentException("counter value required for suite: " + suite.value());
      }
      if (context.counter() < 0) {
        throw new IllegalArgumentException("counter value must be non-negative");
      }
      counterHex = Long.toHexString(context.counter()).toUpperCase(Locale.ROOT);
    }

    String questionHex = "";
    if (dataInput.challengeQuestion().isPresent()) {
      questionHex = formatQuestion(dataInput.challengeQuestion().orElseThrow(), context);
    }

    String passwordHex = "";
    if (dataInput.pin().isPresent()) {
      SecretMaterial material =
          descriptor
              .pinHash()
              .orElseGet(
                  () -> {
                    String runtimePin = context.pinHashHex();
                    if (runtimePin == null || runtimePin.isBlank()) {
                      throw new IllegalArgumentException(
                          "pin hash required for suite: " + suite.value());
                    }
                    return SecretMaterial.fromHex(normalizeHex(runtimePin));
                  });
      passwordHex = HEX.formatHex(material.value()).toUpperCase(Locale.ROOT);
    }

    String sessionHex = "";
    if (dataInput.sessionInformation().isPresent()) {
      sessionHex =
          encodeSession(
              dataInput.sessionInformation().orElseThrow(),
              context.sessionInformation(),
              suite.value());
    }

    String timeHex = "";
    if (dataInput.timestamp().isPresent()) {
      if (context.timestampHex() == null || context.timestampHex().isBlank()) {
        throw new IllegalArgumentException("timestamp value required for suite: " + suite.value());
      }
      timeHex = normalizeHex(context.timestampHex());
    }

    return computeReferenceOcra(
        suite.value(), keyHex, counterHex, questionHex, passwordHex, sessionHex, timeHex);
  }

  private static String formatQuestion(
      OcraChallengeQuestion challenge, OcraExecutionContext context) {
    String value = resolveChallengeInput(context);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("challenge question required for suite: " + challenge);
    }

    return switch (challenge.format()) {
      case NUMERIC -> {
        if (!value.chars().allMatch(Character::isDigit)) {
          throw new IllegalArgumentException("numeric challenge must contain digits only");
        }
        yield new BigInteger(value, 10).toString(16).toUpperCase(Locale.ROOT);
      }
      case HEX -> {
        if (!value.matches("[0-9a-fA-F]+")) {
          throw new IllegalArgumentException(
              "hex challenge must contain hexadecimal characters only");
        }
        yield value.toUpperCase(Locale.ROOT);
      }
      case ALPHANUMERIC, CHARACTER ->
          HEX.formatHex(value.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))
              .toUpperCase(Locale.ROOT);
    };
  }

  private static String encodeSession(
      OcraSessionSpecification specification, String sessionInformation, String suite) {
    if (sessionInformation == null || sessionInformation.isBlank()) {
      throw new IllegalArgumentException("session information required for suite: " + suite);
    }
    String normalized = normalizeHex(sessionInformation);
    int targetLength = specification.lengthBytes() * 2;
    if (normalized.length() > targetLength) {
      throw new IllegalArgumentException(
          "session information exceeds declared length of "
              + specification.lengthBytes()
              + " bytes");
    }
    StringBuilder builder = new StringBuilder(normalized);
    while (builder.length() < targetLength) {
      builder.insert(0, '0');
    }
    return builder.toString();
  }

  private static String resolveChallengeInput(OcraExecutionContext context) {
    if (context.question() != null && !context.question().isBlank()) {
      return context.question().trim();
    }
    if (context.clientChallenge() == null && context.serverChallenge() == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    if (context.clientChallenge() != null) {
      builder.append(context.clientChallenge());
    }
    if (context.serverChallenge() != null) {
      builder.append(context.serverChallenge());
    }
    return builder.toString();
  }

  private static String computeReferenceOcra(
      String ocraSuite,
      String key,
      String counter,
      String question,
      String password,
      String sessionInformation,
      String timeStamp) {

    String crypto;
    String[] parts = ocraSuite.split(":");
    String cryptoFunction = parts[1];
    String dataInput = parts[2];

    if (cryptoFunction.toLowerCase(Locale.ROOT).contains("sha256")) {
      crypto = "HmacSHA256";
    } else if (cryptoFunction.toLowerCase(Locale.ROOT).contains("sha512")) {
      crypto = "HmacSHA512";
    } else {
      crypto = "HmacSHA1";
    }

    int codeDigits = Integer.decode(cryptoFunction.substring(cryptoFunction.lastIndexOf('-') + 1));

    int ocraSuiteLength = ocraSuite.getBytes(StandardCharsets.US_ASCII).length;
    int counterLength = 0;
    int questionLength = 0;
    int passwordLength = 0;
    int sessionLength = 0;
    int timeLength = 0;

    if (dataInput.toLowerCase(Locale.ROOT).startsWith("c")) {
      while (counter.length() < 16) {
        counter = "0" + counter;
      }
      counterLength = 8;
    }

    if (dataInput.toLowerCase(Locale.ROOT).startsWith("q")
        || dataInput.toLowerCase(Locale.ROOT).contains("-q")) {
      while (question.length() < 256) {
        question = question + "0";
      }
      questionLength = 128;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("psha1")) {
      while (password.length() < 40) {
        password = "0" + password;
      }
      passwordLength = 20;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("psha256")) {
      while (password.length() < 64) {
        password = "0" + password;
      }
      passwordLength = 32;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("psha512")) {
      while (password.length() < 128) {
        password = "0" + password;
      }
      passwordLength = 64;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("s064")) {
      while (sessionInformation.length() < 128) {
        sessionInformation = "0" + sessionInformation;
      }
      sessionLength = 64;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("s128")) {
      while (sessionInformation.length() < 256) {
        sessionInformation = "0" + sessionInformation;
      }
      sessionLength = 128;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("s256")) {
      while (sessionInformation.length() < 512) {
        sessionInformation = "0" + sessionInformation;
      }
      sessionLength = 256;
    }

    if (dataInput.toLowerCase(Locale.ROOT).contains("s512")) {
      while (sessionInformation.length() < 1024) {
        sessionInformation = "0" + sessionInformation;
      }
      sessionLength = 512;
    }

    if (dataInput.toLowerCase(Locale.ROOT).startsWith("t")
        || dataInput.toLowerCase(Locale.ROOT).contains("-t")) {
      while (timeStamp.length() < 16) {
        timeStamp = "0" + timeStamp;
      }
      timeLength = 8;
    }

    byte[] msg =
        new byte
            [ocraSuiteLength
                + counterLength
                + questionLength
                + passwordLength
                + sessionLength
                + timeLength
                + 1];

    byte[] suiteBytes = ocraSuite.getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(suiteBytes, 0, msg, 0, suiteBytes.length);
    msg[suiteBytes.length] = 0x00;

    int offset = suiteBytes.length + 1;

    if (counterLength > 0) {
      byte[] counterBytes = hexStr2Bytes(counter);
      System.arraycopy(counterBytes, 0, msg, offset, counterBytes.length);
      offset += counterBytes.length;
    }

    if (questionLength > 0) {
      byte[] questionBytes = hexStr2Bytes(question);
      System.arraycopy(questionBytes, 0, msg, offset, questionBytes.length);
      offset += questionBytes.length;
    }

    if (passwordLength > 0) {
      byte[] passwordBytes = hexStr2Bytes(password);
      System.arraycopy(passwordBytes, 0, msg, offset, passwordBytes.length);
      offset += passwordBytes.length;
    }

    if (sessionLength > 0) {
      byte[] sessionBytes = hexStr2Bytes(sessionInformation);
      System.arraycopy(sessionBytes, 0, msg, offset, sessionBytes.length);
      offset += sessionBytes.length;
    }

    if (timeLength > 0) {
      byte[] timeBytes = hexStr2Bytes(timeStamp);
      System.arraycopy(timeBytes, 0, msg, offset, timeBytes.length);
    }

    byte[] hash = hmac(crypto, hexStr2Bytes(key), msg);
    return truncate(hash, codeDigits);
  }

  private static byte[] hmac(String algorithm, byte[] keyBytes, byte[] message) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(keyBytes, "RAW"));
      return mac.doFinal(message);
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("Unable to initialize OCRA HMAC engine", ex);
    }
  }

  private static byte[] hexStr2Bytes(String hex) {
    byte[] withSign = new BigInteger("10" + hex, 16).toByteArray();
    byte[] result = new byte[withSign.length - 1];
    System.arraycopy(withSign, 1, result, 0, result.length);
    return result;
  }

  private static String normalizeHex(String value) {
    String trimmed = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    if ((trimmed.length() & 1) == 1) {
      trimmed = '0' + trimmed;
    }
    return trimmed;
  }

  private static String truncate(byte[] hmac, int digits) {
    int offset = hmac[hmac.length - 1] & 0x0F;
    int binary =
        ((hmac[offset] & 0x7F) << 24)
            | ((hmac[offset + 1] & 0xFF) << 16)
            | ((hmac[offset + 2] & 0xFF) << 8)
            | (hmac[offset + 3] & 0xFF);

    long modulus = 1L;
    for (int i = 0; i < digits; i++) {
      modulus *= 10L;
    }
    long otp = (binary & 0xFFFFFFFFL) % modulus;
    return String.format(Locale.ROOT, "%0" + digits + "d", otp);
  }

  /** Execution context capturing runtime inputs. */
  public record OcraExecutionContext(
      Long counter,
      String question,
      String sessionInformation,
      String clientChallenge,
      String serverChallenge,
      String pinHashHex,
      String timestampHex) {

    public OcraExecutionContext {
      // values validated lazily during execution
    }
  }
}
