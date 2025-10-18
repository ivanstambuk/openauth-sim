package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
class Fido2AttestationManualEndpointTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.database-path", () -> "unused");
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class CredentialStoreConfiguration {

    @org.springframework.context.annotation.Bean
    io.openauth.sim.core.store.CredentialStore credentialStore() {
      return new InMemoryCredentialStore();
    }
  }

  private static final class InMemoryCredentialStore
      implements io.openauth.sim.core.store.CredentialStore {
    private final java.util.Map<String, io.openauth.sim.core.model.Credential> backing =
        new java.util.LinkedHashMap<>();

    @Override
    public void save(io.openauth.sim.core.model.Credential credential) {
      backing.put(credential.name(), credential);
    }

    @Override
    public java.util.Optional<io.openauth.sim.core.model.Credential> findByName(String name) {
      return java.util.Optional.ofNullable(backing.get(name));
    }

    @Override
    public java.util.List<io.openauth.sim.core.model.Credential> findAll() {
      return java.util.List.copyOf(backing.values());
    }

    @Override
    public boolean delete(String name) {
      return backing.remove(name) != null;
    }

    @Override
    public void close() {
      backing.clear();
    }
  }

  @Test
  @DisplayName("Manual attestation generation (unsigned) succeeds and emits metadata")
  void manualUnsignedAttestationWorks() throws Exception {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("inputSource", "MANUAL");
    root.put("format", WebAuthnAttestationFormat.PACKED.label());
    root.put("relyingPartyId", "example.org");
    root.put("origin", "https://example.org");
    root.put("challenge", "dGVzdC1tYW51YWwtY2hhbGxlbmdl");
    root.put("credentialPrivateKey", "cHJpdmF0ZS1rZXktY3JlZC");
    root.put("signingMode", "UNSIGNED");

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/attest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MAPPER.writeValueAsString(root)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode json = MAPPER.readTree(response);
    assertThat(json.get("status").asText()).isEqualTo("success");
    JsonNode attestation = json.path("generatedAttestation");
    assertThat(attestation.path("type").asText()).isEqualTo("public-key");
    assertThat(attestation.path("response").path("attestationObject").asText()).isNotBlank();
    assertThat(json.path("metadata").path("attestationFormat").asText()).isEqualTo("packed");
    assertThat(json.path("metadata").path("generationMode").asText()).isEqualTo("unsigned");
    assertThat(json.path("metadata").path("inputSource").asText()).isEqualTo("manual");
  }

  @Test
  @DisplayName("Manual attestation generation (custom-root) includes chain and telemetry")
  void manualCustomRootAttestationWorks() throws Exception {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("inputSource", "MANUAL");
    root.put("format", WebAuthnAttestationFormat.PACKED.label());
    root.put("relyingPartyId", "example.org");
    root.put("origin", "https://example.org");
    root.put("challenge", "dGVzdC1tYW51YWwtY2hhbGxlbmdl");
    root.put("credentialPrivateKey", "cHJpdmF0ZS1rZXktY3JlZC");
    root.put("attestationPrivateKey", "YXR0ZXN0LXBriy10ZXN0");
    root.put("attestationCertificateSerial", "c2VyaWFsLXRlc3Q");
    root.put("signingMode", "CUSTOM_ROOT");
    root.putArray("customRootCertificates")
        .add("-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----\n");

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/attest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MAPPER.writeValueAsString(root)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode json = MAPPER.readTree(response);
    assertThat(json.get("status").asText()).isEqualTo("success");
    assertThat(json.path("metadata").path("generationMode").asText()).isEqualTo("custom_root");
    assertThat(json.path("metadata").path("customRootCount").asInt()).isEqualTo(1);
    assertThat(json.path("metadata").path("inputSource").asText()).isEqualTo("manual");
  }
}
