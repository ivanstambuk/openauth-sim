package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private static final String ATTR_ATTESTATION_ENABLED = "fido2.attestation.enabled";

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
        List<String> expectedNames = expectedSeededCredentialNames();
        int expectedCanonicalCount = expectedNames.size();
        assertThat(root.get("addedCount").asInt()).isEqualTo(expectedCanonicalCount);
        JsonNode addedIds = root.get("addedCredentialIds");
        assertThat(addedIds).isNotNull();
        List<String> added = new ArrayList<>();
        addedIds.forEach(node -> added.add(node.asText()));
        expectedNames = expectedNames.stream().sorted().toList();
        assertThat(added).containsExactlyInAnyOrderElementsOf(expectedNames);

        List<String> persisted = credentialStore.findAll().stream()
                .map(Credential::name)
                .sorted()
                .toList();
        assertThat(persisted).isEqualTo(expectedNames);
        assertThat(persisted).allMatch(name -> !name.startsWith("w3c-"));

        credentialStore.findAll().forEach(credential -> assertThat(credential.attributes())
                .containsEntry(ATTR_ATTESTATION_ENABLED, "true"));
    }

    @Test
    @DisplayName("Skips seeding when stored attestation credentials already exist")
    void skipsExistingStoredAttestations() throws Exception {
        mockMvc.perform(post("/api/v1/webauthn/attestations/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

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

        List<String> persisted = credentialStore.findAll().stream()
                .map(Credential::name)
                .sorted()
                .toList();
        List<String> expectedNames =
                expectedSeededCredentialNames().stream().sorted().toList();
        assertThat(persisted).isEqualTo(expectedNames);
    }

    @Test
    @DisplayName("Enriches canonical assertion credentials without creating duplicates")
    void updatesCanonicalAssertionCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/webauthn/credentials/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

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

        Map<String, Credential> credentialsByName = credentialStore.findAll().stream()
                .collect(Collectors.toMap(Credential::name, credential -> credential));

        List<String> expectedNames = WebAuthnGeneratorSamples.samples().stream()
                .map(WebAuthnGeneratorSamples.Sample::key)
                .toList();
        assertThat(credentialsByName.keySet()).containsExactlyInAnyOrderElementsOf(expectedNames);

        for (WebAuthnAttestationVector vector : CANONICAL_VECTORS) {
            String canonicalName = resolveSample(vector).key();
            Credential credential = credentialsByName.get(canonicalName);
            assertThat(credential).isNotNull();
            assertThat(credential.attributes())
                    .containsEntry(ATTR_ATTESTATION_ENABLED, "true")
                    .containsKey("fido2.attestation.stored.attestationObject")
                    .containsKey("fido2.attestation.stored.clientDataJson")
                    .containsKey("fido2.attestation.stored.expectedChallenge");
        }
    }

    @Test
    @DisplayName("Canonical attestation catalogue exposes synthetic packed PS256 vector")
    void canonicalCatalogueIncludesSyntheticPackedPs256() {
        boolean present =
                CANONICAL_VECTORS.stream().anyMatch(vector -> "synthetic-packed-ps256".equals(vector.vectorId()));

        assertThat(present)
                .as("synthetic-packed-ps256 should be part of canonical stored attestation seeds")
                .isTrue();
    }

    @Test
    @DisplayName("Seeds PS256 stored attestation challenge metadata")
    void seedsPs256StoredChallenge() throws Exception {
        WebAuthnAttestationVector ps256Vector = WebAuthnAttestationSamples.vectors().stream()
                .filter(vector -> "synthetic-packed-ps256".equals(vector.vectorId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected synthetic-packed-ps256 attestation vector"));
        String expectedCredentialName = resolveSample(ps256Vector).key();
        String expectedChallenge = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(ps256Vector.registration().challenge());

        mockMvc.perform(post("/api/v1/webauthn/attestations/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        Credential stored = credentialStore
                .findByName(expectedCredentialName)
                .orElseThrow(() -> new AssertionError("Expected PS256 stored credential to be present"));
        assertThat(stored.attributes()).containsEntry("fido2.attestation.stored.expectedChallenge", expectedChallenge);
    }

    private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
        Set<WebAuthnSignatureAlgorithm> algorithms = new LinkedHashSet<>();
        return WebAuthnAttestationSamples.vectors().stream()
                .filter(vector -> algorithms.add(vector.algorithm()))
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

    private static WebAuthnGeneratorSamples.Sample resolveSample(WebAuthnAttestationVector vector) {
        return WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> Arrays.equals(
                        sample.credentialId(), vector.registration().credentialId()))
                .findFirst()
                .or(() -> WebAuthnGeneratorSamples.samples().stream()
                        .filter(sample -> sample.algorithm() == vector.algorithm())
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException("Missing generator sample for " + vector.vectorId()));
    }

    private static List<String> expectedSeededCredentialNames() {
        Set<WebAuthnSignatureAlgorithm> algorithms = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (algorithms.add(vector.algorithm())) {
                names.add(resolveSample(vector).key());
            }
        }
        return List.copyOf(names);
    }
}
