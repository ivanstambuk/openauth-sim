package io.openauth.sim.core.fido2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Access point for the synthetic WebAuthn assertion vectors stored in {@code
 * docs/webauthn_assertion_vectors.json}.
 */
public final class WebAuthnJsonVectorFixtures {

  private WebAuthnJsonVectorFixtures() {
    throw new AssertionError("Utility class");
  }

  /** Load all WebAuthn assertion vectors from the JSON bundle. */
  public static Stream<WebAuthnJsonVector> loadAll() {
    Object decoded = JsonParser.parse(readBundle());
    if (!(decoded instanceof List<?> entries)) {
      throw new IllegalStateException("Expected top-level JSON array for WebAuthn vectors");
    }

    List<WebAuthnJsonVector> vectors = new ArrayList<>();
    for (Object entry : entries) {
      Map<String, Object> root = asObject(entry, "vector");
      vectors.add(toVector(root));
    }
    return vectors.stream();
  }

  static Object parseJson(String input) {
    return JsonParser.parse(input);
  }

  private static WebAuthnJsonVector toVector(Map<String, Object> root) {
    String vectorId = requireString(root, "vector_id");

    Map<String, Object> request = asObject(root.get("request"), vectorId + ".request");
    Map<String, Object> response = asObject(root.get("response"), vectorId + ".response");
    Map<String, Object> responsePayload =
        asObject(response.get("response"), vectorId + ".response.payload");
    Map<String, Object> keyMaterial =
        asObject(root.get("key_material"), vectorId + ".key_material");
    Map<String, Object> computed = asObject(root.get("computed"), vectorId + ".computed");

    byte[] expectedChallenge =
        decodeBase64Value(request.get("challenge"), vectorId + ".request.challenge");
    byte[] authenticatorData =
        decodeBase64Value(
            responsePayload.get("authenticatorData"), vectorId + ".authenticatorData");
    byte[] clientDataJson =
        decodeBase64Value(responsePayload.get("clientDataJSON"), vectorId + ".clientDataJSON");
    byte[] signature = decodeBase64Value(responsePayload.get("signature"), vectorId + ".signature");
    byte[] credentialId = decodeBase64Value(response.get("rawId"), vectorId + ".response.rawId");
    byte[] publicKeyCose =
        decodeBase64Value(
            keyMaterial.get("publicKeyCose_b64u"), vectorId + ".key_material.publicKeyCose_b64u");

    Map<String, Object> clientData =
        asObject(
            JsonParser.parse(new String(clientDataJson, StandardCharsets.UTF_8)),
            vectorId + ".clientDataJSON");

    String expectedType = requireString(clientData, "type");
    String origin = requireString(clientData, "origin");
    String challengeFromClient = requireString(clientData, "challenge");
    byte[] clientDataChallenge =
        decodeBase64Url(challengeFromClient, vectorId + ".clientDataJSON.challenge");
    if (!Arrays.equals(expectedChallenge, clientDataChallenge)) {
      throw new IllegalStateException("Challenge mismatch for vector " + vectorId);
    }

    String relyingPartyId = requireString(computed, "rpId");
    long signatureCounter = requireLong(computed, "signCount");
    boolean userVerificationRequired =
        "required".equalsIgnoreCase(String.valueOf(request.get("userVerification")));

    WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(keyMaterial.get("algorithm"));
    String privateKeyJwk = parsePrivateKeyJwk(keyMaterial.get("keyPairJwk"));

    WebAuthnStoredCredential storedCredential =
        new WebAuthnStoredCredential(
            relyingPartyId,
            credentialId,
            publicKeyCose,
            signatureCounter,
            userVerificationRequired,
            algorithm);

    WebAuthnAssertionRequest assertionRequest =
        new WebAuthnAssertionRequest(
            relyingPartyId,
            origin,
            expectedChallenge,
            clientDataJson,
            authenticatorData,
            signature,
            expectedType);

    return new WebAuthnJsonVector(
        vectorId, algorithm, storedCredential, assertionRequest, privateKeyJwk);
  }

  private static WebAuthnSignatureAlgorithm resolveAlgorithm(Object value) {
    String label = Objects.toString(value, "");
    if (label.equalsIgnoreCase("Ed25519")) {
      return WebAuthnSignatureAlgorithm.EDDSA;
    }
    return WebAuthnSignatureAlgorithm.fromLabel(label);
  }

  private static String readBundle() {
    Path path = resolveBundlePath();
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (IOException ioe) {
      throw new IllegalStateException("Unable to read WebAuthn JSON vector bundle", ioe);
    }
  }

  private static Path resolveBundlePath() {
    Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    Path direct = workingDirectory.resolve("docs/webauthn_assertion_vectors.json");
    if (Files.exists(direct)) {
      return direct;
    }
    Path parent = workingDirectory.getParent();
    if (parent != null) {
      Path candidate = parent.resolve("docs/webauthn_assertion_vectors.json");
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    return direct;
  }

  private static Map<String, Object> asObject(Object value, String context) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        if (!(key instanceof String)) {
          throw new IllegalStateException(context + " contains non-string key");
        }
        result.put((String) key, entry.getValue());
      }
      return result;
    }
    throw new IllegalStateException(context + " must be a JSON object");
  }

  private static String requireString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value instanceof String str) {
      return str;
    }
    throw new IllegalStateException(objectDescription(object, key) + " must be a string");
  }

  private static long requireLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String str && !str.isBlank()) {
      try {
        return Long.parseLong(str.trim());
      } catch (NumberFormatException ex) {
        throw new IllegalStateException(objectDescription(object, key) + " must be a number", ex);
      }
    }
    throw new IllegalStateException(objectDescription(object, key) + " must be a number");
  }

  private static byte[] decodeBase64Value(Object value, String context) {
    if (value == null) {
      return new byte[0];
    }
    if (value instanceof String str) {
      return decodeBase64Url(str, context);
    }
    throw new IllegalStateException(
        context + " must be a base64url string (see .gitleaks allowlist documentation)");
  }

  private static byte[] decodeBase64Url(String encoded, String context) {
    try {
      String normalized = encoded.replace("\n", "").replace("\r", "").replace(" ", "").trim();
      int padding = (4 - (normalized.length() % 4)) % 4;
      normalized = normalized + "====".substring(0, padding);
      return Base64.getUrlDecoder().decode(normalized);
    } catch (IllegalArgumentException iae) {
      throw new IllegalStateException(context + " contains invalid base64url data", iae);
    }
  }

  private static String parsePrivateKeyJwk(Object value) {
    if (value == null) {
      return null;
    }
    Map<String, Object> jwkObject = asObject(value, "key_material.keyPairJwk");
    return toCanonicalJson(jwkObject);
  }

  private static String toCanonicalJson(Map<String, Object> object) {
    StringBuilder builder = new StringBuilder();
    builder.append('{');
    List<String> keys = new ArrayList<>(object.keySet());
    Collections.sort(keys);
    for (int index = 0; index < keys.size(); index++) {
      if (index > 0) {
        builder.append(',');
      }
      String key = keys.get(index);
      builder.append('"').append(escapeJson(key)).append('"').append(':');
      Object fieldValue = object.get(key);
      if (fieldValue == null) {
        builder.append("null");
      } else if (fieldValue instanceof String str) {
        builder.append('"').append(escapeJson(str)).append('"');
      } else if (fieldValue instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedMap = (Map<String, Object>) nested;
        builder.append(toCanonicalJson(nestedMap));
      } else {
        builder.append('"').append(escapeJson(String.valueOf(fieldValue))).append('"');
      }
    }
    builder.append('}');
    return builder.toString();
  }

  private static String escapeJson(String input) {
    StringBuilder builder = new StringBuilder(input.length() + 16);
    for (int i = 0; i < input.length(); i++) {
      char ch = input.charAt(i);
      switch (ch) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (ch < 0x20) {
            builder.append(String.format("\\u%04x", (int) ch));
          } else {
            builder.append(ch);
          }
        }
      }
    }
    return builder.toString();
  }

  private static String objectDescription(Map<String, Object> object, String key) {
    return "Field '" + key + "' in object " + object.keySet();
  }

  private static final class JsonParser {
    private final String input;
    private int index;

    private JsonParser(String input) {
      this.input = input;
    }

    static Object parse(String input) {
      JsonParser parser = new JsonParser(input);
      parser.skipWhitespace();
      Object value = parser.readValue();
      parser.skipWhitespace();
      if (!parser.isAtEnd()) {
        throw new IllegalStateException("Unexpected trailing data in JSON bundle");
      }
      return value;
    }

    private Object readValue() {
      skipWhitespace();
      if (isAtEnd()) {
        throw new IllegalStateException("Unexpected end of JSON input");
      }
      char ch = input.charAt(index);
      return switch (ch) {
        case '{' -> readObject();
        case '[' -> readArray();
        case '"' -> readString();
        case 't', 'f' -> readBoolean();
        case 'n' -> readNull();
        default -> readNumber();
      };
    }

    private Map<String, Object> readObject() {
      expect('{');
      Map<String, Object> map = new LinkedHashMap<>();
      skipWhitespace();
      if (peek('}')) {
        index++;
        return map;
      }
      while (true) {
        skipWhitespace();
        String key = readString();
        skipWhitespace();
        expect(':');
        skipWhitespace();
        Object value = readValue();
        map.put(key, value);
        skipWhitespace();
        if (peek('}')) {
          index++;
          break;
        }
        expect(',');
      }
      return map;
    }

    private List<Object> readArray() {
      expect('[');
      List<Object> values = new ArrayList<>();
      skipWhitespace();
      if (peek(']')) {
        index++;
        return values;
      }
      while (true) {
        values.add(readValue());
        skipWhitespace();
        if (peek(']')) {
          index++;
          break;
        }
        expect(',');
      }
      return values;
    }

    private String readString() {
      expect('"');
      StringBuilder builder = new StringBuilder();
      while (!isAtEnd()) {
        char ch = input.charAt(index++);
        if (ch == '"') {
          return builder.toString();
        }
        if (ch == '\\') {
          if (isAtEnd()) {
            throw new IllegalStateException("Unterminated escape sequence in JSON string");
          }
          char escape = input.charAt(index++);
          builder.append(resolveEscape(escape));
        } else {
          builder.append(ch);
        }
      }
      throw new IllegalStateException("Unterminated string literal in JSON input");
    }

    private char resolveEscape(char escape) {
      return switch (escape) {
        case '"' -> '"';
        case '\\' -> '\\';
        case '/' -> '/';
        case 'b' -> '\b';
        case 'f' -> '\f';
        case 'n' -> '\n';
        case 'r' -> '\r';
        case 't' -> '\t';
        case 'u' -> readUnicode();
        default ->
            throw new IllegalStateException("Unsupported escape sequence \\" + escape + "\"");
      };
    }

    private char readUnicode() {
      if (index + 4 > input.length()) {
        throw new IllegalStateException("Invalid unicode escape in JSON string");
      }
      String hex = input.substring(index, index + 4);
      index += 4;
      try {
        return (char) Integer.parseInt(hex, 16);
      } catch (NumberFormatException nfe) {
        throw new IllegalStateException("Invalid unicode escape in JSON string", nfe);
      }
    }

    private Object readBoolean() {
      if (match("true")) {
        return Boolean.TRUE;
      }
      if (match("false")) {
        return Boolean.FALSE;
      }
      throw new IllegalStateException("Invalid boolean literal in JSON input");
    }

    private Object readNull() {
      if (match("null")) {
        return null;
      }
      throw new IllegalStateException("Invalid null literal in JSON input");
    }

    private Number readNumber() {
      int start = index;
      if (peek('-')) {
        index++;
      }
      readDigits();
      boolean decimal = false;
      if (peek('.')) {
        decimal = true;
        index++;
        readDigits();
      }
      if (peek('e') || peek('E')) {
        decimal = true;
        index++;
        if (peek('+') || peek('-')) {
          index++;
        }
        readDigits();
      }
      String number = input.substring(start, index);
      try {
        if (decimal) {
          return Double.parseDouble(number);
        }
        return Long.parseLong(number);
      } catch (NumberFormatException nfe) {
        throw new IllegalStateException("Invalid number literal in JSON input", nfe);
      }
    }

    private void readDigits() {
      if (isAtEnd() || !Character.isDigit(input.charAt(index))) {
        throw new IllegalStateException("Invalid numeric literal in JSON input");
      }
      while (!isAtEnd() && Character.isDigit(input.charAt(index))) {
        index++;
      }
    }

    private void expect(char expected) {
      if (isAtEnd() || input.charAt(index) != expected) {
        throw new IllegalStateException("Expected '" + expected + "' in JSON input");
      }
      index++;
    }

    private boolean peek(char expected) {
      return !isAtEnd() && input.charAt(index) == expected;
    }

    private boolean match(String keyword) {
      if (input.regionMatches(index, keyword, 0, keyword.length())) {
        index += keyword.length();
        return true;
      }
      return false;
    }

    private void skipWhitespace() {
      while (!isAtEnd() && Character.isWhitespace(input.charAt(index))) {
        index++;
      }
    }

    private boolean isAtEnd() {
      return index >= input.length();
    }
  }

  public record WebAuthnJsonVector(
      String vectorId,
      WebAuthnSignatureAlgorithm algorithm,
      WebAuthnStoredCredential storedCredential,
      WebAuthnAssertionRequest assertionRequest,
      String privateKeyJwk) {
    // Marker type for parsed JSON vectors.
  }
}
