package io.openauth.sim.rest.emv.cap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
final class EmvCapCredentialDirectoryControllerTest {

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("credentials.db");
        registry.add(
                "openauth.sim.persistence.database-path",
                () -> databasePath.toAbsolutePath().toString());
        registry.add("openauth.sim.persistence.enable-store", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @Autowired
    private ObjectMapper objectMapper;

    private final EmvCapSeedApplicationService seedService = new EmvCapSeedApplicationService();

    @BeforeEach
    void setUp() {
        credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
        seedService.seed(
                EmvCapSeedSamples.samples().stream()
                        .map(sample -> sample.toSeedCommand())
                        .toList(),
                credentialStore);
    }

    @Test
    @DisplayName("Directory endpoint lists seeded EMV/CAP credentials with metadata")
    void directoryListsSeededCredentials() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/emv/cap/credentials").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root.isArray()).isTrue();
        assertThat(root).hasSizeGreaterThanOrEqualTo(3);

        JsonNode identifyNode = findById(root, "emv-cap-identify-baseline");
        assertThat(identifyNode).isNotNull();
        assertThat(identifyNode.path("mode").asText()).isEqualTo("IDENTIFY");
        assertThat(identifyNode.path("label").asText()).isEqualTo("CAP Identify baseline");
        assertThat(identifyNode.path("masterKey").asText()).isNotEmpty();
        assertThat(identifyNode.path("defaultAtc").asText()).isEqualTo("00B4");
        assertThat(identifyNode.path("defaults").path("challenge").asText()).isEmpty();
        assertThat(identifyNode.path("transaction").path("iccResolved").asText())
                .isNotEmpty();

        JsonNode metadata = identifyNode.path("metadata");
        assertThat(metadata.path("presetKey").asText()).isEqualTo("emv-cap.identify.baseline");
        assertThat(metadata.path("vectorId").asText()).isEqualTo("identify-baseline");
    }

    private static JsonNode findById(JsonNode root, String id) {
        for (JsonNode node : root) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }
}
