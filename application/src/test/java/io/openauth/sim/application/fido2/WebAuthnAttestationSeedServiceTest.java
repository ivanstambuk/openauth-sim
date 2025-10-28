package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationResult;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationSeedServiceTest {

    private static final List<WebAuthnAttestationVector> CANONICAL_VECTORS = selectCanonicalVectors();
    private static final String ATTR_ATTESTATION_ENABLED = "fido2.attestation.enabled";
    private static final String ATTR_STORED_ATTESTATION_OBJECT = "fido2.attestation.stored.attestationObject";
    private static final String ATTR_STORED_CLIENT_DATA = "fido2.attestation.stored.clientDataJson";
    private static final String ATTR_STORED_CHALLENGE = "fido2.attestation.stored.expectedChallenge";
    private static final String ATTR_METADATA_LABEL = WebAuthnCredentialPersistenceAdapter.ATTR_METADATA_LABEL;

    private final CapturingCredentialStore credentialStore = new CapturingCredentialStore();
    private final WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();

    @BeforeEach
    void resetStore() {
        credentialStore.reset();
    }

    @Test
    @DisplayName("Seeds curated WebAuthn attestation credentials when missing")
    void seedsCuratedAttestationCredentials() {
        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService();
        List<SeedCommand> commands =
                CANONICAL_VECTORS.stream().map(this::toSeedCommand).toList();

        SeedResult result = service.seed(commands, credentialStore);

        List<String> expectedIds = canonicalCredentialNames();
        assertEquals(expectedIds, result.addedCredentialIds().stream().sorted().toList());
        assertEquals(expectedIds.size(), result.addedCount());

        List<Credential> persisted = credentialStore.findAll();
        assertEquals(expectedIds.size(), persisted.size());

        Map<String, WebAuthnAttestationCredentialDescriptor> descriptors = persisted.stream()
                .collect(Collectors.toMap(
                        Credential::name,
                        credential -> deserialize(VersionedCredentialRecordMapper.toRecord(credential))));

        Map<String, SeedCommand> latestCommandByCanonical = new LinkedHashMap<>();
        for (int index = 0; index < CANONICAL_VECTORS.size(); index++) {
            WebAuthnAttestationVector vector = CANONICAL_VECTORS.get(index);
            String canonicalName = resolveSampleFor(vector).sample().key();
            latestCommandByCanonical.put(canonicalName, commands.get(index));
        }

        for (Map.Entry<String, SeedCommand> entry : latestCommandByCanonical.entrySet()) {
            String canonicalName = entry.getKey();
            SeedCommand command = entry.getValue();
            WebAuthnAttestationCredentialDescriptor stored = descriptors.get(canonicalName);
            assertEquals(command.descriptor().attestationId(), stored.attestationId());
            assertEquals(command.descriptor().format(), stored.format());
            assertEquals(command.descriptor().signingMode(), stored.signingMode());
            assertTrue(stored.certificateChainPem().size()
                    >= command.descriptor().certificateChainPem().size());
            Credential credential = persisted.stream()
                    .filter(item -> item.name().equals(canonicalName))
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    expectedLabelFor(stored),
                    credential.attributes().get(WebAuthnCredentialPersistenceAdapter.ATTR_METADATA_LABEL));
        }

        List<Operation> expectedOperations = CANONICAL_VECTORS.stream()
                .map(vector -> Operation.save(resolveSampleFor(vector).sample().key(), CredentialType.FIDO2))
                .toList();
        assertIterableEquals(expectedOperations, credentialStore.operations());
    }

    @Test
    @DisplayName("Skips attestation credentials when curated identifiers already exist")
    void skipsExistingAttestationCredentials() {
        List<SeedCommand> commands =
                CANONICAL_VECTORS.stream().map(this::toSeedCommand).toList();

        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService();
        service.seed(commands, credentialStore);
        credentialStore.resetOperations();

        SeedResult result = service.seed(commands, credentialStore);

        assertEquals(0, result.addedCount());
        assertTrue(result.addedCredentialIds().isEmpty());

        List<Operation> expectedOperations = CANONICAL_VECTORS.stream()
                .map(vector -> Operation.save(resolveSampleFor(vector).sample().key(), CredentialType.FIDO2))
                .toList();
        assertIterableEquals(expectedOperations, credentialStore.operations());
    }

    @Test
    @DisplayName("Updates existing assertion credential with stored attestation metadata")
    void updatesExistingAssertionCredential() {
        WebAuthnAttestationVector vector = CANONICAL_VECTORS.get(0);
        SampleMapping mapping = resolveSampleFor(vector);

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(mapping.sample().key())
                .relyingPartyId(mapping.sample().relyingPartyId())
                .credentialId(mapping.sample().credentialId())
                .publicKeyCose(mapping.sample().publicKeyCose())
                .signatureCounter(mapping.sample().signatureCounter())
                .userVerificationRequired(mapping.sample().userVerificationRequired())
                .algorithm(mapping.sample().algorithm())
                .build();

        Credential assertionCredential =
                VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(credentialDescriptor));
        SecretMaterial originalSecret = assertionCredential.secret();
        credentialStore.save(assertionCredential);
        credentialStore.resetOperations();

        SeedCommand command = toSeedCommand(vector);
        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService(persistenceAdapter);

        SeedResult result = service.seed(List.of(command), credentialStore);

        assertEquals(0, result.addedCount());
        assertTrue(result.addedCredentialIds().isEmpty(), "expected no new credential identifiers");

        List<Credential> persisted = credentialStore.findAll();
        assertEquals(1, persisted.size(), "expected only the original credential to remain");

        Credential enriched = persisted.get(0);
        assertEquals(mapping.sample().key(), enriched.name(), "expected existing credential name to be reused");
        assertArrayEquals(
                originalSecret.value(),
                enriched.secret().value(),
                "expected existing credential secret (credential id) to be preserved");

        List<Operation> operations = credentialStore.operations();
        assertEquals(1, operations.size(), "expected a single save operation");
        assertEquals(
                mapping.sample().key(),
                operations.get(0).name(),
                "expected seeding to save the existing credential identifier");

        VersionedCredentialRecord record = VersionedCredentialRecordMapper.toRecord(enriched);
        WebAuthnAttestationCredentialDescriptor storedDescriptor = persistenceAdapter.deserializeAttestation(record);
        assertEquals(command.descriptor().format(), storedDescriptor.format());
        assertEquals(command.descriptor().signingMode(), storedDescriptor.signingMode());
        assertEquals(command.descriptor().attestationId(), storedDescriptor.attestationId());
        assertEquals(command.descriptor().origin(), storedDescriptor.origin());
        assertTrue(
                Arrays.equals(
                        command.descriptor().credentialDescriptor().credentialId(),
                        storedDescriptor.credentialDescriptor().credentialId()),
                "expected credential IDs to match");

        Map<String, String> attributes = enriched.attributes();
        assertEquals("true", attributes.get(ATTR_ATTESTATION_ENABLED), "attestation flag should be enabled");
        assertEquals(encode(command.attestationObject()), attributes.get(ATTR_STORED_ATTESTATION_OBJECT));
        assertEquals(encode(command.clientDataJson()), attributes.get(ATTR_STORED_CLIENT_DATA));
        assertEquals(encode(command.expectedChallenge()), attributes.get(ATTR_STORED_CHALLENGE));
    }

    @Test
    @DisplayName("Enriches PS256 stored credential metadata when seeded")
    void enrichesPs256StoredCredentialMetadata() {
        WebAuthnAttestationVector ps256Vector = WebAuthnAttestationSamples.vectors().stream()
                .filter(vector -> "synthetic-packed-ps256".equals(vector.vectorId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected synthetic-packed-ps256 vector to be available"));
        SampleMapping mapping = resolveSampleFor(ps256Vector);

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(mapping.sample().key())
                .relyingPartyId(mapping.sample().relyingPartyId())
                .credentialId(mapping.sample().credentialId())
                .publicKeyCose(mapping.sample().publicKeyCose())
                .signatureCounter(mapping.sample().signatureCounter())
                .userVerificationRequired(mapping.sample().userVerificationRequired())
                .algorithm(mapping.sample().algorithm())
                .build();

        Credential existing =
                VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(credentialDescriptor));
        credentialStore.save(existing);

        SeedCommand command = toSeedCommand(ps256Vector);
        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService(persistenceAdapter);

        SeedResult result = service.seed(List.of(command), credentialStore);
        assertEquals(0, result.addedCount());
        assertTrue(result.addedCredentialIds().isEmpty(), "expected no new credential identifiers");

        Credential stored = credentialStore
                .findByName(mapping.sample().key())
                .orElseThrow(() -> new AssertionError("Expected enriched credential to remain present"));
        VersionedCredentialRecord record = VersionedCredentialRecordMapper.toRecord(stored);
        WebAuthnAttestationCredentialDescriptor descriptor = persistenceAdapter.deserializeAttestation(record);
        assertEquals("synthetic-packed-ps256", descriptor.attestationId());
        assertEquals(WebAuthnAttestationFormat.PACKED, descriptor.format());
        assertEquals(
                WebAuthnSignatureAlgorithm.PS256,
                descriptor.credentialDescriptor().algorithm());

        Map<String, String> attributes = stored.attributes();
        assertEquals("true", attributes.get(ATTR_ATTESTATION_ENABLED));
        assertEquals(encode(command.attestationObject()), attributes.get(ATTR_STORED_ATTESTATION_OBJECT));
        assertEquals(encode(command.clientDataJson()), attributes.get(ATTR_STORED_CLIENT_DATA));
        assertEquals(encode(command.expectedChallenge()), attributes.get(ATTR_STORED_CHALLENGE));
    }

    @Test
    @DisplayName("Includes synthetic packed PS256 attestation in canonical seed catalogue")
    void includesSyntheticPackedPs256Attestation() {
        boolean present =
                CANONICAL_VECTORS.stream().anyMatch(vector -> "synthetic-packed-ps256".equals(vector.vectorId()));

        assertTrue(
                present,
                "expected canonical attestation vectors to include synthetic-packed-ps256 so PS256 credentials seed automatically");
    }

    @Test
    @DisplayName("Falls back to algorithm mapping when credential id is unknown")
    void resolvesCanonicalNameByAlgorithm() {
        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name("unmapped-attestation")
                .relyingPartyId("example.org")
                .credentialId(new byte[] {0x01})
                .publicKeyCose(new byte[] {0x02})
                .signatureCounter(0)
                .userVerificationRequired(false)
                .algorithm(WebAuthnSignatureAlgorithm.ES256)
                .build();

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name("unmapped-attestation")
                .format(WebAuthnAttestationFormat.PACKED)
                .signingMode(SigningMode.SELF_SIGNED)
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId("example.org")
                .origin("https://example.org")
                .attestationId("unmapped-attestation")
                .credentialPrivateKeyBase64Url("AQ")
                .attestationPrivateKeyBase64Url(null)
                .attestationCertificateSerialBase64Url("AQ")
                .certificateChainPem(List.of())
                .customRootCertificatesPem(List.of())
                .build();

        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService(persistenceAdapter);
        SeedCommand command = new SeedCommand(descriptor, new byte[] {0x03}, new byte[] {0x04}, new byte[] {0x05});

        SeedResult result = service.seed(List.of(command), credentialStore);
        String expectedName = WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> sample.algorithm() == WebAuthnSignatureAlgorithm.ES256)
                .map(WebAuthnGeneratorSamples.Sample::key)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(expectedName), result.addedCredentialIds());
        assertEquals(1, credentialStore.findAll().size());
    }

    @Test
    @DisplayName("Persists canonical attestation metadata labels including format and W3C section")
    void persistsCanonicalMetadataLabels() {
        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService();
        List<SeedCommand> commands =
                CANONICAL_VECTORS.stream().map(this::toSeedCommand).toList();

        service.seed(commands, credentialStore);

        Map<String, Credential> persisted = credentialStore.findAll().stream()
                .collect(Collectors.toMap(Credential::name, credential -> credential));

        for (Credential credential : persisted.values()) {
            VersionedCredentialRecord record = VersionedCredentialRecordMapper.toRecord(credential);
            WebAuthnAttestationCredentialDescriptor descriptor = persistenceAdapter.deserializeAttestation(record);
            String label = credential.attributes().get(ATTR_METADATA_LABEL);
            assertEquals(expectedLabelFor(descriptor), label, "expected metadata label for " + credential.name());
        }
    }

    @Test
    @DisplayName("Falls back to format and origin when attestation metadata is missing")
    void resolvesLabelUsingDescriptorOrigin() {
        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name("custom-attestation")
                .relyingPartyId("example.org")
                .credentialId(new byte[] {0x01})
                .publicKeyCose(new byte[] {0x02})
                .signatureCounter(0)
                .userVerificationRequired(false)
                .algorithm(WebAuthnSignatureAlgorithm.ES256)
                .build();

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name("custom-attestation")
                .format(WebAuthnAttestationFormat.PACKED)
                .signingMode(SigningMode.SELF_SIGNED)
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId("example.org")
                .origin("https://synthetic.example")
                .attestationId("synthetic-packed-es256")
                .credentialPrivateKeyBase64Url("AQ")
                .attestationPrivateKeyBase64Url("AQ")
                .attestationCertificateSerialBase64Url("AQ")
                .certificateChainPem(List.of())
                .customRootCertificatesPem(List.of())
                .build();

        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService();
        SeedCommand command = new SeedCommand(descriptor, new byte[] {0x03}, new byte[] {0x04}, new byte[] {0x05});

        CapturingCredentialStore store = new CapturingCredentialStore();
        service.seed(List.of(command), store);

        Credential stored = store.findAll().get(0);
        assertEquals(
                "ES256 (packed, https://synthetic.example)", stored.attributes().get(ATTR_METADATA_LABEL));
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static SampleMapping resolveSampleFor(WebAuthnAttestationVector vector) {
        return WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> Arrays.equals(
                        sample.credentialId(), vector.registration().credentialId()))
                .findFirst()
                .or(() -> WebAuthnGeneratorSamples.samples().stream()
                        .filter(sample -> sample.algorithm() == vector.algorithm())
                        .findFirst())
                .map(sample -> new SampleMapping(vector, sample))
                .orElseThrow(
                        () -> new IllegalStateException("Unable to resolve generator sample for " + vector.vectorId()));
    }

    private static List<String> canonicalCredentialNames() {
        return CANONICAL_VECTORS.stream()
                .map(WebAuthnAttestationSeedServiceTest::resolveSampleFor)
                .map(mapping -> mapping.sample().key())
                .distinct()
                .sorted()
                .toList();
    }

    private record SampleMapping(WebAuthnAttestationVector vector, WebAuthnGeneratorSamples.Sample sample) {
        // marker
    }

    private SeedCommand toSeedCommand(WebAuthnAttestationVector vector) {
        Optional<WebAuthnFixture> fixture = findFixture(vector.vectorId());
        WebAuthnCredentialDescriptor credentialDescriptor;
        if (fixture.isPresent()) {
            WebAuthnFixture resolved = fixture.get();
            credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                    .name(vector.vectorId())
                    .relyingPartyId(resolved.storedCredential().relyingPartyId())
                    .credentialId(resolved.storedCredential().credentialId())
                    .publicKeyCose(resolved.storedCredential().publicKeyCose())
                    .signatureCounter(resolved.storedCredential().signatureCounter())
                    .userVerificationRequired(resolved.storedCredential().userVerificationRequired())
                    .algorithm(resolved.algorithm())
                    .build();
        } else {
            WebAuthnGeneratorSamples.Sample sample = resolveSampleFor(vector).sample();
            credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                    .name(vector.vectorId())
                    .relyingPartyId(sample.relyingPartyId())
                    .credentialId(sample.credentialId())
                    .publicKeyCose(sample.publicKeyCose())
                    .signatureCounter(sample.signatureCounter())
                    .userVerificationRequired(sample.userVerificationRequired())
                    .algorithm(sample.algorithm())
                    .build();
        }

        GenerationResult generationResult =
                generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
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

        return new SeedCommand(
                descriptor,
                generationResult.attestationObject(),
                generationResult.clientDataJson(),
                vector.registration().challenge());
    }

    private static String expectedLabelFor(WebAuthnAttestationCredentialDescriptor descriptor) {
        String algorithmLabel = descriptor.credentialDescriptor().algorithm() == null
                ? ""
                : descriptor.credentialDescriptor().algorithm().label();
        String formatLabel =
                descriptor.format() == null ? "" : descriptor.format().label();
        String section = "";
        String origin = descriptor.origin() == null ? "" : descriptor.origin().trim();
        String attestationId = descriptor.attestationId();
        if (attestationId != null && !attestationId.isBlank()) {
            try {
                WebAuthnAttestationVector vector = WebAuthnAttestationSamples.require(attestationId);
                section = vector.w3cSection() == null ? "" : vector.w3cSection().trim();
                if (origin.isBlank() && vector.origin() != null) {
                    origin = vector.origin().trim();
                }
            } catch (IllegalArgumentException ignored) {
                // ignore missing vector metadata
            }
        }
        if (!algorithmLabel.isBlank() && !formatLabel.isBlank() && !section.isBlank()) {
            return algorithmLabel + " (" + formatLabel + ", W3C " + section + ")";
        }
        if (!algorithmLabel.isBlank() && !formatLabel.isBlank() && !origin.isBlank()) {
            return algorithmLabel + " (" + formatLabel + ", " + origin + ")";
        }
        if (!algorithmLabel.isBlank() && !formatLabel.isBlank()) {
            return algorithmLabel + " (" + formatLabel + ")";
        }
        if (!algorithmLabel.isBlank()) {
            return algorithmLabel;
        }
        return descriptor.name();
    }

    private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
        Set<WebAuthnSignatureAlgorithm> seen = new LinkedHashSet<>();
        List<WebAuthnAttestationVector> selected = new ArrayList<>();
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (seen.add(vector.algorithm())) {
                if (!isInlineCompatible(generator, vector)) {
                    seen.remove(vector.algorithm());
                    continue;
                }
                selected.add(vector);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("Attestation sample catalogue is empty");
        }
        return List.copyOf(selected);
    }

    private static boolean isInlineCompatible(
            WebAuthnAttestationGenerator generator, WebAuthnAttestationVector vector) {
        try {
            generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
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
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static Optional<WebAuthnFixture> findFixture(String vectorId) {
        return WebAuthnFixtures.w3cFixtures().stream()
                .filter(fixture -> fixture.id().equals(vectorId))
                .findFirst();
    }

    private WebAuthnAttestationCredentialDescriptor deserialize(VersionedCredentialRecord record) {
        return new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter().deserializeAttestation(record);
    }

    private static final class CapturingCredentialStore implements CredentialStore {

        private final Map<String, Credential> store = new LinkedHashMap<>();
        private final List<Operation> operations = new ArrayList<>();

        void reset() {
            store.clear();
            operations.clear();
        }

        void resetOperations() {
            operations.clear();
        }

        List<Operation> operations() {
            return List.copyOf(operations);
        }

        @Override
        public void save(Credential credential) {
            store.put(credential.name(), credential);
            operations.add(Operation.save(credential.name(), credential.type()));
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(store.get(name));
        }

        @Override
        public List<Credential> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public boolean delete(String name) {
            Credential removed = store.remove(name);
            if (removed != null) {
                operations.add(Operation.delete(name));
                return true;
            }
            return false;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private record Operation(String name, CredentialType type, String action) {
        static Operation save(String name, CredentialType type) {
            return new Operation(name, type, "save");
        }

        static Operation delete(String name) {
            return new Operation(name, CredentialType.FIDO2, "delete");
        }
    }
}
