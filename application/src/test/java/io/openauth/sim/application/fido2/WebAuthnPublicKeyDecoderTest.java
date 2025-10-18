package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class WebAuthnPublicKeyDecoderTest {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  @Test
  void decodeBase64UrlCoseReturnsBytes() {
    WebAuthnJsonVector vector = vector("ES256:uv0_up1");
    String base64 = URL_ENCODER.encodeToString(vector.storedCredential().publicKeyCose());

    byte[] decoded = WebAuthnPublicKeyDecoder.decode(base64, WebAuthnSignatureAlgorithm.ES256);

    assertArrayEquals(vector.storedCredential().publicKeyCose(), decoded);
  }

  @Test
  void decodeEcJwkReturnsPublicKeyCose() {
    WebAuthnJsonVector vector = vector("ES256:uv0_up1");
    String jwk = publicOnlyJwk(vector.privateKeyJwk());

    byte[] decoded = WebAuthnPublicKeyDecoder.decode(jwk, WebAuthnSignatureAlgorithm.ES256);

    assertArrayEquals(vector.storedCredential().publicKeyCose(), decoded);
  }

  @Test
  void decodeRsaJwkReturnsPublicKeyCose() {
    WebAuthnJsonVector vector = vector("RS256:uv0_up1");
    String jwk = publicOnlyJwk(vector.privateKeyJwk());

    byte[] decoded = WebAuthnPublicKeyDecoder.decode(jwk, WebAuthnSignatureAlgorithm.RS256);

    assertArrayEquals(vector.storedCredential().publicKeyCose(), decoded);
  }

  @Test
  void decodeEcPemReturnsPublicKeyCose() {
    WebAuthnJsonVector vector = vector("ES256:uv0_up1");
    Map<String, Object> jwkMap = parseJwk(vector.privateKeyJwk());
    String pem = pemFromEcJwk(jwkMap, WebAuthnSignatureAlgorithm.ES256);

    byte[] decoded = WebAuthnPublicKeyDecoder.decode(pem, WebAuthnSignatureAlgorithm.ES256);

    assertArrayEquals(vector.storedCredential().publicKeyCose(), decoded);
  }

  @Test
  void decodeRsaPemReturnsPublicKeyCose() {
    WebAuthnJsonVector vector = vector("RS256:uv0_up1");
    Map<String, Object> jwkMap = parseJwk(vector.privateKeyJwk());
    String pem = pemFromRsaJwk(jwkMap);

    byte[] decoded = WebAuthnPublicKeyDecoder.decode(pem, WebAuthnSignatureAlgorithm.RS256);

    assertArrayEquals(vector.storedCredential().publicKeyCose(), decoded);
  }

  @Test
  void decodeRejectsUnknownFormat() {
    assertThrows(
        IllegalArgumentException.class,
        () -> WebAuthnPublicKeyDecoder.decode("not-a-valid-key", WebAuthnSignatureAlgorithm.ES256));
  }

  private static WebAuthnJsonVector vector(String id) {
    return WebAuthnJsonVectorFixtures.loadAll()
        .filter(vector -> vector.vectorId().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Vector not found: " + id));
  }

  private static String publicOnlyJwk(String privateJwk) {
    Map<String, Object> parsed = parseJwk(privateJwk);
    parsed.remove("d");
    parsed.remove("p");
    parsed.remove("q");
    parsed.remove("dp");
    parsed.remove("dq");
    parsed.remove("qi");
    return toJson(parsed);
  }

  private static Map<String, Object> parseJwk(String jwk) {
    Object parsed = SimpleJson.parse(jwk);
    if (!(parsed instanceof Map<?, ?> map)) {
      throw new IllegalStateException("Expected JWK object");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private static String pemFromEcJwk(
      Map<String, Object> jwk, WebAuthnSignatureAlgorithm algorithm) {
    try {
      byte[] x = decodeField(jwk, "x");
      byte[] y = decodeField(jwk, "y");

      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec(curveName(algorithm)));
      ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);

      ECPublicKeySpec keySpec =
          new ECPublicKeySpec(new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), spec);
      PublicKey key = KeyFactory.getInstance("EC").generatePublic(keySpec);
      return toPem(key.getEncoded());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to construct EC public key from JWK", ex);
    }
  }

  private static String pemFromRsaJwk(Map<String, Object> jwk) {
    try {
      byte[] modulus = decodeField(jwk, "n");
      byte[] exponent = decodeField(jwk, "e");
      RSAPublicKeySpec spec =
          new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent));
      PublicKey key = KeyFactory.getInstance("RSA").generatePublic(spec);
      return toPem(key.getEncoded());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to construct RSA public key from JWK", ex);
    }
  }

  private static byte[] decodeField(Map<String, Object> jwk, String field) {
    Object value = jwk.get(field);
    if (!(value instanceof String raw) || raw.isBlank()) {
      throw new IllegalStateException("Missing JWK field: " + field);
    }
    return URL_DECODER.decode(raw);
  }

  private static String curveName(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "secp256r1";
      case ES384 -> "secp384r1";
      case ES512 -> "secp521r1";
      default ->
          throw new IllegalArgumentException(
              "Unsupported EC algorithm for test fixture: " + algorithm);
    };
  }

  private static String toPem(byte[] encoded) {
    String base64 = Base64.getEncoder().encodeToString(encoded);
    StringBuilder builder = new StringBuilder();
    builder.append("-----BEGIN PUBLIC KEY-----\n");
    for (int index = 0; index < base64.length(); index += 64) {
      int end = Math.min(base64.length(), index + 64);
      builder.append(base64, index, end).append('\n');
    }
    builder.append("-----END PUBLIC KEY-----");
    return builder.toString();
  }

  private static String toJson(Map<String, Object> map) {
    StringBuilder builder = new StringBuilder("{");
    String[] keys =
        map.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toArray(String[]::new);
    for (int index = 0; index < keys.length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      String key = keys[index];
      builder.append('"').append(escape(key)).append('"').append(':');
      Object value = map.get(key);
      if (value == null) {
        builder.append("null");
      } else if (value instanceof String str) {
        builder.append('"').append(escape(str)).append('"');
      } else if (value instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedMap = (Map<String, Object>) nested;
        builder.append(toJson(nestedMap));
      } else {
        builder.append('"').append(escape(String.valueOf(value))).append('"');
      }
    }
    builder.append('}');
    return builder.toString();
  }

  private static String escape(String value) {
    StringBuilder builder = new StringBuilder(value.length());
    for (char ch : value.toCharArray()) {
      builder.append(
          switch (ch) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
              if (ch < 0x20) {
                yield String.format(Locale.ROOT, "\\u%04x", (int) ch);
              }
              yield String.valueOf(ch);
            }
          });
    }
    return builder.toString();
  }
}
