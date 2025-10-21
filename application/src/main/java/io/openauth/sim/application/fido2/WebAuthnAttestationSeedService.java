package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Service that seeds curated WebAuthn attestation credentials into the shared credential store. */
public final class WebAuthnAttestationSeedService {

    private static final String ATTR_ATTESTATION_OBJECT = "fido2.attestation.stored.attestationObject";
    private static final String ATTR_CLIENT_DATA_JSON = "fido2.attestation.stored.clientDataJson";
    private static final String ATTR_EXPECTED_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    public WebAuthnAttestationSeedService() {
        this(new WebAuthnCredentialPersistenceAdapter());
    }

    WebAuthnAttestationSeedService(WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    /**
     * Seeds attestation credentials when they are not already present.
     *
     * @param commands list of descriptors and deterministic payloads to seed
     * @param credentialStore shared credential store
     * @return result describing how many credentials were added
     */
    public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(credentialStore, "credentialStore");
        if (commands.isEmpty()) {
            return new SeedResult(List.of());
        }

        List<String> addedCredentialIds = new ArrayList<>();
        for (SeedCommand command : commands) {
            WebAuthnAttestationCredentialDescriptor descriptor = command.descriptor();
            String credentialName = descriptor.name();
            if (credentialStore.findByName(credentialName).isPresent()) {
                continue;
            }

            VersionedCredentialRecord baseRecord = persistenceAdapter.serializeAttestation(descriptor);
            Map<String, String> attributes = new LinkedHashMap<>(baseRecord.attributes());
            attributes.put(ATTR_ATTESTATION_OBJECT, encode(command.attestationObject()));
            attributes.put(ATTR_CLIENT_DATA_JSON, encode(command.clientDataJson()));
            attributes.put(ATTR_EXPECTED_CHALLENGE, encode(command.expectedChallenge()));

            VersionedCredentialRecord enriched = new VersionedCredentialRecord(
                    baseRecord.schemaVersion(),
                    baseRecord.name(),
                    baseRecord.type(),
                    baseRecord.secret(),
                    baseRecord.createdAt(),
                    baseRecord.updatedAt(),
                    Map.copyOf(attributes));

            Credential credential = VersionedCredentialRecordMapper.toCredential(enriched);
            credentialStore.save(credential);
            addedCredentialIds.add(credentialName);
        }

        return new SeedResult(addedCredentialIds);
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static byte[] cloneBytes(byte[] value, String attribute) {
        Objects.requireNonNull(value, attribute + " must not be null");
        return value.clone();
    }

    public record SeedCommand(
            WebAuthnAttestationCredentialDescriptor descriptor,
            byte[] attestationObject,
            byte[] clientDataJson,
            byte[] expectedChallenge) {

        public SeedCommand(
                WebAuthnAttestationCredentialDescriptor descriptor,
                byte[] attestationObject,
                byte[] clientDataJson,
                byte[] expectedChallenge) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
            this.attestationObject = cloneBytes(attestationObject, "attestationObject");
            this.clientDataJson = cloneBytes(clientDataJson, "clientDataJson");
            this.expectedChallenge = cloneBytes(expectedChallenge, "expectedChallenge");
        }

        @Override
        public byte[] attestationObject() {
            return cloneBytes(attestationObject, "attestationObject");
        }

        @Override
        public byte[] clientDataJson() {
            return cloneBytes(clientDataJson, "clientDataJson");
        }

        @Override
        public byte[] expectedChallenge() {
            return cloneBytes(expectedChallenge, "expectedChallenge");
        }
    }

    public record SeedResult(int addedCount, List<String> addedCredentialIds) {

        public SeedResult(int addedCount, List<String> addedCredentialIds) {
            List<String> ids = addedCredentialIds == null ? List.of() : List.copyOf(addedCredentialIds);
            this.addedCredentialIds = ids;
            this.addedCount = ids.size();
        }

        public SeedResult(List<String> addedCredentialIds) {
            this(addedCredentialIds == null ? 0 : addedCredentialIds.size(), addedCredentialIds);
        }
    }
}
