package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Utility that normalises WebAuthn private-key material supplied as JWK or PEM/PKCS#8 blobs. The
 * parser produces a canonical Base64URL encoding of the private scalar/exponent so core attestation
 * fixtures can continue to validate deterministic inputs regardless of the format an operator
 * supplies.
 */
final class WebAuthnPrivateKeyParser {

  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private static final String JSON_PREFIX = "{";
  private static final String PEM_BEGIN = "-----BEGIN";

  private WebAuthnPrivateKeyParser() {
    throw new AssertionError("Utility class");
  }

  static ParsedKey parse(String value, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(algorithm, "algorithm");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Private key material must not be blank");
    }

    if (trimmed.startsWith(JSON_PREFIX) && trimmed.endsWith("}")) {
      return parseJwk(trimmed, algorithm);
    }
    if (trimmed.startsWith(PEM_BEGIN)) {
      return parsePem(trimmed, algorithm);
    }
    throw new IllegalArgumentException("Private key must be provided as JWK or PEM/PKCS#8");
  }

  static WebAuthnSignatureAlgorithm inferAlgorithm(String value) throws GeneralSecurityException {
    Objects.requireNonNull(value, "value");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Private key material must not be blank");
    }
    if (trimmed.startsWith(JSON_PREFIX) && trimmed.endsWith("}")) {
      Map<String, String> fields = extractJsonFields(trimmed);
      String algLabel = fields.get("alg");
      if (algLabel != null && !algLabel.isBlank()) {
        return WebAuthnSignatureAlgorithm.fromLabel(algLabel.trim());
      }
      String kty = require(fields, "kty");
      if ("EC".equalsIgnoreCase(kty)) {
        String curve = require(fields, "crv");
        return switch (curve.toUpperCase()) {
          case "P-256" -> WebAuthnSignatureAlgorithm.ES256;
          case "P-384" -> WebAuthnSignatureAlgorithm.ES384;
          case "P-521" -> WebAuthnSignatureAlgorithm.ES512;
          default -> throw new IllegalArgumentException("Unsupported EC curve: " + curve);
        };
      }
      if ("RSA".equalsIgnoreCase(kty)) {
        return WebAuthnSignatureAlgorithm.RS256;
      }
      if ("OKP".equalsIgnoreCase(kty)) {
        String curve = require(fields, "crv");
        if (!"Ed25519".equalsIgnoreCase(curve)) {
          throw new IllegalArgumentException("Unsupported OKP curve: " + curve);
        }
        return WebAuthnSignatureAlgorithm.EDDSA;
      }
      throw new IllegalArgumentException("Unable to infer algorithm for JWK key type: " + kty);
    }
    if (trimmed.startsWith(PEM_BEGIN)) {
      String normalized =
          trimmed
              .replaceAll("-----BEGIN [^-]+-----", "")
              .replaceAll("-----END [^-]+-----", "")
              .replaceAll("\\s", "");
      byte[] der;
      try {
        der = Base64.getMimeDecoder().decode(normalized);
      } catch (IllegalArgumentException ex) {
        throw new GeneralSecurityException("Unable to decode PEM payload", ex);
      }

      try {
        PrivateKey privateKey =
            KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (privateKey instanceof ECPrivateKey ecKey) {
          ECParameterSpec params = ecKey.getParams();
          if (params == null) {
            throw new GeneralSecurityException("Unable to determine EC curve parameters");
          }
          int fieldSize = params.getCurve().getField().getFieldSize();
          return switch (fieldSize) {
            case 256 -> WebAuthnSignatureAlgorithm.ES256;
            case 384 -> WebAuthnSignatureAlgorithm.ES384;
            case 521 -> WebAuthnSignatureAlgorithm.ES512;
            default ->
                throw new IllegalArgumentException("Unsupported EC field size: " + fieldSize);
          };
        }
      } catch (GeneralSecurityException ignored) {
        // continue
      }

      try {
        PrivateKey privateKey =
            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (privateKey instanceof RSAPrivateKey) {
          return WebAuthnSignatureAlgorithm.RS256;
        }
      } catch (GeneralSecurityException ignored) {
        // continue
      }

      try {
        PrivateKey privateKey =
            KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (privateKey != null) {
          return WebAuthnSignatureAlgorithm.EDDSA;
        }
      } catch (GeneralSecurityException ignored) {
        // fall through
      }

      throw new GeneralSecurityException("Unsupported PEM private key");
    }
    throw new IllegalArgumentException("Private key must be provided as JWK or PEM/PKCS#8");
  }

  private static ParsedKey parseJwk(String jwkJson, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    Map<String, String> fields = extractJsonFields(jwkJson);
    String kty = require(fields, "kty");

    String algLabel = fields.get("alg");
    if (algLabel != null && !algLabel.isBlank()) {
      WebAuthnSignatureAlgorithm resolved = WebAuthnSignatureAlgorithm.fromLabel(algLabel.trim());
      if (resolved != algorithm) {
        throw new IllegalArgumentException(
            "JWK algorithm " + resolved.label() + " does not match expected " + algorithm.label());
      }
    }

    return switch (algorithm) {
      case ES256, ES384, ES512 -> parseEcJwk(kty, fields, algorithm);
      case RS256, PS256 -> parseRsaJwk(kty, fields);
      case EDDSA -> parseEdJwk(kty, fields);
    };
  }

  private static ParsedKey parsePem(String pem, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (pem.contains("EC PRIVATE KEY")) {
      throw new GeneralSecurityException(
          "SEC1 EC private keys are not supported; provide PKCS#8 PRIVATE KEY");
    }
    if (pem.contains("RSA PRIVATE KEY")) {
      throw new GeneralSecurityException(
          "PKCS#1 RSA private keys are not supported; provide PKCS#8 PRIVATE KEY");
    }

    String normalized =
        pem.replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s", "");

    byte[] der;
    try {
      der = Base64.getMimeDecoder().decode(normalized);
    } catch (IllegalArgumentException ex) {
      throw new GeneralSecurityException("Unable to decode PEM payload", ex);
    }

    KeyFactory factory = keyFactoryFor(algorithm);
    PrivateKey privateKey;
    try {
      privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (InvalidKeySpecException ex) {
      throw new GeneralSecurityException("Unsupported PEM key", ex);
    }

    return switch (algorithm) {
      case ES256, ES384, ES512 -> encodeEcPrivateKey((ECPrivateKey) privateKey, algorithm);
      case RS256, PS256 -> encodeRsaPrivateKey((RSAPrivateKey) privateKey);
      case EDDSA -> encodeEdPrivateKey(privateKey);
    };
  }

  private static ParsedKey parseEcJwk(
      String kty, Map<String, String> fields, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    if (!"EC".equalsIgnoreCase(kty)) {
      throw new IllegalArgumentException("Expected EC key type for algorithm " + algorithm.label());
    }

    String curve = require(fields, "crv");
    String expectedCurve = curveForAlgorithm(algorithm);
    if (!expectedCurve.equalsIgnoreCase(curve)) {
      throw new IllegalArgumentException("JWK curve " + curve + " does not match " + expectedCurve);
    }

    byte[] d = decodeUrl(fields, "d");
    String canonical = URL_ENCODER.encodeToString(padToLength(d, privateScalarLength(algorithm)));
    String canonicalJwk = canonicalizeEcJwk(expectedCurve, fields);
    return new ParsedKey(canonical, canonicalJwk);
  }

  private static ParsedKey parseRsaJwk(String kty, Map<String, String> fields) {
    if (!"RSA".equalsIgnoreCase(kty)) {
      throw new IllegalArgumentException("Expected RSA key type");
    }
    byte[] modulus = decodeUrl(fields, "n");
    byte[] exponent = decodeUrl(fields, "e");
    byte[] privateExponent = decodeUrl(fields, "d");

    int expectedLength = modulus.length;
    String canonical = URL_ENCODER.encodeToString(padToLength(privateExponent, expectedLength));
    String canonicalJwk = canonicalizeRsaJwk(fields, modulus, exponent, privateExponent);

    return new ParsedKey(canonical, canonicalJwk);
  }

  private static ParsedKey parseEdJwk(String kty, Map<String, String> fields) {
    if (!"OKP".equalsIgnoreCase(kty)) {
      throw new IllegalArgumentException("Expected OKP key type for EdDSA");
    }
    String curve = require(fields, "crv");
    if (!"Ed25519".equalsIgnoreCase(curve)) {
      throw new IllegalArgumentException("Unsupported OKP curve: " + curve);
    }
    byte[] d = decodeUrl(fields, "d");
    String canonical = URL_ENCODER.encodeToString(padToLength(d, 32));
    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"OKP\"");
    builder.add("\"crv\":\"Ed25519\"");
    builder.add("\"x\":\"" + require(fields, "x").trim() + "\"");
    builder.add("\"d\":\"" + URL_ENCODER.encodeToString(padToLength(d, 32)) + "\"");
    return new ParsedKey(canonical, builder.toString());
  }

  static ParsedKey fromFixtureKey(
      String base64Url, WebAuthnSignatureAlgorithm algorithm, PublicKey publicKey)
      throws GeneralSecurityException {
    Objects.requireNonNull(base64Url, "base64Url");
    Objects.requireNonNull(algorithm, "algorithm");
    Objects.requireNonNull(publicKey, "publicKey");
    String normalized = normalizeBase64(base64Url);

    return switch (algorithm) {
      case ES256, ES384, ES512 -> deriveEcFixtureKey(normalized, algorithm, publicKey);
      case RS256, PS256 -> deriveRsaFixtureKey(normalized, publicKey);
      case EDDSA -> deriveEdFixtureKey(normalized, publicKey);
    };
  }

  private static ParsedKey deriveEcFixtureKey(
      String base64Url, WebAuthnSignatureAlgorithm algorithm, PublicKey publicKey)
      throws GeneralSecurityException {
    if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
      throw new GeneralSecurityException(
          "Expected EC public key for algorithm " + algorithm.label());
    }
    byte[] decoded = decodeFixtureBytes(base64Url);
    byte[] canonicalBytes = padToLength(decoded, privateScalarLength(algorithm));
    String canonical = URL_ENCODER.encodeToString(canonicalBytes);

    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"EC\"");
    builder.add("\"crv\":\"" + curveForAlgorithm(algorithm) + "\"");
    builder.add(
        "\"x\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(ecPublicKey.getW().getAffineX(), coordinateLength(algorithm)))
            + "\"");
    builder.add(
        "\"y\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(ecPublicKey.getW().getAffineY(), coordinateLength(algorithm)))
            + "\"");
    builder.add("\"d\":\"" + canonical + "\"");
    return new ParsedKey(canonical, builder.toString());
  }

  private static ParsedKey deriveRsaFixtureKey(String base64Url, PublicKey publicKey)
      throws GeneralSecurityException {
    if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
      throw new GeneralSecurityException("Expected RSA public key for attestation fixture");
    }
    byte[] decoded = decodeFixtureBytes(base64Url);
    int modulusLength = unsignedLength(rsaPublicKey.getModulus());
    byte[] canonicalBytes = padToLength(decoded, modulusLength);
    String canonical = URL_ENCODER.encodeToString(canonicalBytes);

    String modulus =
        URL_ENCODER.encodeToString(toUnsigned(rsaPublicKey.getModulus(), modulusLength));
    String exponent =
        URL_ENCODER.encodeToString(
            toUnsigned(
                rsaPublicKey.getPublicExponent(),
                unsignedLength(rsaPublicKey.getPublicExponent())));

    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"RSA\"");
    builder.add("\"n\":\"" + modulus + "\"");
    builder.add("\"e\":\"" + exponent + "\"");
    builder.add("\"d\":\"" + canonical + "\"");
    return new ParsedKey(canonical, builder.toString());
  }

  private static ParsedKey deriveEdFixtureKey(String base64Url, PublicKey publicKey)
      throws GeneralSecurityException {
    if (!(publicKey instanceof EdECPublicKey)) {
      throw new GeneralSecurityException("Expected Ed25519 public key for attestation fixture");
    }
    byte[] decoded = decodeFixtureBytes(base64Url);
    byte[] canonicalBytes = padToLength(decoded, 32);
    String canonical = URL_ENCODER.encodeToString(canonicalBytes);
    String publicComponent = URL_ENCODER.encodeToString(extractEd25519PublicKey(publicKey));

    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"OKP\"");
    builder.add("\"crv\":\"Ed25519\"");
    builder.add("\"x\":\"" + publicComponent + "\"");
    builder.add("\"d\":\"" + canonical + "\"");
    return new ParsedKey(canonical, builder.toString());
  }

  private static byte[] decodeFixtureBytes(String base64Url) throws GeneralSecurityException {
    try {
      return URL_DECODER.decode(normalizeBase64(base64Url));
    } catch (IllegalArgumentException ex) {
      throw new GeneralSecurityException("Fixture private key must be Base64URL encoded", ex);
    }
  }

  private static byte[] extractEd25519PublicKey(PublicKey publicKey)
      throws GeneralSecurityException {
    byte[] encoded = publicKey.getEncoded();
    if (encoded == null || encoded.length < 44) {
      throw new GeneralSecurityException("Unable to extract Ed25519 public key bytes");
    }
    return Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
  }

  static String prettyPrintJwk(String jwk) {
    if (jwk == null) {
      return null;
    }
    String trimmed = jwk.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
      return jwk;
    }
    String body = trimmed.substring(1, trimmed.length() - 1);
    if (body.isBlank()) {
      return "{\n}";
    }
    String[] fields = body.split(",");
    StringBuilder builder = new StringBuilder();
    builder.append("{\n");
    for (int index = 0; index < fields.length; index++) {
      builder.append("  ").append(fields[index].trim());
      if (index < fields.length - 1) {
        builder.append(",");
      }
      builder.append("\n");
    }
    builder.append("}");
    return builder.toString();
  }

  private static ParsedKey encodeEcPrivateKey(
      ECPrivateKey privateKey, WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    ECParameterSpec params = ensureParams(privateKey.getParams(), algorithm);
    byte[] scalar = toUnsigned(privateKey.getS(), privateScalarLength(algorithm));
    String canonical = URL_ENCODER.encodeToString(scalar);

    ECPoint publicPoint = scalarMultiply(params.getGenerator(), privateKey.getS(), params);
    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"EC\"");
    builder.add("\"crv\":\"" + curveForAlgorithm(algorithm) + "\"");
    builder.add(
        "\"x\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(publicPoint.getAffineX(), coordinateLength(algorithm)))
            + "\"");
    builder.add(
        "\"y\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(publicPoint.getAffineY(), coordinateLength(algorithm)))
            + "\"");
    builder.add("\"d\":\"" + canonical + "\"");

    return new ParsedKey(canonical, builder.toString());
  }

  private static ParsedKey encodeRsaPrivateKey(RSAPrivateKey privateKey)
      throws GeneralSecurityException {
    if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
      throw new GeneralSecurityException("RSA private key must expose CRT parameters");
    }

    int length = unsignedLength(privateKey.getModulus());
    byte[] d = toUnsigned(privateKey.getPrivateExponent(), length);
    String canonical = URL_ENCODER.encodeToString(d);

    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"RSA\"");
    builder.add(
        "\"n\":\""
            + URL_ENCODER.encodeToString(toUnsigned(privateKey.getModulus(), length))
            + "\"");
    builder.add(
        "\"e\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(crt.getPublicExponent(), unsignedLength(crt.getPublicExponent())))
            + "\"");
    builder.add("\"d\":\"" + canonical + "\"");
    builder.add(
        "\"p\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(crt.getPrimeP(), unsignedLength(crt.getPrimeP())))
            + "\"");
    builder.add(
        "\"q\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(crt.getPrimeQ(), unsignedLength(crt.getPrimeQ())))
            + "\"");
    builder.add(
        "\"dp\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(crt.getPrimeExponentP(), unsignedLength(crt.getPrimeExponentP())))
            + "\"");
    builder.add(
        "\"dq\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(crt.getPrimeExponentQ(), unsignedLength(crt.getPrimeExponentQ())))
            + "\"");
    builder.add(
        "\"qi\":\""
            + URL_ENCODER.encodeToString(
                toUnsigned(crt.getCrtCoefficient(), unsignedLength(crt.getCrtCoefficient())))
            + "\"");

    return new ParsedKey(canonical, builder.toString());
  }

  private static ParsedKey encodeEdPrivateKey(PrivateKey privateKey)
      throws GeneralSecurityException {
    byte[] pkcs8 = privateKey.getEncoded();
    if (pkcs8 == null || pkcs8.length < 44) {
      throw new GeneralSecurityException("Unable to extract Ed25519 private key bytes");
    }
    // PKCS#8 stores the private scalar as the final 32 bytes (ASN.1 OCTET STRING).
    byte[] scalar = new byte[32];
    System.arraycopy(pkcs8, pkcs8.length - 32, scalar, 0, 32);
    String canonical = URL_ENCODER.encodeToString(scalar);

    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"OKP\"");
    builder.add("\"crv\":\"Ed25519\"");
    builder.add("\"d\":\"" + canonical + "\"");

    return new ParsedKey(canonical, builder.toString());
  }

  private static KeyFactory keyFactoryFor(WebAuthnSignatureAlgorithm algorithm)
      throws GeneralSecurityException {
    return switch (algorithm) {
      case ES256, ES384, ES512 -> KeyFactory.getInstance("EC");
      case RS256, PS256 -> KeyFactory.getInstance("RSA");
      case EDDSA -> KeyFactory.getInstance("Ed25519");
    };
  }

  private static Map<String, String> extractJsonFields(String json) {
    Map<String, String> fields = new LinkedHashMap<>();
    String trimmed = json.trim();
    if (trimmed.length() < 2 || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
      throw new IllegalArgumentException("Invalid JSON object");
    }

    String body = trimmed.substring(1, trimmed.length() - 1);
    int index = 0;
    while (index < body.length()) {
      int keyStart = body.indexOf('"', index);
      if (keyStart < 0) {
        break;
      }
      int keyEnd = body.indexOf('"', keyStart + 1);
      if (keyEnd < 0) {
        break;
      }
      String key = body.substring(keyStart + 1, keyEnd);
      int separator = body.indexOf(':', keyEnd + 1);
      if (separator < 0) {
        break;
      }
      int valueStart = separator + 1;
      while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
        valueStart++;
      }
      String value;
      if (valueStart < body.length() && body.charAt(valueStart) == '"') {
        int valueEnd = body.indexOf('"', valueStart + 1);
        if (valueEnd < 0) {
          throw new IllegalArgumentException("Malformed JSON string value for " + key);
        }
        value = body.substring(valueStart + 1, valueEnd);
        index = valueEnd + 1;
      } else {
        int valueEnd = body.indexOf(',', valueStart);
        if (valueEnd < 0) {
          valueEnd = body.length();
        }
        value = body.substring(valueStart, valueEnd).trim();
        index = valueEnd + 1;
      }
      fields.put(key, value);
    }
    return fields;
  }

  private static String require(Map<String, String> fields, String name) {
    String value = fields.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("JWK field '" + name + "' is required");
    }
    return value.trim();
  }

  private static byte[] decodeUrl(Map<String, String> fields, String name) {
    try {
      return URL_DECODER.decode(normalizeBase64(require(fields, name)));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("JWK field '" + name + "' must be Base64URL", ex);
    }
  }

  private static String canonicalizeEcJwk(String curve, Map<String, String> fields) {
    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"EC\"");
    builder.add("\"crv\":\"" + curve + "\"");
    builder.add("\"x\":\"" + require(fields, "x") + "\"");
    builder.add("\"y\":\"" + require(fields, "y") + "\"");
    builder.add("\"d\":\"" + require(fields, "d") + "\"");
    return builder.toString();
  }

  private static String canonicalizeRsaJwk(
      Map<String, String> fields, byte[] modulus, byte[] exponent, byte[] privateExponent) {
    StringJoiner builder = new StringJoiner(",", "{", "}");
    builder.add("\"kty\":\"RSA\"");
    builder.add("\"n\":\"" + require(fields, "n") + "\"");
    builder.add("\"e\":\"" + require(fields, "e") + "\"");
    builder.add("\"d\":\"" + require(fields, "d") + "\"");
    // Preserve CRT parameters if present to avoid dropping user-specified values.
    if (fields.containsKey("p")) {
      builder.add("\"p\":\"" + require(fields, "p") + "\"");
    }
    if (fields.containsKey("q")) {
      builder.add("\"q\":\"" + require(fields, "q") + "\"");
    }
    if (fields.containsKey("dp")) {
      builder.add("\"dp\":\"" + require(fields, "dp") + "\"");
    }
    if (fields.containsKey("dq")) {
      builder.add("\"dq\":\"" + require(fields, "dq") + "\"");
    }
    if (fields.containsKey("qi")) {
      builder.add("\"qi\":\"" + require(fields, "qi") + "\"");
    }
    return builder.toString();
  }

  private static ECParameterSpec ensureParams(
      ECParameterSpec params, WebAuthnSignatureAlgorithm alg) throws GeneralSecurityException {
    if (params != null) {
      return params;
    }
    AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance("EC");
    algorithmParameters.init(new ECGenParameterSpec(curveForAlgorithm(alg)));
    return algorithmParameters.getParameterSpec(ECParameterSpec.class);
  }

  private static String curveForAlgorithm(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "P-256";
      case ES384 -> "P-384";
      case ES512 -> "P-521";
      default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
    };
  }

  private static byte[] padToLength(byte[] input, int length) {
    if (input.length == length) {
      return input;
    }
    byte[] output = new byte[length];
    int copy = Math.min(input.length, length);
    System.arraycopy(input, input.length - copy, output, length - copy, copy);
    return output;
  }

  private static byte[] toUnsigned(BigInteger value, int length) {
    byte[] bytes = value.toByteArray();
    if (bytes.length == length) {
      return bytes;
    }
    if (bytes.length == length + 1 && bytes[0] == 0) {
      byte[] trimmed = new byte[length];
      System.arraycopy(bytes, 1, trimmed, 0, length);
      return trimmed;
    }
    return padToLength(bytes, length);
  }

  private static int privateScalarLength(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> 32;
      case ES384 -> 48;
      case ES512 -> 66;
      default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + algorithm);
    };
  }

  private static int coordinateLength(WebAuthnSignatureAlgorithm algorithm) {
    return privateScalarLength(algorithm);
  }

  private static int unsignedLength(BigInteger value) {
    int bitLength = value.bitLength();
    return (bitLength + 7) / 8;
  }

  private static String normalizeBase64(String value) {
    return value.replaceAll("\\s", "");
  }

  private static ECPoint scalarMultiply(ECPoint point, BigInteger scalar, ECParameterSpec params) {
    ECPoint result = ECPoint.POINT_INFINITY;
    ECPoint addend = point;
    BigInteger k = scalar;
    while (k.signum() > 0) {
      if (k.testBit(0)) {
        result = addPoints(result, addend, params);
      }
      addend = addPoints(addend, addend, params);
      k = k.shiftRight(1);
    }
    return result;
  }

  private static ECPoint addPoints(ECPoint p, ECPoint q, ECParameterSpec params) {
    if (p.equals(ECPoint.POINT_INFINITY)) {
      return q;
    }
    if (q.equals(ECPoint.POINT_INFINITY)) {
      return p;
    }

    var curve = params.getCurve();
    var field = curve.getField();
    if (!(field instanceof java.security.spec.ECFieldFp primeField)) {
      throw new IllegalArgumentException("Only prime field curves are supported");
    }
    BigInteger modulus = primeField.getP();
    BigInteger a = curve.getA();

    BigInteger px = p.getAffineX();
    BigInteger py = p.getAffineY();
    BigInteger qx = q.getAffineX();
    BigInteger qy = q.getAffineY();

    if (px.equals(qx) && py.add(qy).mod(modulus).equals(BigInteger.ZERO)) {
      return ECPoint.POINT_INFINITY;
    }

    BigInteger lambda;
    if (px.equals(qx) && py.equals(qy)) {
      BigInteger numerator = px.pow(2).multiply(BigInteger.valueOf(3)).add(a).mod(modulus);
      BigInteger denominator = py.shiftLeft(1).modInverse(modulus);
      lambda = numerator.multiply(denominator).mod(modulus);
    } else {
      BigInteger numerator = qy.subtract(py).mod(modulus);
      BigInteger denominator = qx.subtract(px).mod(modulus).modInverse(modulus);
      lambda = numerator.multiply(denominator).mod(modulus);
    }

    BigInteger rx = lambda.pow(2).subtract(px).subtract(qx).mod(modulus);
    BigInteger ry = lambda.multiply(px.subtract(rx)).subtract(py).mod(modulus);
    return new ECPoint(rx, ry);
  }

  static ParsedKey fromCanonicalBase64(String base64) {
    return new ParsedKey(base64, null);
  }

  record ParsedKey(String canonicalBase64Url, String jwkRepresentation) {
    // Value carrier for canonicalised private-key material (Base64URL + optional JWK).
  }
}
