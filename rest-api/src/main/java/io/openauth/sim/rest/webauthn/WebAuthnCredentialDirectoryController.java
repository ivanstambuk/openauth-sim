package io.openauth.sim.rest.webauthn;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.InlineVector;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.SeedDefinition;
import java.util.Comparator;
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

  private static final Comparator<WebAuthnCredentialSummary> SUMMARY_COMPARATOR =
      Comparator.comparing(WebAuthnCredentialSummary::id, String.CASE_INSENSITIVE_ORDER);

  private static final String ATTR_RP_ID = "fido2.rpId";
  private static final String ATTR_ALGORITHM = "fido2.algorithm";
  private static final String ATTR_LABEL = "fido2.metadata.label";
  private static final String ATTR_UV_REQUIRED = "fido2.userVerificationRequired";

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
  ResponseEntity<WebAuthnStoredSampleResponse> storedSample(
      @PathVariable("credentialId") String credentialId) {
    if (!StringUtils.hasText(credentialId)) {
      return ResponseEntity.notFound().build();
    }
    Optional<SeedDefinition> definition =
        Fido2OperatorSampleData.seedDefinitions().stream()
            .filter(def -> def.credentialId().equals(credentialId))
            .findFirst();
    if (definition.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    InlineVector vector = Fido2OperatorSampleData.inlineVectors().get(0);
    WebAuthnStoredSampleResponse response =
        new WebAuthnStoredSampleResponse(
            definition.get().credentialId(),
            definition.get().relyingPartyId(),
            vector.origin(),
            vector.expectedType(),
            definition.get().algorithm().label(),
            definition.get().userVerificationRequired(),
            vector.expectedChallengeBase64Url(),
            vector.clientDataBase64Url(),
            vector.authenticatorDataBase64Url(),
            vector.signatureBase64Url());
    return ResponseEntity.ok(response);
  }

  private static WebAuthnCredentialSummary toSummary(Credential credential) {
    Map<String, String> attributes = credential.attributes();
    String label =
        Optional.ofNullable(attributes.get(ATTR_LABEL))
            .filter(StringUtils::hasText)
            .orElse(buildLabel(credential.name(), attributes.get(ATTR_ALGORITHM)));
    boolean uvRequired = Boolean.parseBoolean(attributes.getOrDefault(ATTR_UV_REQUIRED, "false"));

    return new WebAuthnCredentialSummary(
        credential.name(),
        label,
        attributes.getOrDefault(ATTR_RP_ID, ""),
        attributes.getOrDefault(ATTR_ALGORITHM, ""),
        uvRequired);
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

  record WebAuthnCredentialSummary(
      String id, String label, String relyingPartyId, String algorithm, boolean userVerification) {

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
      String expectedChallengeBase64Url,
      String clientDataBase64Url,
      String authenticatorDataBase64Url,
      String signatureBase64Url) {
    // DTO marker
  }
}
