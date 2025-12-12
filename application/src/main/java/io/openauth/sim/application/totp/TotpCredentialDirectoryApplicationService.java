package io.openauth.sim.application.totp;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Lists and manages TOTP credentials via the application layer. */
public final class TotpCredentialDirectoryApplicationService {

    private static final String ATTR_ALGORITHM = "totp.algorithm";
    private static final String ATTR_DIGITS = "totp.digits";
    private static final String ATTR_STEP_SECONDS = "totp.stepSeconds";
    private static final String ATTR_DRIFT_BACKWARD = "totp.drift.backward";
    private static final String ATTR_DRIFT_FORWARD = "totp.drift.forward";
    private static final String ATTR_LABEL = "totp.metadata.label";

    private final CredentialStore credentialStore;

    public TotpCredentialDirectoryApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public List<Summary> list() {
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.OATH_TOTP)
                .map(TotpCredentialDirectoryApplicationService::toSummary)
                .sorted(Comparator.comparing(Summary::credentialId, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toUnmodifiableList());
    }

    public boolean delete(String credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return credentialStore.delete(credentialId);
    }

    private static Summary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        String algorithm = attributes.getOrDefault(ATTR_ALGORITHM, "");
        String label = Optional.ofNullable(attributes.get(ATTR_LABEL))
                .filter(value -> !value.isBlank())
                .orElse(credential.name());
        return new Summary(
                credential.name(),
                label,
                algorithm,
                attributes.get(ATTR_DIGITS),
                attributes.get(ATTR_STEP_SECONDS),
                attributes.get(ATTR_DRIFT_BACKWARD),
                attributes.get(ATTR_DRIFT_FORWARD));
    }

    public record Summary(
            String credentialId,
            String label,
            String algorithm,
            String digits,
            String stepSeconds,
            String driftBackward,
            String driftForward) {}
}
