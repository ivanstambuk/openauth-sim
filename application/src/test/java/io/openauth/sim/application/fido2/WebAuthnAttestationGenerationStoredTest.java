package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
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
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationGenerationStoredTest {

    private final WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();

    @Test
    void generatesAttestationFromStoredCredential() throws Exception {
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow();

        GenerationCommand.Inline inlineCommand = new GenerationCommand.Inline(
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

        WebAuthnAttestationGenerator.GenerationResult generated = generator.generate(inlineCommand);

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

        WebAuthnAttestationCredentialDescriptor storedDescriptor = WebAuthnAttestationCredentialDescriptor.builder()
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
                .certificateChainPem(generated.certificateChainPem())
                .customRootCertificatesPem(List.of())
                .build();

        var record = persistenceAdapter.serializeAttestation(storedDescriptor);

        try (MapDbCredentialStore store = MapDbCredentialStore.inMemory().open()) {
            store.save(VersionedCredentialRecordMapper.toCredential(record));

            WebAuthnAttestationGenerationApplicationService service =
                    new WebAuthnAttestationGenerationApplicationService(
                            generator, new Fido2TelemetryAdapter("test"), store, persistenceAdapter);

            WebAuthnAttestationGenerationApplicationService.GenerationCommand.Stored command =
                    new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Stored(
                            storedDescriptor.name(),
                            storedDescriptor.format(),
                            storedDescriptor.credentialDescriptor().relyingPartyId(),
                            storedDescriptor.origin(),
                            vector.registration().challenge());

            WebAuthnAttestationGenerationApplicationService.GenerationResult result = service.generate(command);

            assertEquals(storedDescriptor.attestationId(), result.attestation().attestationId());
            assertIterableEquals(storedDescriptor.certificateChainPem(), result.certificateChainPem());
            assertEquals("stored", result.telemetry().fields().get("inputSource"));
            assertEquals(storedDescriptor.name(), result.telemetry().fields().get("storedCredentialId"));
        }
    }
}
