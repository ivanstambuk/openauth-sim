package io.openauth.sim.application.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapCredentialDescriptor;
import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Application-level orchestrator for EMV/CAP replay validation. */
public final class EmvCapReplayApplicationService {

    private static final EmvCapCredentialPersistenceAdapter CREDENTIAL_ADAPTER =
            new EmvCapCredentialPersistenceAdapter();

    private static final String SOURCE_STORED = "stored";
    private static final String SOURCE_INLINE = "inline";
    private static final String REASON_MATCH = "match";
    private static final String REASON_MISMATCH = "otp_mismatch";

    private final CredentialStore credentialStore;
    private final EmvCapEvaluationApplicationService evaluationService;

    public EmvCapReplayApplicationService(
            CredentialStore credentialStore, EmvCapEvaluationApplicationService evaluationService) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.evaluationService = Objects.requireNonNull(evaluationService, "evaluationService");
    }

    public ReplayResult replay(ReplayCommand command) {
        return replay(command, false);
    }

    public ReplayResult replay(ReplayCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");
        String normalizedOtp = normalizeOtp(command.otp());

        if (command instanceof ReplayCommand.Stored stored) {
            EvaluationRequest request = resolveStoredRequest(stored);
            return processReplay(
                    request,
                    normalizedOtp,
                    SOURCE_STORED,
                    Optional.of(stored.credentialId()),
                    stored.driftBackward(),
                    stored.driftForward(),
                    verbose);
        }

        if (command instanceof ReplayCommand.Inline inline) {
            return processReplay(
                    inline.request(),
                    normalizedOtp,
                    SOURCE_INLINE,
                    Optional.empty(),
                    inline.driftBackward(),
                    inline.driftForward(),
                    verbose);
        }

        throw new IllegalArgumentException("Unsupported EMV/CAP replay command " + command);
    }

    private ReplayResult processReplay(
            EvaluationRequest baseRequest,
            String suppliedOtp,
            String credentialSource,
            Optional<String> credentialId,
            int driftBackward,
            int driftForward,
            boolean verbose) {

        Objects.requireNonNull(baseRequest, "baseRequest");

        List<Integer> deltas = previewDeltas(driftBackward, driftForward);
        Optional<EmvCapEvaluationApplicationService.Trace> lastTrace = Optional.empty();
        for (int delta : deltas) {
            EvaluationRequest candidate = delta == 0 ? baseRequest : adjustAtc(baseRequest, delta);
            if (candidate == null) {
                continue;
            }

            EmvCapEvaluationApplicationService.EvaluationResult evaluation =
                    evaluationService.evaluate(candidate, verbose);
            if (verbose) {
                lastTrace = evaluation.traceOptional();
            }
            if (otpMatches(evaluation.otp(), suppliedOtp)) {
                TelemetrySignal telemetry = successTelemetry(
                        candidate,
                        credentialSource,
                        credentialId,
                        driftBackward,
                        driftForward,
                        delta,
                        suppliedOtp.length());

                Optional<EmvCapEvaluationApplicationService.Trace> trace =
                        verbose ? evaluation.traceOptional() : Optional.empty();

                return new ReplayResult(
                        telemetry,
                        true,
                        OptionalInt.of(delta),
                        credentialSource,
                        credentialId,
                        driftBackward,
                        driftForward,
                        candidate.mode(),
                        Optional.of(candidate),
                        trace);
            }
        }

        TelemetrySignal telemetry = mismatchTelemetry(
                baseRequest, credentialSource, credentialId, driftBackward, driftForward, suppliedOtp.length());

        return new ReplayResult(
                telemetry,
                false,
                OptionalInt.empty(),
                credentialSource,
                credentialId,
                driftBackward,
                driftForward,
                baseRequest.mode(),
                Optional.of(baseRequest),
                lastTrace);
    }

    private EvaluationRequest resolveStoredRequest(ReplayCommand.Stored command) {
        Credential credential = credentialStore
                .findByName(command.credentialId())
                .orElseThrow(
                        () -> new IllegalArgumentException("Unknown EMV/CAP credential " + command.credentialId()));

        EmvCapCredentialDescriptor descriptor =
                CREDENTIAL_ADAPTER.deserialize(VersionedCredentialRecordMapper.toRecord(credential));

        if (descriptor.mode() != command.mode()) {
            throw new IllegalArgumentException("Credential " + command.credentialId() + " is registered as "
                    + descriptor.mode() + " but command requested " + command.mode());
        }

        if (command.overrideRequest().isPresent()) {
            EvaluationRequest override = command.overrideRequest().get();
            if (override.mode() != command.mode()) {
                throw new IllegalArgumentException("Override request mode must match command mode");
            }
            return override;
        }

        int previewBackward = command.driftBackward();
        int previewForward = command.driftForward();

        CustomerInputs inputs = new CustomerInputs(
                descriptor.defaultChallenge(), descriptor.defaultReference(), descriptor.defaultAmount());

        Optional<String> iccOverride = descriptor.resolvedIccDataHex().or(() -> descriptor.iccDataHex());
        TransactionData transactionData = new TransactionData(descriptor.terminalDataHex(), iccOverride);

        return new EvaluationRequest(
                descriptor.mode(),
                descriptor.masterKey().asHex(),
                descriptor.defaultAtcHex(),
                descriptor.branchFactor(),
                descriptor.height(),
                previewBackward,
                previewForward,
                descriptor.ivHex(),
                descriptor.cdol1Hex(),
                descriptor.issuerProprietaryBitmapHex(),
                inputs,
                transactionData,
                descriptor.iccDataTemplateHex(),
                descriptor.issuerApplicationDataHex());
    }

    private static String normalizeOtp(String otp) {
        Objects.requireNonNull(otp, "otp");
        String normalized = otp.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("otp must not be blank");
        }
        if (!normalized.matches("\\d+")) {
            throw new IllegalArgumentException("otp must contain only digits");
        }
        return normalized;
    }

    private static EvaluationRequest adjustAtc(EvaluationRequest request, int delta) {
        if (delta == 0) {
            return request;
        }
        String atc = request.atcHex();
        int width = atc.length();
        int base = Integer.parseUnsignedInt(atc, 16);
        long maxValue = (1L << (width * 4)) - 1;
        long candidateValue = base + (long) delta;
        if (candidateValue < 0 || candidateValue > maxValue) {
            return null;
        }
        String formatted = String.format(Locale.ROOT, "%0" + width + "X", candidateValue);
        return new EvaluationRequest(
                request.mode(),
                request.masterKeyHex(),
                formatted,
                request.branchFactor(),
                request.height(),
                request.previewWindowBackward(),
                request.previewWindowForward(),
                request.ivHex(),
                request.cdol1Hex(),
                request.issuerProprietaryBitmapHex(),
                request.customerInputs(),
                request.transactionData(),
                request.iccDataTemplateHex(),
                request.issuerApplicationDataHex());
    }

    private static List<Integer> previewDeltas(int backward, int forward) {
        List<Integer> deltas = new ArrayList<>();
        deltas.add(0);
        int limit = Math.max(backward, forward);
        for (int step = 1; step <= limit; step++) {
            if (step <= forward) {
                deltas.add(step);
            }
            if (step <= backward) {
                deltas.add(-step);
            }
        }
        return deltas;
    }

    private static boolean otpMatches(String candidate, String supplied) {
        return candidate != null && candidate.trim().equals(supplied);
    }

    private static TelemetrySignal successTelemetry(
            EvaluationRequest request,
            String credentialSource,
            Optional<String> credentialId,
            int driftBackward,
            int driftForward,
            int matchedDelta,
            int suppliedOtpLength) {

        Map<String, Object> fields = baseTelemetryFields(
                request, credentialSource, credentialId, driftBackward, driftForward, suppliedOtpLength);
        fields.put("matchedDelta", matchedDelta);
        fields.put("match", true);

        return new TelemetrySignal(request.mode(), TelemetryStatus.SUCCESS, REASON_MATCH, null, true, fields);
    }

    private static TelemetrySignal mismatchTelemetry(
            EvaluationRequest request,
            String credentialSource,
            Optional<String> credentialId,
            int driftBackward,
            int driftForward,
            int suppliedOtpLength) {

        Map<String, Object> fields = baseTelemetryFields(
                request, credentialSource, credentialId, driftBackward, driftForward, suppliedOtpLength);
        fields.put("match", false);

        return new TelemetrySignal(request.mode(), TelemetryStatus.INVALID, REASON_MISMATCH, null, true, fields);
    }

    private static Map<String, Object> baseTelemetryFields(
            EvaluationRequest request,
            String credentialSource,
            Optional<String> credentialId,
            int driftBackward,
            int driftForward,
            int suppliedOtpLength) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("mode", request.mode().name());
        fields.put("credentialSource", credentialSource);
        credentialId.ifPresent(id -> fields.put("credentialId", id));
        fields.put("suppliedOtpLength", suppliedOtpLength);
        fields.put("driftBackward", driftBackward);
        fields.put("driftForward", driftForward);
        fields.put("branchFactor", request.branchFactor());
        fields.put("height", request.height());
        fields.put("ipbMaskLength", request.issuerProprietaryBitmapHex().length() / 2);
        fields.put("atc", request.atcHex());
        return fields;
    }

    /** Describes a replay invocation originating from a facade. */
    public sealed interface ReplayCommand permits ReplayCommand.Stored, ReplayCommand.Inline {

        EmvCapMode mode();

        String otp();

        int driftBackward();

        int driftForward();

        /** Stored-credential replay referencing a seeded EMV/CAP descriptor. */
        record Stored(
                String credentialId,
                EmvCapMode mode,
                String otp,
                int driftBackward,
                int driftForward,
                Optional<EvaluationRequest> overrideRequest)
                implements ReplayCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
                if (credentialId.isEmpty()) {
                    throw new IllegalArgumentException("credentialId must not be empty");
                }
                mode = Objects.requireNonNull(mode, "mode");
                otp = Objects.requireNonNull(otp, "otp").trim();
                if (otp.isEmpty()) {
                    throw new IllegalArgumentException("otp must not be empty");
                }
                driftBackward = requireNonNegative(driftBackward, "driftBackward");
                driftForward = requireNonNegative(driftForward, "driftForward");
                overrideRequest = overrideRequest == null ? Optional.empty() : overrideRequest;
            }
        }

        /** Inline replay supplying full EMV/CAP parameters without persistence. */
        record Inline(EvaluationRequest request, String otp, int driftBackward, int driftForward)
                implements ReplayCommand {

            public Inline {
                request = Objects.requireNonNull(request, "request");
                otp = Objects.requireNonNull(otp, "otp").trim();
                if (otp.isEmpty()) {
                    throw new IllegalArgumentException("otp must not be empty");
                }
                driftBackward = requireNonNegative(driftBackward, "driftBackward");
                driftForward = requireNonNegative(driftForward, "driftForward");
            }

            @Override
            public EmvCapMode mode() {
                return request.mode();
            }
        }

        private static int requireNonNegative(int value, String field) {
            if (value < 0) {
                throw new IllegalArgumentException(field + " must be non-negative");
            }
            return value;
        }
    }

    /** Result envelope returned to facades after processing a replay command. */
    public record ReplayResult(
            TelemetrySignal telemetry,
            boolean match,
            OptionalInt matchedDelta,
            String credentialSource,
            Optional<String> credentialId,
            int driftBackward,
            int driftForward,
            EmvCapMode mode,
            Optional<EvaluationRequest> effectiveRequest,
            Optional<EmvCapEvaluationApplicationService.Trace> trace) {

        public ReplayResult {
            telemetry = Objects.requireNonNull(telemetry, "telemetry");
            matchedDelta = matchedDelta == null ? OptionalInt.empty() : matchedDelta;
            credentialSource = Objects.requireNonNull(credentialSource, "credentialSource");
            credentialId = credentialId == null ? Optional.empty() : credentialId;
            driftBackward = requireNonNegative(driftBackward, "driftBackward");
            driftForward = requireNonNegative(driftForward, "driftForward");
            mode = Objects.requireNonNull(mode, "mode");
            effectiveRequest = effectiveRequest == null ? Optional.empty() : effectiveRequest;
            trace = trace == null ? Optional.empty() : trace;
        }

        public Optional<EmvCapEvaluationApplicationService.Trace> traceOptional() {
            return trace;
        }

        public TelemetryFrame telemetryFrame(String telemetryId) {
            return telemetry.emit(replayAdapterForMode(mode), telemetryId);
        }

        private static io.openauth.sim.application.telemetry.EmvCapTelemetryAdapter replayAdapterForMode(
                EmvCapMode mode) {
            return switch (mode) {
                case IDENTIFY -> TelemetryContracts.emvCapReplayIdentifyAdapter();
                case RESPOND -> TelemetryContracts.emvCapReplayRespondAdapter();
                case SIGN -> TelemetryContracts.emvCapReplaySignAdapter();
            };
        }

        private static int requireNonNegative(int value, String field) {
            if (value < 0) {
                throw new IllegalArgumentException(field + " must be non-negative");
            }
            return value;
        }
    }
}
