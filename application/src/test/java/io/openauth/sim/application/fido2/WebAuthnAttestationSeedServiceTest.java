package io.openauth.sim.application.fido2;

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
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
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

    private final CapturingCredentialStore credentialStore = new CapturingCredentialStore();
    private final WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();

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

        List<String> expectedIds = commands.stream()
                .map(command -> command.descriptor().name())
                .sorted()
                .toList();
        assertEquals(expectedIds, result.addedCredentialIds().stream().sorted().toList());
        assertEquals(expectedIds.size(), result.addedCount());

        List<Credential> persisted = credentialStore.findAll();
        assertEquals(commands.size(), persisted.size());

        Map<String, WebAuthnAttestationCredentialDescriptor> descriptors = persisted.stream()
                .collect(Collectors.toMap(
                        Credential::name,
                        credential -> deserialize(VersionedCredentialRecordMapper.toRecord(credential))));

        for (SeedCommand command : commands) {
            WebAuthnAttestationCredentialDescriptor descriptor = command.descriptor();
            WebAuthnAttestationCredentialDescriptor stored = descriptors.get(descriptor.name());
            assertEquals(descriptor.attestationId(), stored.attestationId());
            assertEquals(descriptor.format(), stored.format());
            assertEquals(descriptor.signingMode(), stored.signingMode());
            assertEquals(
                    descriptor.credentialDescriptor().credentialId().length,
                    stored.credentialDescriptor().credentialId().length);
            assertTrue(stored.certificateChainPem().size()
                    >= descriptor.certificateChainPem().size());
        }

        List<Operation> expectedOperations = commands.stream()
                .map(command -> Operation.save(command.descriptor().name(), CredentialType.FIDO2))
                .toList();
        assertIterableEquals(expectedOperations, credentialStore.operations());
    }

    @Test
    @DisplayName("Skips attestation credentials when curated identifiers already exist")
    void skipsExistingAttestationCredentials() {
        List<SeedCommand> commands =
                CANONICAL_VECTORS.stream().map(this::toSeedCommand).toList();
        SeedCommand existing = commands.get(0);
        credentialStore.save(serialize(existing.descriptor()));
        credentialStore.resetOperations();

        WebAuthnAttestationSeedService service = new WebAuthnAttestationSeedService();
        SeedResult result = service.seed(commands, credentialStore);

        List<String> expectedIds = commands.stream()
                .map(command -> command.descriptor().name())
                .filter(name -> !name.equals(existing.descriptor().name()))
                .sorted()
                .toList();
        assertEquals(expectedIds, result.addedCredentialIds().stream().sorted().toList());
        assertEquals(expectedIds.size(), result.addedCount());

        List<Operation> expectedOperations = commands.stream()
                .filter(command -> !command.equals(existing))
                .map(command -> Operation.save(command.descriptor().name(), CredentialType.FIDO2))
                .toList();
        assertIterableEquals(expectedOperations, credentialStore.operations());
    }

    private SeedCommand toSeedCommand(WebAuthnAttestationVector vector) {
        WebAuthnFixture fixture = findFixture(vector.vectorId()).orElseGet(WebAuthnFixtures::loadPackedEs256);

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(vector.vectorId())
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(fixture.algorithm())
                .build();

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

    private Credential serialize(WebAuthnAttestationCredentialDescriptor descriptor) {
        VersionedCredentialRecord record =
                new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter().serializeAttestation(descriptor);
        return VersionedCredentialRecordMapper.toCredential(record);
    }

    private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
        Set<WebAuthnAttestationFormat> seen = new LinkedHashSet<>();
        List<WebAuthnAttestationVector> selected = new ArrayList<>();
        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
            if (seen.add(vector.format())) {
                if (!isInlineCompatible(generator, vector)) {
                    seen.remove(vector.format());
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
