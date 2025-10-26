package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.core.fido2.SignatureInspector;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.store.CredentialStore;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
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
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String EXTENSIONS_CBOR_HEX =
            "a4696372656450726f7073a162726bf56a6372656450726f74656374a166706f6c696379026c6c61726765426c6f624b657958200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f206b686d61632d736563726574f5";
    private static final byte[] EXTENSIONS_CBOR = hexToBytes(EXTENSIONS_CBOR_HEX);
    private static final String LARGE_BLOB_KEY_B64U = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

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
        String response = mockMvc.perform(post("/api/v1/webauthn/replay")
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

        JsonNode root = JSON.readTree(mockMvc.perform(post("/api/v1/webauthn/replay")
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
        String response = mockMvc.perform(post("/api/v1/webauthn/replay")
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

        JsonNode root = JSON.readTree(mockMvc.perform(post("/api/v1/webauthn/replay")
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

        mockMvc.perform(post("/api/v1/webauthn/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inlinePayload(sample, jsonEscape("@@not-a-key@@"), sample.algorithm())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(result -> {
                    JsonNode node = JSON.readTree(result.getResponse().getContentAsString());
                    assertEquals(
                            "public_key_format_invalid", node.get("reasonCode").asText());
                });
    }

    @Test
    @DisplayName("Inline WebAuthn replay returns verbose trace when requested")
    void inlineReplayReturnsVerboseTrace() throws Exception {
        Sample sample = sample(WebAuthnSignatureAlgorithm.ES256);
        String basePayload = inlinePayload(sample, publicOnlyJwk(sample), sample.algorithm());
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(basePayload);
        payload.put("verbose", true);

        String response = mockMvc.perform(post("/api/v1/webauthn/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(response);
        JsonNode trace = root.get("trace");
        assertThat(trace).isNotNull();
        assertEquals("fido2.assertion.evaluate.inline", trace.get("operation").asText());
        assertThat(trace.get("steps").isArray()).isTrue();
        assertThat(trace.get("steps")).isNotEmpty();

        Map<String, String> extensions = orderedAttributes(step(trace, "parse.extensions"));
        assertThat(extensions).containsEntry("extensions.present", "false").containsEntry("extensions.cbor.hex", "");

        Map<String, String> verifySignature = orderedAttributes(step(trace, "verify.signature"));
        SignatureInspector.SignatureDetails signatureDetails =
                SignatureInspector.inspect(sample.algorithm(), sample.signature());
        String signaturePrefix = signatureDetails.ecdsa().isPresent() ? "sig.der" : "sig.raw";

        assertThat(verifySignature)
                .containsEntry("alg", sample.algorithm().name())
                .containsEntry("cose.alg", String.valueOf(sample.algorithm().coseIdentifier()))
                .containsEntry("cose.alg.name", sample.algorithm().name())
                .containsEntry("valid", "true")
                .containsEntry("verify.ok", "true")
                .containsEntry("policy.lowS.enforced", "false")
                .containsEntry(signaturePrefix + ".b64u", signatureDetails.base64Url())
                .containsEntry(signaturePrefix + ".len", String.valueOf(signatureDetails.length()));

        signatureDetails.ecdsa().ifPresent(ecdsa -> assertThat(verifySignature)
                .containsEntry("ecdsa.r.hex", ecdsa.rHex())
                .containsEntry("ecdsa.s.hex", ecdsa.sHex())
                .containsEntry("ecdsa.lowS", String.valueOf(ecdsa.lowS())));
    }

    @Test
    @DisplayName("Inline WebAuthn replay emits extension metadata when present")
    void inlineReplayWithExtensionsReturnsExtensionMetadata() throws Exception {
        Sample sample = sample(WebAuthnSignatureAlgorithm.ES256);
        byte[] extendedAuthenticatorData = extendAuthenticatorData(sample.authenticatorData(), EXTENSIONS_CBOR);
        byte[] signature = signAssertion(
                sample.privateKeyJwk(), sample.algorithm(), extendedAuthenticatorData, sample.clientDataJson());

        String basePayload = inlinePayload(sample, publicOnlyJwk(sample), sample.algorithm());
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(basePayload);
        payload.put("authenticatorData", URL_ENCODER.encodeToString(extendedAuthenticatorData));
        payload.put("signature", URL_ENCODER.encodeToString(signature));
        payload.put("verbose", true);

        String response = mockMvc.perform(post("/api/v1/webauthn/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(response);
        JsonNode trace = root.get("trace");
        assertThat(trace).isNotNull();
        assertThat(root.get("status").asText()).isEqualTo("match");
        Map<String, String> extensions = orderedAttributes(step(trace, "parse.extensions"));
        assertThat(extensions)
                .containsEntry("extensions.present", "true")
                .containsEntry("extensions.cbor.hex", EXTENSIONS_CBOR_HEX)
                .containsEntry("ext.credProps.rk", "true")
                .containsEntry("ext.credProtect.policy", "required")
                .containsEntry("ext.largeBlobKey.b64u", LARGE_BLOB_KEY_B64U)
                .containsEntry("ext.hmac-secret", "requested");
    }

    @Test
    @DisplayName("Inline WebAuthn replay surfaces signature decode errors")
    void inlineReplayNotesSignatureDecodeError() throws Exception {
        Sample sample = sample(WebAuthnSignatureAlgorithm.ES256);
        String basePayload = inlinePayload(sample, publicOnlyJwk(sample), sample.algorithm());
        com.fasterxml.jackson.databind.node.ObjectNode payload =
                (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(basePayload);
        byte[] malformedSignature = new byte[] {0x01, 0x02};
        payload.put("signature", URL_ENCODER.encodeToString(malformedSignature));
        payload.put("verbose", true);

        String response = mockMvc.perform(post("/api/v1/webauthn/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(payload)))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = JSON.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("signature_invalid");
        assertThat(root.get("reasonCode").asText()).isEqualTo("signature_invalid");
        JsonNode trace = root.get("trace");
        assertThat(trace).isNotNull();

        Map<String, String> verifySignature = orderedAttributes(step(trace, "verify.signature"));
        assertThat(verifySignature)
                .containsEntry("valid", "false")
                .containsEntry("verify.ok", "false")
                .containsEntry("sig.raw.b64u", URL_ENCODER.encodeToString(malformedSignature));
        JsonNode notes = step(trace, "verify.signature").path("notes");
        assertThat(notes.has("signature.decode.error")).isTrue();
    }

    private static JsonNode step(JsonNode trace, String id) {
        for (JsonNode step : trace.withArray("steps")) {
            if (id.equals(step.get("id").asText())) {
                return step;
            }
        }
        throw new AssertionError("Missing trace step: " + id);
    }

    private static Map<String, String> orderedAttributes(JsonNode step) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (JsonNode attribute : step.withArray("orderedAttributes")) {
            attributes.put(
                    attribute.get("name").asText(), attribute.get("value").asText());
        }
        return attributes;
    }

    private static Sample sample(WebAuthnSignatureAlgorithm algorithm) {
        return WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> sample.algorithm() == algorithm)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing sample for " + algorithm));
    }

    private static String inlinePayload(Sample sample, String publicKey, WebAuthnSignatureAlgorithm alg)
            throws Exception {
        String relyingPartyId = sample.relyingPartyId();
        String origin = sample.origin();
        String expectedType = sample.expectedType();
        String credentialId = sample.credentialIdBase64Url();
        String challenge = sample.challengeBase64Url();
        String clientData = sample.clientDataBase64Url();
        String authenticatorData = sample.authenticatorDataBase64Url();
        String signature = sample.signatureBase64Url();

        String body = """
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
        """.formatted(
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
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, modulus), new BigInteger(1, exponent));
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

    private static byte[] extendAuthenticatorData(byte[] authenticatorData, byte[] extensions) {
        byte[] original = authenticatorData == null ? new byte[0] : authenticatorData;
        if (original.length < 33) {
            throw new IllegalArgumentException("Authenticator data must include RP ID hash and flags");
        }
        byte[] extended = Arrays.copyOf(original, original.length + extensions.length);
        extended[32] = (byte) (extended[32] | 0x80);
        System.arraycopy(extensions, 0, extended, original.length, extensions.length);
        return extended;
    }

    private static byte[] signAssertion(
            String privateKeyJwk,
            WebAuthnSignatureAlgorithm algorithm,
            byte[] authenticatorData,
            byte[] clientDataJson) {
        try {
            PrivateKey privateKey = privateKeyFromJwk(privateKeyJwk, algorithm);
            Signature signature = signatureFor(algorithm);
            signature.initSign(privateKey);
            byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJson);
            byte[] payload = concat(authenticatorData, clientDataHash);
            signature.update(payload);
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign assertion with extensions", ex);
        }
    }

    private static PrivateKey privateKeyFromJwk(String jwk, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        Map<String, Object> map = parseJwk(jwk);
        String curve = requireString(map, "crv");
        if (!curve.equalsIgnoreCase(namedCurveLabel(algorithm))) {
            throw new IllegalArgumentException(
                    "JWK curve " + curve + " does not match expected " + namedCurveLabel(algorithm));
        }
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName(algorithm)));
        ECParameterSpec parameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
        byte[] scalar = decodeField(map, "d");
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(new BigInteger(1, scalar), parameterSpec);
        try {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        } catch (InvalidKeySpecException ex) {
            throw new GeneralSecurityException("Unable to materialise EC private key from JWK", ex);
        }
    }

    private static Signature signatureFor(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256 -> Signature.getInstance("SHA256withECDSA");
            case ES384 -> Signature.getInstance("SHA384withECDSA");
            case ES512 -> Signature.getInstance("SHA512withECDSA");
            case RS256 -> Signature.getInstance("SHA256withRSA");
            case PS256 -> Signature.getInstance("RSASSA-PSS");
            case EDDSA -> Signature.getInstance("Ed25519");
        };
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] combined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        throw new IllegalArgumentException("Missing JWK field: " + key);
    }

    private static String namedCurveLabel(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "P-256";
            case ES384 -> "P-384";
            case ES512 -> "P-521";
            case RS256, PS256, EDDSA ->
                throw new IllegalArgumentException("Unsupported algorithm for EC curve: " + algorithm);
        };
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
    }

    private static String curveName(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "secp256r1";
            case ES384 -> "secp384r1";
            case ES512 -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported EC algorithm for PEM conversion: " + algorithm);
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
                + value.replace("\\", "\\\\")
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
        private final Map<String, io.openauth.sim.core.model.Credential> credentials = new ConcurrentHashMap<>();

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
