package io.openauth.sim.rest.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.rest.support.OpenApiSchemaAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class Oid4vpRestContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.enable-store", () -> false);
        registry.add("openauth.sim.persistence.database-path", () -> "build/tmp/oid4vp-rest-contract.db");
    }

    @Test
    @Tag("schemaContract")
    @DisplayName("POST /requests returns request metadata, QR payload, telemetry, and verbose trace")
    void createAuthorizationRequestReturnsVerboseTrace() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/eudiw/openid4vp/requests")
                        .queryParam("verbose", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profile": "HAIP",
                                  "responseMode": "DIRECT_POST_JWT",
                                  "dcqlPreset": "pid-haip-baseline",
                                  "signedRequest": true,
                                  "includeQrAscii": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OpenApiSchemaAssertions.assertMatchesComponentSchema("AuthorizationResponse", responseBody);

        JsonNode response = MAPPER.readTree(responseBody);
        assertEquals("HAIP", response.get("profile").asText());
        assertTrue(response.hasNonNull("authorizationRequest"));
        assertTrue(response.hasNonNull("qr"));
        assertTrue(response.hasNonNull("telemetry"));
        JsonNode trace = response.get("trace");
        assertEquals("eudiw.request.create", trace.get("operation").asText());
        assertTrue(trace.get("metadata").has("request_id"));
        assertTrue(trace.get("steps").isArray());
    }

    @Test
    @Tag("schemaContract")
    @DisplayName("POST /wallet/simulate emits presentation metadata, vpToken JSON, and verbose trace hashes")
    void walletSimulationReturnsPresentationsAndTrace() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/eudiw/openid4vp/wallet/simulate")
                        .queryParam("verbose", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "REQ-7K3D",
                                  "walletPreset": "pid-haip-baseline",
                                  "profile": "HAIP",
                                  "trustedAuthorityPolicy": "aki:s9tIpP7qrS9=",
                                  "inlineSdJwt": null,
                                  "inlineMdoc": null
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OpenApiSchemaAssertions.assertMatchesComponentSchema("WalletSimulationResponse", responseBody);

        JsonNode response = MAPPER.readTree(responseBody);
        assertEquals("SUCCESS", response.get("status").asText());
        JsonNode presentation = response.get("presentations").get(0);
        assertEquals("dc+sd-jwt", presentation.get("format").asText());
        assertEquals(
                "aki:s9tIpP7qrS9=", presentation.get("trustedAuthorityMatch").asText());
        JsonNode trace = response.get("trace");
        assertEquals("eudiw.wallet.simulate", trace.get("operation").asText());
        JsonNode steps = trace.get("steps");
        assertTrue(steps.isArray());
        assertTrue(steps.toString().contains("wallet.presentation"));
    }

    @Test
    @DisplayName("POST /validate surfaces problem-details on Trusted Authority failure")
    void validateReturnsProblemDetailsOnTrustedAuthorityFailure() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/eudiw/openid4vp/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "presetId": "pid-haip-baseline",
                                  "trustedAuthorityPolicy": "aki:unknown"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode problem = MAPPER.readTree(responseBody);
        assertEquals(
                "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#error/invalid_scope",
                problem.get("type").asText());
        assertEquals(400, problem.get("status").asInt());
        assertTrue(problem.get("detail").asText().contains("Trusted Authority"));
        assertTrue(problem.hasNonNull("violations"));
    }

    @Test
    @Tag("schemaContract")
    @DisplayName("POST /presentations/seed returns ingestion metadata and telemetry")
    void presentationSeedReturnsCounts() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/eudiw/openid4vp/presentations/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "synthetic",
                                  "presentations": ["pid-haip-baseline"],
                                  "metadata": {
                                    "requestedBy": "Oid4vpRestContractTest"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        OpenApiSchemaAssertions.assertMatchesComponentSchema("SeedResponse", responseBody);

        JsonNode response = MAPPER.readTree(responseBody);
        assertEquals("synthetic", response.get("source").asText());
        assertEquals(1, response.get("requestedCount").asInt());
        assertEquals(1, response.get("ingestedCount").asInt());
        JsonNode provenance = response.get("provenance");
        assertTrue(provenance.get("version").asText().contains("2025"));
        assertEquals("sha256:synthetic-openid4vp-v1", provenance.get("sha256").asText());
        JsonNode presentations = response.get("presentations");
        assertTrue(presentations.isArray());
        assertTrue(presentations.get(0).get("trustedAuthorities").isArray());
        JsonNode telemetry = response.get("telemetry");
        assertEquals("oid4vp.fixtures.ingested", telemetry.get("event").asText());
        assertEquals(
                "Synthetic PID fixtures",
                telemetry.get("fields").get("provenanceSource").asText());
    }

    @TestConfiguration
    static class InMemoryStoreConfig {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {
        private final java.util.LinkedHashMap<String, Credential> store = new java.util.LinkedHashMap<>();

        @Override
        public void save(Credential credential) {
            store.put(credential.name(), credential);
        }

        @Override
        public java.util.Optional<Credential> findByName(String name) {
            return java.util.Optional.ofNullable(store.get(name));
        }

        @Override
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(store.values());
        }

        @Override
        public boolean delete(String name) {
            return store.remove(name) != null;
        }

        @Override
        public void close() {
            store.clear();
        }
    }
}
