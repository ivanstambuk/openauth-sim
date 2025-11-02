package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.Resolved;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
final class EmvCapCredentialSeedingEndpointTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.emv.cap.telemetry");

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configurePersistence(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("emv-cap-seed-endpoint.db");
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
    void clearStore() {
        credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
        assertThat(credentialStore.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/v1/emv/cap/credentials/seed populates canonical stored credentials")
    void seedEndpointPopulatesCanonicalCredentials() throws Exception {
        TestLogHandler handler = registerTelemetryHandler();
        try {
            String body = mockMvc.perform(
                            post("/api/v1/emv/cap/credentials/seed").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = OBJECT_MAPPER.readTree(body);

            int canonicalSize = EmvCapSeedSamples.samples().size();
            assertThat(response.get("addedCount").asInt()).isEqualTo(canonicalSize);
            assertThat(response.get("canonicalCount").asInt()).isEqualTo(canonicalSize);

            List<String> addedIds = collectText(response.get("addedCredentialIds")).stream()
                    .sorted()
                    .toList();
            assertThat(addedIds)
                    .containsExactlyElementsOf(EmvCapSeedSamples.samples().stream()
                            .map(SeedSample::credentialId)
                            .sorted()
                            .toList());

            Map<String, Credential> persisted = byCredentialId();
            assertThat(persisted.keySet())
                    .containsExactlyInAnyOrderElementsOf(EmvCapSeedSamples.samples().stream()
                            .map(SeedSample::credentialId)
                            .toList());

            for (SeedSample sample : EmvCapSeedSamples.samples()) {
                Credential credential = persisted.get(sample.credentialId());
                assertThat(credential).isNotNull();
                assertThat(credential.type()).isEqualTo(CredentialType.EMV_CA);
                assertThat(credential.secret().asHex().toUpperCase())
                        .isEqualTo(sample.vector().input().masterKeyHex());
                assertEmvAttributes(sample, credential);
            }

            assertThat(handler.records())
                    .anyMatch(record -> record.getMessage().contains("emv.cap.seed")
                            && record.getMessage().contains("addedCount=" + canonicalSize));
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    @Test
    @DisplayName("Repeated seeding only inserts missing EMV/CAP credentials")
    void reseedingAddsOnlyMissingCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/emv/cap/credentials/seed").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThatNoException().isThrownBy(() -> credentialStore.delete("emv-cap-identify-baseline"));

        TestLogHandler handler = registerTelemetryHandler();
        try {
            String body = mockMvc.perform(
                            post("/api/v1/emv/cap/credentials/seed").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode response = OBJECT_MAPPER.readTree(body);
            assertThat(response.get("addedCount").asInt()).isEqualTo(1);
            assertThat(collectText(response.get("addedCredentialIds"))).containsExactly("emv-cap-identify-baseline");

            assertThat(handler.records())
                    .anyMatch(record -> record.getMessage().contains("emv.cap.seed")
                            && record.getMessage().contains("addedCount=1"));
        } finally {
            deregisterTelemetryHandler(handler);
        }
    }

    private void assertEmvAttributes(SeedSample sample, Credential credential) {
        Map<String, String> attributes = credential.attributes();
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_MODE))
                .isEqualTo(sample.mode().name());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_BRANCH_FACTOR))
                .isEqualTo(Integer.toString(sample.vector().input().branchFactor()));
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_HEIGHT))
                .isEqualTo(Integer.toString(sample.vector().input().height()));
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_IV))
                .isEqualTo(sample.vector().input().ivHex());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_CDOL1))
                .isEqualTo(sample.vector().input().cdol1Hex());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_IPB))
                .isEqualTo(sample.vector().input().issuerProprietaryBitmapHex());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_ICC_TEMPLATE))
                .isEqualTo(sample.vector().input().iccDataTemplateHex());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_ISSUER_APPLICATION_DATA))
                .isEqualTo(sample.vector().input().issuerApplicationDataHex());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_ATC))
                .isEqualTo(sample.vector().input().atcHex());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_CHALLENGE))
                .isEqualTo(sample.vector().input().customerInputs().challenge());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_REFERENCE))
                .isEqualTo(sample.vector().input().customerInputs().reference());
        assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_DEFAULT_AMOUNT))
                .isEqualTo(sample.vector().input().customerInputs().amount());

        optionalString(sample.vector().outputs().generateAcInputTerminalHex()).ifPresent(value -> assertThat(
                        attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_TERMINAL))
                .isEqualTo(value));
        optionalString(sample.vector().outputs().generateAcInputIccHex())
                .ifPresent(value -> assertThat(attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_ICC))
                        .isEqualTo(value));
        Optional.ofNullable(sample.vector().resolved())
                .map(Resolved::iccDataHex)
                .flatMap(EmvCapCredentialSeedingEndpointTest::optionalString)
                .ifPresent(value -> assertThat(
                                attributes.get(EmvCapCredentialPersistenceAdapter.ATTR_TRANSACTION_ICC_RESOLVED))
                        .isEqualTo(value));

        sample.metadata().forEach((key, value) -> assertThat(attributes.get("emv.cap.metadata." + key))
                .isEqualTo(value));
    }

    private Map<String, Credential> byCredentialId() {
        return credentialStore.findAll().stream()
                .sorted(Comparator.comparing(Credential::name))
                .collect(Collectors.toMap(
                        Credential::name, credential -> credential, (first, second) -> first, LinkedHashMap::new));
    }

    private static List<String> collectText(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(element -> values.add(element.asText()));
        return values;
    }

    private static Optional<String> optionalString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(trimmed);
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

    private static final class TestLogHandler extends Handler {

        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

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
            // no-op
        }

        List<LogRecord> records() {
            return List.copyOf(records);
        }
    }
}
