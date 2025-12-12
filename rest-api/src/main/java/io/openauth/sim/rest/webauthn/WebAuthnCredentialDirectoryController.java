package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnCredentialDirectoryApplicationService;
import io.openauth.sim.application.fido2.WebAuthnCredentialDirectoryApplicationService.Summary;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.InlineVector;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.SeedDefinition;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
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

    private static final int SIGNING_KEY_HANDLE_LENGTH = 12;
    private static final String PRIVATE_KEY_PLACEHOLDER = "[stored-server-side]";

    private final WebAuthnCredentialDirectoryApplicationService directoryService;

    WebAuthnCredentialDirectoryController(
            ObjectProvider<WebAuthnCredentialDirectoryApplicationService> directoryServiceProvider) {
        this.directoryService = directoryServiceProvider.getIfAvailable();
    }

    @GetMapping("/credentials")
    List<WebAuthnCredentialSummary> listCredentials() {
        if (directoryService == null) {
            return List.of();
        }
        return directoryService.list().stream()
                .map(WebAuthnCredentialDirectoryController::toSummary)
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

    private String computeSigningKeyHandle(String credentialId) {
        if (directoryService == null || !StringUtils.hasText(credentialId)) {
            return "";
        }
        return directoryService.signingKeyHandle(credentialId.trim()).orElse("");
    }

    private static String signingKeyHandle(Fido2OperatorSampleData.SeedDefinition definition) {
        if (definition == null) {
            return "";
        }
        String privateKeyJwk = definition.privateKeyJwk();
        if (!StringUtils.hasText(privateKeyJwk)) {
            return "";
        }
        return signingKeyHandle(privateKeyJwk.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    private static WebAuthnCredentialSummary toSummary(Summary summary) {
        return new WebAuthnCredentialSummary(
                summary.credentialId(),
                summary.label(),
                summary.relyingPartyId(),
                summary.algorithm(),
                summary.userVerificationRequired(),
                summary.attestationChallenge());
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
