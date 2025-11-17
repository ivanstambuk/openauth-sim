package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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
class TotpHelperEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String ENDPOINT = "/api/v1/totp/helper/current";
    private static final String SECRET_HEX = "3132333435363738393031323334353637383930";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "in-memory");
    }

    @BeforeEach
    void resetStore() {
        if (credentialStore instanceof TotpHelperEndpointTest.InMemoryCredentialStore inMemory) {
            inMemory.reset();
        }
    }

    @Test
    @DisplayName("Returns current OTP metadata for stored credential")
    void returnsCurrentOtp() throws Exception {
        persistCredential("helper-demo", TotpHashAlgorithm.SHA1, 8, Duration.ofSeconds(30));

        String payload = """
                {"credentialId":"helper-demo","timestamp":59,"window":{"backward":1,"forward":1}}
                """;

        String response = mockMvc.perform(
                        post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = MAPPER.readTree(response);
        assertEquals("helper-demo", node.get("credentialId").asText());
        assertEquals("94287082", node.get("otp").asText());
        assertEquals(59L, node.get("generationEpochSeconds").asLong());
        assertEquals(89L, node.get("expiresEpochSeconds").asLong());
        JsonNode metadata = node.get("metadata");
        assertEquals("SHA1", metadata.get("algorithm").asText());
        assertEquals(8, metadata.get("digits").asInt());
        assertEquals(30L, metadata.get("stepSeconds").asLong());
        assertEquals(1, metadata.get("driftBackwardSteps").asInt());
        assertEquals(1, metadata.get("driftForwardSteps").asInt());
        assertTrue(metadata.has("telemetryId"));
        assertEquals("generated", metadata.get("reasonCode").asText());
    }

    @Test
    @DisplayName("Returns 422 when credential does not exist")
    void returnsUnprocessableForMissingCredential() throws Exception {
        String payload = """
                {"credentialId":"missing"}
                """;

        mockMvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity());
    }

    private void persistCredential(String credentialId, TotpHashAlgorithm algorithm, int digits, Duration step) {
        TotpDescriptor descriptor = TotpDescriptor.create(
                credentialId, SecretMaterial.fromHex(SECRET_HEX), algorithm, digits, step, TotpDriftWindow.of(1, 1));
        TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter(FIXED_CLOCK);
        VersionedCredentialRecord record = adapter.serialize(descriptor);
        Credential credential = VersionedCredentialRecordMapper.toCredential(record);
        credentialStore.save(credential);
    }

    @TestConfiguration
    static class InMemoryStoreConfiguration {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {

        private final LinkedHashMap<String, Credential> backing = new LinkedHashMap<>();

        void reset() {
            backing.clear();
        }

        @Override
        public void save(Credential credential) {
            backing.put(credential.name(), credential);
        }

        @Override
        public java.util.Optional<Credential> findByName(String name) {
            return java.util.Optional.ofNullable(backing.get(name));
        }

        @Override
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(backing.values());
        }

        @Override
        public boolean delete(String name) {
            return backing.remove(name) != null;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
