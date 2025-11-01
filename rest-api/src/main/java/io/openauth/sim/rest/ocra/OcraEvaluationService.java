package io.openauth.sim.rest.ocra;

import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationValidationException;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.NormalizedRequest;
import io.openauth.sim.application.ocra.OcraEvaluationRequests;
import io.openauth.sim.application.ocra.OcraInlineIdentifiers;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.rest.VerboseTracePayload;
import io.openauth.sim.rest.support.InlineSecretInput;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
class OcraEvaluationService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

    static {
        TELEMETRY_LOGGER.setLevel(Level.ALL);
    }

    private final OcraEvaluationApplicationService applicationService;

    OcraEvaluationService(OcraEvaluationApplicationService applicationService) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
    }

    OcraEvaluationResponse evaluate(OcraEvaluationRequest rawRequest) {
        Objects.requireNonNull(rawRequest, "request");

        long started = System.nanoTime();
        String telemetryId = nextTelemetryId();
        CommandEnvelope envelope = null;

        try {
            boolean verbose = Boolean.TRUE.equals(rawRequest.verbose());
            envelope = CommandEnvelope.from(rawRequest);
            EvaluationResult result = applicationService.evaluate(envelope.command(), verbose);
            NormalizedRequest normalized = result.request();
            long durationMillis = toMillis(started);

            TelemetryFrame frame = TelemetryContracts.ocraEvaluationAdapter()
                    .success(
                            telemetryId,
                            successFields(
                                    result,
                                    hasText(normalized.sessionHex()),
                                    hasText(normalized.clientChallenge()),
                                    hasText(normalized.serverChallenge()),
                                    hasText(normalized.pinHashHex()),
                                    hasText(normalized.timestampHex()),
                                    durationMillis));

            logEvaluation(Level.INFO, frame);

            VerboseTracePayload tracePayload =
                    result.verboseTrace().map(VerboseTracePayload::from).orElse(null);
            return new OcraEvaluationResponse(result.suite(), result.otp(), telemetryId, tracePayload);
        } catch (EvaluationValidationException ex) {
            long durationMillis = toMillis(started);
            FailureDetails failure = FailureDetails.from(ex);
            String suite = suiteOrUnknown(envelope, rawRequest);

            TelemetryFrame frame = TelemetryContracts.ocraEvaluationAdapter()
                    .validationFailure(
                            telemetryId,
                            failure.reasonCode(),
                            failure.message(),
                            failure.sanitized(),
                            failureFields(
                                    suite,
                                    hasCredentialReference(envelope, rawRequest),
                                    hasSession(envelope, rawRequest),
                                    hasClientChallenge(envelope, rawRequest),
                                    hasServerChallenge(envelope, rawRequest),
                                    hasPin(envelope, rawRequest),
                                    hasTimestamp(envelope, rawRequest),
                                    durationMillis));

            logEvaluation(Level.WARNING, frame);

            throw new OcraEvaluationValidationException(
                    telemetryId,
                    suite,
                    failure.field(),
                    failure.reasonCode(),
                    failure.message(),
                    failure.sanitized(),
                    ex,
                    null);
        } catch (ValidationError ex) {
            long durationMillis = toMillis(started);
            FailureDetails failure = FailureDetails.from(ex);
            String suite = suiteOrUnknown(envelope, rawRequest);

            TelemetryFrame frame = TelemetryContracts.ocraEvaluationAdapter()
                    .validationFailure(
                            telemetryId,
                            failure.reasonCode(),
                            failure.message(),
                            failure.sanitized(),
                            failureFields(
                                    suite,
                                    hasCredentialReference(envelope, rawRequest),
                                    hasSession(envelope, rawRequest),
                                    hasClientChallenge(envelope, rawRequest),
                                    hasServerChallenge(envelope, rawRequest),
                                    hasPin(envelope, rawRequest),
                                    hasTimestamp(envelope, rawRequest),
                                    durationMillis));

            logEvaluation(Level.WARNING, frame);

            throw new OcraEvaluationValidationException(
                    telemetryId, suite, failure.field(), failure.reasonCode(), failure.message(), true, ex, null);
        } catch (IllegalArgumentException ex) {
            long durationMillis = toMillis(started);
            FailureDetails failure = FailureDetails.fromIllegalArgument(ex.getMessage());
            String suite = suiteOrUnknown(envelope, rawRequest);

            TelemetryFrame frame = TelemetryContracts.ocraEvaluationAdapter()
                    .validationFailure(
                            telemetryId,
                            failure.reasonCode(),
                            failure.message(),
                            true,
                            failureFields(
                                    suite,
                                    hasCredentialReference(envelope, rawRequest),
                                    hasSession(envelope, rawRequest),
                                    hasClientChallenge(envelope, rawRequest),
                                    hasServerChallenge(envelope, rawRequest),
                                    hasPin(envelope, rawRequest),
                                    hasTimestamp(envelope, rawRequest),
                                    durationMillis));

            logEvaluation(Level.WARNING, frame);

            throw new OcraEvaluationValidationException(
                    telemetryId, suite, failure.field(), failure.reasonCode(), failure.message(), true, ex, null);
        } catch (RuntimeException ex) {
            long durationMillis = toMillis(started);
            String suite = suiteOrUnknown(envelope, rawRequest);

            TelemetryFrame frame = TelemetryContracts.ocraEvaluationAdapter()
                    .error(
                            telemetryId,
                            "unexpected_error",
                            ex.getMessage(),
                            false,
                            failureFields(
                                    suite,
                                    hasCredentialReference(envelope, rawRequest),
                                    hasSession(envelope, rawRequest),
                                    hasClientChallenge(envelope, rawRequest),
                                    hasServerChallenge(envelope, rawRequest),
                                    hasPin(envelope, rawRequest),
                                    hasTimestamp(envelope, rawRequest),
                                    durationMillis));

            logEvaluation(Level.SEVERE, frame);
            throw ex;
        }
    }

    private static boolean hasSession(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        return envelope != null ? hasText(envelope.normalized().sessionHex()) : hasText(raw.sessionHex());
    }

    private static boolean hasClientChallenge(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        return envelope != null ? hasText(envelope.normalized().clientChallenge()) : hasText(raw.clientChallenge());
    }

    private static boolean hasServerChallenge(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        return envelope != null ? hasText(envelope.normalized().serverChallenge()) : hasText(raw.serverChallenge());
    }

    private static boolean hasPin(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        return envelope != null ? hasText(envelope.normalized().pinHashHex()) : hasText(raw.pinHashHex());
    }

    private static boolean hasTimestamp(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        return envelope != null ? hasText(envelope.normalized().timestampHex()) : hasText(raw.timestampHex());
    }

    private static Map<String, Object> successFields(
            EvaluationResult result,
            boolean hasSession,
            boolean hasClientChallenge,
            boolean hasServerChallenge,
            boolean hasPin,
            boolean hasTimestamp,
            long durationMillis) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("suite", Objects.requireNonNullElse(result.suite(), "unknown"));
        fields.put("hasCredentialReference", result.credentialReference());
        fields.put("hasSessionPayload", hasSession);
        fields.put("hasClientChallenge", hasClientChallenge);
        fields.put("hasServerChallenge", hasServerChallenge);
        fields.put("hasPin", hasPin);
        fields.put("hasTimestamp", hasTimestamp);
        fields.put("durationMillis", durationMillis);
        return fields;
    }

    private static Map<String, Object> failureFields(
            String suite,
            boolean hasCredentialReference,
            boolean hasSession,
            boolean hasClientChallenge,
            boolean hasServerChallenge,
            boolean hasPin,
            boolean hasTimestamp,
            long durationMillis) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("suite", Objects.requireNonNullElse(suite, "unknown"));
        fields.put("hasCredentialReference", hasCredentialReference);
        fields.put("hasSessionPayload", hasSession);
        fields.put("hasClientChallenge", hasClientChallenge);
        fields.put("hasServerChallenge", hasServerChallenge);
        fields.put("hasPin", hasPin);
        fields.put("hasTimestamp", hasTimestamp);
        fields.put("durationMillis", durationMillis);
        return fields;
    }

    private static void logEvaluation(Level level, TelemetryFrame frame) {
        StringBuilder builder = new StringBuilder("event=rest.")
                .append(frame.event())
                .append(" status=")
                .append(frame.status());

        frame.fields()
                .forEach((key, value) ->
                        builder.append(' ').append(key).append('=').append(value));

        LogRecord record = new LogRecord(level, builder.toString());
        TELEMETRY_LOGGER.log(record);
        for (Handler handler : TELEMETRY_LOGGER.getHandlers()) {
            handler.publish(record);
            handler.flush();
        }
    }

    private static boolean hasCredentialReference(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        if (envelope != null) {
            return envelope.credentialReference();
        }
        return hasText(raw.credentialId());
    }

    private static String suiteOrUnknown(CommandEnvelope envelope, OcraEvaluationRequest raw) {
        if (envelope != null) {
            NormalizedRequest normalized = envelope.normalized();
            if (normalized instanceof NormalizedRequest.InlineSecret inline) {
                return inline.suite();
            }
            if (envelope.requestedSuite() != null) {
                return envelope.requestedSuite();
            }
        }
        String suite = raw.suite();
        return suite == null || suite.isBlank() ? "unknown" : suite.trim();
    }

    private static long toMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nextTelemetryId() {
        return "rest-ocra-" + UUID.randomUUID();
    }

    private record CommandEnvelope(
            EvaluationCommand command,
            NormalizedRequest normalized,
            boolean credentialReference,
            String requestedSuite) {

        static CommandEnvelope from(OcraEvaluationRequest request) {
            boolean hasCredential = hasText(request.credentialId());
            boolean hasInline = InlineSecretInput.hasSecret(request.sharedSecretHex(), request.sharedSecretBase32());

            if (hasCredential && hasInline) {
                throw new ValidationError(
                        "request", "credential_conflict", "Provide either credentialId or sharedSecretHex, not both");
            }
            if (!hasCredential && !hasInline) {
                throw new ValidationError(
                        "request", "credential_missing", "credentialId or sharedSecretHex must be provided");
            }

            if (hasCredential) {
                EvaluationCommand command = OcraEvaluationRequests.stored(new OcraEvaluationRequests.StoredInputs(
                        request.credentialId(),
                        request.challenge(),
                        request.sessionHex(),
                        request.clientChallenge(),
                        request.serverChallenge(),
                        request.pinHashHex(),
                        request.timestampHex(),
                        request.counter()));
                NormalizedRequest normalized = OcraEvaluationApplicationService.NormalizedRequest.from(command);
                return new CommandEnvelope(command, normalized, true, request.suite());
            }

            String suite = request.suite();
            if (!hasText(suite)) {
                throw new ValidationError("suite", "missing_required", "suite is required for inline mode");
            }

            String secretHex = InlineSecretInput.resolveHex(
                    request.sharedSecretHex(),
                    request.sharedSecretBase32(),
                    () -> new ValidationError(
                            "sharedSecretHex",
                            "shared_secret_missing",
                            "sharedSecretHex or sharedSecretBase32 must be provided"),
                    () -> new ValidationError(
                            "request",
                            "shared_secret_conflict",
                            "Provide either sharedSecretHex or sharedSecretBase32, not both"),
                    message -> new ValidationError("sharedSecretBase32", "shared_secret_base32_invalid", message));

            String identifier = inlineIdentifier(suite, secretHex);
            EvaluationCommand command = OcraEvaluationRequests.inline(new OcraEvaluationRequests.InlineInputs(
                    identifier,
                    suite,
                    secretHex,
                    request.challenge(),
                    request.sessionHex(),
                    request.clientChallenge(),
                    request.serverChallenge(),
                    request.pinHashHex(),
                    request.timestampHex(),
                    request.counter(),
                    null));
            NormalizedRequest normalized = OcraEvaluationApplicationService.NormalizedRequest.from(command);
            return new CommandEnvelope(command, normalized, false, suite);
        }

        private static String inlineIdentifier(String suite, String secretHex) {
            return OcraInlineIdentifiers.sharedIdentifier(suite, secretHex);
        }
    }

    static final class ValidationError extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        private final String field;
        private final String reasonCode;

        ValidationError(String field, String reasonCode, String message) {
            super(message);
            this.field = field;
            this.reasonCode = reasonCode;
        }

        String field() {
            return field;
        }

        String reasonCode() {
            return reasonCode;
        }
    }

    record FailureDetails(String field, String reasonCode, String message, boolean sanitized) {

        static FailureDetails from(EvaluationValidationException exception) {
            return new FailureDetails(
                    exception.field(), exception.reasonCode(), exception.getMessage(), exception.sanitized());
        }

        static FailureDetails from(ValidationError error) {
            return new FailureDetails(error.field(), error.reasonCode(), error.getMessage(), true);
        }

        static FailureDetails fromIllegalArgument(String message) {
            if (message == null || message.isBlank()) {
                return new FailureDetails("request", "invalid_input", "Invalid input", true);
            }
            String trimmed = message.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("pin") && lower.contains("sha1") && lower.contains("40")) {
                return new FailureDetails("pinHashHex", "pin_hash_mismatch", trimmed, true);
            }
            if (lower.contains("session") && lower.contains("required")) {
                return new FailureDetails(
                        "sessionHex", "session_required", "sessionHex is required for the requested suite", true);
            }
            if (lower.contains("session") && lower.contains("not permitted")) {
                return new FailureDetails("sessionHex", "session_not_permitted", trimmed, true);
            }
            if (lower.contains("timestamp") && lower.contains("outside")) {
                return new FailureDetails("timestampHex", "timestamp_drift_exceeded", trimmed, true);
            }
            if (lower.contains("timestamp") && lower.contains("not permitted")) {
                return new FailureDetails("timestampHex", "timestamp_not_permitted", trimmed, true);
            }
            if (lower.contains("timestamp") && lower.contains("valid time")) {
                return new FailureDetails("timestampHex", "timestamp_invalid", trimmed, true);
            }
            if (lower.contains("timestamphex") && lower.contains("hexadecimal")) {
                return new FailureDetails("timestampHex", "timestamp_invalid", trimmed, true);
            }
            if (lower.contains("pin") && lower.contains("not permitted")) {
                return new FailureDetails("pinHashHex", "pin_hash_not_permitted", trimmed, true);
            }
            if (lower.contains("pin") && lower.contains("required")) {
                return new FailureDetails("pinHashHex", "pin_hash_required", trimmed, true);
            }
            if (lower.contains("counter") && lower.contains("required")) {
                return new FailureDetails("counter", "counter_required", trimmed, true);
            }
            if (lower.contains("counter") && lower.contains("not permitted")) {
                return new FailureDetails("counter", "counter_not_permitted", trimmed, true);
            }
            if (lower.contains("counter") && lower.contains("negative")) {
                return new FailureDetails(
                        "counter", "counter_negative", "counter must be greater than or equal to zero", true);
            }
            if (lower.contains("countervalue") && lower.contains(">=")) {
                return new FailureDetails(
                        "counter", "counter_negative", "counter must be greater than or equal to zero", true);
            }
            if (lower.contains("credential") && lower.contains("required")) {
                return new FailureDetails("credentialId", "credential_missing", trimmed, true);
            }
            if (lower.contains("credentialid") && lower.contains("sharedsecrethex") && lower.contains("provided")) {
                return new FailureDetails("credentialId", "credential_missing", trimmed, true);
            }
            if (lower.contains("sharedsecret") && lower.contains("required")) {
                return new FailureDetails("sharedSecretHex", "missing_required", trimmed, true);
            }
            return new FailureDetails("request", "invalid_input", trimmed, true);
        }
    }
}
