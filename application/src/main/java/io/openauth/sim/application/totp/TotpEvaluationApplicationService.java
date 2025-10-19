package io.openauth.sim.application.totp;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.otp.totp.TotpValidator;
import io.openauth.sim.core.otp.totp.TotpVerificationResult;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level orchestrator for validating TOTP submissions. */
public final class TotpEvaluationApplicationService {

    private static final String INLINE_DESCRIPTOR_NAME = "totp-inline-request";

    private final CredentialStore credentialStore;
    private final Clock clock;
    private final TotpCredentialPersistenceAdapter persistenceAdapter;

    public TotpEvaluationApplicationService(CredentialStore credentialStore) {
        this(credentialStore, Clock.systemUTC());
    }

    public TotpEvaluationApplicationService(CredentialStore credentialStore, Clock clock) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.persistenceAdapter = new TotpCredentialPersistenceAdapter();
    }

    public EvaluationResult evaluate(EvaluationCommand command) {
        Objects.requireNonNull(command, "command");
        if (command instanceof EvaluationCommand.Stored stored) {
            return evaluateStored(stored);
        }
        if (command instanceof EvaluationCommand.Inline inline) {
            return evaluateInline(inline);
        }
        throw new IllegalStateException("Unsupported TOTP evaluation command: " + command);
    }

    private EvaluationResult evaluateStored(EvaluationCommand.Stored command) {
        Credential credential =
                credentialStore.findByName(command.credentialId()).orElse(null);
        if (credential == null) {
            return credentialNotFound(command.credentialId());
        }
        if (credential.type() != CredentialType.OATH_TOTP) {
            return validationFailure(
                    command.credentialId(),
                    true,
                    null,
                    null,
                    null,
                    command.driftWindow(),
                    "credential_not_totp",
                    "Credential is not a TOTP entry",
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE);
        }

        TotpDescriptor descriptor;
        try {
            descriptor = persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential));
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    command.credentialId(),
                    true,
                    null,
                    null,
                    null,
                    command.driftWindow(),
                    "credential_metadata_invalid",
                    ex.getMessage(),
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE);
        }

        String candidateOtp = sanitizeOtp(command.otp());
        boolean validationRequested = !candidateOtp.isEmpty();

        if (!validationRequested) {
            Instant evaluationInstant = defaultInstant(command.evaluationInstant());
            Instant generationInstant = command.timestampOverride().orElse(evaluationInstant);
            String generatedOtp = TotpGenerator.generate(descriptor, generationInstant);
            return successResult(
                    true,
                    command.credentialId(),
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    command.driftWindow(),
                    0,
                    command.timestampOverride().isPresent(),
                    "generated",
                    generatedOtp);
        }

        if (!isValidOtpFormat(candidateOtp, descriptor.digits())) {
            return validationFailure(
                    command.credentialId(),
                    true,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    command.driftWindow(),
                    "otp_invalid_format",
                    "OTP must be numeric and match the required digit length",
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE);
        }

        Instant evaluationInstant = defaultInstant(command.evaluationInstant());
        TotpVerificationResult verification = TotpValidator.verify(
                descriptor,
                candidateOtp,
                evaluationInstant,
                command.driftWindow(),
                command.timestampOverride().orElse(null));

        if (verification.valid()) {
            return successResult(
                    true,
                    command.credentialId(),
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    command.driftWindow(),
                    verification.matchedSkewSteps(),
                    command.timestampOverride().isPresent(),
                    "validated",
                    null);
        }

        return validationFailure(
                command.credentialId(),
                true,
                descriptor.algorithm(),
                descriptor.digits(),
                descriptor.stepDuration(),
                command.driftWindow(),
                "otp_out_of_window",
                "OTP did not match within the permitted drift window",
                command.timestampOverride().isPresent(),
                verification.matchedSkewSteps());
    }

    private EvaluationResult evaluateInline(EvaluationCommand.Inline command) {
        SecretMaterial secret;
        try {
            secret = SecretMaterial.fromHex(command.sharedSecretHex().trim());
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    null,
                    false,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow(),
                    "shared_secret_invalid",
                    ex.getMessage(),
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE);
        }

        TotpDescriptor descriptor;
        try {
            descriptor = TotpDescriptor.create(
                    INLINE_DESCRIPTOR_NAME,
                    secret,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow());
        } catch (IllegalArgumentException ex) {
            return validationFailure(
                    null,
                    false,
                    command.algorithm(),
                    command.digits(),
                    command.stepDuration(),
                    command.driftWindow(),
                    "validation_error",
                    ex.getMessage(),
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE);
        }

        String candidateOtp = sanitizeOtp(command.otp());
        boolean validationRequested = !candidateOtp.isEmpty();

        if (!validationRequested) {
            Instant evaluationInstant = defaultInstant(command.evaluationInstant());
            Instant generationInstant = command.timestampOverride().orElse(evaluationInstant);
            String generatedOtp = TotpGenerator.generate(descriptor, generationInstant);
            return successResult(
                    false,
                    null,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    descriptor.driftWindow(),
                    0,
                    command.timestampOverride().isPresent(),
                    "generated",
                    generatedOtp);
        }

        if (!isValidOtpFormat(candidateOtp, descriptor.digits())) {
            return validationFailure(
                    null,
                    false,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    descriptor.driftWindow(),
                    "otp_invalid_format",
                    "OTP must be numeric and match the required digit length",
                    command.timestampOverride().isPresent(),
                    Integer.MIN_VALUE);
        }

        Instant evaluationInstant = defaultInstant(command.evaluationInstant());
        TotpVerificationResult verification = TotpValidator.verify(
                descriptor,
                candidateOtp,
                evaluationInstant,
                descriptor.driftWindow(),
                command.timestampOverride().orElse(null));

        if (verification.valid()) {
            return successResult(
                    false,
                    null,
                    descriptor.algorithm(),
                    descriptor.digits(),
                    descriptor.stepDuration(),
                    descriptor.driftWindow(),
                    verification.matchedSkewSteps(),
                    command.timestampOverride().isPresent(),
                    "validated",
                    null);
        }

        return validationFailure(
                null,
                false,
                descriptor.algorithm(),
                descriptor.digits(),
                descriptor.stepDuration(),
                descriptor.driftWindow(),
                "otp_out_of_window",
                "OTP did not match within the permitted drift window",
                command.timestampOverride().isPresent(),
                verification.matchedSkewSteps());
    }

    private EvaluationResult credentialNotFound(String credentialId) {
        return validationFailure(
                credentialId,
                false,
                null,
                null,
                null,
                TotpDriftWindow.of(0, 0),
                "credential_not_found",
                "Credential not found",
                false,
                Integer.MIN_VALUE);
    }

    private EvaluationResult successResult(
            boolean credentialReference,
            String credentialId,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            int matchedSkewSteps,
            boolean timestampOverrideProvided,
            String reasonCode,
            String otp) {

        Map<String, Object> fields = telemetryFields(
                credentialReference,
                credentialId,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                timestampOverrideProvided,
                matchedSkewSteps);

        TelemetrySignal telemetry = new TelemetrySignal(TelemetryStatus.SUCCESS, reasonCode, null, true, fields);

        return new EvaluationResult(
                telemetry,
                credentialReference,
                credentialId,
                true,
                matchedSkewSteps,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                otp);
    }

    private EvaluationResult validationFailure(
            String credentialId,
            boolean credentialReference,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            String reasonCode,
            String reason,
            boolean timestampOverrideProvided,
            int matchedSkewSteps) {

        Map<String, Object> fields = telemetryFields(
                credentialReference,
                credentialId,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                timestampOverrideProvided,
                matchedSkewSteps);

        TelemetrySignal telemetry = new TelemetrySignal(TelemetryStatus.INVALID, reasonCode, reason, true, fields);

        return new EvaluationResult(
                telemetry,
                credentialReference,
                credentialId,
                false,
                matchedSkewSteps,
                algorithm,
                digits,
                stepDuration,
                driftWindow,
                null);
    }

    private Map<String, Object> telemetryFields(
            boolean credentialReference,
            String credentialId,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            boolean timestampOverrideProvided,
            int matchedSkewSteps) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialReference", credentialReference);
        if (credentialReference && credentialId != null) {
            fields.put("credentialId", credentialId);
        }
        if (algorithm != null) {
            fields.put("algorithm", algorithm.name());
        }
        if (digits != null) {
            fields.put("digits", digits);
        }
        if (stepDuration != null) {
            fields.put("stepSeconds", stepDuration.getSeconds());
        }
        if (driftWindow != null) {
            fields.put("driftBackwardSteps", driftWindow.backwardSteps());
            fields.put("driftForwardSteps", driftWindow.forwardSteps());
        }
        fields.put("timestampOverrideProvided", timestampOverrideProvided);
        fields.put("matchedSkewSteps", matchedSkewSteps);
        return fields;
    }

    private boolean isValidOtpFormat(String otp, int expectedDigits) {
        return otp.length() == expectedDigits && otp.chars().allMatch(Character::isDigit);
    }

    private String sanitizeOtp(String otp) {
        return otp == null ? "" : otp.trim();
    }

    private Instant defaultInstant(Instant provided) {
        return provided != null ? provided : clock.instant();
    }

    public sealed interface EvaluationCommand permits EvaluationCommand.Stored, EvaluationCommand.Inline {

        record Stored(
                String credentialId,
                String otp,
                TotpDriftWindow driftWindow,
                Instant evaluationInstant,
                Optional<Instant> timestampOverride)
                implements EvaluationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
                otp = Objects.requireNonNull(otp, "otp");
                driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
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
                implements EvaluationCommand {

            public Inline {
                sharedSecretHex = Objects.requireNonNull(sharedSecretHex, "sharedSecretHex");
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                stepDuration = Objects.requireNonNull(stepDuration, "stepDuration");
                otp = Objects.requireNonNull(otp, "otp");
                driftWindow = Objects.requireNonNull(driftWindow, "driftWindow");
                timestampOverride = timestampOverride == null ? Optional.empty() : timestampOverride;
            }
        }
    }

    public record EvaluationResult(
            TelemetrySignal telemetry,
            boolean credentialReference,
            String credentialId,
            boolean valid,
            int matchedSkewSteps,
            TotpHashAlgorithm algorithm,
            Integer digits,
            Duration stepDuration,
            TotpDriftWindow driftWindow,
            String otp) {

        public TelemetryFrame evaluationFrame(String telemetryId) {
            return telemetry.emit(TelemetryContracts.totpEvaluationAdapter(), telemetryId);
        }
    }

    public record TelemetrySignal(
            TelemetryStatus status, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {

        public TelemetryFrame emit(
                io.openauth.sim.application.telemetry.TotpTelemetryAdapter adapter, String telemetryId) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(telemetryId, "telemetryId");
            String statusKey =
                    switch (status) {
                        case SUCCESS -> "success";
                        case INVALID -> "invalid";
                        case ERROR -> "error";
                    };
            return adapter.status(statusKey, telemetryId, reasonCode, sanitized, reason, fields);
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }
}
