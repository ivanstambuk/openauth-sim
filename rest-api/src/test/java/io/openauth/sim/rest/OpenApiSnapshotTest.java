package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OpenApiSnapshotTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final Path JSON_SNAPSHOT_PATH =
            Path.of("..", "docs", "3-reference", "rest-openapi.json").normalize();
    private static final Path YAML_SNAPSHOT_PATH =
            Path.of("..", "docs", "3-reference", "rest-openapi.yaml").normalize();

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("openapi-snapshot.db");
        registry.add(
                "openauth.sim.persistence.database-path",
                () -> databasePath.toAbsolutePath().toString());
        registry.add("openauth.sim.persistence.enable-store", () -> "true");
    }

    @BeforeAll
    static void ensureSnapshotDirectoryExists() throws IOException {
        createParentDirectory(JSON_SNAPSHOT_PATH);
        createParentDirectory(YAML_SNAPSHOT_PATH);
    }

    @Test
    @DisplayName("OpenAPI snapshot stays in sync with generated contract")
    void openApiMatchesSnapshot() throws Exception {
        String jsonResponse =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        JsonNode currentJson = normaliseJson(jsonResponse);

        if (shouldWriteSnapshot() || Files.notExists(JSON_SNAPSHOT_PATH)) {
            writeJsonSnapshot(currentJson);
        }

        assertTrue(Files.exists(JSON_SNAPSHOT_PATH), "Expected OpenAPI JSON snapshot at " + JSON_SNAPSHOT_PATH);

        JsonNode expectedJson = normaliseJson(Files.readString(JSON_SNAPSHOT_PATH));
        assertEquals(expectedJson, currentJson, "OpenAPI JSON snapshot drift detected");

        String yamlResponse = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode currentYaml = normaliseYaml(yamlResponse);

        if (shouldWriteSnapshot() || Files.notExists(YAML_SNAPSHOT_PATH)) {
            writeYamlSnapshot(currentYaml);
        }

        assertTrue(Files.exists(YAML_SNAPSHOT_PATH), "Expected OpenAPI YAML snapshot at " + YAML_SNAPSHOT_PATH);

        JsonNode expectedYaml = normaliseYaml(Files.readString(YAML_SNAPSHOT_PATH));
        assertEquals(expectedYaml, currentYaml, "OpenAPI YAML snapshot drift detected");
    }

    @Test
    @DisplayName("OpenAPI documents HOTP replay endpoint")
    void openApiDocumentsHotpReplayEndpoint() throws Exception {
        String jsonResponse =
                mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

        JsonNode root = normaliseJson(jsonResponse);
        JsonNode paths = root.path("paths");
        assertTrue(paths.has("/api/v1/hotp/replay"), "HOTP replay path must be documented");
        JsonNode replayOperation = paths.path("/api/v1/hotp/replay").path("post");
        assertTrue(replayOperation.isObject(), "HOTP replay POST operation must be present in OpenAPI contract");
    }

    private static JsonNode normaliseJson(String json) throws IOException {
        return JSON_MAPPER.readTree(json);
    }

    private static void writeJsonSnapshot(JsonNode node) throws IOException {
        String formatted =
                JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node) + System.lineSeparator();
        Path absolute = JSON_SNAPSHOT_PATH.toAbsolutePath().normalize();
        Files.writeString(absolute, formatted, StandardCharsets.UTF_8);
    }

    private static JsonNode normaliseYaml(String yaml) throws IOException {
        return YAML_MAPPER.readTree(yaml);
    }

    private static void writeYamlSnapshot(JsonNode yamlNode) throws IOException {
        String formatted = YAML_MAPPER.writeValueAsString(yamlNode).replace("\r\n", "\n");
        if (!formatted.endsWith("\n")) {
            formatted = formatted + System.lineSeparator();
        }
        Path absolute = YAML_SNAPSHOT_PATH.toAbsolutePath().normalize();
        Files.writeString(absolute, formatted, StandardCharsets.UTF_8);
    }

    private static boolean shouldWriteSnapshot() {
        if (Boolean.parseBoolean(System.getProperty("openapi.snapshot.write", "false"))) {
            return true;
        }
        String env = System.getenv("OPENAPI_SNAPSHOT_WRITE");
        return env != null && Boolean.parseBoolean(env);
    }

    private static void createParentDirectory(Path snapshotPath) throws IOException {
        Path parent = snapshotPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
