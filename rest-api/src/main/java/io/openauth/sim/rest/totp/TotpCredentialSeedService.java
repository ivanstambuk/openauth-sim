package io.openauth.sim.rest.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.totp.TotpSeedApplicationService;
import io.openauth.sim.application.totp.TotpSeedApplicationService.SeedCommand;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.rest.ui.TotpOperatorSampleData;
import io.openauth.sim.rest.ui.TotpOperatorSampleData.SampleDefinition;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
final class TotpCredentialSeedService {

  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.totp.telemetry");

  static {
    TELEMETRY_LOGGER.setLevel(Level.ALL);
  }

  private final CredentialStore credentialStore;
  private final TotpSeedApplicationService seedApplicationService;

  TotpCredentialSeedService(
      ObjectProvider<CredentialStore> credentialStoreProvider,
      TotpSeedApplicationService seedApplicationService) {
    this.credentialStore = credentialStoreProvider.getIfAvailable();
    this.seedApplicationService =
        Objects.requireNonNull(seedApplicationService, "seedApplicationService");
  }

  SeedResult seedCanonicalCredentials() {
    List<SampleDefinition> definitions = TotpOperatorSampleData.seedDefinitions();
    if (credentialStore == null) {
      SeedResult result = SeedResult.disabled(definitions.size());
      logSeed(Level.WARNING, "unavailable", result, definitions, "credential store unavailable");
      return result;
    }

    List<SeedCommand> commands =
        definitions.stream()
            .map(
                definition ->
                    new SeedCommand(
                        definition.credentialId(),
                        definition.sharedSecretHex(),
                        definition.algorithm(),
                        definition.digits(),
                        Duration.ofSeconds(definition.stepSeconds()),
                        TotpDriftWindow.of(
                            definition.driftBackwardSteps(), definition.driftForwardSteps()),
                        definition.metadata()))
            .collect(Collectors.toUnmodifiableList());

    TotpSeedApplicationService.SeedResult applicationResult =
        seedApplicationService.seed(commands, credentialStore);
    SeedResult result = new SeedResult(definitions.size(), applicationResult.addedCredentialIds());
    String status = result.addedCount() == 0 ? "noop" : "seeded";
    logSeed(Level.INFO, status, result, definitions, null);
    return result;
  }

  private void logSeed(
      Level level,
      String status,
      SeedResult result,
      List<SampleDefinition> definitions,
      String reason) {
    String telemetryId = "rest-totp-seed-" + UUID.randomUUID();
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("addedCount", result.addedCount());
    fields.put("canonicalCount", result.canonicalCount());
    fields.put("existingCount", result.existingCount());
    fields.put("trigger", "ui");
    fields.put(
        "algorithms",
        definitions.stream()
            .map(sample -> sample.algorithm().name())
            .collect(Collectors.toUnmodifiableList()));
    fields.put(
        "stepSeconds",
        definitions.stream()
            .map(SampleDefinition::stepSeconds)
            .map(Long::valueOf)
            .collect(Collectors.toUnmodifiableList()));
    fields.put(
        "digits",
        definitions.stream()
            .map(SampleDefinition::digits)
            .collect(Collectors.toUnmodifiableList()));
    if (!result.addedCredentialIds().isEmpty()) {
      fields.put("addedCredentialIds", result.addedCredentialIds());
    }

    TelemetryFrame frame =
        TelemetryContracts.totpSeedingAdapter()
            .status(status, telemetryId, status, true, reason, fields);
    logFrame(level, frame);
  }

  private void logFrame(Level level, TelemetryFrame frame) {
    Objects.requireNonNull(frame, "frame");
    StringBuilder builder =
        new StringBuilder("event=rest.")
            .append(frame.event())
            .append(" status=")
            .append(frame.status());
    frame
        .fields()
        .forEach((key, value) -> builder.append(' ').append(key).append('=').append(value));
    LogRecord record = new LogRecord(level, builder.toString());
    TELEMETRY_LOGGER.log(record);
    for (Handler handler : TELEMETRY_LOGGER.getHandlers()) {
      handler.publish(record);
      handler.flush();
    }
  }

  static final class SeedResult {
    private final int canonicalCount;
    private final List<String> addedCredentialIds;

    SeedResult(int canonicalCount, List<String> addedCredentialIds) {
      this.canonicalCount = canonicalCount;
      this.addedCredentialIds = List.copyOf(addedCredentialIds);
    }

    static SeedResult disabled(int canonicalCount) {
      return new SeedResult(canonicalCount, List.of());
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
