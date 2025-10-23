package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.trace.VerboseTrace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level wrapper that provides replay diagnostics for WebAuthn assertions. */
public final class WebAuthnReplayApplicationService {

    private final WebAuthnEvaluationApplicationService evaluationService;

    public WebAuthnReplayApplicationService(WebAuthnEvaluationApplicationService evaluationService) {
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService");
    }

    public ReplayResult replay(ReplayCommand command) {
        return replay(command, false);
    }

    public ReplayResult replay(ReplayCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        if (command instanceof ReplayCommand.Stored stored) {
            var evaluationResult = evaluationService.evaluate(
                    new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                            stored.credentialId(),
                            stored.relyingPartyId(),
                            stored.origin(),
                            stored.expectedType(),
                            stored.expectedChallenge(),
                            stored.clientDataJson(),
                            stored.authenticatorData(),
                            stored.signature()),
                    verbose);
            return fromEvaluation(evaluationResult, "stored");
        }

        if (command instanceof ReplayCommand.Inline inline) {
            var evaluationResult = evaluationService.evaluate(
                    new WebAuthnEvaluationApplicationService.EvaluationCommand.Inline(
                            inline.credentialName(),
                            inline.relyingPartyId(),
                            inline.origin(),
                            inline.expectedType(),
                            inline.credentialId(),
                            inline.publicKeyCose(),
                            inline.signatureCounter(),
                            inline.userVerificationRequired(),
                            inline.algorithm(),
                            inline.expectedChallenge(),
                            inline.clientDataJson(),
                            inline.authenticatorData(),
                            inline.signature()),
                    verbose);
            return fromEvaluation(evaluationResult, "inline");
        }

        throw new IllegalArgumentException("Unsupported WebAuthn replay command: " + command);
    }

    public sealed interface ReplayCommand permits ReplayCommand.Stored, ReplayCommand.Inline {

        String relyingPartyId();

        String origin();

        String expectedType();

        byte[] expectedChallenge();

        byte[] clientDataJson();

        byte[] authenticatorData();

        byte[] signature();

        record Stored(
                String credentialId,
                String relyingPartyId,
                String origin,
                String expectedType,
                byte[] expectedChallenge,
                byte[] clientDataJson,
                byte[] authenticatorData,
                byte[] signature)
                implements ReplayCommand {

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
                implements ReplayCommand {

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

    public record ReplayResult(
            WebAuthnEvaluationApplicationService.TelemetrySignal telemetry,
            boolean match,
            boolean credentialReference,
            String credentialId,
            String credentialSource,
            Optional<WebAuthnVerificationError> error,
            Map<String, Object> supplementalFields,
            VerboseTrace trace) {

        public ReplayResult {
            Objects.requireNonNull(telemetry, "telemetry");
            credentialSource = credentialSource == null ? "unknown" : credentialSource;
            error = error == null ? Optional.empty() : error;
            supplementalFields =
                    Map.copyOf(new LinkedHashMap<>(supplementalFields == null ? Map.of() : supplementalFields));
        }

        public TelemetryFrame replayFrame(
                io.openauth.sim.application.telemetry.Fido2TelemetryAdapter adapter, String telemetryId) {
            return telemetry.emit(adapter, telemetryId);
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    private ReplayResult fromEvaluation(
            WebAuthnEvaluationApplicationService.EvaluationResult evaluationResult, String credentialSource) {

        Map<String, Object> supplemental = new LinkedHashMap<>();
        supplemental.put(
                "evaluationStatus", evaluationResult.telemetry().status().name());

        return new ReplayResult(
                evaluationResult.telemetry(),
                evaluationResult.valid(),
                evaluationResult.credentialReference(),
                evaluationResult.credentialId(),
                credentialSource,
                evaluationResult.error(),
                supplemental,
                evaluationResult.verboseTrace().orElse(null));
    }
}
