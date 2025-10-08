package io.openauth.sim.application.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.telemetry.TotpTelemetryAdapter;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level orchestrator for replaying TOTP submissions without mutating state. */
public final class TotpReplayApplicationService {

  private final TotpEvaluationApplicationService evaluationService;

  public TotpReplayApplicationService(TotpEvaluationApplicationService evaluationService) {
    this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService");
  }

  public ReplayResult replay(ReplayCommand command) {
    Objects.requireNonNull(command, "command");

    if (command instanceof ReplayCommand.Stored stored) {
      EvaluationResult result =
          evaluationService.evaluate(
              new EvaluationCommand.Stored(
                  stored.credentialId(),
                  stored.otp(),
                  stored.driftWindow(),
                  stored.evaluationInstant(),
                  stored.timestampOverride()));
      return translate(result, "stored", true, stored.credentialId());
    }

    if (command instanceof ReplayCommand.Inline inline) {
      EvaluationResult result =
          evaluationService.evaluate(
              new EvaluationCommand.Inline(
                  inline.sharedSecretHex(),
                  inline.algorithm(),
                  inline.digits(),
                  inline.stepDuration(),
                  inline.otp(),
                  inline.driftWindow(),
                  inline.evaluationInstant(),
                  inline.timestampOverride()));
      return translate(result, "inline", false, null);
    }

    throw new IllegalStateException("Unsupported TOTP replay command: " + command);
  }

  private ReplayResult translate(
      EvaluationResult evaluationResult,
      String credentialSource,
      boolean credentialReference,
      String credentialId) {

    TelemetrySignal base = evaluationResult.telemetry();
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", credentialSource);
    fields.put("credentialReference", credentialReference);
    if (credentialReference && credentialId != null && !credentialId.isBlank()) {
      fields.put("credentialId", credentialId);
    }
    if (evaluationResult.algorithm() != null) {
      fields.put("algorithm", evaluationResult.algorithm().name());
    }
    if (evaluationResult.digits() != null) {
      fields.put("digits", evaluationResult.digits());
    }
    if (evaluationResult.stepDuration() != null) {
      fields.put("stepSeconds", evaluationResult.stepDuration().getSeconds());
    }
    if (evaluationResult.driftWindow() != null) {
      fields.put("driftBackwardSteps", evaluationResult.driftWindow().backwardSteps());
      fields.put("driftForwardSteps", evaluationResult.driftWindow().forwardSteps());
    }

    Map<String, Object> baseFields = base.fields();
    boolean timestampOverrideProvided =
        baseFields != null
            && Boolean.parseBoolean(
                String.valueOf(
                    baseFields.getOrDefault("timestampOverrideProvided", Boolean.FALSE)));
    fields.put("timestampOverrideProvided", timestampOverrideProvided);
    fields.put("matchedSkewSteps", evaluationResult.matchedSkewSteps());

    TelemetrySignal telemetry =
        switch (base.status()) {
          case SUCCESS ->
              new TelemetrySignal(TelemetryStatus.SUCCESS, "match", base.reason(), true, fields);
          case INVALID ->
              new TelemetrySignal(
                  TelemetryStatus.INVALID, base.reasonCode(), base.reason(), true, fields);
          case ERROR ->
              new TelemetrySignal(
                  TelemetryStatus.ERROR, base.reasonCode(), base.reason(), true, fields);
        };

    return new ReplayResult(
        telemetry,
        credentialReference,
        credentialId,
        base.status() == TelemetryStatus.SUCCESS,
        evaluationResult.matchedSkewSteps(),
        evaluationResult.algorithm(),
        evaluationResult.digits(),
        evaluationResult.stepDuration(),
        evaluationResult.driftWindow(),
        credentialSource,
        timestampOverrideProvided);
  }

  public sealed interface ReplayCommand permits ReplayCommand.Stored, ReplayCommand.Inline {

    String otp();

    TotpDriftWindow driftWindow();

    Instant evaluationInstant();

    Optional<Instant> timestampOverride();

    record Stored(
        String credentialId,
        String otp,
        TotpDriftWindow driftWindow,
        Instant evaluationInstant,
        Optional<Instant> timestampOverride)
        implements ReplayCommand {

      public Stored {
        credentialId = Objects.requireNonNull(credentialId, "credentialId").trim();
        otp = Objects.requireNonNull(otp, "otp");
        driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
        evaluationInstant = evaluationInstant;
        timestampOverride = timestampOverride == null ? Optional.empty() : timestampOverride;
      }
    }

    record Inline(
        String sharedSecretHex,
        TotpHashAlgorithm algorithm,
        int digits,
        Duration stepDuration,
        String otp,
        TotpDriftWindow driftWindow,
        Instant evaluationInstant,
        Optional<Instant> timestampOverride)
        implements ReplayCommand {

      public Inline {
        sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
        algorithm = Objects.requireNonNull(algorithm, "algorithm");
        stepDuration = Objects.requireNonNull(stepDuration, "stepDuration");
        otp = Objects.requireNonNull(otp, "otp");
        driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
        evaluationInstant = evaluationInstant;
        timestampOverride = timestampOverride == null ? Optional.empty() : timestampOverride;
      }
    }
  }

  public record ReplayResult(
      TelemetrySignal telemetry,
      boolean credentialReference,
      String credentialId,
      boolean match,
      int matchedSkewSteps,
      TotpHashAlgorithm algorithm,
      Integer digits,
      Duration stepDuration,
      TotpDriftWindow driftWindow,
      String credentialSource,
      boolean timestampOverrideProvided) {

    public TelemetryFrame replayFrame(String telemetryId) {
      return telemetry.emit(TelemetryContracts.totpReplayAdapter(), telemetryId);
    }

    public TelemetryFrame replayFrame(TotpTelemetryAdapter adapter, String telemetryId) {
      return telemetry.emit(adapter, telemetryId);
    }
  }
}
