package io.openauth.sim.rest.emv.cap;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapCredentialDescriptor;
import io.openauth.sim.core.emv.cap.EmvCapCredentialPersistenceAdapter;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
final class EmvCapEvaluationService {

    private static final Logger TELEMETRY_LOGGER = Logger.getLogger("io.openauth.sim.rest.emv.cap.telemetry");
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("^[0-9A-FX]+$");
    private static final EmvCapCredentialPersistenceAdapter CREDENTIAL_ADAPTER =
            new EmvCapCredentialPersistenceAdapter();

    private final EmvCapEvaluationApplicationService applicationService;
    private final ObjectProvider<CredentialStore> credentialStoreProvider;

    EmvCapEvaluationService(
            EmvCapEvaluationApplicationService applicationService,
            ObjectProvider<CredentialStore> credentialStoreProvider) {
        this.applicationService = Objects.requireNonNull(applicationService, "applicationService");
        this.credentialStoreProvider = Objects.requireNonNull(credentialStoreProvider, "credentialStoreProvider");
    }

    EmvCapEvaluationResponse evaluate(EmvCapEvaluationRequest request) {
        if (request == null) {
            throw validation("invalid_request", "Request body is required", Map.of());
        }

        boolean includeTrace = request.includeTrace() == null || Boolean.TRUE.equals(request.includeTrace());

        String rawCredentialId = request.credentialId();
        boolean credentialIdProvided = rawCredentialId != null;
        Optional<String> credentialId =
                Optional.ofNullable(rawCredentialId).map(String::trim).filter(value -> !value.isEmpty());

        if (credentialIdProvided && credentialId.isEmpty()) {
            throw validation("missing_field", "credentialId is required", Map.of("field", "credentialId"));
        }

        EmvCapCredentialDescriptor descriptor =
                credentialId.map(this::loadDescriptor).orElse(null);

        EmvCapMode mode = resolveMode(request.mode(), descriptor, credentialId);

        String masterKey = resolveHex(
                request.masterKey(), descriptor != null ? descriptor.masterKey().asHex() : null, "masterKey");
        String atc = resolveHex(request.atc(), descriptor != null ? descriptor.defaultAtcHex() : null, "atc");
        int branchFactor = resolvePositive(
                request.branchFactor(), descriptor != null ? descriptor.branchFactor() : null, "branchFactor");
        int height = resolvePositive(request.height(), descriptor != null ? descriptor.height() : null, "height");
        String iv = resolveHex(request.iv(), descriptor != null ? descriptor.ivHex() : null, "iv");
        String cdol1 = resolveHex(request.cdol1(), descriptor != null ? descriptor.cdol1Hex() : null, "cdol1");
        String issuerProprietaryBitmap = resolveHex(
                request.issuerProprietaryBitmap(),
                descriptor != null ? descriptor.issuerProprietaryBitmapHex() : null,
                "issuerProprietaryBitmap");
        String iccTemplate = resolveTemplate(
                request.iccDataTemplate(),
                descriptor != null ? descriptor.iccDataTemplateHex() : null,
                "iccDataTemplate");
        String issuerApplicationData = resolveHex(
                request.issuerApplicationData(),
                descriptor != null ? descriptor.issuerApplicationDataHex() : null,
                "issuerApplicationData");

        CustomerInputs customerInputs = resolveCustomerInputs(request.customerInputs(), descriptor);
        TransactionData transactionData = resolveTransactionData(request.transactionData(), descriptor);

        EvaluationRequest evaluationRequest;
        try {
            evaluationRequest = new EvaluationRequest(
                    mode,
                    masterKey,
                    atc,
                    branchFactor,
                    height,
                    iv,
                    cdol1,
                    issuerProprietaryBitmap,
                    customerInputs,
                    transactionData,
                    iccTemplate,
                    issuerApplicationData);
        } catch (IllegalArgumentException ex) {
            throw validation(
                    "invalid_input", normalizeMessage(ex.getMessage()), Map.of("field", extractField(ex.getMessage())));
        }

        String credentialSource = credentialId.isPresent() ? "stored" : "inline";

        EvaluationResult result = applicationService.evaluate(evaluationRequest, includeTrace);
        TelemetrySignal signal = result.telemetry();
        TelemetryFrame frame = appendCredentialMetadata(
                result.telemetryFrame(nextTelemetryId()), credentialSource, credentialId.orElse(null));

        logTelemetry(frame, credentialSource);

        if (signal.status() == TelemetryStatus.SUCCESS) {
            return successResponse(result, evaluationRequest, frame, signal, includeTrace);
        }

        if (signal.status() == TelemetryStatus.INVALID) {
            Map<String, Object> details = new LinkedHashMap<>(signal.fields());
            if (signal.reason() != null && !signal.reason().isBlank()) {
                details.put("reason", signal.reason().trim().replaceAll("\\s+", " "));
            }
            details.putIfAbsent("mode", mode.name());
            details.putIfAbsent("sanitized", signal.sanitized());
            details.put("credentialSource", credentialSource);
            credentialId.ifPresent(id -> details.put("credentialId", id));
            throw validation(signal.reasonCode(), "EMV/CAP input validation failed", details);
        }

        Map<String, Object> details = new LinkedHashMap<>(signal.fields());
        details.putIfAbsent("mode", mode.name());
        details.putIfAbsent("sanitized", signal.sanitized());
        details.put("credentialSource", credentialSource);
        credentialId.ifPresent(id -> details.put("credentialId", id));
        if (signal.reason() != null && !signal.reason().isBlank()) {
            details.put("reason", signal.reason().trim().replaceAll("\\s+", " "));
        }
        throw unexpected("EMV/CAP evaluation failed unexpectedly", details);
    }

    private EmvCapEvaluationResponse successResponse(
            EvaluationResult result,
            EvaluationRequest evaluationRequest,
            TelemetryFrame frame,
            TelemetrySignal signal,
            boolean includeTrace) {
        EmvCapTracePayload tracePayload = null;
        if (includeTrace) {
            tracePayload = result.traceOptional()
                    .map(trace -> toTracePayload(trace, evaluationRequest))
                    .orElse(null);
        }
        EmvCapTelemetryPayload telemetryPayload = new EmvCapTelemetryPayload(frame, signal.reasonCode());
        return new EmvCapEvaluationResponse(result.otp(), result.maskLength(), tracePayload, telemetryPayload);
    }

    private EmvCapTracePayload toTracePayload(Trace trace, EvaluationRequest evaluationRequest) {
        if (trace == null) {
            return null;
        }
        EmvCapTracePayload.GenerateAcInput generateAcInput = new EmvCapTracePayload.GenerateAcInput(
                trace.generateAcInput().terminalHex(), trace.generateAcInput().iccHex());
        return new EmvCapTracePayload(
                trace.masterKeySha256(),
                trace.sessionKey(),
                generateAcInput,
                trace.generateAcResult(),
                trace.bitmask(),
                trace.maskedDigits(),
                trace.issuerApplicationData(),
                evaluationRequest.iccDataTemplateHex(),
                trace.generateAcInput().iccHex());
    }

    private static String safeDecimal(String value) {
        return value == null ? "" : value.trim();
    }

    private static Optional<String> optionalHex(String value, String field) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw validation(
                    "invalid_hex_length",
                    field + " must contain an even number of hex characters",
                    Map.of("field", field));
        }
        if (!HEX_PATTERN.matcher(normalized).matches()) {
            throw validation("invalid_hex", field + " must be hexadecimal", Map.of("field", field));
        }
        return Optional.of(normalized);
    }

    private String resolveHex(String value, String fallback, String field) {
        if (hasText(value)) {
            return requireHex(value, field);
        }
        if (fallback != null) {
            return fallback;
        }
        throw validation("missing_field", field + " is required", Map.of("field", field));
    }

    private String resolveTemplate(String value, String fallback, String field) {
        if (hasText(value)) {
            return requireTemplate(value, field);
        }
        if (fallback != null) {
            return fallback;
        }
        throw validation("missing_field", field + " is required", Map.of("field", field));
    }

    private int resolvePositive(Integer value, Integer fallback, String field) {
        if (value != null) {
            return requirePositive(value, field);
        }
        if (fallback != null) {
            return fallback;
        }
        throw validation("missing_field", field + " is required", Map.of("field", field));
    }

    private CustomerInputs resolveCustomerInputs(
            EmvCapEvaluationRequest.CustomerInputs payload, EmvCapCredentialDescriptor descriptor) {
        String challenge = payload != null && payload.challenge() != null
                ? safeDecimal(payload.challenge())
                : descriptor != null ? descriptor.defaultChallenge() : "";
        String reference = payload != null && payload.reference() != null
                ? safeDecimal(payload.reference())
                : descriptor != null ? descriptor.defaultReference() : "";
        String amount = payload != null && payload.amount() != null
                ? safeDecimal(payload.amount())
                : descriptor != null ? descriptor.defaultAmount() : "";
        return new CustomerInputs(challenge, reference, amount);
    }

    private TransactionData resolveTransactionData(
            EmvCapEvaluationRequest.TransactionData payload, EmvCapCredentialDescriptor descriptor) {
        Optional<String> terminal = descriptor != null ? descriptor.terminalDataHex() : Optional.empty();
        Optional<String> icc =
                descriptor != null ? descriptor.resolvedIccDataHex().or(descriptor::iccDataHex) : Optional.empty();

        if (payload != null) {
            if (payload.terminal() != null) {
                terminal = optionalHex(payload.terminal(), "transactionData.terminal");
            }
            if (payload.icc() != null) {
                icc = optionalHex(payload.icc(), "transactionData.icc");
            }
        }

        return new TransactionData(terminal, icc);
    }

    private static TelemetryFrame appendCredentialMetadata(
            TelemetryFrame frame, String credentialSource, String credentialId) {
        Map<String, Object> fields = new LinkedHashMap<>(frame.fields());
        fields.put("credentialSource", credentialSource);
        if (credentialId != null && !credentialId.isBlank()) {
            fields.put("credentialId", credentialId);
        }
        return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), fields);
    }

    private EmvCapMode resolveMode(String value, EmvCapCredentialDescriptor descriptor, Optional<String> credentialId) {
        if (hasText(value)) {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            try {
                EmvCapMode parsed = EmvCapMode.fromLabel(normalized);
                if (descriptor != null && parsed != descriptor.mode()) {
                    throw validation(
                            "mode_mismatch",
                            "Stored credential "
                                    + credentialId.orElse("unknown")
                                    + " is registered as "
                                    + descriptor.mode()
                                    + " but request specified "
                                    + parsed,
                            Map.of(
                                    "field",
                                    "mode",
                                    "expected",
                                    descriptor.mode().name()));
                }
                return parsed;
            } catch (IllegalArgumentException ex) {
                throw validation(
                        "invalid_mode",
                        "Mode must be IDENTIFY, RESPOND, or SIGN",
                        Map.of("field", "mode", "allowedValues", "IDENTIFY, RESPOND, SIGN"));
            }
        }
        if (descriptor != null) {
            return descriptor.mode();
        }
        throw validation("missing_field", "mode is required", Map.of("field", "mode"));
    }

    private EmvCapCredentialDescriptor loadDescriptor(String credentialId) {
        CredentialStore store = credentialStoreProvider.getIfAvailable();
        if (store == null) {
            throw validation(
                    "credential_store_unavailable",
                    "Stored EMV/CAP credential support is not configured",
                    Map.of("field", "credentialId"));
        }
        Credential credential = store.findByName(credentialId)
                .orElseThrow(() -> validation(
                        "credential_not_found",
                        "Unknown EMV/CAP credential " + credentialId,
                        Map.of("field", "credentialId", "credentialId", credentialId)));
        return CREDENTIAL_ADAPTER.deserialize(VersionedCredentialRecordMapper.toRecord(credential));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireTemplate(String value, String field) {
        String normalized = requireText(value, field).toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw validation(
                    "invalid_template_length",
                    field + " must contain an even number of characters",
                    Map.of("field", field));
        }
        if (!TEMPLATE_PATTERN.matcher(normalized).matches()) {
            throw validation(
                    "invalid_template",
                    field + " must contain hexadecimal characters or 'X' placeholders",
                    Map.of("field", field));
        }
        return normalized;
    }

    private static String requireHex(String value, String field) {
        String normalized = requireText(value, field).toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw validation(
                    "invalid_hex_length",
                    field + " must contain an even number of hex characters",
                    Map.of("field", field));
        }
        if (!HEX_PATTERN.matcher(normalized).matches()) {
            throw validation("invalid_hex", field + " must be hexadecimal", Map.of("field", field));
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null) {
            throw validation("missing_field", field + " is required", Map.of("field", field));
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw validation("missing_field", field + " is required", Map.of("field", field));
        }
        return trimmed;
    }

    private static int requirePositive(Integer value, String field) {
        if (value == null) {
            throw validation("missing_field", field + " is required", Map.of("field", field));
        }
        if (value <= 0) {
            throw validation("invalid_number", field + " must be positive", Map.of("field", field));
        }
        return value;
    }

    private static void logTelemetry(TelemetryFrame frame, String credentialSource) {
        if (!TELEMETRY_LOGGER.isLoggable(Level.FINE)) {
            return;
        }
        StringBuilder builder = new StringBuilder("event=")
                .append(frame.event())
                .append(" status=")
                .append(frame.status())
                .append(" credentialSource=")
                .append(credentialSource);
        frame.fields().forEach((key, value) -> {
            if (!"sessionKey".equalsIgnoreCase(key) && !"masterKey".equalsIgnoreCase(key)) {
                builder.append(' ').append(key).append('=').append(value);
            }
        });
        TELEMETRY_LOGGER.fine(builder.toString());
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return "Invalid input";
        }
        return message.trim().replaceAll("\\s+", " ");
    }

    private static String extractField(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String trimmed = message.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex);
        }
        return trimmed;
    }

    private static String nextTelemetryId() {
        return "rest-emv-cap-" + UUID.randomUUID();
    }

    private static EmvCapEvaluationValidationException validation(
            String reasonCode, String message, Map<String, Object> details) {
        return new EmvCapEvaluationValidationException(reasonCode, message, details);
    }

    private static EmvCapEvaluationUnexpectedException unexpected(String message, Map<String, Object> details) {
        return new EmvCapEvaluationUnexpectedException(message, null, details);
    }
}
