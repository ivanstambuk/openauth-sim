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
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.LinkedHashMap;
import java.util.Map;
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
class HotpStoredSampleEndpointTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final String CREDENTIAL_ID = "rest-hotp-sample";

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
  @DisplayName("Stored HOTP sample returns current counter + matching OTP without mutation")
  void storedSampleReturnsMatchingOtp() throws Exception {
    credentialStore.save(
        storedCredential(
            HotpHashAlgorithm.SHA1,
            6,
            7L,
            Map.of(
                "presetKey", "ui-hotp-demo",
                "presetLabel", "stored-demo",
                "notes", "Seeded HOTP demo credential.")));

    String responseBody =
        mockMvc
            .perform(
                get("/api/v1/hotp/credentials/{credentialId}/sample", CREDENTIAL_ID)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode response = JSON.readTree(responseBody);
    assertEquals(CREDENTIAL_ID, response.path("credentialId").asText());
    assertEquals(7L, response.path("counter").asLong());
    assertEquals("SHA1", response.path("algorithm").asText());
    assertEquals(6, response.path("digits").asInt());
    assertEquals(generateOtp(HotpHashAlgorithm.SHA1, 6, 7L), response.path("otp").asText());

    assertThat(response.path("metadata").isMissingNode()).isFalse();
    assertThat(response.path("metadata").path("samplePresetKey").asText())
        .isEqualTo("ui-hotp-demo");
    assertThat(response.path("metadata").path("samplePresetLabel").asText())
        .isEqualTo("stored-demo");

    assertEquals(
        "7",
        credentialStore.findByName(CREDENTIAL_ID).orElseThrow().attributes().get("hotp.counter"));
  }

  @Test
  @DisplayName("Stored HOTP sample returns 404 when credential missing")
  void storedSampleMissingReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/hotp/credentials/{credentialId}/sample", "missing-credential")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  private Credential storedCredential(
      HotpHashAlgorithm algorithm, int digits, long counter, Map<String, String> metadata) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("hotp.algorithm", algorithm.name());
    attributes.put("hotp.digits", Integer.toString(digits));
    attributes.put("hotp.counter", Long.toString(counter));
    if (metadata != null) {
      metadata.forEach((key, value) -> attributes.put("hotp.metadata." + key, value));
    }
    return Credential.create(
        CREDENTIAL_ID, CredentialType.OATH_HOTP, SecretMaterial.fromHex(SECRET_HEX), attributes);
  }

  private static String generateOtp(HotpHashAlgorithm algorithm, int digits, long counter) {
    HotpDescriptor descriptor =
        HotpDescriptor.create(CREDENTIAL_ID, SecretMaterial.fromHex(SECRET_HEX), algorithm, digits);
    return HotpGenerator.generate(descriptor, counter);
  }

  @TestConfiguration
  static class HotpSampleTestConfiguration {

    @Bean
    CredentialStore hotpSampleCredentialStore() {
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
}
