package io.openauth.sim.infra.persistence.webauthn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationCommand;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebAuthnCredentialStoreAttestationTest {

    private final WebAuthnCredentialPersistenceAdapter adapter = new WebAuthnCredentialPersistenceAdapter();

    @Test
    void persistsAndReloadsStoredAttestation() throws Exception {
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyBase64Url(),
                vector.keyMaterial().attestationPrivateKeyBase64Url(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.SELF_SIGNED,
                List.of());

        WebAuthnAttestationGenerator.GenerationResult generationResult =
                new WebAuthnAttestationGenerator().generate(command);

        WebAuthnFixtures.WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name("stored-packed-es256")
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(fixture.algorithm())
                .build();

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name("stored-packed-es256")
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

        Credential credential = VersionedCredentialRecordMapper.toCredential(adapter.serializeAttestation(descriptor));

        try (var store = CredentialStoreFactory.openInMemoryStore()) {
            store.save(credential);
            List<Credential> all = store.findAll();
            assertEquals(1, all.size(), "store should contain the persisted attestation credential");
            assertEquals("stored-packed-es256", all.get(0).name());

            Credential reloaded = store.findByName("stored-packed-es256").orElseThrow();

            WebAuthnAttestationCredentialDescriptor deserialized =
                    adapter.deserializeAttestation(VersionedCredentialRecordMapper.toRecord(reloaded));

            assertEquals(descriptor.name(), deserialized.name());
            assertEquals(descriptor.format(), deserialized.format());
            assertEquals(descriptor.signingMode(), deserialized.signingMode());
            assertEquals(
                    descriptor.credentialDescriptor().relyingPartyId(),
                    deserialized.credentialDescriptor().relyingPartyId());
            assertEquals(descriptor.origin(), deserialized.origin());
            assertEquals(descriptor.attestationId(), deserialized.attestationId());
            assertEquals(descriptor.credentialPrivateKeyBase64Url(), deserialized.credentialPrivateKeyBase64Url());
            assertEquals(descriptor.attestationPrivateKeyBase64Url(), deserialized.attestationPrivateKeyBase64Url());
            assertEquals(
                    descriptor.attestationCertificateSerialBase64Url(),
                    deserialized.attestationCertificateSerialBase64Url());
            assertIterableEquals(descriptor.certificateChainPem(), deserialized.certificateChainPem());
            assertIterableEquals(descriptor.customRootCertificatesPem(), deserialized.customRootCertificatesPem());
        }
    }
}
