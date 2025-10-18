package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import java.util.List;
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
class Fido2AttestationEndpointTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Encoder MIME_ENCODER = Base64.getMimeEncoder(64, new byte[] {'\n'});

  private static final WebAuthnAttestationVector VECTOR =
      WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Missing packed attestation vector"));

  private static final List<X509Certificate> CERTIFICATE_CHAIN = vectorCertificateChain(VECTOR);
  private static final String TRUST_ANCHOR_PEM = resolveTrustAnchorPem();
  private static final String EXPECTED_ATTESTATION =
      encode(VECTOR.registration().attestationObject());
  private static final String EXPECTED_CLIENT_DATA = encode(VECTOR.registration().clientDataJson());
  private static final String EXPECTED_CHALLENGE = encode(VECTOR.registration().challenge());
  private static final List<String> EXPECTED_CERTIFICATE_CHAIN_PEM =
      CERTIFICATE_CHAIN.stream().map(Fido2AttestationEndpointTest::toPemUnchecked).toList();
  private static final String EXPECTED_CREDENTIAL_ID = encode(VECTOR.registration().credentialId());

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("openauth.sim.persistence.database-path", () -> "unused");
  }

  @Test
  @DisplayName("Attestation endpoint generates self-signed payloads matching fixtures")
  void generateSelfSignedAttestationMatchesFixture() throws Exception {
    String payload = generationPayload("SELF_SIGNED", false);

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/attest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode root = MAPPER.readTree(response);
    assertThat(root.get("status").asText()).isEqualTo("success");

    JsonNode attestation = root.get("generatedAttestation");
    assertThat(attestation.get("type").asText()).isEqualTo("public-key");
    assertThat(attestation.get("id").asText()).isEqualTo(EXPECTED_CREDENTIAL_ID);
    assertThat(attestation.get("rawId").asText()).isEqualTo(EXPECTED_CREDENTIAL_ID);
    assertThat(attestation.has("attestationId")).isFalse();
    assertThat(attestation.get("format").asText()).isEqualTo(VECTOR.format().label());
    JsonNode responsePayload = attestation.get("response");
    assertThat(responsePayload.get("attestationObject").asText()).isEqualTo(EXPECTED_ATTESTATION);
    assertThat(responsePayload.get("clientDataJSON").asText()).isEqualTo(EXPECTED_CLIENT_DATA);
    assertThat(responsePayload.has("expectedChallenge")).isFalse();
    assertThat(responsePayload.has("signatureIncluded")).isFalse();
    assertThat(responsePayload.has("certificateChain")).isFalse();

    JsonNode metadata = root.get("metadata");
    assertThat(metadata.get("telemetryId").asText()).isNotBlank();
    assertThat(metadata.get("reasonCode").asText()).isEqualTo("generated");
    assertThat(metadata.get("attestationFormat").asText()).isEqualTo(VECTOR.format().label());
    assertThat(metadata.get("generationMode").asText()).isEqualTo("self_signed");
    assertThat(metadata.get("signatureIncluded").asBoolean()).isTrue();
    assertThat(metadata.get("customRootCount").asInt()).isZero();
    JsonNode certificateChainPem = metadata.get("certificateChainPem");
    assertThat(certificateChainPem).isNotNull();
    assertThat(certificateChainPem.isArray()).isTrue();
    assertThat(certificateChainPem.size()).isEqualTo(EXPECTED_CERTIFICATE_CHAIN_PEM.size());
    for (int index = 0; index < EXPECTED_CERTIFICATE_CHAIN_PEM.size(); index++) {
      assertThat(certificateChainPem.get(index).asText())
          .isEqualTo(EXPECTED_CERTIFICATE_CHAIN_PEM.get(index));
    }
  }

  @Test
  @DisplayName("Attestation endpoint can emit unsigned payloads")
  void generateUnsignedAttestationOmitsSignature() throws Exception {
    String payload = generationPayload("UNSIGNED", false);

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/attest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode root = MAPPER.readTree(response);
    assertThat(root.get("status").asText()).isEqualTo("success");

    JsonNode attestation = root.get("generatedAttestation");
    JsonNode responsePayload = attestation.get("response");
    assertThat(responsePayload.has("signatureIncluded")).isFalse();
    assertThat(responsePayload.has("certificateChain")).isFalse();

    JsonNode metadata = root.get("metadata");
    assertThat(metadata.get("generationMode").asText()).isEqualTo("unsigned");
    assertThat(metadata.get("signatureIncluded").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Attestation endpoint signs payloads with custom root certificates")
  void generateCustomRootAttestationUsesProvidedCertificate() throws Exception {
    if (TRUST_ANCHOR_PEM.isBlank()) {
      throw new IllegalStateException("Packed attestation fixture missing root certificate");
    }

    String payload = generationPayload("CUSTOM_ROOT", true);

    String response =
        mockMvc
            .perform(
                post("/api/v1/webauthn/attest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    JsonNode root = MAPPER.readTree(response);
    assertThat(root.get("status").asText()).isEqualTo("success");

    JsonNode attestation = root.get("generatedAttestation");
    assertThat(attestation.get("id").asText()).isEqualTo(EXPECTED_CREDENTIAL_ID);
    assertThat(attestation.get("rawId").asText()).isEqualTo(EXPECTED_CREDENTIAL_ID);
    JsonNode responsePayload = attestation.get("response");
    assertThat(responsePayload.get("attestationObject").asText()).isNotBlank();
    assertThat(responsePayload.get("clientDataJSON").asText()).isEqualTo(EXPECTED_CLIENT_DATA);

    JsonNode metadata = root.get("metadata");
    assertThat(metadata.get("generationMode").asText()).isEqualTo("custom_root");
    assertThat(metadata.get("customRootCount").asInt()).isEqualTo(1);
    assertThat(metadata.get("customRootSource").asText()).isEqualTo("inline");
    assertThat(metadata.get("certificateChainCount").asInt()).isGreaterThanOrEqualTo(1);
    assertThat(metadata.get("signatureIncluded").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("Attestation endpoint rejects legacy Base64 private-key inputs")
  void generateRejectsLegacyBase64PrivateKeys() throws Exception {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("attestationId", VECTOR.vectorId());
    root.put("format", VECTOR.format().label());
    root.put("relyingPartyId", VECTOR.relyingPartyId());
    root.put("origin", VECTOR.origin());
    root.put("challenge", EXPECTED_CHALLENGE);
    root.put("credentialPrivateKey", VECTOR.keyMaterial().credentialPrivateKeyBase64Url());
    root.put("attestationPrivateKey", VECTOR.keyMaterial().attestationPrivateKeyBase64Url());
    root.put(
        "attestationCertificateSerial",
        VECTOR.keyMaterial().attestationCertificateSerialBase64Url());
    root.put("signingMode", "SELF_SIGNED");

    mockMvc
        .perform(
            post("/api/v1/webauthn/attest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(root.toString()))
        .andExpect(status().isUnprocessableEntity());
  }

  private static String generationPayload(String signingMode, boolean includeCustomRoot)
      throws Exception {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("attestationId", VECTOR.vectorId());
    root.put("format", VECTOR.format().label());
    root.put("relyingPartyId", VECTOR.relyingPartyId());
    root.put("origin", VECTOR.origin());
    root.put("challenge", EXPECTED_CHALLENGE);
    root.put(
        "credentialPrivateKey",
        toPemPrivateKey(VECTOR.algorithm(), VECTOR.keyMaterial().credentialPrivateKeyBase64Url()));
    root.put(
        "attestationPrivateKey",
        toPemPrivateKey(VECTOR.algorithm(), VECTOR.keyMaterial().attestationPrivateKeyBase64Url()));
    root.put(
        "attestationCertificateSerial",
        VECTOR.keyMaterial().attestationCertificateSerialBase64Url());
    root.put("signingMode", signingMode);

    if (includeCustomRoot && !TRUST_ANCHOR_PEM.isBlank()) {
      root.putArray("customRootCertificates").add(TRUST_ANCHOR_PEM);
    }

    return MAPPER.writeValueAsString(root);
  }

  private static String toPemPrivateKey(WebAuthnSignatureAlgorithm algorithm, String base64UrlKey)
      throws GeneralSecurityException {
    if (base64UrlKey == null || base64UrlKey.isBlank()) {
      return "";
    }
    byte[] scalar = Base64.getUrlDecoder().decode(base64UrlKey);
    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
    parameters.init(new ECGenParameterSpec(curveFor(algorithm)));
    ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
    KeyFactory factory = KeyFactory.getInstance("EC");
    ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(new BigInteger(1, scalar), spec);
    PrivateKey privateKey = factory.generatePrivate(privateKeySpec);
    String body = MIME_ENCODER.encodeToString(privateKey.getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
  }

  private static String curveFor(WebAuthnSignatureAlgorithm algorithm) {
    return switch (algorithm) {
      case ES256 -> "secp256r1";
      case ES384 -> "secp384r1";
      case ES512 -> "secp521r1";
      default ->
          throw new IllegalArgumentException(
              "PEM conversion supported only for EC algorithms; found " + algorithm);
    };
  }

  private static List<X509Certificate> vectorCertificateChain(WebAuthnAttestationVector vector) {
    return new WebAuthnAttestationVerifier()
        .verify(
            new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin()))
        .certificateChain();
  }

  private static String encode(byte[] value) {
    return URL_ENCODER.encodeToString(value);
  }

  private static String toPem(X509Certificate certificate) throws CertificateEncodingException {
    String encoded = MIME_ENCODER.encodeToString(certificate.getEncoded());
    return "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n";
  }

  private static String resolveTrustAnchorPem() {
    if (CERTIFICATE_CHAIN.isEmpty()) {
      return "";
    }
    try {
      return toPem(CERTIFICATE_CHAIN.get(CERTIFICATE_CHAIN.size() - 1));
    } catch (CertificateEncodingException ex) {
      throw new IllegalStateException("Unable to encode trust anchor", ex);
    }
  }

  private static String toPemUnchecked(X509Certificate certificate) {
    try {
      return toPem(certificate);
    } catch (CertificateEncodingException ex) {
      throw new IllegalStateException("Unable to encode certificate", ex);
    }
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
}
