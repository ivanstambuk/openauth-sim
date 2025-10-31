package io.openauth.sim.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
class Fido2AttestationStoredOperatorControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @DisplayName("Operator API exposes stored attestation metadata")
    void storedAttestationMetadataExposed() throws Exception {
        StoredAttestationSeed seed = savePackedSeed("stored-packed-es256");

        String response = mockMvc.perform(get("/api/v1/webauthn/attestations/{id}", seed.credentialName()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(response);
        assertThat(root.get("storedCredentialId").asText()).isEqualTo(seed.credentialName());
        assertThat(root.get("attestationId").asText()).isEqualTo(seed.attestationId());
        assertThat(root.get("format").asText()).isEqualTo(seed.format().label());
        assertThat(root.get("relyingPartyId").asText()).isEqualTo(seed.relyingPartyId());
        assertThat(root.get("origin").asText()).isEqualTo(seed.origin());
        assertThat(root.get("signingMode").asText()).isEqualTo("self_signed");
        assertThat(root.get("certificateChainPem").size())
                .isEqualTo(seed.certificateChainPem().size());
        JsonNode anchorSummaries = root.get("trustAnchorSummaries");
        assertThat(anchorSummaries).isNotNull();
        assertThat(anchorSummaries.isArray()).isTrue();
        assertThat(anchorSummaries)
                .anySatisfy(node -> assertThat(node.asText()).isEqualTo("W3C Packed ES256 Sample Authenticator"));
    }

    @Test
    @DisplayName("Operator API returns 404 for missing stored attestation")
    void storedAttestationMetadataNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/webauthn/attestations/{id}", "missing-attestation"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Operator API falls back to certificate subjects when metadata is unavailable")
    void storedAttestationMetadataSubjectFallback() throws Exception {
        StoredAttestationSeed seed = savePackedSeed("stored-packed-es256-fallback", "custom-packed-es256");

        String response = mockMvc.perform(get("/api/v1/webauthn/attestations/{id}", seed.credentialName()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = MAPPER.readTree(response);
        JsonNode summaries = root.get("trustAnchorSummaries");
        assertThat(summaries).isNotNull();
        assertThat(summaries.isArray()).isTrue();

        List<String> expectedSubjects = extractCertificateSubjects(seed.certificateChainPem());
        assertThat(expectedSubjects).isNotEmpty();
        assertThat(summaries).anySatisfy(node -> assertThat(expectedSubjects).contains(node.asText()));
    }

    private StoredAttestationSeed savePackedSeed(String credentialName) {
        return savePackedSeed(credentialName, "w3c-packed-es256");
    }

    private StoredAttestationSeed savePackedSeed(String credentialName, String attestationId) {
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
        GenerationResult seedResult = generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Manual(
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
                .attestationId(attestationId)
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
                attestationId,
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                seedResult.certificateChainPem());
    }

    private record StoredAttestationSeed(
            String credentialName,
            String attestationId,
            WebAuthnAttestationFormat format,
            String relyingPartyId,
            String origin,
            List<String> certificateChainPem) {
        // test seed DTO
    }

    private static List<String> extractCertificateSubjects(List<String> certificateChainPem)
            throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<String> subjects = new ArrayList<>();
        for (String pem : certificateChainPem) {
            if (pem == null || pem.isBlank()) {
                continue;
            }
            ByteArrayInputStream inputStream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
            Collection<? extends java.security.cert.Certificate> certificates =
                    factory.generateCertificates(inputStream);
            for (java.security.cert.Certificate certificate : certificates) {
                if (certificate instanceof X509Certificate x509) {
                    subjects.add(displayName(x509));
                }
            }
        }
        return subjects;
    }

    private static String displayName(X509Certificate certificate) {
        String subject = certificate.getSubjectX500Principal().getName();
        String commonName = extractCommonName(subject);
        return commonName != null && !commonName.isBlank() ? commonName : subject;
    }

    private static String extractCommonName(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            LdapName ldapName = new LdapName(subject);
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    Object value = rdn.getValue();
                    return value == null ? null : value.toString();
                }
            }
        } catch (InvalidNameException ignored) {
            // Fall back to the full subject string when parsing fails.
        }
        return null;
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
