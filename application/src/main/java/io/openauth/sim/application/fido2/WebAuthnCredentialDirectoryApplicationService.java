package io.openauth.sim.application.fido2;

import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Lists stored WebAuthn credentials and exposes derived metadata via the application layer. */
public final class WebAuthnCredentialDirectoryApplicationService {

    private static final Comparator<Summary> SUMMARY_COMPARATOR = Comparator.comparingInt(
                    WebAuthnCredentialDirectoryApplicationService::algorithmSortKey)
            .thenComparing(Summary::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Summary::credentialId, String.CASE_INSENSITIVE_ORDER);

    private static final int UNKNOWN_ALGORITHM_RANK = WebAuthnSignatureAlgorithm.values().length;
    private static final int SIGNING_KEY_HANDLE_LENGTH = 12;

    private static final String ATTR_RP_ID = "fido2.rpId";
    private static final String ATTR_ALGORITHM = "fido2.algorithm";
    private static final String ATTR_LABEL = WebAuthnCredentialPersistenceAdapter.ATTR_METADATA_LABEL;
    private static final String ATTR_UV_REQUIRED = "fido2.userVerificationRequired";
    private static final String ATTR_ATTESTATION_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private final CredentialStore credentialStore;

    public WebAuthnCredentialDirectoryApplicationService(CredentialStore credentialStore) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
    }

    public List<Summary> list() {
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.FIDO2)
                .map(WebAuthnCredentialDirectoryApplicationService::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
    }

    public Optional<String> signingKeyHandle(String credentialId) {
        if (!hasText(credentialId)) {
            return Optional.empty();
        }
        return credentialStore
                .findByName(credentialId.trim())
                .map(WebAuthnCredentialDirectoryApplicationService::signingKeyHandleForCredential)
                .filter(WebAuthnCredentialDirectoryApplicationService::hasText);
    }

    private static Summary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        String algorithm = attributes.getOrDefault(ATTR_ALGORITHM, "");
        String label = Optional.ofNullable(attributes.get(ATTR_LABEL))
                .filter(WebAuthnCredentialDirectoryApplicationService::hasText)
                .orElse(buildLabel(credential.name(), algorithm));
        boolean uvRequired = Boolean.parseBoolean(attributes.getOrDefault(ATTR_UV_REQUIRED, "false"));
        String attestationChallenge = attributes.getOrDefault(ATTR_ATTESTATION_CHALLENGE, "");

        return new Summary(
                credential.name(),
                label,
                attributes.getOrDefault(ATTR_RP_ID, ""),
                algorithm,
                uvRequired,
                attestationChallenge);
    }

    private static int algorithmSortKey(Summary summary) {
        if (summary == null) {
            return UNKNOWN_ALGORITHM_RANK;
        }
        return algorithmSortKeyFromLabel(summary.algorithm());
    }

    private static int algorithmSortKeyFromLabel(String algorithm) {
        if (!hasText(algorithm)) {
            return UNKNOWN_ALGORITHM_RANK;
        }
        try {
            return WebAuthnSignatureAlgorithm.fromLabel(algorithm.trim()).ordinal();
        } catch (IllegalArgumentException ex) {
            return UNKNOWN_ALGORITHM_RANK;
        }
    }

    private static String buildLabel(String id, String algorithm) {
        if (id == null) {
            return "";
        }
        String trimmedId = id.trim();
        if (!hasText(algorithm)) {
            return trimmedId;
        }
        return trimmedId + " (" + algorithm + ")";
    }

    private static String signingKeyHandleForCredential(Credential credential) {
        if (credential == null || credential.secret() == null) {
            return "";
        }
        return signingKeyHandle(extractKeyMaterial(credential.secret().value()));
    }

    private static byte[] extractKeyMaterial(byte[] secretBytes) {
        String secretText = new String(secretBytes, StandardCharsets.UTF_8).trim();
        if (secretText.startsWith("{") && secretText.endsWith("}")) {
            try {
                Object parsed = SimpleJson.parse(secretText);
                if (parsed instanceof Map<?, ?> map) {
                    Object d = map.get("d");
                    if (d instanceof String dValue && !dValue.isBlank()) {
                        return Base64.getUrlDecoder().decode(dValue);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // fall back to raw secret bytes
            }
        }
        return secretBytes.clone();
    }

    private static String signingKeyHandle(byte[] keyMaterial) {
        if (keyMaterial == null || keyMaterial.length == 0) {
            return "";
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(keyMaterial);
            String hex = HexFormat.of().formatHex(digest);
            return hex.substring(0, Math.min(SIGNING_KEY_HANDLE_LENGTH, hex.length()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record Summary(
            String credentialId,
            String label,
            String relyingPartyId,
            String algorithm,
            boolean userVerificationRequired,
            String attestationChallenge) {
        public Summary {
            Objects.requireNonNull(credentialId, "credentialId");
        }
    }
}
