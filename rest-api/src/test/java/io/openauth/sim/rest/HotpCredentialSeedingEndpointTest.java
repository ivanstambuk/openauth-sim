package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class HotpCredentialSeedingEndpointTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.hotp.telemetry");

    private static final List<SeedExpectation> CANONICAL_CREDENTIALS = List.of(
            new SeedExpectation("ui-hotp-demo", "3132333435363738393031323334353637383930", "SHA1", 6, 0L),
            new SeedExpectation("ui-hotp-demo-sha1-8", "3132333435363738393031323334353637383930", "SHA1", 8, 5L),
            new SeedExpectation("ui-hotp-demo-sha256", "3132333435363738393031323334353637383930", "SHA256", 8, 5L),
            new SeedExpectation("ui-hotp-demo-sha256-6", "3132333435363738393031323334353637383930", "SHA256", 6, 5L),
            new SeedExpectation(
                    "ui-hotp-demo-sha512",
                    "3132333435363738393031323334353637383930313233343536373839303132",
                    "SHA512",
                    8,
                    5L),
            new SeedExpectation(
                    "ui-hotp-demo-sha512-6",
                    "3132333435363738393031323334353637383930313233343536373839303132",
                    "SHA512",
                    6,
                    5L));

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configurePersistence(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("hotp-seed-endpoint.db");
        registry.add(
                "openauth.sim.persistence.database-path",
                () -> databasePath.toAbsolutePath().toString());
        registry.add("openauth.sim.persistence.enable-store", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @BeforeEach
    void clearCredentialStore() {
        credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
        assertThat(credentialStore.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/hotp/credentials/seed populates canonical stored credentials")
    void seedEndpointPopulatesCanonicalCredentials() throws Exception {
        TestLogHandler logHandler = registerTelemetryHandler();
        try {
            String responseBody = mockMvc.perform(
                            post("/api/v1/hotp/credentials/seed").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = OBJECT_MAPPER.readTree(responseBody);
            assertThat(response.get("addedCount").asInt()).isEqualTo(CANONICAL_CREDENTIALS.size());
            assertThat(response.get("canonicalCount").asInt()).isEqualTo(CANONICAL_CREDENTIALS.size());
            assertThat(response.get("addedCredentialIds").isArray()).isTrue();

            List<String> addedIds = collectText(response.get("addedCredentialIds")).stream()
                    .sorted()
                    .collect(Collectors.toList());
            assertThat(addedIds)
                    .containsExactlyElementsOf(CANONICAL_CREDENTIALS.stream()
                            .map(SeedExpectation::credentialId)
                            .sorted()
                            .collect(Collectors.toList()));

            Map<String, Credential> persisted = byCredentialId();
            assertThat(persisted.keySet())
                    .containsExactlyInAnyOrderElementsOf(CANONICAL_CREDENTIALS.stream()
                            .map(SeedExpectation::credentialId)
                            .toList());
            for (SeedExpectation expectation : CANONICAL_CREDENTIALS) {
                Credential credential = persisted.get(expectation.credentialId());
                assertThat(credential.type()).isEqualTo(CredentialType.OATH_HOTP);
                assertThat(credential.secret().asHex()).isEqualTo(expectation.secretHex());
                assertThat(credential.attributes().get("hotp.algorithm")).isEqualTo(expectation.algorithm());
                assertThat(credential.attributes().get("hotp.digits"))
                        .isEqualTo(Integer.toString(expectation.digits()));
                assertThat(credential.attributes().get("hotp.counter")).isEqualTo(Long.toString(expectation.counter()));
            }

            assertThat(logHandler.records()).anySatisfy(record -> assertThat(record.getMessage())
                    .contains("hotp.seed")
                    .contains("addedCount=" + CANONICAL_CREDENTIALS.size()));
        } finally {
            deregisterTelemetryHandler(logHandler);
        }
    }

    @Test
    @DisplayName("Repeated seeding only adds missing HOTP credentials and emits telemetry")
    void reseedingOnlyAddsMissingCredentials() throws Exception {
        String initialResponse = mockMvc.perform(
                        post("/api/v1/hotp/credentials/seed").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = OBJECT_MAPPER.readTree(initialResponse);
        assertThat(first.get("addedCount").asInt()).isEqualTo(CANONICAL_CREDENTIALS.size());

        assertThatNoException().isThrownBy(() -> credentialStore.delete("ui-hotp-demo"));

        TestLogHandler logHandler = registerTelemetryHandler();
        try {
            String secondResponse = mockMvc.perform(
                            post("/api/v1/hotp/credentials/seed").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode second = OBJECT_MAPPER.readTree(secondResponse);
            assertThat(second.get("addedCount").asInt()).isEqualTo(1);
            assertThat(collectText(second.get("addedCredentialIds"))).containsExactly("ui-hotp-demo");

            Map<String, Credential> persisted = byCredentialId();
            assertThat(persisted.keySet())
                    .containsExactlyInAnyOrderElementsOf(CANONICAL_CREDENTIALS.stream()
                            .map(SeedExpectation::credentialId)
                            .toList());

            assertThat(logHandler.records()).anySatisfy(record -> assertThat(record.getMessage())
                    .contains("hotp.seed")
                    .contains("addedCount=1"));
        } finally {
            deregisterTelemetryHandler(logHandler);
        }
    }

    private Map<String, Credential> byCredentialId() {
        return credentialStore.findAll().stream()
                .collect(Collectors.toMap(
                        Credential::name,
                        credential -> credential,
                        (first, second) -> first,
                        () -> new LinkedHashMap<>()));
    }

    private static List<String> collectText(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(element -> values.add(element.asText()));
        return values;
    }

    private static TestLogHandler registerTelemetryHandler() {
        TestLogHandler handler = new TestLogHandler();
        TELEMETRY_LOGGER.addHandler(handler);
        TELEMETRY_LOGGER.setLevel(Level.ALL);
        return handler;
    }

    private static void deregisterTelemetryHandler(TestLogHandler handler) {
        TELEMETRY_LOGGER.removeHandler(handler);
    }

    private record SeedExpectation(String credentialId, String secretHex, String algorithm, int digits, long counter) {
        // Marker record for concise fixture definitions.
    }

    private static final class TestLogHandler extends Handler {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record != null) {
                records.add(record);
            }
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }

        List<LogRecord> records() {
            return records.stream()
                    .sorted(Comparator.comparing(LogRecord::getMillis))
                    .collect(Collectors.toList());
        }
    }
}
