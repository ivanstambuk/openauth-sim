package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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
final class OcraCredentialSeedingEndpointTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");
  private static final List<String> CANONICAL_SUITES =
      List.of(
          "OCRA-1:HOTP-SHA256-8:QA08-S064",
          "OCRA-1:HOTP-SHA256-8:QA08-S128",
          "OCRA-1:HOTP-SHA256-8:QA08-S256",
          "OCRA-1:HOTP-SHA256-8:QA08-S512",
          "OCRA-1:HOTP-SHA256-6:C-QH64");

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configurePersistence(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("seed-endpoint.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private CredentialStore credentialStore;

  @BeforeEach
  void clearCredentialStore() {
    credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
    assertThat(credentialStore.findAll()).isEmpty();
  }

  @Test
  @DisplayName("POST /api/v1/ocra/credentials/seed populates canonical suites when empty")
  void seedEndpointPopulatesCanonicalSuites() throws Exception {
    TestLogHandler logHandler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/ocra/credentials/seed").contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = OBJECT_MAPPER.readTree(responseBody);
      assertThat(response.get("addedCount").asInt()).isEqualTo(CANONICAL_SUITES.size());
      assertThat(response.get("addedCredentialIds")).isNotNull();

      Set<String> suites = new HashSet<>();
      for (Credential credential : credentialStore.findAll()) {
        suites.add(credential.attributes().get(OcraCredentialPersistenceAdapter.ATTR_SUITE));
      }
      assertThat(suites).containsExactlyInAnyOrderElementsOf(CANONICAL_SUITES);

      assertThat(logHandler.records())
          .anySatisfy(
              record ->
                  assertThat(record.getMessage())
                      .contains("ocra.seed")
                      .contains("addedCount=" + CANONICAL_SUITES.size()));
    } finally {
      deregisterTelemetryHandler(logHandler);
    }
  }

  @Test
  @DisplayName("Repeated seeding only adds missing suites and emits telemetry")
  void reseedingOnlyAddsMissingSuites() throws Exception {
    String initialResponse =
        mockMvc
            .perform(post("/api/v1/ocra/credentials/seed").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode first = OBJECT_MAPPER.readTree(initialResponse);
    assertThat(first.get("addedCount").asInt()).isEqualTo(CANONICAL_SUITES.size());

    // Delete a single credential to simulate missing data.
    assertThatNoException()
        .describedAs("should delete existing credential for reseed scenario")
        .isThrownBy(() -> credentialStore.delete(credentialStore.findAll().get(0).name()));

    TestLogHandler logHandler = registerTelemetryHandler();
    try {
      String secondResponse =
          mockMvc
              .perform(
                  post("/api/v1/ocra/credentials/seed").contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode second = OBJECT_MAPPER.readTree(secondResponse);
      assertThat(second.get("addedCount").asInt()).isEqualTo(1);
      assertThat(second.get("addedCredentialIds")).isNotNull();
      assertThat(second.get("addedCredentialIds")).isNotEmpty();

      Set<String> suites = new HashSet<>();
      for (Credential credential : credentialStore.findAll()) {
        suites.add(credential.attributes().get(OcraCredentialPersistenceAdapter.ATTR_SUITE));
      }
      assertThat(suites).containsExactlyInAnyOrderElementsOf(CANONICAL_SUITES);

      assertThat(logHandler.records())
          .anySatisfy(
              record ->
                  assertThat(record.getMessage()).contains("ocra.seed").contains("addedCount=1"));
    } finally {
      deregisterTelemetryHandler(logHandler);
    }
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
    private final List<LogRecord> records = new java.util.concurrent.CopyOnWriteArrayList<>();

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
      return records;
    }
  }
}
