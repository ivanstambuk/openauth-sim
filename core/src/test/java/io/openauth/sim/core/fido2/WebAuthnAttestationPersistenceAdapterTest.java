package io.openauth.sim.core.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.GenerationCommand;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationPersistenceAdapterTest {

    private final WebAuthnCredentialPersistenceAdapter adapter = new WebAuthnCredentialPersistenceAdapter();

    @Test
    void serializeAndDeserializeStoredAttestation() {
        WebAuthnAttestationFixtures.WebAuthnAttestationVector vector =
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
                WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED,
                List.of());

        WebAuthnAttestationGenerator.GenerationResult generationResult =
                new WebAuthnAttestationGenerator().generate(command);

        WebAuthnFixtures.WebAuthnFixture assertionFixture = WebAuthnFixtures.loadPackedEs256();

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name("stored-packed-es256")
                .relyingPartyId(assertionFixture.storedCredential().relyingPartyId())
                .credentialId(assertionFixture.storedCredential().credentialId())
                .publicKeyCose(assertionFixture.storedCredential().publicKeyCose())
                .signatureCounter(assertionFixture.storedCredential().signatureCounter())
                .userVerificationRequired(assertionFixture.storedCredential().userVerificationRequired())
                .algorithm(assertionFixture.algorithm())
                .build();

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name("stored-packed-es256")
                .format(vector.format())
                .signingMode(WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED)
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

        VersionedCredentialRecord record = adapter.serializeAttestation(descriptor);

        assertEquals("stored-packed-es256", record.name());
        assertEquals(
                descriptor.credentialPrivateKeyBase64Url(),
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(record.secret().value()));
        assertEquals("packed", record.attributes().get("fido2.attestation.format"));
        assertEquals("SELF_SIGNED", record.attributes().get("fido2.attestation.signingMode"));
        assertEquals(
                vector.keyMaterial().attestationPrivateKeyBase64Url(),
                record.attributes().get("fido2.attestation.privateKey"));
        assertEquals(
                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                record.attributes().get("fido2.attestation.certificateSerial"));
        WebAuthnAttestationCredentialDescriptor deserialized = adapter.deserializeAttestation(record);

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
