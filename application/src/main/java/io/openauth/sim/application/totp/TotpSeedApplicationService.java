package io.openauth.sim.application.totp;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service responsible for seeding canonical TOTP credentials. */
public final class TotpSeedApplicationService {

    private static final String ATTR_ALGORITHM = "totp.algorithm";
    private static final String ATTR_DIGITS = "totp.digits";
    private static final String ATTR_STEP_SECONDS = "totp.stepSeconds";
    private static final String ATTR_DRIFT_BACKWARD = "totp.drift.backward";
    private static final String ATTR_DRIFT_FORWARD = "totp.drift.forward";
    private static final String ATTR_METADATA_PREFIX = "totp.metadata.";

    private final TotpCredentialPersistenceAdapter persistenceAdapter = new TotpCredentialPersistenceAdapter();

    /**
     * Seeds the provided TOTP credentials into the {@link CredentialStore}, skipping identifiers that
     * already exist.
     */
    public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(credentialStore, "credentialStore");

        List<String> added = new ArrayList<>();
        for (SeedCommand command : commands) {
            if (credentialStore.exists(command.credentialId())) {
                continue;
            }

            SecretMaterial secret = SecretMaterial.fromHex(command.sharedSecretHex());
            TotpDescriptor descriptor = TotpDescriptor.create(
                    command.credentialId(),
                    secret,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow());

            Credential serialized =
                    VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
            Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
            command.metadata().forEach((key, value) -> attributes.put(ATTR_METADATA_PREFIX + key, value));

            attributes.put(ATTR_ALGORITHM, command.algorithm().name());
            attributes.put(ATTR_DIGITS, Integer.toString(command.digits()));
            attributes.put(
                    ATTR_STEP_SECONDS, Long.toString(command.stepDuration().toSeconds()));
            attributes.put(
                    ATTR_DRIFT_BACKWARD, Integer.toString(command.driftWindow().backwardSteps()));
            attributes.put(
                    ATTR_DRIFT_FORWARD, Integer.toString(command.driftWindow().forwardSteps()));

            Credential persisted = new Credential(
                    serialized.name(),
                    CredentialType.OATH_TOTP,
                    serialized.secret(),
                    attributes,
                    serialized.createdAt(),
                    serialized.updatedAt());

            credentialStore.save(persisted);
            added.add(command.credentialId());
        }

        return new SeedResult(List.copyOf(added));
    }

    /** Command describing a canonical TOTP credential to seed. */
    public record SeedCommand(
            String credentialId,
            String sharedSecretHex,
            TotpHashAlgorithm algorithm,
            int digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            Map<String, String> metadata) {

        public SeedCommand {
            credentialId = Objects.requireNonNull(credentialId, "credentialId");
            sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
            algorithm = Objects.requireNonNull(algorithm, "algorithm");
            stepDuration = Objects.requireNonNull(stepDuration, "stepDuration");
            driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    /** Result of a seeding request, including the credential identifiers that were created. */
    public record SeedResult(List<String> addedCredentialIds) {

        public SeedResult {
            Objects.requireNonNull(addedCredentialIds, "addedCredentialIds");
            addedCredentialIds = List.copyOf(addedCredentialIds);
        }
    }
}
