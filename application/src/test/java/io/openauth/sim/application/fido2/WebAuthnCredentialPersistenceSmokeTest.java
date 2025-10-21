package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationCommand;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebAuthnCredentialPersistenceSmokeTest {

    private final WebAuthnCredentialPersistenceAdapter adapter = new WebAuthnCredentialPersistenceAdapter();

    @Test
    void roundTripsStoredAttestationDescriptor() {
        WebAuthnAttestationFixtures.WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.TPM).stream()
                        .findFirst()
                        .orElseThrow();

        List<String> customRoots = List.of("-----BEGIN CERTIFICATE-----\\nroot\\n-----END CERTIFICATE-----");

        GenerationCommand.Inline command = new GenerationCommand.Inline(
                vector.vectorId(),
                vector.format(),
                vector.relyingPartyId(),
                vector.origin(),
                vector.registration().challenge(),
                vector.keyMaterial().credentialPrivateKeyBase64Url(),
                vector.keyMaterial().attestationPrivateKeyBase64Url(),
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                SigningMode.CUSTOM_ROOT,
                customRoots);

        WebAuthnAttestationGenerator.GenerationResult generatorResult =
                new WebAuthnAttestationGenerator().generate(command);

        WebAuthnFixtures.WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name("stored-tpm")
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(fixture.algorithm())
                .build();

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name("stored-tpm")
                .format(vector.format())
                .signingMode(SigningMode.CUSTOM_ROOT)
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId(vector.relyingPartyId())
                .origin(vector.origin())
                .attestationId(vector.vectorId())
                .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                .attestationCertificateSerialBase64Url(vector.keyMaterial().attestationCertificateSerialBase64Url())
                .certificateChainPem(generatorResult.certificateChainPem())
                .customRootCertificatesPem(customRoots)
                .build();

        var record = adapter.serializeAttestation(descriptor);
        var roundTrip = adapter.deserializeAttestation(record);
        var credential = VersionedCredentialRecordMapper.toCredential(record);

        assertEquals("stored-tpm", credential.name());
        assertEquals(descriptor.signingMode(), roundTrip.signingMode());
        assertIterableEquals(descriptor.customRootCertificatesPem(), roundTrip.customRootCertificatesPem());
        assertIterableEquals(descriptor.certificateChainPem(), roundTrip.certificateChainPem());
        assertEquals(descriptor.attestationPrivateKeyBase64Url(), roundTrip.attestationPrivateKeyBase64Url());
        assertEquals(
                descriptor.attestationCertificateSerialBase64Url(), roundTrip.attestationCertificateSerialBase64Url());
    }
}
