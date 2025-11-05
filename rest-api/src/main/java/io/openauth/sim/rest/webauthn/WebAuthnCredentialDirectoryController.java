package io.openauth.sim.rest.webauthn;

import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.InlineVector;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.SeedDefinition;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/webauthn", produces = MediaType.APPLICATION_JSON_VALUE)
final class WebAuthnCredentialDirectoryController {

    private static final Comparator<WebAuthnCredentialSummary> SUMMARY_COMPARATOR = Comparator.comparingInt(
                    WebAuthnCredentialDirectoryController::algorithmSortKey)
            .thenComparing(WebAuthnCredentialSummary::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(WebAuthnCredentialSummary::id, String.CASE_INSENSITIVE_ORDER);

    private static final int UNKNOWN_ALGORITHM_RANK = WebAuthnSignatureAlgorithm.values().length;
    private static final int SIGNING_KEY_HANDLE_LENGTH = 12;
    private static final String PRIVATE_KEY_PLACEHOLDER = "[stored-server-side]";

    private static final String ATTR_RP_ID = "fido2.rpId";
    private static final String ATTR_ALGORITHM = "fido2.algorithm";
    private static final String ATTR_LABEL = WebAuthnCredentialPersistenceAdapter.ATTR_METADATA_LABEL;
    private static final String ATTR_UV_REQUIRED = "fido2.userVerificationRequired";
    private static final String ATTR_ATTESTATION_CHALLENGE = "fido2.attestation.stored.expectedChallenge";

    private final CredentialStore credentialStore;

    WebAuthnCredentialDirectoryController(ObjectProvider<CredentialStore> credentialStoreProvider) {
        this.credentialStore = credentialStoreProvider.getIfAvailable();
    }

    @GetMapping("/credentials")
    List<WebAuthnCredentialSummary> listCredentials() {
        if (credentialStore == null) {
            return List.of();
        }
        return credentialStore.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.FIDO2)
                .map(WebAuthnCredentialDirectoryController::toSummary)
                .sorted(SUMMARY_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
    }

    @GetMapping("/credentials/{credentialId}/sample")
    ResponseEntity<WebAuthnStoredSampleResponse> storedSample(@PathVariable("credentialId") String credentialId) {
        if (!StringUtils.hasText(credentialId)) {
            return ResponseEntity.notFound().build();
        }
        Optional<SeedDefinition> definition = Fido2OperatorSampleData.seedDefinitions().stream()
                .filter(def -> def.credentialId().equals(credentialId))
                .findFirst();
        if (definition.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String presetKey = definition
                .get()
                .metadata()
                .getOrDefault(
                        "presetKey",
                        Fido2OperatorSampleData.inlineVectors().get(0).key());
        InlineVector vector = Fido2OperatorSampleData.inlineVectors().stream()
                .filter(candidate -> candidate.key().equals(presetKey))
                .findFirst()
                .orElseGet(() -> Fido2OperatorSampleData.inlineVectors().isEmpty()
                        ? null
                        : Fido2OperatorSampleData.inlineVectors().get(0));
        if (vector == null) {
            return ResponseEntity.notFound().build();
        }
        String signingKeyHandle = computeSigningKeyHandle(definition.get().credentialId());
        if (!StringUtils.hasText(signingKeyHandle)) {
            signingKeyHandle = signingKeyHandle(definition.get());
        }
        WebAuthnStoredSampleResponse response = new WebAuthnStoredSampleResponse(
                definition.get().credentialId(),
                definition.get().relyingPartyId(),
                vector.origin(),
                vector.expectedType(),
                definition.get().algorithm().label(),
                definition.get().userVerificationRequired(),
                vector.expectedChallengeBase64Url(),
                signingKeyHandle,
                PRIVATE_KEY_PLACEHOLDER);
        return ResponseEntity.ok(response);
    }

    private static WebAuthnCredentialSummary toSummary(Credential credential) {
        Map<String, String> attributes = credential.attributes();
        String label = Optional.ofNullable(attributes.get(ATTR_LABEL))
                .filter(StringUtils::hasText)
                .orElse(buildLabel(credential.name(), attributes.get(ATTR_ALGORITHM)));
        boolean uvRequired = Boolean.parseBoolean(attributes.getOrDefault(ATTR_UV_REQUIRED, "false"));
        String attestationChallenge = attributes.getOrDefault(ATTR_ATTESTATION_CHALLENGE, "");

        return new WebAuthnCredentialSummary(
                credential.name(),
                label,
                attributes.getOrDefault(ATTR_RP_ID, ""),
                attributes.getOrDefault(ATTR_ALGORITHM, ""),
                uvRequired,
                attestationChallenge);
    }

    private static int algorithmSortKey(WebAuthnCredentialSummary summary) {
        if (summary == null) {
            return UNKNOWN_ALGORITHM_RANK;
        }
        return algorithmSortKeyFromLabel(summary.algorithm());
    }

    private static int algorithmSortKeyFromLabel(String algorithm) {
        if (!StringUtils.hasText(algorithm)) {
            return UNKNOWN_ALGORITHM_RANK;
        }
        try {
            return WebAuthnSignatureAlgorithm.fromLabel(algorithm).ordinal();
        } catch (IllegalArgumentException ex) {
            return UNKNOWN_ALGORITHM_RANK;
        }
    }

    private static String buildLabel(String id, String algorithm) {
        if (id == null) {
            return "";
        }
        String trimmedId = id.trim();
        if (!StringUtils.hasText(algorithm)) {
            return trimmedId;
        }
        return trimmedId + " (" + algorithm + ")";
    }

    private String computeSigningKeyHandle(String credentialId) {
        if (credentialStore == null || !StringUtils.hasText(credentialId)) {
            return "";
        }
        return credentialStore
                .findByName(credentialId.trim())
                .map(WebAuthnCredentialDirectoryController::signingKeyHandleForCredential)
                .orElse("");
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

    private static String signingKeyHandle(Fido2OperatorSampleData.SeedDefinition definition) {
        if (definition == null) {
            return "";
        }
        String privateKeyJwk = definition.privateKeyJwk();
        if (!StringUtils.hasText(privateKeyJwk)) {
            return "";
        }
        byte[] keyMaterial = extractKeyMaterial(privateKeyJwk.getBytes(StandardCharsets.UTF_8));
        return signingKeyHandle(keyMaterial);
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

    record WebAuthnCredentialSummary(
            String id,
            String label,
            String relyingPartyId,
            String algorithm,
            boolean userVerification,
            String attestationChallenge) {

        WebAuthnCredentialSummary {
            Objects.requireNonNull(id, "id");
        }
    }

    record WebAuthnStoredSampleResponse(
            String credentialId,
            String relyingPartyId,
            String origin,
            String expectedType,
            String algorithm,
            boolean userVerificationRequired,
            String challenge,
            String signingKeyHandle,
            String privateKeyPlaceholder) {
        // DTO marker
    }
}
