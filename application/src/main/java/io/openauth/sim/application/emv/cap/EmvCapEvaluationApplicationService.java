package io.openauth.sim.application.emv.cap;

import io.openauth.sim.application.telemetry.EmvCapTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapEngine;
import io.openauth.sim.core.emv.cap.EmvCapInput;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapResult;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application-level orchestration for EMV/CAP simulations. */
public final class EmvCapEvaluationApplicationService {

    public EvaluationResult evaluate(EvaluationRequest request) {
        return evaluate(request, false);
    }

    public EvaluationResult evaluate(EvaluationRequest request, boolean verboseTrace) {
        Objects.requireNonNull(request, "request");
        try {
            EmvCapInput input = toDomainInput(request);
            EmvCapResult result = EmvCapEngine.evaluate(input);

            int maskedDigitsCount = countMaskedDigits(result.maskedDigitsOverlay());
            int ipbMaskLength = request.issuerProprietaryBitmapHex().length() / 2;

            TelemetrySignal telemetry = TelemetrySignal.success(
                    request.mode(),
                    request.atcHex(),
                    ipbMaskLength,
                    maskedDigitsCount,
                    request.branchFactor(),
                    request.height());

            Trace trace = verboseTrace ? toTrace(result, request) : null;

            return new EvaluationResult(telemetry, result.otp().decimal(), maskedDigitsCount, trace);
        } catch (IllegalArgumentException ex) {
            int ipbMaskLength = safeMaskLength(request);
            TelemetrySignal telemetry = TelemetrySignal.validationFailure(
                    request.mode(),
                    request.atcHex(),
                    ipbMaskLength,
                    request.branchFactor(),
                    request.height(),
                    ex.getMessage());
            return new EvaluationResult(telemetry, "", 0, null);
        } catch (RuntimeException ex) {
            int ipbMaskLength = safeMaskLength(request);
            TelemetrySignal telemetry = TelemetrySignal.error(
                    request.mode(),
                    request.atcHex(),
                    ipbMaskLength,
                    request.branchFactor(),
                    request.height(),
                    ex.getClass().getName() + ": " + ex.getMessage());
            return new EvaluationResult(telemetry, "", 0, null);
        }
    }

    private static EmvCapInput toDomainInput(EvaluationRequest request) {
        EmvCapInput.CustomerInputs customerInputs = new EmvCapInput.CustomerInputs(
                request.customerInputs().challenge(),
                request.customerInputs().reference(),
                request.customerInputs().amount());

        EmvCapInput.TransactionData transactionData = request.transactionData() == null
                ? EmvCapInput.TransactionData.empty()
                : new EmvCapInput.TransactionData(
                        request.transactionData().terminalHexOverride(),
                        request.transactionData().iccHexOverride());

        return new EmvCapInput(
                request.mode(),
                request.masterKeyHex(),
                request.atcHex(),
                request.branchFactor(),
                request.height(),
                request.ivHex(),
                request.cdol1Hex(),
                request.issuerProprietaryBitmapHex(),
                customerInputs,
                transactionData,
                request.iccDataTemplateHex(),
                request.issuerApplicationDataHex());
    }

    private static Trace toTrace(EmvCapResult result, EvaluationRequest request) {
        return new Trace(
                masterKeyDigest(request.masterKeyHex()),
                result.sessionKeyHex(),
                new Trace.GenerateAcInput(
                        result.generateAcInput().terminalHex(),
                        result.generateAcInput().iccHex()),
                result.generateAcResultHex(),
                result.bitmaskOverlay(),
                result.maskedDigitsOverlay(),
                request.issuerApplicationDataHex());
    }

    private static int safeMaskLength(EvaluationRequest request) {
        if (request == null || request.issuerProprietaryBitmapHex() == null) {
            return 0;
        }
        String bitmap = request.issuerProprietaryBitmapHex();
        if ((bitmap.length() & 1) == 1) {
            return 0;
        }
        return bitmap.length() / 2;
    }

    private static int countMaskedDigits(String maskedDigitsOverlay) {
        int count = 0;
        for (int i = 0; i < maskedDigitsOverlay.length(); i++) {
            if (maskedDigitsOverlay.charAt(i) != '.') {
                count++;
            }
        }
        return count;
    }

    /** Immutable evaluation request payload supplied by facades. */
    public record EvaluationRequest(
            EmvCapMode mode,
            String masterKeyHex,
            String atcHex,
            int branchFactor,
            int height,
            String ivHex,
            String cdol1Hex,
            String issuerProprietaryBitmapHex,
            CustomerInputs customerInputs,
            TransactionData transactionData,
            String iccDataTemplateHex,
            String issuerApplicationDataHex) {

        public EvaluationRequest {
            mode = Objects.requireNonNull(mode, "mode");
            masterKeyHex = normalizeHex(masterKeyHex, "masterKey");
            atcHex = normalizeHex(atcHex, "atc");
            branchFactor = requirePositive(branchFactor, "branchFactor");
            height = requirePositive(height, "height");
            ivHex = normalizeHex(ivHex, "iv");
            cdol1Hex = normalizeHex(cdol1Hex, "cdol1");
            issuerProprietaryBitmapHex = normalizeHex(issuerProprietaryBitmapHex, "issuerProprietaryBitmap");
            customerInputs = customerInputs == null ? new CustomerInputs("", "", "") : customerInputs;
            transactionData = transactionData == null ? TransactionData.empty() : transactionData;
            iccDataTemplateHex = normalizeTemplate(iccDataTemplateHex, "iccDataTemplate");
            issuerApplicationDataHex = normalizeHex(issuerApplicationDataHex, "issuerApplicationData");
        }
    }

    /** Wrapper around customer-supplied challenge/reference values. */
    public record CustomerInputs(String challenge, String reference, String amount) {

        public CustomerInputs {
            challenge = normalizeDecimal(challenge);
            reference = normalizeDecimal(reference);
            amount = normalizeDecimal(amount);
        }
    }

    /** Optional overrides for precomputed terminal/ICC payloads. */
    public record TransactionData(Optional<String> terminalHexOverride, Optional<String> iccHexOverride) {

        private static final TransactionData EMPTY = new TransactionData(Optional.empty(), Optional.empty());

        public TransactionData {
            terminalHexOverride = normalizeOptionalHex(terminalHexOverride, "transactionData.terminal");
            iccHexOverride = normalizeOptionalHex(iccHexOverride, "transactionData.icc");
        }

        public static TransactionData empty() {
            return EMPTY;
        }
    }

    /** Successful evaluation response returned to callers. */
    public record EvaluationResult(TelemetrySignal telemetry, String otp, int maskLength, Trace trace) {

        public Optional<Trace> traceOptional() {
            return Optional.ofNullable(trace);
        }

        public TelemetryFrame telemetryFrame(String telemetryId) {
            return telemetry.emit(adapterForMode(telemetry.mode()), telemetryId);
        }
    }

    /** Structured trace payload exposed to verbose consumers. */
    public record Trace(
            String masterKeySha256,
            String sessionKey,
            GenerateAcInput generateAcInput,
            String generateAcResult,
            String bitmask,
            String maskedDigits,
            String issuerApplicationData) {

        public Trace {
            masterKeySha256 = Objects.requireNonNull(masterKeySha256, "masterKeySha256");
            if (!masterKeySha256.startsWith("sha256:")) {
                throw new IllegalArgumentException("masterKeySha256 must start with sha256:");
            }
            sessionKey = normalizeHex(sessionKey, "trace.sessionKey");
            generateAcInput = Objects.requireNonNull(generateAcInput, "generateAcInput");
            generateAcResult = normalizeHex(generateAcResult, "trace.generateAcResult");
            bitmask = Objects.requireNonNull(bitmask, "bitmask");
            maskedDigits = Objects.requireNonNull(maskedDigits, "maskedDigits");
            issuerApplicationData = normalizeHex(issuerApplicationData, "trace.issuerApplicationData");
        }

        public record GenerateAcInput(String terminalHex, String iccHex) {

            public GenerateAcInput {
                terminalHex = normalizeHex(terminalHex, "trace.generateAcInput.terminal");
                iccHex = normalizeHex(iccHex, "trace.generateAcInput.icc");
            }
        }
    }

    private static String masterKeyDigest(String masterKeyHex) {
        String normalized = normalizeHex(masterKeyHex, "masterKey");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(hexToBytes(normalized));
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
        }
        return builder.toString();
    }

    /** Telemetry emission container for evaluation outcomes. */
    public record TelemetrySignal(
            EmvCapMode mode,
            TelemetryStatus status,
            String reasonCode,
            String reason,
            boolean sanitized,
            Map<String, Object> fields) {

        public TelemetryFrame emit(EmvCapTelemetryAdapter adapter, String telemetryId) {
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

        static TelemetrySignal success(
                EmvCapMode mode, String atc, int ipbMaskLength, int maskedDigitsCount, int branchFactor, int height) {
            return new TelemetrySignal(
                    mode,
                    TelemetryStatus.SUCCESS,
                    "generated",
                    null,
                    true,
                    telemetryFields(mode, atc, ipbMaskLength, maskedDigitsCount, branchFactor, height));
        }

        static TelemetrySignal validationFailure(
                EmvCapMode mode, String atc, int ipbMaskLength, int branchFactor, int height, String reason) {
            return new TelemetrySignal(
                    mode,
                    TelemetryStatus.INVALID,
                    "invalid_input",
                    reason,
                    true,
                    telemetryFields(mode, atc, ipbMaskLength, 0, branchFactor, height));
        }

        static TelemetrySignal error(
                EmvCapMode mode, String atc, int ipbMaskLength, int branchFactor, int height, String reason) {
            return new TelemetrySignal(
                    mode,
                    TelemetryStatus.ERROR,
                    "unexpected_error",
                    reason,
                    false,
                    telemetryFields(mode, atc, ipbMaskLength, 0, branchFactor, height));
        }

        private static Map<String, Object> telemetryFields(
                EmvCapMode mode, String atc, int ipbMaskLength, int maskedDigitsCount, int branchFactor, int height) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("mode", mode.name());
            fields.put("atc", atc);
            fields.put("ipbMaskLength", ipbMaskLength);
            fields.put("maskedDigitsCount", maskedDigitsCount);
            if (branchFactor > 0) {
                fields.put("branchFactor", branchFactor);
            }
            if (height > 0) {
                fields.put("height", height);
            }
            return fields;
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }

    private static EmvCapTelemetryAdapter adapterForMode(EmvCapMode mode) {
        return switch (mode) {
            case IDENTIFY -> TelemetryContracts.emvCapIdentifyAdapter();
            case RESPOND -> TelemetryContracts.emvCapRespondAdapter();
            case SIGN -> TelemetryContracts.emvCapSignAdapter();
        };
    }

    private static String normalizeHex(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of hex characters");
        }
        if (!normalized.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(field + " must be hexadecimal");
        }
        return normalized;
    }

    private static String normalizeTemplate(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " must contain an even number of characters");
        }
        if (!normalized.matches("[0-9A-FX]+")) {
            throw new IllegalArgumentException(field + " must contain hexadecimal characters or 'X' placeholders");
        }
        return normalized;
    }

    private static String normalizeDecimal(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static Optional<String> normalizeOptionalHex(Optional<String> value, String field) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        String text = value.get().trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if ((text.length() & 1) == 1) {
            throw new IllegalArgumentException(field + " override must contain an even number of hex characters");
        }
        if (!text.matches("[0-9A-F]+")) {
            throw new IllegalArgumentException(field + " override must be hexadecimal");
        }
        return Optional.of(text);
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
