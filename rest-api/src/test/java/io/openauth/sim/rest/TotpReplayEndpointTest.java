package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.encoding.Base32SecretCodec;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
class TotpReplayEndpointTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.totp.telemetry");
    private static final SecretMaterial STORED_SECRET = SecretMaterial.fromHex("31323334353637383930313233343536");
    private static final SecretMaterial INLINE_SECRET =
            SecretMaterial.fromHex("3132333435363738393031323334353637383930313233343536373839303132");
    private static final String CREDENTIAL_ID = "rest-totp-replay";

    static {
        TELEMETRY_LOGGER.setLevel(Level.ALL);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "unused");
    }

    @BeforeEach
    void resetStore() {
        if (credentialStore instanceof InMemoryCredentialStore store) {
            store.reset();
        }
    }

    @Test
    @DisplayName("Stored TOTP replay returns match without mutating credential metadata")
    void storedReplayReturnsMatch() throws Exception {
        TotpDescriptor descriptor = TotpDescriptor.create(
                CREDENTIAL_ID,
                STORED_SECRET,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1));
        Credential credential = VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
        credentialStore.save(credential);

        Instant timestamp = Instant.ofEpochSecond(1_700_000_030L);
        String otp = TotpGenerator.generate(descriptor, timestamp);

        TestLogHandler handler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(post("/api/v1/totp/replay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                              {
                                "credentialId": "%s",
                                "otp": "%s",
                                "timestamp": %d,
                                "driftBackward": 1,
                                "driftForward": 1
                              }
                              """.formatted(CREDENTIAL_ID, otp, timestamp.getEpochSecond())))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = JSON.readTree(responseBody);
            assertEquals("match", response.get("status").asText());
            assertEquals("match", response.get("reasonCode").asText());
            JsonNode metadata = response.get("metadata");
            assertEquals("stored", metadata.get("credentialSource").asText());
            assertEquals(0, metadata.get("matchedSkewSteps").asInt());
            assertEquals("SHA1", metadata.get("algorithm").asText());
            assertEquals(6, metadata.get("digits").asInt());
            assertEquals(30L, metadata.get("stepSeconds").asLong());
            assertEquals(1, metadata.get("driftBackwardSteps").asInt());
            assertEquals(1, metadata.get("driftForwardSteps").asInt());
            assertTrue(metadata.get("telemetryId").asText().startsWith("rest-totp-"));
            assertFalse(metadata.get("timestampOverrideProvided").asBoolean());

            assertThat(credentialStore.findByName(CREDENTIAL_ID)).isPresent();

            assertTelemetry(
                    handler,
                    record -> record.getMessage().contains("event=rest.totp.replay")
                            && record.getMessage().contains("credentialSource=stored"));
            assertFalse(
                    handler.loggedSecret(STORED_SECRET.asHex()),
                    () -> "secret material leaked in telemetry: " + handler.records());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @Test
    @DisplayName("Stored TOTP replay returns verbose trace when requested")
    void storedTotpReplayReturnsVerboseTrace() throws Exception {
        TotpDescriptor descriptor = TotpDescriptor.create(
                CREDENTIAL_ID,
                STORED_SECRET,
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1));
        Credential credential = VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
        credentialStore.save(credential);

        Instant timestamp = Instant.ofEpochSecond(1_800_000_000L);
        String otp = TotpGenerator.generate(descriptor, timestamp);

        String responseBody = mockMvc.perform(post("/api/v1/totp/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {
                            \"credentialId\": \"%s\",
                            \"otp\": \"%s\",
                            \"timestamp\": %d,
                            \"driftBackward\": 1,
                            \"driftForward\": 1,
                            \"verbose\": true
                          }
                          """.formatted(CREDENTIAL_ID, otp, timestamp.getEpochSecond())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = JSON.readTree(responseBody);
        JsonNode trace = response.get("trace");
        assertNotNull(trace, "Expected trace payload when verbose=true");
        assertEquals("totp.replay.stored", trace.get("operation").asText());

        JsonNode metadata = trace.get("metadata");
        assertEquals("TOTP", metadata.get("protocol").asText());
        assertEquals("stored", metadata.get("mode").asText());
        assertEquals("educational", metadata.get("tier").asText());

        var resolveAttributes = orderedAttributes(step(trace, "resolve.credential"));
        assertTrue(resolveAttributes.containsKey("credentialId"));
        assertTrue(resolveAttributes.containsKey("secret.hash"));
        assertTrue(
                resolveAttributes.get("secret.hash").startsWith("sha256:"), "Secret hash should expose sha256 digest");

        var deriveAttributes = orderedAttributes(step(trace, "derive.time-counter"));
        assertTrue(deriveAttributes.containsKey("time.counter.hex"));
        assertTrue(deriveAttributes.containsKey("epoch.seconds"));

        var computeAttributes = orderedAttributes(step(trace, "compute.hmac"));
        assertTrue(computeAttributes.containsKey("hmac.hex"));

        var reduceAttributes = orderedAttributes(step(trace, "mod.reduce"));
        assertTrue(reduceAttributes.containsKey("otp"));
    }

    @Test
    @DisplayName("Inline TOTP replay accepts Base32 shared secrets")
    void inlineTotpReplayAcceptsBase32Secret() throws Exception {
        String base32 = "JBSWY3DPEHPK3PXP";
        String secretHex = Base32SecretCodec.toUpperHex(base32);
        TotpDescriptor descriptor = TotpDescriptor.create(
                "inline-base32",
                SecretMaterial.fromHex(secretHex),
                TotpHashAlgorithm.SHA1,
                6,
                Duration.ofSeconds(30),
                TotpDriftWindow.of(1, 1));
        Instant timestamp = Instant.ofEpochSecond(1_700_000_030L);
        String otp = TotpGenerator.generate(descriptor, timestamp);

        String responseBody = mockMvc.perform(post("/api/v1/totp/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {
                            "sharedSecretBase32": "%s",
                            "algorithm": "SHA1",
                            "digits": 6,
                            "stepSeconds": 30,
                            "driftBackward": 1,
                            "driftForward": 1,
                            "timestamp": %d,
                            "otp": "%s"
                          }
                          """.formatted(base32, timestamp.getEpochSecond(), otp)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = JSON.readTree(responseBody);
        assertEquals("match", response.get("status").asText());
        JsonNode metadata = response.get("metadata");
        assertEquals("inline", metadata.get("credentialSource").asText());
        assertEquals(6, metadata.get("digits").asInt());
        assertEquals(30L, metadata.get("stepSeconds").asLong());
    }

    @Test
    @DisplayName("Inline TOTP replay rejects invalid Base32 secrets")
    void inlineTotpReplayRejectsInvalidBase32Secret() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/totp/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {
                            "sharedSecretBase32": "!!!!",
                            "otp": "123456"
                          }
                          """))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = JSON.readTree(responseBody);
        assertEquals("shared_secret_base32_invalid", response.get("reasonCode").asText());
        assertEquals("sharedSecretBase32", response.get("details").get("field").asText());
    }

    @Test
    @DisplayName("Inline TOTP replay outside drift returns mismatch without revealing secrets")
    void inlineReplayOutsideDriftReturnsMismatch() throws Exception {
        TotpDescriptor descriptor = TotpDescriptor.create(
                "inline", INLINE_SECRET, TotpHashAlgorithm.SHA512, 8, Duration.ofSeconds(60), TotpDriftWindow.of(0, 0));
        Instant issuedAt = Instant.ofEpochSecond(1_700_000_500L);
        String otp = TotpGenerator.generate(descriptor, issuedAt);

        TestLogHandler handler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(post("/api/v1/totp/replay")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                              {
                                "sharedSecretHex": "%s",
                                "algorithm": "SHA512",
                                "digits": 8,
                                "stepSeconds": 60,
                                "driftBackward": 0,
                                "driftForward": 0,
                                "timestamp": %d,
                                "timestampOverride": %d,
                                "otp": "%s"
                              }
                              """.formatted(
                                            INLINE_SECRET.asHex(),
                                            issuedAt.plusSeconds(180).getEpochSecond(),
                                            issuedAt.minusSeconds(120).getEpochSecond(),
                                            otp)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = JSON.readTree(responseBody);
            assertEquals("mismatch", response.get("status").asText());
            assertEquals("otp_out_of_window", response.get("reasonCode").asText());
            JsonNode metadata = response.get("metadata");
            assertEquals("inline", metadata.get("credentialSource").asText());
            assertEquals(8, metadata.get("digits").asInt());
            assertEquals(60L, metadata.get("stepSeconds").asLong());
            assertTrue(metadata.get("timestampOverrideProvided").asBoolean());
            assertTrue(metadata.get("telemetryId").asText().startsWith("rest-totp-"));

            assertTelemetry(
                    handler,
                    record -> record.getMessage().contains("event=rest.totp.replay")
                            && record.getMessage().contains("credentialSource=inline"));
            assertFalse(
                    handler.loggedSecret(INLINE_SECRET.asHex()),
                    () -> "secret material leaked in telemetry: " + handler.records());
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @TestConfiguration
    static class InMemoryStoreConfiguration {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {

        private final LinkedHashMap<String, Credential> store = new LinkedHashMap<>();

        void reset() {
            store.clear();
        }

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

    private static TestLogHandler registerTelemetryHandler() {
        TestLogHandler handler = new TestLogHandler();
        TELEMETRY_LOGGER.addHandler(handler);
        return handler;
    }

    private static void deregisterTelemetryHandler(TestLogHandler handler) {
        TELEMETRY_LOGGER.removeHandler(handler);
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

    private static void assertTelemetry(TestLogHandler handler, java.util.function.Predicate<LogRecord> filter) {
        assertTrue(
                handler.records().stream().anyMatch(filter),
                () -> "Expected telemetry record not found. Captured=" + handler.records());
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
        public void close() {
            records.clear();
        }

        java.util.List<LogRecord> records() {
            return java.util.List.copyOf(records);
        }

        boolean loggedSecret(String secretHex) {
            return records.stream().anyMatch(record -> record.getMessage().contains(secretHex));
        }
    }
}
