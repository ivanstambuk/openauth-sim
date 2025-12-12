package io.openauth.sim.application.hotp;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Lists and manages HOTP credentials via the application layer. */
public final class HotpCredentialDirectoryApplicationService {

    private static final String ATTR_DIGITS = "hotp.digits";
    private static final String ATTR_COUNTER = "hotp.counter";
    private static final String ATTR_ALGORITHM = "hotp.algorithm";
    private static final String ATTR_LABEL = "label";
    private static final String ATTR_LEGACY_LABEL = "hotp.metadata.label";

    private final CredentialStore credentialStore;

    public HotpCredentialDirectoryApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public List<Summary> list() {
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.OATH_HOTP)
                .map(HotpCredentialDirectoryApplicationService::toSummary)
                .sorted(Comparator.comparing(Summary::credentialId, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toUnmodifiableList());
    }

    public boolean delete(String credentialId) {
        Objects.requireNonNull(credentialId, "credentialId");
        return credentialStore.delete(credentialId);
    }

    private static Summary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        return new Summary(
                credential.name(),
                resolveLabel(attributes, credential.name()),
                attributes.get(ATTR_ALGORITHM),
                attributes.get(ATTR_DIGITS),
                attributes.get(ATTR_COUNTER));
    }

    private static String resolveLabel(Map<String, String> attributes, String fallbackId) {
        String label = attributes.getOrDefault(ATTR_LABEL, attributes.get(ATTR_LEGACY_LABEL));
        if (label != null && !label.isBlank()) {
            return label;
        }
        return null;
    }

    public record Summary(String credentialId, String label, String algorithm, String digits, String counter) {}
}
