package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
class Fido2AttestationStoredEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

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
    @DisplayName("Stored attestation generation returns deterministic payload and telemetry")
    void storedAttestationGeneratesPayload() throws Exception {
        StoredAttestationSeed seed = savePackedSeed("stored-packed-es256");

        String response = mockMvc.perform(post("/api/v1/webauthn/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "inputSource": "STORED",
                          "credentialId": "%s",
                          "format": "%s",
                          "relyingPartyId": "%s",
                          "origin": "%s",
                          "challenge": "%s"
                        }
                        """.formatted(
                                        seed.credentialName(),
                                        seed.format().label(),
                                        seed.relyingPartyId(),
                                        seed.origin(),
                                        seed.challengeBase64())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("success");

        JsonNode attestation = root.get("generatedAttestation");
        assertThat(attestation.get("type").asText()).isEqualTo("public-key");
        JsonNode attestationResponse = attestation.get("response");
        assertThat(attestationResponse.get("attestationObject").asText()).isEqualTo(seed.expectedAttestationObject());
        assertThat(attestationResponse.get("clientDataJSON").asText()).isEqualTo(seed.expectedClientData());

        JsonNode metadata = root.get("metadata");
        assertThat(metadata.get("generationMode").asText()).isEqualTo("self_signed");
        assertThat(metadata.get("inputSource").asText()).isEqualTo("stored");
        assertThat(metadata.get("storedCredentialId").asText()).isEqualTo(seed.credentialName());
        JsonNode certificatePem = metadata.get("certificateChainPem");
        assertThat(certificatePem.size()).isEqualTo(seed.certificateChainPem().size());
        for (int index = 0; index < seed.certificateChainPem().size(); index++) {
            String expectedCert = seed.certificateChainPem().get(index).replaceAll("\\s+", "");
            String actualCert = certificatePem.get(index).asText().replaceAll("\\s+", "");
            assertThat(actualCert).isEqualTo(expectedCert);
        }
    }

    @Test
    @DisplayName("Stored attestation generation requires credential identifier")
    void storedAttestationRequiresCredentialId() throws Exception {
        savePackedSeed("stored-packed-es256");

        String response = mockMvc.perform(post("/api/v1/webauthn/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "inputSource": "STORED",
                          "format": "packed",
                          "challenge": "%s"
                        }
                        """.formatted(encode("stored-challenge".getBytes(StandardCharsets.UTF_8)))))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("invalid");
        assertThat(root.get("reasonCode").asText()).isEqualTo("credential_id_required");
    }

    @Test
    @DisplayName("Stored attestation generation fails when credential missing")
    void storedAttestationFailsWhenCredentialNotFound() throws Exception {
        String response = mockMvc.perform(post("/api/v1/webauthn/attest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "inputSource": "STORED",
                          "credentialId": "missing-attestation",
                          "format": "packed",
                          "challenge": "%s"
                        }
                        """.formatted(encode("stored-challenge".getBytes(StandardCharsets.UTF_8)))))
                .andExpect(status().isUnprocessableEntity())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("invalid");
        assertThat(root.get("reasonCode").asText()).isEqualTo("stored_credential_not_found");
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private StoredAttestationSeed savePackedSeed(String credentialName) throws Exception {
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing packed attestation vector"));
        WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(credentialName)
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(WebAuthnSignatureAlgorithm.ES256)
                .build();

        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        byte[] challenge = vector.registration().challenge();
        GenerationResult seedResult = generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                challenge,
                vector.keyMaterial().credentialPrivateKeyBase64Url(),
                vector.keyMaterial().attestationPrivateKeyBase64Url(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of()));

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name(credentialName)
                .format(vector.format())
                .signingMode(SigningMode.SELF_SIGNED)
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId(vector.relyingPartyId())
                .origin(vector.origin())
                .attestationId(vector.vectorId())
                .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                .attestationCertificateSerialBase64Url(vector.keyMaterial().attestationCertificateSerialBase64Url())
                .certificateChainPem(seedResult.certificateChainPem())
                .customRootCertificatesPem(List.of())
                .build();

        Credential credential = VersionedCredentialRecordMapper.toCredential(
                new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter().serializeAttestation(descriptor));
        credentialStore.save(credential);

        return new StoredAttestationSeed(
                credentialName,
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                challenge,
                encode(seedResult.attestationObject()),
                encode(seedResult.clientDataJson()),
                seedResult.certificateChainPem());
    }

    private record StoredAttestationSeed(
            String credentialName,
            WebAuthnAttestationFormat format,
            String relyingPartyId,
            String origin,
            byte[] challenge,
            String expectedAttestationObject,
            String expectedClientData,
            List<String> certificateChainPem) {

        String challengeBase64() {
            return encode(challenge);
        }
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
