package io.openauth.sim.application.totp;

import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Helper seam that returns the current OTP and metadata for stored TOTP credentials. */
public final class TotpCurrentOtpHelperService {

    private final TotpEvaluationApplicationService evaluationService;
    private final Clock clock;

    public TotpCurrentOtpHelperService(TotpEvaluationApplicationService evaluationService, Clock clock) {
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Looks up the current OTP for a stored credential. The underlying evaluation service mirrors the
     * standard stored-evaluation path but skips OTP validation so the caller can inspect metadata and
     * reuse the generated code for subsequent calls.
     */
    public LookupResult lookup(LookupCommand command) {
        Objects.requireNonNull(command, "command");
        TotpDriftWindow driftWindow = Objects.requireNonNull(command.driftWindow(), "driftWindow");
        Instant evaluationInstant = command.evaluationInstant().orElse(clock.instant());
        Optional<Instant> timestampOverride = command.timestampOverride();

        TotpEvaluationApplicationService.EvaluationCommand.Stored evalCommand =
                new TotpEvaluationApplicationService.EvaluationCommand.Stored(
                        command.credentialId(), "", driftWindow, evaluationInstant, timestampOverride);

        TotpEvaluationApplicationService.EvaluationResult result = evaluationService.evaluate(evalCommand, false);
        Instant generationInstant = timestampOverride.orElse(evaluationInstant);
        return new LookupResult(result, generationInstant, timestampOverride.isPresent());
    }

    public record LookupCommand(
            String credentialId,
            TotpDriftWindow driftWindow,
            Optional<Instant> evaluationInstant,
            Optional<Instant> timestampOverride) {

        public LookupCommand {
            credentialId = Objects.requireNonNull(credentialId, "credentialId").trim();
            if (credentialId.isEmpty()) {
                throw new IllegalArgumentException("credentialId must not be blank");
            }
            driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
            evaluationInstant = evaluationInstant == null ? Optional.empty() : evaluationInstant;
            timestampOverride = timestampOverride == null ? Optional.empty() : timestampOverride;
        }
    }

    public record LookupResult(
            TotpEvaluationApplicationService.EvaluationResult evaluationResult,
            Instant generationInstant,
            boolean timestampOverrideProvided) {

        public LookupResult {
            Objects.requireNonNull(evaluationResult, "evaluationResult");
            Objects.requireNonNull(generationInstant, "generationInstant");
        }

        public Instant expiresAt() {
            Duration step =
                    evaluationResult.stepDuration() != null ? evaluationResult.stepDuration() : Duration.ofSeconds(30);
            return generationInstant.plus(step);
        }
    }
}
