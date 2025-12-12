package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Loads stored WebAuthn attestation metadata through the application layer. */
public final class WebAuthnAttestationStoredMetadataApplicationService {

    private static final String ATTR_ATTESTATION_OBJECT = "fido2.attestation.stored.attestationObject";
    private static final String ATTR_CLIENT_DATA_JSON = "fido2.attestation.stored.clientDataJson";
    private static final String ATTR_EXPECTED_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private final CredentialStore credentialStore;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    public WebAuthnAttestationStoredMetadataApplicationService(
            CredentialStore credentialStore, WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    /** Convenience factory for facades that should not instantiate core collaborators directly. */
    public static WebAuthnAttestationStoredMetadataApplicationService usingDefaults(CredentialStore credentialStore) {
        return new WebAuthnAttestationStoredMetadataApplicationService(
                credentialStore, new WebAuthnCredentialPersistenceAdapter());
    }

    public Optional<StoredAttestation> detail(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            return Optional.empty();
        }
        return credentialStore
                .findByName(credentialId.trim())
                .map(VersionedCredentialRecordMapper::toRecord)
                .flatMap(record -> {
                    try {
                        WebAuthnAttestationCredentialDescriptor descriptor =
                                persistenceAdapter.deserializeAttestation(record);
                        return Optional.of(toStoredAttestation(record, descriptor));
                    } catch (IllegalArgumentException ex) {
                        return Optional.empty();
                    }
                });
    }

    private static StoredAttestation toStoredAttestation(
            VersionedCredentialRecord record, WebAuthnAttestationCredentialDescriptor descriptor) {
        Map<String, String> attributes = record.attributes();
        return new StoredAttestation(
                descriptor.name(),
                descriptor.attestationId(),
                descriptor.format().label(),
                descriptor.credentialDescriptor().relyingPartyId(),
                descriptor.origin(),
                descriptor.signingMode().name().toLowerCase(),
                descriptor.certificateChainPem(),
                descriptor.customRootCertificatesPem(),
                attributes.getOrDefault(ATTR_EXPECTED_CHALLENGE, ""),
                attributes.getOrDefault(ATTR_ATTESTATION_OBJECT, ""),
                attributes.getOrDefault(ATTR_CLIENT_DATA_JSON, ""));
    }

    public record StoredAttestation(
            String storedCredentialId,
            String attestationId,
            String format,
            String relyingPartyId,
            String origin,
            String signingMode,
            List<String> certificateChainPem,
            List<String> customRootCertificatesPem,
            String challenge,
            String attestationObject,
            String clientDataJson) {
        public StoredAttestation {
            Objects.requireNonNull(storedCredentialId, "storedCredentialId");
            Objects.requireNonNull(attestationId, "attestationId");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(relyingPartyId, "relyingPartyId");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(signingMode, "signingMode");
            certificateChainPem = certificateChainPem == null ? List.of() : List.copyOf(certificateChainPem);
            customRootCertificatesPem =
                    customRootCertificatesPem == null ? List.of() : List.copyOf(customRootCertificatesPem);
            Objects.requireNonNull(challenge, "challenge");
            Objects.requireNonNull(attestationObject, "attestationObject");
            Objects.requireNonNull(clientDataJson, "clientDataJson");
        }
    }
}
