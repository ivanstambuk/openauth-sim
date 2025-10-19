package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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
class HotpEvaluationEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.hotp.telemetry");
    private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
    private static final String CREDENTIAL_ID = "rest-hotp-demo";
    private static final String INLINE_SHA256_PRESET_KEY = "seeded-demo-sha256";
    private static final String INLINE_SHA256_PRESET_LABEL = "SHA-256, 8 digits";
    private static final String INLINE_SHA256_OTP = "89697997";
    private static final long INLINE_SHA256_COUNTER = 5L;

    static {
        TELEMETRY_LOGGER.setLevel(Level.ALL);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "unused");
    }

    @BeforeEach
    void resetStore() {
        if (credentialStore instanceof InMemoryCredentialStore store) {
            store.reset();
            store.setFailOnSave(false);
        }
    }

    @Test
    @DisplayName("Stored HOTP evaluation generates OTP, advances counter, and emits telemetry")
    void storedCredentialEvaluationGeneratesOtp() throws Exception {
        credentialStore.save(storedCredential(0L));

        TestLogHandler handler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(post("/api/v1/hotp/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                              {
                                "credentialId": "%s"
                              }
                              """.formatted(CREDENTIAL_ID)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            assertEquals("generated", response.get("status").asText());
            assertEquals("generated", response.get("reasonCode").asText());
            assertTrue(response.hasNonNull("otp"));

            JsonNode metadata = response.get("metadata");
            assertEquals("stored", metadata.get("credentialSource").asText());
            assertEquals(0L, metadata.get("previousCounter").asLong());
            assertEquals(1L, metadata.get("nextCounter").asLong());
            assertTrue(metadata.get("telemetryId").asText().startsWith("rest-hotp-"));

            assertTrue(credentialStore.findByName(CREDENTIAL_ID).isPresent());
            assertEquals(
                    "1",
                    credentialStore
                            .findByName(CREDENTIAL_ID)
                            .orElseThrow()
                            .attributes()
                            .get("hotp.counter"));

            assertTelemetry(handler, record -> record.getMessage().contains("event=rest.hotp.evaluate"));
            assertFalse(
                    handler.loggedSecret(SECRET_HEX),
                    () -> "secret material leaked in telemetry: " + handler.records());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @Test
    @DisplayName("Inline HOTP evaluation generates OTP and emits telemetry without persistence")
    void inlineHotpEvaluationGeneratesOtp() throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(post("/api/v1/hotp/evaluate/inline")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                              {
                                "sharedSecretHex": "%s",
                                "algorithm": "SHA1",
                                "digits": 6,
                                "counter": 5,
                                "metadata": {
                                  "source": "integration-test"
                                }
                              }
                              """.formatted(SECRET_HEX)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = MAPPER.readTree(responseBody);
            assertEquals("generated", response.get("status").asText());
            assertEquals("generated", response.get("reasonCode").asText());
            assertTrue(response.hasNonNull("otp"));

            JsonNode metadata = response.get("metadata");
            assertEquals("inline", metadata.get("credentialSource").asText());
            assertEquals(5L, metadata.get("previousCounter").asLong());
            assertEquals(6L, metadata.get("nextCounter").asLong());
            assertTrue(metadata.get("telemetryId").asText().startsWith("rest-hotp-"));

            assertTelemetry(
                    handler,
                    record -> record.getMessage().contains("event=rest.hotp.evaluate")
                            && record.getMessage().contains("credentialSource=inline"));
            assertFalse(
                    handler.loggedSecret(SECRET_HEX),
                    () -> "secret material leaked in telemetry: " + handler.records());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @Test
    @DisplayName("Inline HOTP evaluation using SHA-256 preset surfaces preset metadata")
    void inlineHotpEvaluationPresetMetadata() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/hotp/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "sharedSecretHex": "%s",
                              "algorithm": "SHA256",
                              "digits": 8,
                              "counter": %d,
                              "metadata": {
                                "presetKey": "%s",
                                "presetLabel": "%s"
                              }
                            }
                            """.formatted(
                                        SECRET_HEX,
                                        INLINE_SHA256_COUNTER,
                                        INLINE_SHA256_PRESET_KEY,
                                        INLINE_SHA256_PRESET_LABEL)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = MAPPER.readTree(responseBody);
        assertEquals("generated", response.get("status").asText());
        assertEquals("generated", response.get("reasonCode").asText());
        assertEquals(INLINE_SHA256_OTP, response.get("otp").asText());

        JsonNode metadata = response.get("metadata");
        assertTrue(metadata.has("samplePresetKey"), "Expected samplePresetKey to be exposed");
        assertEquals(INLINE_SHA256_PRESET_KEY, metadata.get("samplePresetKey").asText());
        assertTrue(metadata.has("samplePresetLabel"), "Expected samplePresetLabel to be exposed");
        assertEquals(
                INLINE_SHA256_PRESET_LABEL, metadata.get("samplePresetLabel").asText());
        assertEquals("inline", metadata.get("credentialSource").asText());
        assertEquals("SHA256", metadata.get("hashAlgorithm").asText());
        assertEquals(8, metadata.get("digits").asInt());
        assertEquals(INLINE_SHA256_COUNTER, metadata.get("previousCounter").asLong());
        assertEquals(INLINE_SHA256_COUNTER + 1, metadata.get("nextCounter").asLong());
    }

    private Credential storedCredential(long counter) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("hotp.algorithm", HotpHashAlgorithm.SHA1.name());
        attributes.put("hotp.digits", "6");
        attributes.put("hotp.counter", Long.toString(counter));
        return Credential.create(
                CREDENTIAL_ID, CredentialType.OATH_HOTP, SecretMaterial.fromHex(SECRET_HEX), attributes);
    }

    private TestLogHandler registerTelemetryHandler() {
        TestLogHandler handler = new TestLogHandler();
        TELEMETRY_LOGGER.addHandler(handler);
        return handler;
    }

    private void deregisterTelemetryHandler(TestLogHandler handler) {
        TELEMETRY_LOGGER.removeHandler(handler);
    }

    private static void assertTelemetry(TestLogHandler handler, java.util.function.Predicate<LogRecord> predicate) {
        boolean match = handler.records().stream().anyMatch(predicate);
        if (!match) {
            throw new AssertionError("Expected telemetry record matching predicate");
        }
    }

    @TestConfiguration
    static class InMemoryStoreConfig {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {

        private final LinkedHashMap<String, Credential> store = new LinkedHashMap<>();
        private boolean failOnSave;

        void reset() {
            store.clear();
        }

        void setFailOnSave(boolean failOnSave) {
            this.failOnSave = failOnSave;
        }

        @Override
        public void save(Credential credential) {
            if (failOnSave) {
                throw new IllegalStateException("save disabled");
            }
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
            // no-op
        }
    }

    private static final class TestLogHandler extends Handler {

        private final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() throws SecurityException {
            // no-op
        }

        boolean loggedSecret(String secret) {
            return records.stream()
                    .map(LogRecord::getMessage)
                    .filter(msg -> msg != null && !msg.isBlank())
                    .anyMatch(msg -> msg.toLowerCase(Locale.ROOT).contains(secret.toLowerCase(Locale.ROOT)));
        }

        java.util.List<LogRecord> records() {
            return records;
        }
    }
}
