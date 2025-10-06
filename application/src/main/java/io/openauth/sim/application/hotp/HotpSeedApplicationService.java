package io.openauth.sim.application.hotp;

import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Application service responsible for seeding canonical HOTP credentials. */
public final class HotpSeedApplicationService {

  /**
   * Seeds the provided HOTP credentials into the {@link CredentialStore}, skipping identifiers that
   * already exist.
   */
  public SeedResult seed(List<SeedCommand> commands, CredentialStore credentialStore) {
    Objects.requireNonNull(commands, "commands");
    Objects.requireNonNull(credentialStore, "credentialStore");

    HotpIssuanceApplicationService issuanceService =
        new HotpIssuanceApplicationService(credentialStore);

    List<String> added = new ArrayList<>();
    for (SeedCommand command : commands) {
      if (credentialStore.exists(command.credentialId())) {
        continue;
      }

      HotpIssuanceApplicationService.IssuanceCommand issuanceCommand =
          new HotpIssuanceApplicationService.IssuanceCommand(
              command.credentialId(),
              command.sharedSecretHex(),
              command.algorithm(),
              command.digits(),
              command.counter(),
              command.metadata());

      HotpIssuanceApplicationService.IssuanceResult result = issuanceService.issue(issuanceCommand);
      if (result.created()) {
        added.add(command.credentialId());
      }
    }

    return new SeedResult(List.copyOf(added));
  }

  /** Command describing a canonical HOTP credential to seed. */
  public record SeedCommand(
      String credentialId,
      String sharedSecretHex,
      HotpHashAlgorithm algorithm,
      int digits,
      long counter,
      Map<String, String> metadata) {

    public SeedCommand {
      Objects.requireNonNull(credentialId, "credentialId");
      Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
      Objects.requireNonNull(algorithm, "algorithm");
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
