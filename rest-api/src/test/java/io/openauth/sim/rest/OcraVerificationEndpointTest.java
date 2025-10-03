package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
final class OcraVerificationEndpointTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  private static final String STORED_CREDENTIAL_ID = "rest-ocra-replay";
  private static final String SUITE = "OCRA-1:HOTP-SHA256-8:QA08-S064";
  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SESSION_HEX =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
  private static final String CHALLENGE = "SESSION01";
  private static final String EXPECTED_OTP = "17477202";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Stored credential replay returns match with mode-aware telemetry")
  void storedCredentialReplayReturnsMatch() throws Exception {
    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/ocra/verify")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(storedReplayRequest(EXPECTED_OTP)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("match"))
              .andExpect(jsonPath("$.reasonCode").value("match"))
              .andExpect(jsonPath("$.metadata.mode").value("stored"))
              .andExpect(jsonPath("$.metadata.outcome").value("match"))
              .andExpect(jsonPath("$.metadata.telemetryId").isNotEmpty())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = JSON.readTree(responseBody);
      JsonNode metadata = response.get("metadata");
      assertNotNull(metadata, "metadata must be present");
      assertEquals("stored", metadata.get("credentialSource").asText());
      assertFalse(responseBody.contains(SHARED_SECRET_HEX), "response must not leak shared secret");
      assertThat(metadata.get("contextFingerprint").asText())
          .as("context fingerprint must be hashed")
          .matches("[A-Za-z0-9_-]{10,}");

      assertTelemetry(
          handler,
          record ->
              record.getMessage().contains("mode=stored")
                  && record.getMessage().contains("outcome=match")
                  && record.getMessage().contains("credentialSource=stored"));
      assertNoOtpLeak(handler, EXPECTED_OTP);
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  @Test
  @DisplayName("Inline replay mismatch surfaces mode-aware telemetry without leaking secrets")
  void inlineReplayMismatchSurfacesTelemetry() throws Exception {
    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/ocra/verify")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(inlineReplayRequest("00000000")))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.status").value("mismatch"))
              .andExpect(jsonPath("$.reasonCode").value("strict_mismatch"))
              .andExpect(jsonPath("$.metadata.mode").value("inline"))
              .andExpect(jsonPath("$.metadata.outcome").value("mismatch"))
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = JSON.readTree(responseBody);
      JsonNode metadata = response.get("metadata");
      assertEquals("inline", metadata.get("credentialSource").asText());
      assertFalse(responseBody.contains(SHARED_SECRET_HEX), "inline secret must remain hidden");
      assertTelemetry(
          handler,
          record ->
              record.getMessage().contains("mode=inline")
                  && record.getMessage().contains("outcome=mismatch")
                  && record.getMessage().contains("credentialSource=inline"));
      assertNoOtpLeak(handler, "00000000");
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  @Test
  @DisplayName("Stored replay validation failure returns 422 with mode-aware telemetry")
  void storedReplayValidationFailure() throws Exception {
    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/ocra/verify")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(storedReplayRequest("   ")))
              .andExpect(status().isUnprocessableEntity())
              .andExpect(jsonPath("$.error").value("invalid_input"))
              .andExpect(jsonPath("$.message").value("otp is required for verification"))
              .andExpect(jsonPath("$.details.reasonCode").value("otp_missing"))
              .andExpect(jsonPath("$.details.telemetryId").isNotEmpty())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode error = JSON.readTree(responseBody);
      assertEquals("invalid", error.get("details").get("status").asText());
      assertTelemetry(
          handler,
          record ->
              record.getMessage().contains("mode=stored")
                  && record.getMessage().contains("status=invalid")
                  && record.getMessage().contains("reasonCode=otp_missing"));
      assertNoOtpLeak(handler, EXPECTED_OTP);
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  private static String storedReplayRequest(String otp) {
    return """
        {
          "otp": "%s",
          "credentialId": "%s",
          "context": {
            "challenge": "%s",
            "sessionHex": "%s"
          }
        }
        """
        .formatted(otp, STORED_CREDENTIAL_ID, CHALLENGE, SESSION_HEX);
  }

  private static String inlineReplayRequest(String otp) {
    return """
        {
          "otp": "%s",
          "inlineCredential": {
            "suite": "%s",
            "sharedSecretHex": "%s"
          },
          "context": {
            "challenge": "%s",
            "sessionHex": "%s"
          }
        }
        """
        .formatted(otp, SUITE, SHARED_SECRET_HEX, CHALLENGE, SESSION_HEX);
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

  private static void assertTelemetry(
      TestLogHandler handler, java.util.function.Predicate<LogRecord> predicate) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
    while (System.nanoTime() < deadline) {
      if (handler.records().stream().anyMatch(predicate)) {
        return;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    throw new AssertionError("Telemetry event missing expected fields: " + handler.records());
  }

  private static void assertNoOtpLeak(TestLogHandler handler, String otp) {
    String normalized = otp.trim();
    if (normalized.isEmpty()) {
      return;
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    assertFalse(
        handler.records().stream()
            .map(LogRecord::getMessage)
            .map(message -> message.toLowerCase(Locale.ROOT))
            .anyMatch(message -> message.contains(lower)),
        () -> "OTP leaked in telemetry: " + handler.records());
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
      // no-op
    }

    java.util.List<LogRecord> records() {
      return java.util.List.copyOf(records);
    }
  }

  @TestConfiguration
  static class VerificationTestConfig {
    @Bean
    CredentialStore inMemoryCredentialStore() {
      return new InMemoryCredentialStore();
    }
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, Credential> store = new java.util.concurrent.ConcurrentHashMap<>();

    InMemoryCredentialStore() {
      OcraCredentialFactory factory = new OcraCredentialFactory();
      OcraCredentialRequest request =
          new OcraCredentialRequest(
              STORED_CREDENTIAL_ID,
              SUITE,
              SHARED_SECRET_HEX,
              SecretEncoding.HEX,
              null,
              null,
              null,
              Map.of("source", "test"));
      OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
      OcraCredentialPersistenceAdapter adapter = new OcraCredentialPersistenceAdapter();
      VersionedCredentialRecord record = adapter.serialize(descriptor);
      Credential credential = VersionedCredentialRecordMapper.toCredential(record);
      save(credential);
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
}
