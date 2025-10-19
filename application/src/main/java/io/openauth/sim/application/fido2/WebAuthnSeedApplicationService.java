package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service responsible for seeding canonical WebAuthn credentials. */
public final class WebAuthnSeedApplicationService {

    private static final String ATTR_METADATA_PREFIX = "fido2.metadata.";

    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();

    /**
     * Seeds the provided WebAuthn credentials into the {@link CredentialStore}, skipping identifiers
     * that already exist.
     */
    public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(credentialStore, "credentialStore");

        List<String> added = new ArrayList<>();
        for (SeedCommand command : commands) {
            if (credentialStore.exists(command.credentialId())) {
                continue;
            }

            WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                    .name(command.credentialId())
                    .relyingPartyId(command.relyingPartyId())
                    .credentialId(command.credentialIdBytes())
                    .publicKeyCose(command.publicKeyCose())
                    .signatureCounter(command.signatureCounter())
                    .userVerificationRequired(command.userVerificationRequired())
                    .algorithm(command.algorithm())
                    .build();

            Credential serialized =
                    VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
            Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
            command.metadata().forEach((key, value) -> attributes.put(ATTR_METADATA_PREFIX + key, value));

            Credential persisted = new Credential(
                    serialized.name(),
                    CredentialType.FIDO2,
                    serialized.secret(),
                    attributes,
                    serialized.createdAt(),
                    serialized.updatedAt());

            credentialStore.save(persisted);
            added.add(command.credentialId());
        }

        return new SeedResult(List.copyOf(added));
    }

    /** Command describing a canonical WebAuthn credential to seed. */
    public record SeedCommand(
            String credentialId,
            String relyingPartyId,
            byte[] credentialIdBytes,
            byte[] publicKeyCose,
            long signatureCounter,
            boolean userVerificationRequired,
            WebAuthnSignatureAlgorithm algorithm,
            Map<String, String> metadata) {

        public SeedCommand {
            credentialId = Objects.requireNonNull(credentialId, "credentialId");
            relyingPartyId = Objects.requireNonNull(relyingPartyId, "relyingPartyId");
            credentialIdBytes = Objects.requireNonNull(credentialIdBytes, "credentialIdBytes")
                    .clone();
            publicKeyCose =
                    Objects.requireNonNull(publicKeyCose, "publicKeyCose").clone();
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** Result describing the credential identifiers that were seeded. */
    public record SeedResult(List<String> addedCredentialIds) {

        public SeedResult {
            Objects.requireNonNull(addedCredentialIds, "addedCredentialIds");
            addedCredentialIds = List.copyOf(addedCredentialIds);
        }
    }
}
