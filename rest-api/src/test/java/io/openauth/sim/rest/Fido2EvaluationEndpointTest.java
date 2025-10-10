package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.Base64;
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
class Fido2EvaluationEndpointTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  @Autowired private MockMvc mockMvc;
  @Autowired private CredentialStore credentialStore;

  private final io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter persistenceAdapter =
      new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter();

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.database-path", () -> "unused");
  }

  @BeforeEach
  void resetStore() {
    if (credentialStore instanceof InMemoryCredentialStore inMemory) {
      inMemory.reset();
    }
  }

  @Test
  @DisplayName("Stored WebAuthn evaluation returns sanitized success payload")
  void storedEvaluationReturnsSuccess() throws Exception {
    WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name("fido2-packed-es256")
            .relyingPartyId(fixture.storedCredential().relyingPartyId())
            .credentialId(fixture.storedCredential().credentialId())
            .publicKeyCose(fixture.storedCredential().publicKeyCose())
            .signatureCounter(fixture.storedCredential().signatureCounter())
            .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
            .algorithm(WebAuthnSignatureAlgorithm.ES256)
            .build();

    Credential credential =
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
    credentialStore.save(credential);

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credentialId": "fido2-packed-es256",
                          "relyingPartyId": "example.org",
                          "origin": "https://example.org",
                          "expectedType": "webauthn.get",
                          "expectedChallenge": "%s",
                          "clientData": "%s",
                          "authenticatorData": "%s",
                          "signature": "%s"
                        }
                        """
                            .formatted(
                                encode(fixture.request().expectedChallenge()),
                                encode(fixture.request().clientDataJson()),
                                encode(fixture.request().authenticatorData()),
                                encode(fixture.request().signature()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode body = MAPPER.readTree(response);
    assertThat(body.get("status").asText()).isEqualTo("validated");
    assertThat(body.get("valid").asBoolean()).isTrue();
    JsonNode metadata = body.get("metadata");
    assertThat(metadata.get("credentialSource").asText()).isEqualTo("stored");
    assertThat(metadata.get("credentialReference").asBoolean()).isTrue();
    assertThat(metadata.get("relyingPartyId").asText()).isEqualTo("example.org");
    assertThat(metadata.get("origin").asText()).isEqualTo("https://example.org");
    assertThat(metadata.get("algorithm").asText()).isEqualTo("ES256");
    assertThat(metadata.get("userVerificationRequired").asBoolean()).isFalse();
    assertThat(metadata.has("challenge")).isFalse();
    assertThat(metadata.has("clientData")).isFalse();
    assertThat(metadata.has("signature")).isFalse();
  }

  @Test
  @DisplayName("Inline WebAuthn evaluation reports origin mismatch without exposing secrets")
  void inlineEvaluationReportsOriginMismatch() throws Exception {
    WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/evaluate/inline")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credentialName": "cli-inline",
                          "relyingPartyId": "example.org",
                          "origin": "https://malicious.example.org",
                          "expectedType": "webauthn.get",
                          "credentialId": "%s",
                          "publicKey": "%s",
                          "signatureCounter": %d,
                          "userVerificationRequired": false,
                          "algorithm": "ES256",
                          "expectedChallenge": "%s",
                          "clientData": "%s",
                          "authenticatorData": "%s",
                          "signature": "%s"
                        }
                        """
                            .formatted(
                                encode(fixture.storedCredential().credentialId()),
                                encode(fixture.storedCredential().publicKeyCose()),
                                fixture.storedCredential().signatureCounter(),
                                encode(fixture.request().expectedChallenge()),
                                encode(fixture.request().clientDataJson()),
                                encode(fixture.request().authenticatorData()),
                                encode(fixture.request().signature()))))
            .andExpect(status().isUnprocessableEntity())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode body = MAPPER.readTree(response);
    assertThat(body.get("status").asText()).isEqualTo("origin_mismatch");
    assertThat(body.get("metadata").get("credentialReference").asBoolean()).isFalse();
    assertThat(body.get("metadata").has("challenge")).isFalse();
    assertThat(body.get("metadata").has("clientData")).isFalse();
    assertThat(body.get("metadata").has("signature")).isFalse();
  }

  @Test
  @DisplayName("Stored WebAuthn replay delegates to evaluation and remains sanitized")
  void storedReplayDelegatesToEvaluation() throws Exception {
    WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name("fido2-packed-es256")
            .relyingPartyId(fixture.storedCredential().relyingPartyId())
            .credentialId(fixture.storedCredential().credentialId())
            .publicKeyCose(fixture.storedCredential().publicKeyCose())
            .signatureCounter(fixture.storedCredential().signatureCounter())
            .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
            .algorithm(WebAuthnSignatureAlgorithm.ES256)
            .build();

    Credential credential =
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
    credentialStore.save(credential);

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/replay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credentialId": "fido2-packed-es256",
                          "relyingPartyId": "example.org",
                          "origin": "https://example.org",
                          "expectedType": "webauthn.get",
                          "expectedChallenge": "%s",
                          "clientData": "%s",
                          "authenticatorData": "%s",
                          "signature": "%s"
                        }
                        """
                            .formatted(
                                encode(fixture.request().expectedChallenge()),
                                encode(fixture.request().clientDataJson()),
                                encode(fixture.request().authenticatorData()),
                                encode(fixture.request().signature()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode body = MAPPER.readTree(response);
    assertThat(body.get("match").asBoolean()).isTrue();
    JsonNode metadata = body.get("metadata");
    assertThat(metadata.get("credentialSource").asText()).isEqualTo("stored");
    assertThat(metadata.get("credentialReference").asBoolean()).isTrue();
    assertThat(metadata.has("challenge")).isFalse();
    assertThat(metadata.has("clientData")).isFalse();
    assertThat(metadata.has("signature")).isFalse();
  }

  private static String encode(byte[] value) {
    return URL_ENCODER.encodeToString(value);
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
      store.clear();
    }
  }
}
