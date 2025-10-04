package io.openauth.sim.rest.ocra;

import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/ocra", produces = MediaType.APPLICATION_JSON_VALUE)
final class OcraCredentialDirectoryController {

  private static final Comparator<OcraCredentialSummary> SUMMARY_COMPARATOR =
      Comparator.comparing(OcraCredentialSummary::getId, String.CASE_INSENSITIVE_ORDER);

  private final CredentialStore credentialStore;
  private final OcraCredentialSeedService seedService;

  OcraCredentialDirectoryController(
      ObjectProvider<CredentialStore> credentialStoreProvider,
      OcraCredentialSeedService seedService) {
    this.credentialStore = credentialStoreProvider.getIfAvailable();
    this.seedService = Objects.requireNonNull(seedService, "seedService");
  }

  @GetMapping("/credentials")
  List<OcraCredentialSummary> listCredentials() {
    if (credentialStore == null) {
      return List.of();
    }
    return credentialStore.findAll().stream()
        .filter(credential -> credential.type() == CredentialType.OATH_OCRA)
        .map(OcraCredentialDirectoryController::toSummary)
        .sorted(SUMMARY_COMPARATOR)
        .collect(Collectors.toUnmodifiableList());
  }

  @PostMapping(value = "/credentials/seed", produces = MediaType.APPLICATION_JSON_VALUE)
  SeedResponse seedCredentials() {
    OcraCredentialSeedService.SeedResult result = seedService.seedCanonicalCredentials();
    return new SeedResponse(
        result.addedCount(), result.canonicalCount(), result.addedCredentialIds());
  }

  static final class SeedResponse {
    private final int addedCount;
    private final int canonicalCount;
    private final List<String> addedCredentialIds;

    SeedResponse(int addedCount, int canonicalCount, List<String> addedCredentialIds) {
      this.addedCount = addedCount;
      this.canonicalCount = canonicalCount;
      this.addedCredentialIds = List.copyOf(addedCredentialIds);
    }

    public int getAddedCount() {
      return addedCount;
    }

    public int getCanonicalCount() {
      return canonicalCount;
    }

    public List<String> getAddedCredentialIds() {
      return addedCredentialIds;
    }
  }

  private static OcraCredentialSummary toSummary(Credential credential) {
    String identifier = credential.name();
    String suite = credential.attributes().get(OcraCredentialPersistenceAdapter.ATTR_SUITE);
    String label = buildLabel(identifier, suite);
    return new OcraCredentialSummary(identifier, label, suite);
  }

  private static String buildLabel(String identifier, String suite) {
    if (!StringUtils.hasText(identifier)) {
      return identifier;
    }
    if (!StringUtils.hasText(suite)) {
      return identifier;
    }
    return identifier + " (" + suite + ")";
  }
}
