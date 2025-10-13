package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class WebAuthnPublicKeyFormats {

  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final Base64.Decoder MIME_DECODER = Base64.getMimeDecoder();

  private static final byte[] ED25519_PUBLIC_KEY_PREFIX = hexToBytes("302a300506032b6570032100");

  private WebAuthnPublicKeyFormats() {
    throw new AssertionError("Utility class");
  }

  static byte[] decodeBase64Url(String value) {
    try {
      String normalized = value.replaceAll("\\s+", "");
      return URL_DECODER.decode(normalized);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("public key must be Base64URL encoded", ex);
    }
  }

  static byte[] decodeJwk(String jwkJson, WebAuthnSignatureAlgorithm algorithm) {
    Objects.requireNonNull(jwkJson, "jwkJson");
    Map<String, Object> jwk = parseJsonObject(jwkJson);
    String kty = text(jwk, "kty");
    try {
      return switch (algorithm) {
        case ES256, ES384, ES512 -> decodeEcJwk(kty, jwk, algorithm);
        case RS256, PS256 -> decodeRsaJwk(kty, jwk, algorithm);
        case EDDSA -> decodeOkpJwk(kty, jwk, algorithm);
      };
    } catch (GeneralSecurityException ex) {
      throw new IllegalArgumentException("Unable to decode JWK public key: " + ex.getMessage(), ex);
    }
  }

  static byte[] decodePem(String pem, WebAuthnSignatureAlgorithm algorithm) {
    Objects.requireNonNull(pem, "pem");
    String trimmed = pem.trim();
    if (!trimmed.startsWith("-----BEGIN") || !trimmed.contains("-----END")) {
      throw new IllegalArgumentException("Unsupported PEM header");
    }

    String headerLine = trimmed.lines().findFirst().orElse("");
    String footer = "-----END" + headerLine.substring(headerLine.indexOf(' '));

    if (!trimmed.endsWith(footer)) {
      throw new IllegalArgumentException("PEM footer does not match header");
    }

    String base64 = trimmed.replace(headerLine, "").replace(footer, "").replaceAll("\\s", "");
    byte[] der;
    try {
      der = MIME_DECODER.decode(base64);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid PEM payload", ex);
    }

    try {
      KeyFactory factory = keyFactoryFor(algorithm);
      PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(der));
      if (algorithm == WebAuthnSignatureAlgorithm.EDDSA) {
        byte[] keyBytes = extractEd25519Key(publicKey.getEncoded());
        return encodeOkpPublicKey(keyBytes, algorithm);
      }
      return encodePublicKey(publicKey, algorithm);
    } catch (GeneralSecurityException ex) {
      throw new IllegalArgumentException("Unable to decode PEM public key: " + ex.getMessage(), ex);
    }
  }

  static byte[] encodePublicKey(PublicKey publicKey, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (publicKey instanceof ECPublicKey ecKey) {
      return encodeEcPublicKey(ecKey, algorithm);
    }
    if (publicKey instanceof RSAPublicKey rsaKey) {
      return encodeRsaPublicKey(rsaKey, algorithm);
    }
    throw new GeneralSecurityException(
        "Unsupported public key algorithm: " + publicKey.getAlgorithm());
  }

  static byte[] encodeEcPublicKey(ECPublicKey publicKey, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    int coseCurve =
        switch (algorithm) {
          case ES256 -> 1;
          case ES384 -> 2;
          case ES512 -> 3;
          default -> throw new GeneralSecurityException("Unsupported EC algorithm " + algorithm);
        };

    byte[] x = toUnsigned(publicKey.getW().getAffineX(), coordinateLength(algorithm));
    byte[] y = toUnsigned(publicKey.getW().getAffineY(), coordinateLength(algorithm));

    CborWriter writer = new CborWriter();
    writer.startMap(5);
    writer.writeBytes(-3, y);
    writer.writeBytes(-2, x);
    writer.writeInt(-1, coseCurve);
    writer.writeInt(1, 2); // kty: EC2
    writer.writeInt(3, algorithm.coseIdentifier());
    return writer.toByteArray();
  }

  static byte[] encodeRsaPublicKey(RSAPublicKey publicKey, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    CborWriter writer = new CborWriter();
    writer.startMap(4);
    writer.writeBytes(
        -2,
        toUnsigned(publicKey.getPublicExponent(), unsignedLength(publicKey.getPublicExponent())));
    writer.writeBytes(
        -1, toUnsigned(publicKey.getModulus(), unsignedLength(publicKey.getModulus())));
    writer.writeInt(1, 3); // kty: RSA
    int coseAlg =
        algorithm == WebAuthnSignatureAlgorithm.PS256
            ? WebAuthnSignatureAlgorithm.PS256.coseIdentifier()
            : WebAuthnSignatureAlgorithm.RS256.coseIdentifier();
    writer.writeInt(3, coseAlg);
    return writer.toByteArray();
  }

  static byte[] encodeOkpPublicKey(byte[] publicKey, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (algorithm != WebAuthnSignatureAlgorithm.EDDSA) {
      throw new GeneralSecurityException(
          "Unsupported OKP algorithm for COSE encoding: " + algorithm.label());
    }
    if (publicKey.length != 32) {
      throw new GeneralSecurityException("Ed25519 public key must be 32 bytes");
    }
    CborWriter writer = new CborWriter();
    writer.startMap(4);
    writer.writeInt(1, 1); // kty: OKP
    writer.writeInt(3, algorithm.coseIdentifier());
    writer.writeInt(-1, 6); // crv: Ed25519
    writer.writeBytes(-2, publicKey);
    return writer.toByteArray();
  }

  private static byte[] decodeEcJwk(
      String kty, Map<String, Object> jwk, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (!"EC".equalsIgnoreCase(kty)) {
      throw new IllegalArgumentException("Expected EC key type for algorithm " + algorithm.label());
    }

    String curve = text(jwk, "crv");
    String expectedCurve = curveForAlgorithm(algorithm);
    if (!expectedCurve.equalsIgnoreCase(curve)) {
      throw new IllegalArgumentException("JWK curve " + curve + " does not match " + expectedCurve);
    }

    byte[] x = decodeField(jwk, "x");
    byte[] y = decodeField(jwk, "y");

    ECParameterSpec params = ecParametersFor(expectedCurve);
    KeyFactory factory = KeyFactory.getInstance("EC");
    ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
    ECPublicKeySpec publicSpec = new ECPublicKeySpec(point, params);
    PublicKey publicKey = factory.generatePublic(publicSpec);
    return encodeEcPublicKey((ECPublicKey) publicKey, algorithm);
  }

  private static byte[] decodeRsaJwk(
      String kty, Map<String, Object> jwk, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (!"RSA".equalsIgnoreCase(kty)) {
      throw new IllegalArgumentException(
          "Expected RSA key type for algorithm " + algorithm.label());
    }
    BigInteger modulus = new BigInteger(1, decodeField(jwk, "n"));
    BigInteger exponent = new BigInteger(1, decodeField(jwk, "e"));
    KeyFactory factory = KeyFactory.getInstance("RSA");
    PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    return encodeRsaPublicKey((RSAPublicKey) publicKey, algorithm);
  }

  private static byte[] decodeOkpJwk(
      String kty, Map<String, Object> jwk, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (!"OKP".equalsIgnoreCase(kty)) {
      throw new IllegalArgumentException(
          "Expected OKP key type for algorithm " + algorithm.label());
    }
    String curve = text(jwk, "crv");
    if (!"Ed25519".equalsIgnoreCase(curve)) {
      throw new IllegalArgumentException("Unsupported OKP curve: " + curve);
    }
    byte[] publicKey = decodeField(jwk, "x");
    return encodeOkpPublicKey(publicKey, algorithm);
  }

  private static Map<String, Object> parseJsonObject(String json) {
    Object parsed = SimpleJson.parse(json);
    if (!(parsed instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Public key JSON must be an object");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private static String text(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (!(value instanceof String str) || str.isBlank()) {
      throw new IllegalArgumentException("Missing JWK field: " + key);
    }
    return str.trim();
  }

  private static byte[] decodeField(Map<String, Object> map, String key) {
    try {
      String value = text(map, key);
      return URL_DECODER.decode(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid Base64URL value for JWK field: " + key, ex);
    }
  }

  private static KeyFactory keyFactoryFor(WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    return switch (algorithm) {
      case ES256, ES384, ES512 -> KeyFactory.getInstance("EC");
      case RS256, PS256 -> KeyFactory.getInstance("RSA");
      case EDDSA -> KeyFactory.getInstance("Ed25519");
    };
  }

  private static ECParameterSpec ecParametersFor(String curve) throws GeneralSecurityException {
    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
    parameters.init(new ECGenParameterSpec(normalizeCurve(curve)));
    return parameters.getParameterSpec(ECParameterSpec.class);
  }

  private static String curveForAlgorithm(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "P-256";
      case ES384 -> "P-384";
      case ES512 -> "P-521";
      default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
    };
  }

  private static String normalizeCurve(String curve) {
    return switch (curve.toUpperCase(Locale.US)) {
      case "P-256", "SECP256R1" -> "secp256r1";
      case "P-384", "SECP384R1" -> "secp384r1";
      case "P-521", "SECP521R1" -> "secp521r1";
      default -> curve;
    };
  }

  private static byte[] extractEd25519Key(byte[] encoded) throws GeneralSecurityException {
    if (encoded.length != ED25519_PUBLIC_KEY_PREFIX.length + 32) {
      throw new GeneralSecurityException("Unexpected Ed25519 public key length");
    }
    for (int i = 0; i < ED25519_PUBLIC_KEY_PREFIX.length; i++) {
      if (encoded[i] != ED25519_PUBLIC_KEY_PREFIX[i]) {
        throw new GeneralSecurityException("Unsupported Ed25519 public key encoding");
      }
    }
    return Arrays.copyOfRange(encoded, ED25519_PUBLIC_KEY_PREFIX.length, encoded.length);
  }

  private static byte[] toUnsigned(BigInteger value, int length) {
    byte[] bytes = value.toByteArray();
    if (bytes.length == length) {
      return bytes;
    }
    if (bytes.length == length + 1 && bytes[0] == 0) {
      return Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    byte[] result = new byte[length];
    System.arraycopy(
        bytes,
        Math.max(0, bytes.length - length),
        result,
        Math.max(0, length - bytes.length),
        Math.min(bytes.length, length));
    return result;
  }

  private static int coordinateLength(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> 32;
      case ES384 -> 48;
      case ES512 -> 66;
      default -> 32;
    };
  }

  private static int unsignedLength(BigInteger value) {
    return Math.max(1, (value.bitLength() + 7) / 8);
  }

  private static byte[] hexToBytes(String hex) {
    int length = hex.length();
    if (length % 2 != 0) {
      throw new IllegalArgumentException("Hex string must have even length");
    }
    byte[] bytes = new byte[length / 2];
    for (int i = 0; i < bytes.length; i++) {
      int index = i * 2;
      bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
    }
    return bytes;
  }

  private static final class CborWriter {
    private final ByteBuffer buffer = ByteBuffer.allocate(512);

    void startMap(int entries) {
      buffer.put((byte) (0xA0 | entries));
    }

    void writeInt(int key, int value) {
      writeInteger(key);
      writeInteger(value);
    }

    void writeBytes(int key, byte[] value) {
      writeInteger(key);
      writeByteString(value);
    }

    private void writeInteger(int value) {
      if (value >= 0) {
        writeUnsigned(0, value);
      } else {
        writeUnsigned(1, -1 - value);
      }
    }

    private void writeUnsigned(int majorType, int value) {
      if (value < 24) {
        buffer.put((byte) ((majorType << 5) | value));
      } else if (value < 256) {
        buffer.put((byte) ((majorType << 5) | 24));
        buffer.put((byte) value);
      } else if (value < 65536) {
        buffer.put((byte) ((majorType << 5) | 25));
        buffer.putShort((short) value);
      } else {
        buffer.put((byte) ((majorType << 5) | 26));
        buffer.putInt(value);
      }
    }

    private void writeByteString(byte[] value) {
      writeUnsigned(2, value.length);
      buffer.put(value);
    }

    byte[] toByteArray() {
      byte[] data = new byte[buffer.position()];
      buffer.rewind();
      buffer.get(data);
      return data;
    }
  }
}
