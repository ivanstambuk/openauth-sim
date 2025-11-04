package io.openauth.sim.application.emv.cap;

import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.application.telemetry.EmvCapTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapEngine;
import io.openauth.sim.core.emv.cap.EmvCapInput;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapResult;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        int previewBackward = Math.max(0, request.previewWindowBackward());
        int previewForward = Math.max(0, request.previewWindowForward());
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
                    request.height(),
                    previewBackward,
                    previewForward);

            List<OtpPreview> previews = buildPreviewEntries(request, result, previewBackward, previewForward);
            Trace trace =
                    verboseTrace ? toTrace(result, request, maskedDigitsCount, previewBackward, previewForward) : null;

            return new EvaluationResult(telemetry, result.otp().decimal(), maskedDigitsCount, previews, trace);
        } catch (IllegalArgumentException ex) {
            int ipbMaskLength = safeMaskLength(request);
            TelemetrySignal telemetry = TelemetrySignal.validationFailure(
                    request.mode(),
                    request.atcHex(),
                    ipbMaskLength,
                    request.branchFactor(),
                    request.height(),
                    previewBackward,
                    previewForward,
                    ex.getMessage());
            return new EvaluationResult(telemetry, "", 0, List.of(), null);
        } catch (RuntimeException ex) {
            int ipbMaskLength = safeMaskLength(request);
            TelemetrySignal telemetry = TelemetrySignal.error(
                    request.mode(),
                    request.atcHex(),
                    ipbMaskLength,
                    request.branchFactor(),
                    request.height(),
                    previewBackward,
                    previewForward,
                    ex.getClass().getName() + ": " + ex.getMessage());
            return new EvaluationResult(telemetry, "", 0, List.of(), null);
        }
    }

    private static List<OtpPreview> buildPreviewEntries(
            EvaluationRequest request, EmvCapResult centerResult, int backward, int forward) {
        int sanitizedBackward = Math.max(0, backward);
        int sanitizedForward = Math.max(0, forward);
        List<OtpPreview> previews = new ArrayList<>();
        for (int delta = -sanitizedBackward; delta <= sanitizedForward; delta++) {
            Optional<String> adjustedAtc = adjustAtcHex(request.atcHex(), delta);
            if (adjustedAtc.isEmpty()) {
                continue;
            }
            String otp;
            if (delta == 0) {
                otp = centerResult.otp().decimal();
            } else {
                try {
                    EmvCapResult neighbor = EmvCapEngine.evaluate(toDomainInput(request, adjustedAtc.get()));
                    otp = neighbor.otp().decimal();
                } catch (RuntimeException ex) {
                    continue;
                }
            }
            previews.add(OtpPreview.forCounter(adjustedAtc.get(), delta, otp));
        }
        if (previews.isEmpty()) {
            previews.add(OtpPreview.centerOnly(centerResult.otp().decimal()));
        }
        return List.copyOf(previews);
    }

    private static Optional<String> adjustAtcHex(String atcHex, int delta) {
        if (delta == 0) {
            return Optional.of(atcHex);
        }
        BigInteger base = new BigInteger(atcHex, 16);
        BigInteger candidate = base.add(BigInteger.valueOf(delta));
        if (candidate.signum() < 0) {
            return Optional.empty();
        }
        int width = atcHex.length();
        BigInteger limit = BigInteger.ONE.shiftLeft(width * 4).subtract(BigInteger.ONE);
        if (candidate.compareTo(limit) > 0) {
            return Optional.empty();
        }
        String formatted = candidate.toString(16).toUpperCase(Locale.ROOT);
        if (formatted.length() < width) {
            StringBuilder padded = new StringBuilder(width);
            for (int index = formatted.length(); index < width; index++) {
                padded.append('0');
            }
            padded.append(formatted);
            formatted = padded.toString();
        }
        return Optional.of(formatted);
    }

    private static EmvCapInput toDomainInput(EvaluationRequest request) {
        return toDomainInput(request, request.atcHex());
    }

    private static EmvCapInput toDomainInput(EvaluationRequest request, String atcHex) {
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
                atcHex,
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

    private static Trace toTrace(
            EmvCapResult result, EvaluationRequest request, int maskLength, int previewBackward, int previewForward) {
        return new Trace(
                request.atcHex(),
                request.branchFactor(),
                request.height(),
                maskLength,
                previewBackward,
                previewForward,
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
            int previewWindowBackward,
            int previewWindowForward,
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
            previewWindowBackward = Math.max(0, previewWindowBackward);
            previewWindowForward = Math.max(0, previewWindowForward);
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
    public record EvaluationResult(
            TelemetrySignal telemetry, String otp, int maskLength, List<OtpPreview> previews, Trace trace) {

        public EvaluationResult {
            previews = previews == null ? List.of() : List.copyOf(previews);
        }

        public Optional<Trace> traceOptional() {
            return Optional.ofNullable(trace);
        }

        public TelemetryFrame telemetryFrame(String telemetryId) {
            return telemetry.emit(adapterForMode(telemetry.mode()), telemetryId);
        }
    }

    /** Structured trace payload exposed to verbose consumers. */
    public record Trace(
            String atc,
            int branchFactor,
            int height,
            int maskLength,
            int previewWindowBackward,
            int previewWindowForward,
            String masterKeySha256,
            String sessionKey,
            GenerateAcInput generateAcInput,
            String generateAcResult,
            String bitmask,
            String maskedDigits,
            String issuerApplicationData) {

        public Trace {
            atc = normalizeHex(atc, "trace.atc");
            branchFactor = requirePositive(branchFactor, "trace.branchFactor");
            height = requirePositive(height, "trace.height");
            maskLength = Math.max(0, maskLength);
            previewWindowBackward = Math.max(0, previewWindowBackward);
            previewWindowForward = Math.max(0, previewWindowForward);
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
                EmvCapMode mode,
                String atc,
                int ipbMaskLength,
                int maskedDigitsCount,
                int branchFactor,
                int height,
                int previewBackward,
                int previewForward) {
            return new TelemetrySignal(
                    mode,
                    TelemetryStatus.SUCCESS,
                    "generated",
                    null,
                    true,
                    telemetryFields(
                            mode,
                            atc,
                            ipbMaskLength,
                            maskedDigitsCount,
                            branchFactor,
                            height,
                            previewBackward,
                            previewForward));
        }

        static TelemetrySignal validationFailure(
                EmvCapMode mode,
                String atc,
                int ipbMaskLength,
                int branchFactor,
                int height,
                int previewBackward,
                int previewForward,
                String reason) {
            return new TelemetrySignal(
                    mode,
                    TelemetryStatus.INVALID,
                    "invalid_input",
                    reason,
                    true,
                    telemetryFields(
                            mode, atc, ipbMaskLength, 0, branchFactor, height, previewBackward, previewForward));
        }

        static TelemetrySignal error(
                EmvCapMode mode,
                String atc,
                int ipbMaskLength,
                int branchFactor,
                int height,
                int previewBackward,
                int previewForward,
                String reason) {
            return new TelemetrySignal(
                    mode,
                    TelemetryStatus.ERROR,
                    "unexpected_error",
                    reason,
                    false,
                    telemetryFields(
                            mode, atc, ipbMaskLength, 0, branchFactor, height, previewBackward, previewForward));
        }

        private static Map<String, Object> telemetryFields(
                EmvCapMode mode,
                String atc,
                int ipbMaskLength,
                int maskedDigitsCount,
                int branchFactor,
                int height,
                int previewBackward,
                int previewForward) {
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
            fields.put("previewWindowBackward", Math.max(0, previewBackward));
            fields.put("previewWindowForward", Math.max(0, previewForward));
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
