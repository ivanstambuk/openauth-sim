package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class Fido2EvaluationEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String RELYING_PARTY_ID = "example.org";
    private static final String ORIGIN = "https://example.org";
    private static final String EXPECTED_TYPE = "webauthn.get";
    private static final String PRIVATE_KEY_JWK = """
      {
        \"kty\":\"EC\",
        \"crv\":\"P-256\",
        \"x\":\"qdZggyTjMpAsFSTkjMWSwuBQuB3T-w6bDAphr8rHSVk\",
        \"y\":\"cNVi6TQ6udwSbuwQ9JCt0dAxM5LgpenvK6jQPZ2_GTs\",
        \"d\":\"GV7Q6vqPvJNmr1Lu2swyafBOzG9hvrtqs-vronAeZv8\"
      }
      """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    private final WebAuthnAssertionGenerationApplicationService generator =
            new WebAuthnAssertionGenerationApplicationService();
    private final io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter persistenceAdapter =
            new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "unused");
    }

    @BeforeEach
    void resetStore() {
        if (credentialStore instanceof InMemoryCredentialStore inMemory) {
            inMemory.reset();
        }
    }

    @Test
    @DisplayName("Stored WebAuthn evaluation generates an authenticator assertion")
    void storedEvaluationGeneratesAssertion() throws Exception {
        byte[] challenge = "stored-challenge".getBytes(StandardCharsets.UTF_8);
        byte[] credentialId = "stored-credential-id".getBytes(StandardCharsets.UTF_8);

        GenerationResult seed = generator.generate(new GenerationCommand.Inline(
                "seed-inline",
                credentialId,
                WebAuthnSignatureAlgorithm.ES256,
                RELYING_PARTY_ID,
                ORIGIN,
                EXPECTED_TYPE,
                0L,
                false,
                challenge,
                PRIVATE_KEY_JWK));

        saveCredential("stored-credential", seed);

        String response = mockMvc.perform(post("/api/v1/webauthn/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          \"credentialId\": \"stored-credential\",
                          \"relyingPartyId\": \"%s\",
                          \"origin\": \"%s\",
                          \"expectedType\": \"%s\",
                          \"challenge\": \"%s\",
                          \"privateKey\": %s
                        }
                        """.formatted(
                                        RELYING_PARTY_ID,
                                        ORIGIN,
                                        EXPECTED_TYPE,
                                        encode(challenge),
                                        jsonEscape(PRIVATE_KEY_JWK))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("generated");
        JsonNode assertion = root.get("assertion");
        assertThat(assertion.get("type").asText()).isEqualTo("public-key");
        assertThat(assertion.get("id").asText()).isEqualTo(encode(seed.credentialId()));
        assertThat(assertion.has("algorithm")).isFalse();
        assertThat(assertion.has("userVerificationRequired")).isFalse();
        assertThat(assertion.has("relyingPartyId")).isFalse();
        assertThat(assertion.has("origin")).isFalse();
        assertThat(assertion.has("signatureCounter")).isFalse();

        JsonNode responseNode = assertion.get("response");
        byte[] clientDataJson = decode(responseNode.get("clientDataJSON").asText());
        JsonNode clientData = MAPPER.readTree(clientDataJson);
        assertThat(clientData.get("type").asText()).isEqualTo(EXPECTED_TYPE);
        assertThat(clientData.get("origin").asText()).isEqualTo(ORIGIN);
        assertThat(decode(clientData.get("challenge").asText())).isEqualTo(challenge);

        assertThat(responseNode.get("authenticatorData").asText()).isNotBlank();
        assertThat(responseNode.get("signature").asText()).isNotBlank();

        JsonNode metadata = root.get("metadata");
        assertThat(metadata.get("credentialSource").asText()).isEqualTo("stored");
        assertThat(metadata.get("credentialReference").asBoolean()).isTrue();
        assertThat(metadata.get("relyingPartyId").asText()).isEqualTo(RELYING_PARTY_ID);
        assertThat(metadata.get("origin").asText()).isEqualTo(ORIGIN);
        assertThat(metadata.get("algorithm").asText()).isEqualTo("ES256");
        assertThat(metadata.get("userVerificationRequired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Inline WebAuthn evaluation generates an authenticator assertion")
    void inlineEvaluationGeneratesAssertion() throws Exception {
        byte[] challenge = "inline-challenge".getBytes(StandardCharsets.UTF_8);
        byte[] credentialId = "inline-credential-id".getBytes(StandardCharsets.UTF_8);

        String response = mockMvc.perform(post("/api/v1/webauthn/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          \"credentialId\": \"%s\",
                          \"relyingPartyId\": \"%s\",
                          \"origin\": \"%s\",
                          \"algorithm\": \"ES256\",
                          \"signatureCounter\": 0,
                          \"userVerificationRequired\": false,
                          \"challenge\": \"%s\",
                          \"privateKey\": %s
                        }
                        """.formatted(
                                        encode(credentialId),
                                        RELYING_PARTY_ID,
                                        ORIGIN,
                                        encode(challenge),
                                        jsonEscape(PRIVATE_KEY_JWK))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("generated");
        JsonNode assertion = root.get("assertion");
        assertThat(assertion.get("id").asText()).isEqualTo(encode(credentialId));
        assertThat(assertion.has("relyingPartyId")).isFalse();
        assertThat(assertion.has("origin")).isFalse();
        assertThat(assertion.has("algorithm")).isFalse();
        JsonNode responseNode = assertion.get("response");
        assertThat(responseNode.get("signature").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Stored WebAuthn evaluation returns verbose trace when requested")
    void storedEvaluationReturnsVerboseTrace() throws Exception {
        byte[] challenge = "stored-verbose".getBytes(StandardCharsets.UTF_8);
        byte[] credentialId = "stored-credential-trace".getBytes(StandardCharsets.UTF_8);

        GenerationResult seed = generator.generate(new GenerationCommand.Inline(
                "seed-inline-trace",
                credentialId,
                WebAuthnSignatureAlgorithm.ES256,
                RELYING_PARTY_ID,
                ORIGIN,
                EXPECTED_TYPE,
                1L,
                false,
                challenge,
                PRIVATE_KEY_JWK));

        saveCredential("stored-credential-trace", seed);

        String response = mockMvc.perform(post("/api/v1/webauthn/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          \"credentialId\": \"stored-credential-trace\",
                          \"relyingPartyId\": \"%s\",
                          \"origin\": \"%s\",
                          \"expectedType\": \"%s\",
                          \"challenge\": \"%s\",
                          \"privateKey\": %s,
                          \"verbose\": true
                        }
                        """.formatted(
                                        RELYING_PARTY_ID,
                                        ORIGIN,
                                        EXPECTED_TYPE,
                                        encode(challenge),
                                        jsonEscape(PRIVATE_KEY_JWK))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(response);
        JsonNode trace = root.get("trace");
        assertThat(trace).as("verbose trace payload required").isNotNull();
        assertThat(trace.get("operation").asText()).isEqualTo("fido2.assertion.evaluate.stored");

        JsonNode metadata = trace.get("metadata");
        assertThat(metadata.get("credentialSource").asText()).isEqualTo("stored");
        assertThat(metadata.get("credentialId").asText()).isEqualTo("stored-credential-trace");
        assertThat(metadata.get("tier").asText()).isEqualTo("educational");
        assertThat(metadata.get("alg").asText()).isEqualTo("ES256");
        assertThat(metadata.get("cose.alg").asText()).isEqualTo("-7");

        Map<String, String> decodeAttributes = orderedAttributes(step(trace, "decode.challenge"));
        assertThat(decodeAttributes).containsEntry("length", "14");

        Map<String, String> constructAttributes = orderedAttributes(step(trace, "construct.command"));
        assertThat(constructAttributes).containsKeys("signatureCounter", "userVerificationRequired");

        Map<String, String> generateAttributes = orderedAttributes(step(trace, "generate.assertion"));
        assertThat(generateAttributes).containsEntry("alg", "ES256");
        assertThat(generateAttributes).containsEntry("cose.alg", "-7");
        assertThat(generateAttributes).containsEntry("credentialReference", "true");
    }

    @Test
    @DisplayName("Inline WebAuthn evaluation returns verbose trace when requested")
    void inlineEvaluationReturnsVerboseTrace() throws Exception {
        byte[] challenge = "inline-verbose".getBytes(StandardCharsets.UTF_8);
        byte[] credentialId = "inline-credential-trace".getBytes(StandardCharsets.UTF_8);

        String response = mockMvc.perform(post("/api/v1/webauthn/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          \"credentialId\": \"%s\",
                          \"relyingPartyId\": \"%s\",
                          \"origin\": \"%s\",
                          \"algorithm\": \"ES256\",
                          \"signatureCounter\": 0,
                          \"userVerificationRequired\": false,
                          \"challenge\": \"%s\",
                          \"privateKey\": %s,
                          \"verbose\": true
                        }
                        """.formatted(
                                        encode(credentialId),
                                        RELYING_PARTY_ID,
                                        ORIGIN,
                                        encode(challenge),
                                        jsonEscape(PRIVATE_KEY_JWK))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(response);
        JsonNode trace = root.get("trace");
        assertThat(trace).isNotNull();
        assertThat(trace.get("operation").asText()).isEqualTo("fido2.assertion.evaluate.inline");
        JsonNode metadata = trace.get("metadata");
        assertThat(metadata.get("credentialSource").asText()).isEqualTo("inline");
        assertThat(metadata.get("tier").asText()).isEqualTo("educational");
        assertThat(metadata.get("alg").asText()).isEqualTo("ES256");
        assertThat(metadata.get("cose.alg").asText()).isEqualTo("-7");

        Map<String, String> constructAttributes = orderedAttributes(step(trace, "construct.command"));
        assertThat(constructAttributes).containsKeys("credentialName", "signatureCounter", "userVerificationRequired");

        Map<String, String> generateAttributes = orderedAttributes(step(trace, "generate.assertion"));
        assertThat(generateAttributes).containsEntry("alg", "ES256");
        assertThat(generateAttributes).containsEntry("cose.alg", "-7");
        assertThat(generateAttributes).containsEntry("credentialReference", "false");
    }

    @Test
    @DisplayName("Evaluate endpoints reject invalid private keys")
    void evaluateRejectsInvalidPrivateKey() throws Exception {
        mockMvc.perform(post("/api/v1/webauthn/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      \"credentialId\": \"missing\",
                      \"relyingPartyId\": \"%s\",
                      \"origin\": \"%s\",
                      \"expectedType\": \"%s\",
                      \"challenge\": \"%s\",
                      \"privateKey\": \"not-a-key\"
                    }
                    """.formatted(RELYING_PARTY_ID, ORIGIN, EXPECTED_TYPE, encode(new byte[32]))))
                .andExpect(status().isUnprocessableEntity());
    }

    private void saveCredential(String name, GenerationResult seed) {
        WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                .name(name)
                .relyingPartyId(RELYING_PARTY_ID)
                .credentialId(seed.credentialId())
                .publicKeyCose(seed.publicKeyCose())
                .signatureCounter(seed.signatureCounter())
                .userVerificationRequired(seed.userVerificationRequired())
                .algorithm(seed.algorithm())
                .build();

        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
        credentialStore.save(credential);
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

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static byte[] decode(String value) {
        return URL_DECODER.decode(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonEscape(String rawJson) {
        String sanitized = rawJson.stripIndent()
                .trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + sanitized + "\"";
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {
        private final Map<String, Credential> backing = new LinkedHashMap<>();

        @Override
        public void save(Credential credential) {
            backing.put(credential.name(), credential);
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(backing.get(name));
        }

        @Override
        public List<Credential> findAll() {
            return List.copyOf(backing.values());
        }

        @Override
        public boolean delete(String name) {
            return backing.remove(name) != null;
        }

        @Override
        public void close() {
            backing.clear();
        }

        void reset() {
            backing.clear();
        }
    }
}
