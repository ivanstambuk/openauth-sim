package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
final class Fido2AttestationSeedEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<WebAuthnAttestationVector> CANONICAL_VECTORS = selectCanonicalVectors();

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
    @DisplayName("Seeds stored attestation credentials via REST endpoint")
    void seedsStoredAttestations() throws Exception {
        String response = mockMvc.perform(post("/api/v1/webauthn/attestations/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("addedCount").asInt()).isEqualTo(CANONICAL_VECTORS.size());
        JsonNode addedIds = root.get("addedCredentialIds");
        assertThat(addedIds).isNotNull();
        assertThat(addedIds.size()).isEqualTo(CANONICAL_VECTORS.size());

        List<String> persisted = credentialStore.findAll().stream()
                .map(Credential::name)
                .sorted()
                .toList();
        List<String> expected = CANONICAL_VECTORS.stream()
                .map(WebAuthnAttestationVector::vectorId)
                .sorted()
                .toList();
        assertThat(persisted).isEqualTo(expected);
    }

    @Test
    @DisplayName("Skips seeding when stored attestation credentials already exist")
    void skipsExistingStoredAttestations() throws Exception {
        for (WebAuthnAttestationVector vector : CANONICAL_VECTORS) {
            StoredAttestationSeed seed = StoredAttestationSeed.fromVector(vector);
            credentialStore.save(VersionedCredentialRecordMapper.toCredential(
                    new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter()
                            .serializeAttestation(seed.descriptor())));
        }

        String response = mockMvc.perform(post("/api/v1/webauthn/attestations/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("addedCount").asInt()).isZero();
        assertThat(root.get("addedCredentialIds").size()).isZero();
    }

    private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
        Set<WebAuthnAttestationFormat> formats = new LinkedHashSet<>();
        return WebAuthnAttestationSamples.vectors().stream()
                .filter(vector -> formats.add(vector.format()))
                .toList();
    }

    @TestConfiguration
    static class InMemoryStoreConfiguration {

        @Bean
        CredentialStore credentialStore() {
            return new InMemoryCredentialStore();
        }
    }

    static final class InMemoryCredentialStore implements CredentialStore {

        private final Map<String, Credential> store = new LinkedHashMap<>();

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

    private record StoredAttestationSeed(WebAuthnAttestationCredentialDescriptor descriptor) {

        static StoredAttestationSeed fromVector(WebAuthnAttestationVector vector) {
            WebAuthnFixture fixture = resolveFixture(vector);

            WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                    .name(vector.vectorId())
                    .relyingPartyId(fixture.storedCredential().relyingPartyId())
                    .credentialId(fixture.storedCredential().credentialId())
                    .publicKeyCose(fixture.storedCredential().publicKeyCose())
                    .signatureCounter(fixture.storedCredential().signatureCounter())
                    .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                    .algorithm(fixture.algorithm())
                    .build();

            WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
            GenerationResult generationResult =
                    generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Manual(
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
                    .name(vector.vectorId())
                    .format(vector.format())
                    .signingMode(SigningMode.SELF_SIGNED)
                    .credentialDescriptor(credentialDescriptor)
                    .relyingPartyId(vector.relyingPartyId())
                    .origin(vector.origin())
                    .attestationId(vector.vectorId())
                    .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                    .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                    .attestationCertificateSerialBase64Url(vector.keyMaterial().attestationCertificateSerialBase64Url())
                    .certificateChainPem(generationResult.certificateChainPem())
                    .customRootCertificatesPem(List.of())
                    .build();
            return new StoredAttestationSeed(descriptor);
        }

        private static WebAuthnFixture resolveFixture(WebAuthnAttestationVector vector) {
            return findFixture(vector)
                    .orElseThrow(() -> new IllegalStateException("Missing stored fixture for " + vector.vectorId()));
        }

        private static Optional<WebAuthnFixture> findFixture(WebAuthnAttestationVector vector) {
            Optional<WebAuthnFixture> exact = WebAuthnFixtures.w3cFixtures().stream()
                    .filter(candidate -> candidate.id().equals(vector.vectorId()))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
            if (vector.vectorId().startsWith("w3c-")) {
                String trimmed = vector.vectorId().substring(4);
                exact = WebAuthnFixtures.w3cFixtures().stream()
                        .filter(candidate -> candidate.id().equals(trimmed))
                        .findFirst();
                if (exact.isPresent()) {
                    return exact;
                }
            }
            return WebAuthnFixtures.w3cFixtures().stream()
                    .filter(candidate -> candidate.algorithm().equals(vector.algorithm()))
                    .findFirst();
        }
    }
}
