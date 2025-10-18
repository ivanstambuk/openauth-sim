package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class TotpEvaluationEndpointTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final SecretMaterial STORED_SECRET =
      SecretMaterial.fromStringUtf8("1234567890123456789012");
  private static final SecretMaterial INLINE_SECRET =
      SecretMaterial.fromStringUtf8(
          "1234567890123456789012345678901234567890123456789012345678901234");

  private static final String CREDENTIAL_ID = "rest-totp-demo";

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
  @DisplayName("Stored TOTP evaluation generates OTP within drift window")
  void storedTotpEvaluationGeneratesOtp() throws Exception {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            CREDENTIAL_ID,
            STORED_SECRET,
            TotpHashAlgorithm.SHA1,
            6,
            Duration.ofSeconds(30),
            TotpDriftWindow.of(1, 1));
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(adapter.serialize(descriptor));
    credentialStore.save(credential);

    Instant timestamp = Instant.ofEpochSecond(1_111_111_111L);
    String expectedOtp = TotpGenerator.generate(descriptor, timestamp);

    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/totp/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credentialId": "%s",
                          "timestamp": %d,
                          "driftBackward": 1,
                          "driftForward": 1
                        }
                        """
                            .formatted(CREDENTIAL_ID, timestamp.getEpochSecond())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode response = MAPPER.readTree(responseBody);
    assertEquals("generated", response.get("status").asText());
    assertEquals("generated", response.get("reasonCode").asText());
    assertTrue(response.get("valid").asBoolean());
    assertEquals(expectedOtp, response.get("otp").asText());

    JsonNode metadata = response.get("metadata");
    assertEquals("stored", metadata.get("credentialSource").asText());
    assertEquals(0, metadata.get("matchedSkewSteps").asInt());
    assertEquals("SHA1", metadata.get("algorithm").asText());
    assertEquals(6, metadata.get("digits").asInt());
    assertEquals(30L, metadata.get("stepSeconds").asLong());
    assertEquals(1, metadata.get("driftBackwardSteps").asInt());
    assertEquals(1, metadata.get("driftForwardSteps").asInt());
    assertTrue(metadata.get("telemetryId").asText().startsWith("rest-totp-"));
  }

  @Test
  @DisplayName("Inline TOTP evaluation generates OTP when OTP not supplied")
  void inlineTotpEvaluationGeneratesOtp() throws Exception {
    TotpDescriptor descriptor =
        TotpDescriptor.create(
            "inline",
            INLINE_SECRET,
            TotpHashAlgorithm.SHA512,
            8,
            Duration.ofSeconds(60),
            TotpDriftWindow.of(0, 0));
    Instant issuedAt = Instant.ofEpochSecond(1_234_567_890L);
    String expectedOtp = TotpGenerator.generate(descriptor, issuedAt);

    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/totp/evaluate/inline")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sharedSecretHex": "%s",
                          "algorithm": "SHA512",
                          "digits": 8,
                          "stepSeconds": 60,
                          "driftBackward": 1,
                          "driftForward": 1,
                          "timestamp": %d
                        }
                        """
                            .formatted(INLINE_SECRET.asHex(), issuedAt.getEpochSecond())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode response = MAPPER.readTree(responseBody);
    assertEquals("generated", response.get("status").asText());
    assertEquals("generated", response.get("reasonCode").asText());
    assertTrue(response.get("valid").asBoolean());
    assertEquals(expectedOtp, response.get("otp").asText());
    assertEquals("inline", response.get("metadata").get("credentialSource").asText());
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
      // no-op
    }
  }
}
