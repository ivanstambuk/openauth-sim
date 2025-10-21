package io.openauth.sim.rest.webauthn;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/webauthn/attestations")
final class WebAuthnAttestationStoredController {

    private static final String ATTR_ATTESTATION_OBJECT = "fido2.attestation.stored.attestationObject";
    private static final String ATTR_CLIENT_DATA_JSON = "fido2.attestation.stored.clientDataJson";
    private static final String ATTR_EXPECTED_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private final CredentialStore credentialStore;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    WebAuthnAttestationStoredController(
            CredentialStore credentialStore, WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    @GetMapping("/{credentialId}")
    ResponseEntity<StoredAttestationMetadataResponse> metadata(@PathVariable("credentialId") String credentialId) {
        return credentialStore
                .findByName(credentialId)
                .map(credential -> VersionedCredentialRecordMapper.toRecord(credential))
                .map(record -> ResponseEntity.ok(toResponse(record, persistenceAdapter.deserializeAttestation(record))))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored attestation not found"));
    }

    private static StoredAttestationMetadataResponse toResponse(
            io.openauth.sim.core.store.serialization.VersionedCredentialRecord record,
            WebAuthnAttestationCredentialDescriptor descriptor) {
        Map<String, String> attributes = record.attributes();
        return new StoredAttestationMetadataResponse(
                descriptor.name(),
                descriptor.attestationId(),
                descriptor.format().label(),
                descriptor.credentialDescriptor().relyingPartyId(),
                descriptor.origin(),
                descriptor.signingMode().name().toLowerCase(),
                descriptor.certificateChainPem(),
                attributes.getOrDefault(ATTR_EXPECTED_CHALLENGE, ""),
                attributes.getOrDefault(ATTR_ATTESTATION_OBJECT, ""),
                attributes.getOrDefault(ATTR_CLIENT_DATA_JSON, ""));
    }

    record StoredAttestationMetadataResponse(
            String storedCredentialId,
            String attestationId,
            String format,
            String relyingPartyId,
            String origin,
            String signingMode,
            List<String> certificateChainPem,
            String challenge,
            String attestationObject,
            String clientDataJson) {
        // DTO marker
    }
}
