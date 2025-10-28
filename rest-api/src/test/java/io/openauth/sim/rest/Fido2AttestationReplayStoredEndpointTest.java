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
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationCommand;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
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
class Fido2AttestationReplayStoredEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CredentialStore credentialStore;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("openauth.sim.persistence.database-path", () -> "in-memory-replay");
    }

    @BeforeEach
    void resetStore() {
        if (credentialStore instanceof InMemoryCredentialStore inMemory) {
            inMemory.reset();
        }
    }

    @Test
    @DisplayName("Stored attestation replay rehydrates persisted payloads")
    void replayStoredAttestationVerifiesPayload() throws Exception {
        StoredReplaySeed seed = saveStoredReplaySeed("stored-packed-es256");

        String response = mockMvc.perform(post("/api/v1/webauthn/attest/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "inputSource": "STORED",
                          "credentialId": "%s",
                          "format": "%s",
                          "verbose": true
                        }
                        """.formatted(
                                        seed.credentialName(), seed.format().label())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("status").asText()).isEqualTo("success");

        JsonNode metadata = root.get("metadata");
        assertThat(metadata.get("inputSource").asText()).isEqualTo("stored");
        assertThat(metadata.get("storedCredentialId").asText()).isEqualTo(seed.credentialName());

        JsonNode attested = root.get("attestedCredential");
        assertThat(attested.get("credentialId").asText()).isEqualTo(seed.expectedCredentialId());
        assertThat(attested.get("algorithm").asText())
                .isEqualTo(seed.algorithm().name().toLowerCase());
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private StoredReplaySeed saveStoredReplaySeed(String credentialName) throws Exception {
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
        GenerationResult generation = generator.generate(new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
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
                .certificateChainPem(generation.certificateChainPem())
                .customRootCertificatesPem(List.of())
                .build();

        VersionedCredentialRecord serialized =
                new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter().serializeAttestation(descriptor);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
        attributes.put("fido2.attestation.stored.attestationObject", encode(generation.attestationObject()));
        attributes.put("fido2.attestation.stored.clientDataJson", encode(generation.clientDataJson()));
        attributes.put(
                "fido2.attestation.stored.expectedChallenge",
                encode(vector.registration().challenge()));

        VersionedCredentialRecord enriched = new VersionedCredentialRecord(
                serialized.schemaVersion(),
                serialized.name(),
                serialized.type(),
                serialized.secret(),
                serialized.createdAt(),
                serialized.updatedAt(),
                attributes);

        Credential credential = VersionedCredentialRecordMapper.toCredential(enriched);
        credentialStore.save(credential);

        return new StoredReplaySeed(
                credentialName,
                vector.format(),
                encode(fixture.storedCredential().credentialId()),
                fixture.algorithm());
    }

    private static final class StoredReplaySeed {
        private final String credentialName;
        private final WebAuthnAttestationFormat format;
        private final String expectedCredentialId;
        private final WebAuthnSignatureAlgorithm algorithm;

        private StoredReplaySeed(
                String credentialName,
                WebAuthnAttestationFormat format,
                String expectedCredentialId,
                WebAuthnSignatureAlgorithm algorithm) {
            this.credentialName = credentialName;
            this.format = format;
            this.expectedCredentialId = expectedCredentialId;
            this.algorithm = algorithm;
        }

        private String credentialName() {
            return credentialName;
        }

        private WebAuthnAttestationFormat format() {
            return format;
        }

        private String expectedCredentialId() {
            return expectedCredentialId;
        }

        private WebAuthnSignatureAlgorithm algorithm() {
            return algorithm;
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
            // no-op
        }
    }
}
