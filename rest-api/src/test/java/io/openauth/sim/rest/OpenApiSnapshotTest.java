package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OpenApiSnapshotTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path SNAPSHOT_PATH =
      Path.of("..", "docs", "3-reference", "rest-openapi.json").normalize();

  @Autowired private MockMvc mockMvc;

  @BeforeAll
  static void ensureSnapshotDirectoryExists() throws IOException {
    Path parent = SNAPSHOT_PATH.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  @Test
  @DisplayName("OpenAPI snapshot stays in sync with generated contract")
  void openApiMatchesSnapshot() throws Exception {
    String responseBody =
        mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();

    JsonNode current = normalise(responseBody);

    if (shouldWriteSnapshot() || Files.notExists(SNAPSHOT_PATH)) {
      writeSnapshot(current);
    }

    assertTrue(Files.exists(SNAPSHOT_PATH), "Expected OpenAPI snapshot at " + SNAPSHOT_PATH);

    JsonNode expected = normalise(Files.readString(SNAPSHOT_PATH));
    assertEquals(expected, current, "OpenAPI snapshot drift detected");
  }

  private static JsonNode normalise(String json) throws IOException {
    return MAPPER.readTree(json);
  }

  private static void writeSnapshot(JsonNode node) throws IOException {
    String formatted =
        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node) + System.lineSeparator();
    Path absolute = SNAPSHOT_PATH.toAbsolutePath().normalize();
    Files.writeString(absolute, formatted, StandardCharsets.UTF_8);
  }

  private static boolean shouldWriteSnapshot() {
    if (Boolean.parseBoolean(System.getProperty("openapi.snapshot.write", "false"))) {
      return true;
    }
    String env = System.getenv("OPENAPI_SNAPSHOT_WRITE");
    return env != null && Boolean.parseBoolean(env);
  }
}
