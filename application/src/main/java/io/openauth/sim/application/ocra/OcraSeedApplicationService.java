package io.openauth.sim.application.ocra;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service responsible for seeding canonical OCRA credentials. */
public final class OcraSeedApplicationService {

    private final OcraCredentialFactory credentialFactory;
    private final OcraCredentialPersistenceAdapter persistenceAdapter;

    public OcraSeedApplicationService() {
        this(new OcraCredentialFactory(), new OcraCredentialPersistenceAdapter());
    }

    OcraSeedApplicationService(
            OcraCredentialFactory credentialFactory, OcraCredentialPersistenceAdapter persistenceAdapter) {
        this.credentialFactory = Objects.requireNonNull(credentialFactory, "credentialFactory");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(credentialStore, "credentialStore");

        List<String> addedIdentifiers = new ArrayList<>();
        for (SeedCommand command : commands) {
            if (credentialStore.exists(command.credentialName())) {
                continue;
            }
            OcraCredentialRequest request = toRequest(command);
            OcraCredentialDescriptor descriptor = credentialFactory.createDescriptor(request);
            Credential credential =
                    VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
            credentialStore.save(credential);
            addedIdentifiers.add(command.credentialName());
        }
        return new SeedResult(List.copyOf(addedIdentifiers));
    }

    private static OcraCredentialRequest toRequest(SeedCommand command) {
        return new OcraCredentialRequest(
                command.credentialName(),
                command.ocraSuite(),
                command.sharedSecretHex(),
                SecretEncoding.HEX,
                command.counter(),
                command.pinHashHex(),
                command.allowedTimestampDrift(),
                command.metadata());
    }

    public record SeedCommand(
            String credentialName,
            String ocraSuite,
            String sharedSecretHex,
            Long counter,
            String pinHashHex,
            java.time.Duration allowedTimestampDrift,
            Map<String, String> metadata) {

        public SeedCommand {
            Objects.requireNonNull(credentialName, "credentialName");
            Objects.requireNonNull(ocraSuite, "ocraSuite");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record SeedResult(List<String> addedCredentialIds) {

        public SeedResult {
            Objects.requireNonNull(addedCredentialIds, "addedCredentialIds");
            addedCredentialIds = List.copyOf(addedCredentialIds);
        }
    }
}
