package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.store.CredentialStore;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
class Fido2ReplayEndpointTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  @Autowired private MockMvc mockMvc;

  @Autowired private CredentialStore credentialStore;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.database-path", () -> "unused");
  }

  @BeforeEach
  void resetStore() {
    if (credentialStore instanceof InMemoryCredentialStore store) {
      store.reset();
    }
  }

  @Test
  @DisplayName("Inline replay accepts JWK public keys")
  void inlineReplayAcceptsJwk() throws Exception {
    Sample sample = sample(WebAuthnSignatureAlgorithm.ES256);
    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/replay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inlinePayload(sample, publicOnlyJwk(sample), sample.algorithm())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = JSON.readTree(response);
    assertEquals("match", root.get("status").asText());
    assertEquals("match", root.get("reasonCode").asText());
    assertThat(root.get("match").asBoolean()).isTrue();
    JsonNode metadata = root.get("metadata");
    assertThat(metadata.get("credentialSource").asText()).isEqualTo("inline");
    assertThat(metadata.get("credentialReference").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Inline replay accepts PEM public keys")
  void inlineReplayAcceptsPem() throws Exception {
    Sample sample = sample(WebAuthnSignatureAlgorithm.ES256);
    String pem = ecPem(parseJwk(sample.privateKeyJwk()), sample.algorithm());

    JsonNode root =
        JSON.readTree(
            mockMvc
                .perform(
                    post("/api/v1/webauthn/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inlinePayload(sample, jsonEscape(pem), sample.algorithm())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertEquals("match", root.get("status").asText());
    assertEquals("match", root.get("reasonCode").asText());
    assertThat(root.get("match").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Inline replay accepts RSA JWK public keys")
  void inlineReplayAcceptsRsaJwk() throws Exception {
    Sample sample = sample(WebAuthnSignatureAlgorithm.RS256);
    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/replay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(inlinePayload(sample, publicOnlyJwk(sample), sample.algorithm())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = JSON.readTree(response);
    assertEquals("match", root.get("status").asText());
  }

  @Test
  @DisplayName("Inline replay accepts RSA PEM public keys")
  void inlineReplayAcceptsRsaPem() throws Exception {
    Sample sample = sample(WebAuthnSignatureAlgorithm.RS256);
    String pem = rsaPem(parseJwk(sample.privateKeyJwk()));

    JsonNode root =
        JSON.readTree(
            mockMvc
                .perform(
                    post("/api/v1/webauthn/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inlinePayload(sample, jsonEscape(pem), sample.algorithm())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertEquals("match", root.get("status").asText());
  }

  @Test
  @DisplayName("Inline replay rejects unknown public-key formats")
  void inlineReplayRejectsUnknownFormats() throws Exception {
    Sample sample = sample(WebAuthnSignatureAlgorithm.ES256);

    mockMvc
        .perform(
            post("/api/v1/webauthn/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(inlinePayload(sample, jsonEscape("@@not-a-key@@"), sample.algorithm())))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(
            result -> {
              JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
              assertEquals("public_key_format_invalid", node.get("reasonCode").asText());
            });
  }

  private static Sample sample(WebAuthnSignatureAlgorithm algorithm) {
    return WebAuthnGeneratorSamples.samples().stream()
        .filter(sample -> sample.algorithm() == algorithm)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing sample for " + algorithm));
  }

  private static String inlinePayload(
      Sample sample, String publicKey, WebAuthnSignatureAlgorithm alg) throws Exception {
    String relyingPartyId = sample.relyingPartyId();
    String origin = sample.origin();
    String expectedType = sample.expectedType();
    String credentialId = sample.credentialIdBase64Url();
    String challenge = sample.challengeBase64Url();
    String clientData = sample.clientDataBase64Url();
    String authenticatorData = sample.authenticatorDataBase64Url();
    String signature = sample.signatureBase64Url();

    String body =
        """
        {
          "credentialName": "inline-sample",
          "relyingPartyId": "%s",
          "origin": "%s",
          "expectedType": "%s",
          "credentialId": "%s",
          "publicKey": %s,
          "signatureCounter": %d,
          "userVerificationRequired": %s,
          "algorithm": "%s",
          "expectedChallenge": "%s",
          "clientData": "%s",
          "authenticatorData": "%s",
          "signature": "%s"
        }
        """
            .formatted(
                relyingPartyId,
                origin,
                expectedType,
                credentialId,
                publicKey,
                sample.signatureCounter(),
                sample.userVerificationRequired(),
                alg.label(),
                challenge,
                clientData,
                authenticatorData,
                signature);

    // Sanity check to ensure payload is valid JSON before sending.
    JSON.readTree(body);
    return body;
  }

  private static String publicOnlyJwk(Sample sample) {
    Map<String, Object> jwk = parseJwk(sample.privateKeyJwk());
    jwk.remove("d");
    jwk.remove("p");
    jwk.remove("q");
    jwk.remove("dp");
    jwk.remove("dq");
    jwk.remove("qi");
    return jsonEscape(toJson(jwk));
  }

  private static Map<String, Object> parseJwk(String jwk) {
    Object parsed = SimpleJson.parse(jwk);
    if (!(parsed instanceof Map<?, ?> map)) {
      throw new IllegalStateException("Expected JSON object for JWK");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private static String ecPem(Map<String, Object> jwk, WebAuthnSignatureAlgorithm algorithm) {
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
      throw new IllegalStateException("Unable to construct EC PEM", ex);
    }
  }

  private static String rsaPem(Map<String, Object> jwk) {
    try {
      byte[] modulus = decodeField(jwk, "n");
      byte[] exponent = decodeField(jwk, "e");
      RSAPublicKeySpec spec =
          new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent));
      PublicKey key = KeyFactory.getInstance("RSA").generatePublic(spec);
      return toPem(key.getEncoded());
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to construct RSA PEM", ex);
    }
  }

  private static byte[] decodeField(Map<String, Object> jwk, String field) {
    Object value = jwk.get(field);
    if (!(value instanceof String str) || str.isBlank()) {
      throw new IllegalStateException("Missing JWK field: " + field);
    }
    return Base64.getUrlDecoder().decode(str);
  }

  private static String curveName(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "secp256r1";
      case ES384 -> "secp384r1";
      case ES512 -> "secp521r1";
      default ->
          throw new IllegalArgumentException(
              "Unsupported EC algorithm for PEM conversion: " + algorithm);
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

  private static String jsonEscape(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        + "\"";
  }

  @TestConfiguration
  static class TestStoreConfiguration {
    @Bean
    CredentialStore credentialStore() {
      return new InMemoryCredentialStore();
    }
  }

  private static final class InMemoryCredentialStore implements CredentialStore {
    private final Map<String, io.openauth.sim.core.model.Credential> credentials =
        new ConcurrentHashMap<>();

    @Override
    public void save(io.openauth.sim.core.model.Credential credential) {
      credentials.put(credential.name(), credential);
    }

    @Override
    public Optional<io.openauth.sim.core.model.Credential> findByName(String name) {
      return Optional.ofNullable(credentials.get(name));
    }

    @Override
    public java.util.List<io.openauth.sim.core.model.Credential> findAll() {
      return java.util.List.copyOf(credentials.values());
    }

    @Override
    public boolean delete(String name) {
      return credentials.remove(name) != null;
    }

    @Override
    public void close() {
      credentials.clear();
    }

    void reset() {
      credentials.clear();
    }
  }
}
