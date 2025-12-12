package io.openauth.sim.application.ocra;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Manages OCRA credential import/list/delete via the application layer. */
public final class OcraCredentialManagementApplicationService {

    private final CredentialStore credentialStore;
    private final OcraCredentialFactory credentialFactory;
    private final OcraCredentialPersistenceAdapter persistenceAdapter;

    public OcraCredentialManagementApplicationService(
            CredentialStore credentialStore,
            OcraCredentialFactory credentialFactory,
            OcraCredentialPersistenceAdapter persistenceAdapter) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.credentialFactory = Objects.requireNonNull(credentialFactory, "credentialFactory");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    /**
     * Builds a management service with the default OCRA factory and persistence adapter. Useful for
     * facades that should not construct core objects directly.
     */
    public static OcraCredentialManagementApplicationService usingDefaults(CredentialStore credentialStore) {
        return new OcraCredentialManagementApplicationService(
                credentialStore, new OcraCredentialFactory(), new OcraCredentialPersistenceAdapter());
    }

    public ImportResult importCredential(ImportCommand command) {
        Objects.requireNonNull(command, "command");
        OcraCredentialRequest request = toRequest(command);
        OcraCredentialDescriptor descriptor = credentialFactory.createDescriptor(request);
        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
        credentialStore.save(credential);
        return new ImportResult(descriptor.name(), command.metadata());
    }

    public List<Summary> list() {
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.OATH_OCRA)
                .map(this::toSummary)
                .sorted(Comparator.comparing(Summary::credentialId, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toUnmodifiableList());
    }

    public boolean delete(String credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return credentialStore.delete(credentialId);
    }

    public Optional<OcraCredentialDescriptor> find(String credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return credentialStore
                .findByName(credentialId)
                .filter(credential -> credential.type() == CredentialType.OATH_OCRA)
                .map(VersionedCredentialRecordMapper::toRecord)
                .map(persistenceAdapter::deserialize);
    }

    private static OcraCredentialRequest toRequest(ImportCommand command) {
        return new OcraCredentialRequest(
                command.credentialId(),
                command.ocraSuite(),
                command.sharedSecretHex(),
                command.secretEncoding(),
                command.counter().orElse(null),
                command.pinHashHex().orElse(null),
                command.allowedTimestampDrift().orElse(null),
                command.metadata());
    }

    private Summary toSummary(Credential credential) {
        OcraCredentialDescriptor descriptor =
                persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential));
        boolean hasCounter = descriptor.counter().isPresent();
        boolean hasPin = descriptor.pinHash().isPresent();
        boolean hasDrift = descriptor.allowedTimestampDrift().isPresent();
        return new Summary(
                descriptor.name(), descriptor.suite().value(), hasCounter, hasPin, hasDrift, descriptor.metadata());
    }

    public record ImportCommand(
            String credentialId,
            String ocraSuite,
            String sharedSecretHex,
            SecretEncoding secretEncoding,
            Optional<Long> counter,
            Optional<String> pinHashHex,
            Optional<Duration> allowedTimestampDrift,
            Map<String, String> metadata) {

        public ImportCommand {
            Objects.requireNonNull(credentialId, "credentialId");
            Objects.requireNonNull(ocraSuite, "ocraSuite");
            Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            secretEncoding = Objects.requireNonNullElse(secretEncoding, SecretEncoding.HEX);
            counter = counter == null ? Optional.empty() : counter;
            pinHashHex = pinHashHex == null ? Optional.empty() : pinHashHex;
            allowedTimestampDrift = allowedTimestampDrift == null ? Optional.empty() : allowedTimestampDrift;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record ImportResult(String credentialId, Map<String, String> metadata) {}

    public record Summary(
            String credentialId,
            String suite,
            boolean hasCounter,
            boolean hasPin,
            boolean hasDrift,
            Map<String, String> metadata) {}
}
