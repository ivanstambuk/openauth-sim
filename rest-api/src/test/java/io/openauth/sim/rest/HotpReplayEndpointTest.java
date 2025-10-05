package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
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
class HotpReplayEndpointTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.hotp.telemetry");
  private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final String CREDENTIAL_ID = "rest-hotp-replay";

  static {
    TELEMETRY_LOGGER.setLevel(Level.ALL);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private CredentialStore credentialStore;

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
  @DisplayName("Stored HOTP replay returns match without advancing the counter and emits telemetry")
  void storedReplayDoesNotAdvanceCounter() throws Exception {
    credentialStore.save(storedCredential(10L));
    String otp = generateOtp(10L);

    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/hotp/replay")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                              {
                                "credentialId": "%s",
                                "otp": "%s"
                              }
                              """
                              .formatted(CREDENTIAL_ID, otp)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = JSON.readTree(responseBody);
      assertEquals("match", response.get("status").asText());
      assertEquals("match", response.get("reasonCode").asText());
      JsonNode metadata = response.get("metadata");
      assertEquals("stored", metadata.get("credentialSource").asText());
      assertEquals(10L, metadata.get("previousCounter").asLong());
      assertEquals(10L, metadata.get("nextCounter").asLong());
      assertTrue(metadata.get("telemetryId").asText().startsWith("rest-hotp-"));

      assertThat(credentialStore.findByName(CREDENTIAL_ID)).isPresent();
      assertEquals(
          "10",
          credentialStore.findByName(CREDENTIAL_ID).orElseThrow().attributes().get("hotp.counter"));

      assertTelemetry(
          handler,
          record ->
              record.getMessage().contains("event=rest.hotp.replay")
                  && record.getMessage().contains("credentialSource=stored"));
      assertFalse(
          handler.loggedSecret(SECRET_HEX),
          () -> "secret material leaked in telemetry: " + handler.records());
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  @Test
  @DisplayName("Inline HOTP replay matches without mutating counter and emits telemetry")
  void inlineReplayDoesNotAdvanceCounter() throws Exception {
    long counter = 3L;
    String otp = generateOtp(counter);

    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/hotp/replay")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                              {
                                "identifier": "device-inline",
                                "sharedSecretHex": "%s",
                                "algorithm": "SHA1",
                                "digits": 6,
                                "counter": %d,
                                "otp": "%s"
                              }
                              """
                              .formatted(SECRET_HEX, counter, otp)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = JSON.readTree(responseBody);
      assertEquals("match", response.get("status").asText());
      assertEquals("match", response.get("reasonCode").asText());
      JsonNode metadata = response.get("metadata");
      assertEquals("inline", metadata.get("credentialSource").asText());
      assertEquals(counter, metadata.get("previousCounter").asLong());
      assertEquals(counter, metadata.get("nextCounter").asLong());
      assertTrue(metadata.get("telemetryId").asText().startsWith("rest-hotp-"));

      assertTelemetry(
          handler,
          record ->
              record.getMessage().contains("event=rest.hotp.replay")
                  && record.getMessage().contains("credentialSource=inline"));
      assertFalse(
          handler.loggedSecret(SECRET_HEX),
          () -> "secret material leaked in telemetry: " + handler.records());
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  private Credential storedCredential(long counter) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("hotp.algorithm", HotpHashAlgorithm.SHA1.name());
    attributes.put("hotp.digits", "6");
    attributes.put("hotp.counter", Long.toString(counter));
    return Credential.create(
        CREDENTIAL_ID, CredentialType.OATH_HOTP, SecretMaterial.fromHex(SECRET_HEX), attributes);
  }

  private static String generateOtp(long counter) {
    HotpDescriptor descriptor =
        HotpDescriptor.create(
            CREDENTIAL_ID, SecretMaterial.fromHex(SECRET_HEX), HotpHashAlgorithm.SHA1, 6);
    return HotpGenerator.generate(descriptor, counter);
  }

  private static TestLogHandler registerTelemetryHandler() {
    TestLogHandler handler = new TestLogHandler();
    TELEMETRY_LOGGER.addHandler(handler);
    return handler;
  }

  private static void deregisterTelemetryHandler(TestLogHandler handler) {
    TELEMETRY_LOGGER.removeHandler(handler);
  }

  private static void assertTelemetry(
      TestLogHandler handler, java.util.function.Predicate<LogRecord> predicate) {
    assertTrue(
        handler.records().stream().anyMatch(predicate),
        () -> "Expected telemetry matching predicate, got: " + handler.records());
  }

  @TestConfiguration
  static class HotpTestConfiguration {

    @Bean
    CredentialStore inMemoryCredentialStore() {
      return new InMemoryCredentialStore();
    }
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, Credential> store = new java.util.concurrent.ConcurrentHashMap<>();

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

    void reset() {
      store.clear();
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
    public void close() {
      records.clear();
    }

    java.util.List<LogRecord> records() {
      return java.util.List.copyOf(records);
    }

    private boolean loggedSecret(String secret) {
      String needle = secret.toLowerCase(Locale.ROOT);
      return records.stream()
          .map(LogRecord::getMessage)
          .filter(java.util.Objects::nonNull)
          .map(message -> message.toLowerCase(Locale.ROOT))
          .anyMatch(message -> message.contains(needle));
    }
  }
}
