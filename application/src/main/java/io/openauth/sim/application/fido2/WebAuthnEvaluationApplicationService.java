package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAssertionRequest;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.fido2.WebAuthnVerificationResult;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level coordinator for WebAuthn assertion verification + telemetry emission. */
public final class WebAuthnEvaluationApplicationService {

    private final CredentialStore credentialStore;
    private final WebAuthnAssertionVerifier verifier;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    public WebAuthnEvaluationApplicationService(
            CredentialStore credentialStore,
            WebAuthnAssertionVerifier verifier,
            WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    public EvaluationResult evaluate(EvaluationCommand command) {
        return evaluate(command, false);
    }

    public EvaluationResult evaluate(EvaluationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        if (command instanceof EvaluationCommand.Stored stored) {
            return evaluateStored(stored, verbose);
        }
        if (command instanceof EvaluationCommand.Inline inline) {
            return evaluateInline(inline, verbose);
        }

        throw new IllegalArgumentException("Unsupported WebAuthn evaluation command: " + command);
    }

    private EvaluationResult evaluateStored(EvaluationCommand.Stored command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.assertion.evaluate.stored");
        metadata(trace, "protocol", "FIDO2");
        metadata(trace, "mode", "stored");
        metadata(trace, "credentialName", command.credentialId());
        try {
            Optional<Credential> credential = credentialStore.findByName(command.credentialId());
            if (credential.isEmpty()) {
                addStep(trace, step -> step.id("resolve.credential")
                        .summary("Resolve stored credential")
                        .detail("CredentialStore.findByName")
                        .attribute("credentialId", command.credentialId())
                        .attribute("found", false));
                return credentialNotFound(command, buildTrace(trace));
            }

            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", command.credentialId())
                    .attribute("found", true));

            WebAuthnCredentialDescriptor descriptor =
                    persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential.get()));
            addStep(trace, step -> step.id("deserialize.descriptor")
                    .summary("Deserialize stored descriptor")
                    .detail("WebAuthnCredentialPersistenceAdapter.deserialize")
                    .attribute("credentialId", descriptor.name())
                    .attribute("relyingPartyId", descriptor.relyingPartyId())
                    .attribute("algorithm", descriptor.algorithm().name()));

            WebAuthnStoredCredential storedCredential = descriptor.toStoredCredential();
            WebAuthnVerificationResult verification = verifier.verify(storedCredential, toRequest(command));

            return buildVerificationResult(
                    verification,
                    true,
                    descriptor.name(),
                    descriptor.relyingPartyId(),
                    command.origin(),
                    descriptor.algorithm(),
                    descriptor.userVerificationRequired(),
                    "stored",
                    trace);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("deserialize.descriptor")
                    .summary("Deserialize stored descriptor")
                    .detail("WebAuthnCredentialPersistenceAdapter.deserialize")
                    .note("failure", ex.getMessage()));
            return metadataFailure(command, ex.getMessage(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during stored evaluation")
                    .detail(ex.getClass().getName())
                    .note("message", ex.getMessage()));
            return unexpectedError(command.origin(), "stored", ex, buildTrace(trace));
        }
    }

    private EvaluationResult evaluateInline(EvaluationCommand.Inline command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.assertion.evaluate.inline");
        metadata(trace, "protocol", "FIDO2");
        metadata(trace, "mode", "inline");
        try {
            WebAuthnStoredCredential storedCredential = new WebAuthnStoredCredential(
                    command.relyingPartyId(),
                    command.credentialId(),
                    command.publicKeyCose(),
                    command.signatureCounter(),
                    command.userVerificationRequired(),
                    command.algorithm());

            addStep(trace, step -> step.id("construct.credential")
                    .summary("Construct inline credential")
                    .detail("WebAuthnStoredCredential")
                    .attribute("relyingPartyId", command.relyingPartyId())
                    .attribute("algorithm", command.algorithm().name())
                    .attribute("userVerificationRequired", command.userVerificationRequired()));

            WebAuthnVerificationResult verification = verifier.verify(storedCredential, toRequest(command));

            return buildVerificationResult(
                    verification,
                    false,
                    null,
                    command.relyingPartyId(),
                    command.origin(),
                    command.algorithm(),
                    command.userVerificationRequired(),
                    "inline",
                    trace);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("construct.credential")
                    .summary("Construct inline credential")
                    .detail("WebAuthnStoredCredential")
                    .note("failure", ex.getMessage()));
            return inlineValidationFailure(command, ex.getMessage(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during inline evaluation")
                    .detail(ex.getClass().getName())
                    .note("message", ex.getMessage()));
            return unexpectedError(command.origin(), "inline", ex, buildTrace(trace));
        }
    }

    private EvaluationResult credentialNotFound(EvaluationCommand.Stored command, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(
                "stored", false, null, command.relyingPartyId(), command.origin(), null, false, Optional.empty());

        TelemetrySignal telemetry = new TelemetrySignal(
                TelemetryStatus.INVALID, "credential_not_found", "Credential not found", true, fields);

        return new EvaluationResult(
                telemetry, false, false, null, command.relyingPartyId(), null, false, Optional.empty(), trace);
    }

    private EvaluationResult metadataFailure(EvaluationCommand.Stored command, String reason, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(
                "stored",
                true,
                command.credentialId(),
                command.relyingPartyId(),
                command.origin(),
                null,
                false,
                Optional.empty());

        TelemetrySignal telemetry =
                new TelemetrySignal(TelemetryStatus.ERROR, "credential_invalid", reason, true, fields);

        return new EvaluationResult(
                telemetry,
                false,
                true,
                command.credentialId(),
                command.relyingPartyId(),
                null,
                false,
                Optional.empty(),
                trace);
    }

    private EvaluationResult inlineValidationFailure(
            EvaluationCommand.Inline command, String reason, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(
                "inline",
                false,
                null,
                command.relyingPartyId(),
                command.origin(),
                command.algorithm(),
                command.userVerificationRequired(),
                Optional.empty());

        TelemetrySignal telemetry =
                new TelemetrySignal(TelemetryStatus.INVALID, "inline_invalid", reason, true, fields);

        return new EvaluationResult(
                telemetry,
                false,
                false,
                null,
                command.relyingPartyId(),
                command.algorithm(),
                command.userVerificationRequired(),
                Optional.empty(),
                trace);
    }

    private EvaluationResult unexpectedError(String origin, String source, RuntimeException ex, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(source, false, null, null, origin, null, false, Optional.empty());

        TelemetrySignal telemetry =
                new TelemetrySignal(TelemetryStatus.ERROR, "unexpected_error", ex.getMessage(), true, fields);

        return new EvaluationResult(telemetry, false, false, null, null, null, false, Optional.empty(), trace);
    }

    private EvaluationResult buildVerificationResult(
            WebAuthnVerificationResult verification,
            boolean credentialReference,
            String credentialId,
            String relyingPartyId,
            String origin,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            String credentialSource,
            VerboseTrace.Builder trace) {

        boolean success = verification.success();
        Optional<WebAuthnVerificationError> error = verification.error();

        String reasonCode = success
                ? "match"
                : error.map(err -> err.name().toLowerCase(Locale.US)).orElse("verification_failed");

        Map<String, Object> fields = telemetryFields(
                credentialSource,
                credentialReference,
                credentialId,
                relyingPartyId,
                origin,
                algorithm,
                userVerificationRequired,
                error);

        TelemetrySignal telemetry = new TelemetrySignal(
                success ? TelemetryStatus.SUCCESS : TelemetryStatus.INVALID,
                reasonCode,
                success ? null : verification.message(),
                true,
                fields);

        addStep(trace, step -> {
            step.id("verify.assertion")
                    .summary("Verify WebAuthn assertion")
                    .detail("WebAuthnAssertionVerifier.verify")
                    .attribute("credentialSource", credentialSource)
                    .attribute("valid", success);
            error.map(Enum::name).ifPresent(err -> step.note("error", err));
        });

        return new EvaluationResult(
                telemetry,
                success,
                credentialReference,
                credentialReference ? credentialId : null,
                relyingPartyId,
                algorithm,
                userVerificationRequired,
                error,
                buildTrace(trace));
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(
            VerboseTrace.Builder trace, java.util.function.Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
    }

    private static WebAuthnAssertionRequest toRequest(EvaluationCommand command) {
        if (command instanceof EvaluationCommand.Stored stored) {
            return new WebAuthnAssertionRequest(
                    stored.relyingPartyId(),
                    stored.origin(),
                    stored.expectedChallenge(),
                    stored.clientDataJson(),
                    stored.authenticatorData(),
                    stored.signature(),
                    stored.expectedType());
        }
        if (command instanceof EvaluationCommand.Inline inline) {
            return new WebAuthnAssertionRequest(
                    inline.relyingPartyId(),
                    inline.origin(),
                    inline.expectedChallenge(),
                    inline.clientDataJson(),
                    inline.authenticatorData(),
                    inline.signature(),
                    inline.expectedType());
        }
        throw new IllegalArgumentException("Unsupported WebAuthn assertion command: " + command);
    }

    private static Map<String, Object> telemetryFields(
            String credentialSource,
            boolean credentialReference,
            String credentialId,
            String relyingPartyId,
            String origin,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            Optional<WebAuthnVerificationError> error) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", credentialSource);
        fields.put("credentialReference", credentialReference);

        if (credentialReference && hasText(credentialId)) {
            fields.put("credentialId", credentialId);
        }
        if (hasText(relyingPartyId)) {
            fields.put("relyingPartyId", relyingPartyId);
        }
        if (hasText(origin)) {
            fields.put("origin", origin);
        }
        if (algorithm != null) {
            fields.put("algorithm", algorithm.label());
        }
        fields.put("userVerificationRequired", userVerificationRequired);
        error.ifPresent(err -> fields.put("error", err.name().toLowerCase(Locale.US)));
        return fields;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public sealed interface EvaluationCommand permits EvaluationCommand.Stored, EvaluationCommand.Inline {

        record Stored(
                String credentialId,
                String relyingPartyId,
                String origin,
                String expectedType,
                byte[] expectedChallenge,
                byte[] clientDataJson,
                byte[] authenticatorData,
                byte[] signature)
                implements EvaluationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
                relyingPartyId =
                        Objects.requireNonNull(relyingPartyId, "relyingPartyId").trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                expectedType =
                        Objects.requireNonNull(expectedType, "expectedType").trim();
                expectedChallenge = expectedChallenge == null ? new byte[0] : expectedChallenge.clone();
                clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
                authenticatorData = authenticatorData == null ? new byte[0] : authenticatorData.clone();
                signature = signature == null ? new byte[0] : signature.clone();
            }

            @Override
            public byte[] expectedChallenge() {
                return expectedChallenge.clone();
            }

            @Override
            public byte[] clientDataJson() {
                return clientDataJson.clone();
            }

            @Override
            public byte[] authenticatorData() {
                return authenticatorData.clone();
            }

            @Override
            public byte[] signature() {
                return signature.clone();
            }
        }

        record Inline(
                String credentialName,
                String relyingPartyId,
                String origin,
                String expectedType,
                byte[] credentialId,
                byte[] publicKeyCose,
                long signatureCounter,
                boolean userVerificationRequired,
                WebAuthnSignatureAlgorithm algorithm,
                byte[] expectedChallenge,
                byte[] clientDataJson,
                byte[] authenticatorData,
                byte[] signature)
                implements EvaluationCommand {

            public Inline {
                credentialName = credentialName == null ? "webAuthn-inline" : credentialName.trim();
                relyingPartyId =
                        Objects.requireNonNull(relyingPartyId, "relyingPartyId").trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                expectedType =
                        Objects.requireNonNull(expectedType, "expectedType").trim();
                credentialId = credentialId == null ? new byte[0] : credentialId.clone();
                publicKeyCose = publicKeyCose == null ? new byte[0] : publicKeyCose.clone();
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                expectedChallenge = expectedChallenge == null ? new byte[0] : expectedChallenge.clone();
                clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
                authenticatorData = authenticatorData == null ? new byte[0] : authenticatorData.clone();
                signature = signature == null ? new byte[0] : signature.clone();
            }

            @Override
            public byte[] credentialId() {
                return credentialId.clone();
            }

            @Override
            public byte[] publicKeyCose() {
                return publicKeyCose.clone();
            }

            @Override
            public byte[] expectedChallenge() {
                return expectedChallenge.clone();
            }

            @Override
            public byte[] clientDataJson() {
                return clientDataJson.clone();
            }

            @Override
            public byte[] authenticatorData() {
                return authenticatorData.clone();
            }

            @Override
            public byte[] signature() {
                return signature.clone();
            }
        }
    }

    public record EvaluationResult(
            TelemetrySignal telemetry,
            boolean valid,
            boolean credentialReference,
            String credentialId,
            String relyingPartyId,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            Optional<WebAuthnVerificationError> error,
            VerboseTrace trace) {

        public EvaluationResult {
            Objects.requireNonNull(telemetry, "telemetry");
            error = error == null ? Optional.empty() : error;
        }

        public TelemetryFrame evaluationFrame(Fido2TelemetryAdapter adapter, String telemetryId) {
            return telemetry.emit(adapter, telemetryId);
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public record TelemetrySignal(
            TelemetryStatus status, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {

        public TelemetrySignal {
            status = Objects.requireNonNull(status, "status");
            reasonCode = reasonCode == null ? "unspecified" : reasonCode;
            fields = Map.copyOf(new LinkedHashMap<>(fields == null ? Map.of() : fields));
        }

        public TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(telemetryId, "telemetryId");
            String eventStatus =
                    switch (status) {
                        case SUCCESS -> "success";
                        case INVALID -> "invalid";
                        case ERROR -> "error";
                    };
            return adapter.status(eventStatus, telemetryId, reasonCode, sanitized, reason, fields);
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }
}
