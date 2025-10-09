package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
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
import java.util.concurrent.ConcurrentHashMap;
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
class TotpStoredSampleEndpointTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String CREDENTIAL_ID = "rest-totp-sample";
  private static final SecretMaterial SECRET =
      SecretMaterial.fromHex("3132333435363738393031323334353637383930");
  private static final TotpHashAlgorithm ALGORITHM = TotpHashAlgorithm.SHA256;
  private static final int DIGITS = 8;
  private static final Duration STEP_DURATION = Duration.ofSeconds(60);
  private static final TotpDriftWindow DRIFT_WINDOW = TotpDriftWindow.of(2, 3);
  private static final long SAMPLE_TIMESTAMP = 1_701_001_001L;

  @Autowired private MockMvc mockMvc;

  @Autowired private CredentialStore credentialStore;

  private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.database-path", () -> "unused");
  }

  @BeforeEach
  void resetStore() {
    if (credentialStore instanceof InMemoryCredentialStore inMemory) {
      inMemory.reset();
    }
  }

  @Test
  @DisplayName(
      "Stored TOTP sample returns deterministic OTP, timestamp, drift window, and metadata")
  void storedSampleReturnsDeterministicPayload() throws Exception {
    credentialStore.save(storedCredential(metadata()));

    String responseBody =
        mockMvc
            .perform(
                get("/api/v1/totp/credentials/{credentialId}/sample", CREDENTIAL_ID)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode response = JSON.readTree(responseBody);
    assertEquals(CREDENTIAL_ID, response.path("credentialId").asText());
    assertEquals(ALGORITHM.name(), response.path("algorithm").asText());
    assertEquals(DIGITS, response.path("digits").asInt());
    assertEquals(STEP_DURATION.toSeconds(), response.path("stepSeconds").asLong());
    assertEquals(DRIFT_WINDOW.backwardSteps(), response.path("driftBackward").asInt());
    assertEquals(DRIFT_WINDOW.forwardSteps(), response.path("driftForward").asInt());
    assertEquals(SAMPLE_TIMESTAMP, response.path("timestamp").asLong());

    TotpDescriptor descriptor =
        TotpDescriptor.create(
            CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS, STEP_DURATION, DRIFT_WINDOW);
    String expectedOtp =
        TotpGenerator.generate(descriptor, Instant.ofEpochSecond(SAMPLE_TIMESTAMP));
    assertEquals(expectedOtp, response.path("otp").asText());

    JsonNode metadataNode = response.path("metadata");
    assertThat(metadataNode.isMissingNode()).isFalse();
    assertEquals("ui-totp-demo", metadataNode.path("samplePresetKey").asText());
    assertEquals("Seeded stored TOTP credential", metadataNode.path("samplePresetLabel").asText());
    assertEquals("Seeded TOTP credential (test fixture)", metadataNode.path("notes").asText());

    Credential persisted = credentialStore.findByName(CREDENTIAL_ID).orElseThrow();
    assertThat(persisted.attributes())
        .containsAllEntriesOf(storedCredential(metadata()).attributes());
  }

  @Test
  @DisplayName("Stored TOTP sample returns 404 when credential missing")
  void storedSampleMissingReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/totp/credentials/{credentialId}/sample", "unknown")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  private Credential storedCredential(Map<String, String> metadata) {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            CREDENTIAL_ID, SECRET, ALGORITHM, DIGITS, STEP_DURATION, DRIFT_WINDOW);
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
    Map<String, String> attributes = new LinkedHashMap<>(credential.attributes());
    metadata.forEach((key, value) -> attributes.put("totp.metadata." + key, value));

    return new Credential(
        credential.name(),
        CredentialType.OATH_TOTP,
        credential.secret(),
        attributes,
        credential.createdAt(),
        credential.updatedAt());
  }

  private Map<String, String> metadata() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("presetKey", "ui-totp-demo");
    metadata.put("presetLabel", "Seeded stored TOTP credential");
    metadata.put("notes", "Seeded TOTP credential (test fixture)");
    metadata.put("sampleTimestamp", Long.toString(SAMPLE_TIMESTAMP));
    return metadata;
  }

  @TestConfiguration
  static class TotpSampleTestConfiguration {

    @Bean
    CredentialStore totpSampleCredentialStore() {
      return new InMemoryCredentialStore();
    }
  }

  private static final class InMemoryCredentialStore implements CredentialStore {

    private final ConcurrentHashMap<String, Credential> store = new ConcurrentHashMap<>();

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
}
