package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.rest.ui.TotpOperatorSampleData;
import io.openauth.sim.rest.ui.TotpOperatorSampleData.SampleDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class TotpCredentialSeedEndpointTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SEED_ENDPOINT = "/api/v1/totp/credentials/seed";
  private static final List<SampleDefinition> SEED_DEFINITIONS =
      TotpOperatorSampleData.seedDefinitions();
  private static final List<String> CANONICAL_IDS =
      SEED_DEFINITIONS.stream().map(SampleDefinition::credentialId).toList();

  @Autowired private MockMvc mockMvc;
  @Autowired private CredentialStore credentialStore;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.database-path", () -> "in-memory");
  }

  @BeforeEach
  void resetStore() {
    if (credentialStore instanceof InMemoryCredentialStore inMemory) {
      inMemory.reset();
    }
  }

  @Test
  @DisplayName("Seeds canonical TOTP credentials and returns created response")
  void seedCanonicalTotpCredentials() throws Exception {
    String response =
        mockMvc
            .perform(post(SEED_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode node = MAPPER.readTree(response);
    assertEquals(SEED_DEFINITIONS.size(), node.get("addedCount").asInt());
    assertEquals(SEED_DEFINITIONS.size(), node.get("canonicalCount").asInt());
    ArrayNode addedIds = (ArrayNode) node.get("addedCredentialIds");
    assertEquals(SEED_DEFINITIONS.size(), addedIds.size());
    List<String> addedIdList = new java.util.ArrayList<>();
    addedIds.forEach(jsonNode -> addedIdList.add(jsonNode.asText()));
    assertTrue(addedIdList.containsAll(CANONICAL_IDS));

    List<Credential> persisted = credentialStore.findAll();
    assertEquals(SEED_DEFINITIONS.size(), persisted.size());
    Map<String, Credential> byId =
        persisted.stream().collect(java.util.stream.Collectors.toMap(Credential::name, c -> c));

    assertEquals(Set.copyOf(CANONICAL_IDS), byId.keySet());
    for (SampleDefinition definition : SEED_DEFINITIONS) {
      Credential credential = byId.get(definition.credentialId());
      assertNotNull(credential, () -> "Missing credential " + definition.credentialId());
      assertEquals(CredentialType.OATH_TOTP, credential.type());
      Map<String, String> attributes = credential.attributes();
      assertEquals(definition.algorithm().name(), attributes.get("totp.algorithm"));
      assertEquals(Integer.toString(definition.digits()), attributes.get("totp.digits"));
      assertEquals(Long.toString(definition.stepSeconds()), attributes.get("totp.stepSeconds"));
      assertEquals(
          Integer.toString(definition.driftBackwardSteps()), attributes.get("totp.drift.backward"));
      assertEquals(
          Integer.toString(definition.driftForwardSteps()), attributes.get("totp.drift.forward"));
      Map<String, String> metadata = definition.metadata();
      metadata.forEach(
          (key, value) ->
              assertEquals(
                  value,
                  attributes.get("totp.metadata." + key),
                  () -> "Metadata mismatch for %s:%s".formatted(definition.credentialId(), key)));
    }

    String reseed =
        mockMvc
            .perform(post(SEED_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode reseedNode = MAPPER.readTree(reseed);
    assertEquals(0, reseedNode.get("addedCount").asInt());
    assertEquals(SEED_DEFINITIONS.size(), reseedNode.get("canonicalCount").asInt());
    assertEquals(0, reseedNode.get("addedCredentialIds").size());
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
    public List<Credential> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public boolean delete(String name) {
      return store.remove(name) != null;
    }

    @Override
    public void close() {
      // no-op for in-memory store
    }
  }
}
