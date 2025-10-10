package io.openauth.sim.rest.webauthn;

import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService.SeedCommand;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService.SeedResult;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData;
import io.openauth.sim.rest.ui.Fido2OperatorSampleData.SeedDefinition;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
final class WebAuthnCredentialSeedService {

  private static final Logger LOGGER = Logger.getLogger("io.openauth.sim.rest.webauthn.telemetry");

  private final CredentialStore credentialStore;
  private final WebAuthnSeedApplicationService seedApplicationService;
  private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

  WebAuthnCredentialSeedService(
      ObjectProvider<CredentialStore> credentialStoreProvider,
      WebAuthnSeedApplicationService seedApplicationService) {
    this.credentialStore = credentialStoreProvider.getIfAvailable();
    this.seedApplicationService =
        Objects.requireNonNull(seedApplicationService, "seedApplicationService");
  }

  SeedResponse seedCanonicalCredentials() {
    List<SeedDefinition> definitions = Fido2OperatorSampleData.seedDefinitions();
    if (credentialStore == null) {
      SeedResponse response = SeedResponse.disabled(definitions.size());
      logSeed(Level.WARNING, "unavailable", response, "credential store unavailable");
      return response;
    }

    List<SeedCommand> commands =
        definitions.stream()
            .map(
                definition ->
                    new SeedCommand(
                        definition.credentialId(),
                        definition.relyingPartyId(),
                        urlDecoder.decode(definition.credentialIdBase64Url()),
                        urlDecoder.decode(definition.publicKeyCoseBase64Url()),
                        definition.signatureCounter(),
                        definition.userVerificationRequired(),
                        definition.algorithm(),
                        definition.metadata()))
            .toList();

    SeedResult result = seedApplicationService.seed(commands, credentialStore);
    SeedResponse response = new SeedResponse(definitions.size(), result.addedCredentialIds());

    logSeed(
        response.addedCount() > 0 ? Level.INFO : Level.FINE,
        response.addedCount() > 0 ? "seeded" : "noop",
        response,
        null);
    return response;
  }

  private void logSeed(Level level, String status, SeedResponse response, String reason) {
    if (!LOGGER.isLoggable(level)) {
      return;
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("addedCount", response.addedCount());
    fields.put("canonicalCount", response.canonicalCount());
    fields.put("existingCount", response.existingCount());
    if (!response.addedCredentialIds().isEmpty()) {
      fields.put("addedCredentialIds", response.addedCredentialIds());
    }
    Fido2TelemetryAdapter adapter = new Fido2TelemetryAdapter("fido2.seed");
    adapter.status(status, "rest-fido2-seed-" + UUID.randomUUID(), status, true, reason, fields);
    LOGGER.log(level, () -> "event=fido2.seed status=" + status + ' ' + fields);
  }

  static final class SeedResponse {
    private final int canonicalCount;
    private final List<String> addedCredentialIds;

    SeedResponse(int canonicalCount, List<String> addedCredentialIds) {
      this.canonicalCount = canonicalCount;
      this.addedCredentialIds = List.copyOf(addedCredentialIds);
    }

    static SeedResponse disabled(int canonicalCount) {
      return new SeedResponse(canonicalCount, List.of());
    }

    int canonicalCount() {
      return canonicalCount;
    }

    List<String> addedCredentialIds() {
      return addedCredentialIds;
    }

    int addedCount() {
      return addedCredentialIds.size();
    }

    int existingCount() {
      return canonicalCount - addedCredentialIds.size();
    }
  }
}
