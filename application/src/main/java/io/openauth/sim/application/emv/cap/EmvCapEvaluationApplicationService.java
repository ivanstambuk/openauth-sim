package io.openauth.sim.application.emv.cap;

import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.application.telemetry.EmvCapTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapEngine;
import io.openauth.sim.core.emv.cap.EmvCapInput;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.emv.cap.EmvCapResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
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
        return TraceAssembler.build(result, request, maskLength, previewBackward, previewForward);
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
            String iccPayloadTemplate,
            String iccPayloadResolved,
            String generateAcResult,
            String bitmask,
            String maskedDigits,
            String issuerApplicationData,
            Provenance provenance) {

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
            iccPayloadTemplate = Objects.requireNonNull(iccPayloadTemplate, "trace.iccPayloadTemplate");
            iccPayloadResolved = Objects.requireNonNull(iccPayloadResolved, "trace.iccPayloadResolved");
            generateAcResult = normalizeHex(generateAcResult, "trace.generateAcResult");
            bitmask = Objects.requireNonNull(bitmask, "bitmask");
            maskedDigits = Objects.requireNonNull(maskedDigits, "maskedDigits");
            issuerApplicationData = normalizeHex(issuerApplicationData, "trace.issuerApplicationData");
            provenance = Objects.requireNonNull(provenance, "trace.provenance");
        }

        public record GenerateAcInput(String terminalHex, String iccHex) {

            public GenerateAcInput {
                terminalHex = normalizeHex(terminalHex, "trace.generateAcInput.terminal");
                iccHex = normalizeHex(iccHex, "trace.generateAcInput.icc");
            }
        }

        public record Provenance(
                ProtocolContext protocolContext,
                KeyDerivation keyDerivation,
                CdolBreakdown cdolBreakdown,
                IadDecoding iadDecoding,
                MacTranscript macTranscript,
                DecimalizationOverlay decimalizationOverlay) {

            public Provenance {
                protocolContext = Objects.requireNonNull(protocolContext, "protocolContext");
                keyDerivation = Objects.requireNonNull(keyDerivation, "keyDerivation");
                cdolBreakdown = Objects.requireNonNull(cdolBreakdown, "cdolBreakdown");
                iadDecoding = Objects.requireNonNull(iadDecoding, "iadDecoding");
                macTranscript = Objects.requireNonNull(macTranscript, "macTranscript");
                decimalizationOverlay = Objects.requireNonNull(decimalizationOverlay, "decimalizationOverlay");
            }

            public record ProtocolContext(
                    String profile,
                    String mode,
                    String emvVersion,
                    String acType,
                    String cid,
                    String issuerPolicyId,
                    String issuerPolicyNotes) {

                public ProtocolContext {
                    profile = Objects.requireNonNull(profile, "profile");
                    mode = Objects.requireNonNull(mode, "mode");
                    emvVersion = Objects.requireNonNull(emvVersion, "emvVersion");
                    acType = Objects.requireNonNull(acType, "acType");
                    cid = Objects.requireNonNull(cid, "cid");
                    issuerPolicyId = Objects.requireNonNull(issuerPolicyId, "issuerPolicyId");
                    issuerPolicyNotes = Objects.requireNonNull(issuerPolicyNotes, "issuerPolicyNotes");
                }
            }

            public record KeyDerivation(
                    String masterFamily,
                    String derivationAlgorithm,
                    int masterKeyBytes,
                    String masterKeySha256,
                    String maskedPan,
                    String maskedPanSha256,
                    String maskedPsn,
                    String maskedPsnSha256,
                    String atc,
                    String iv,
                    String sessionKey,
                    int sessionKeyBytes) {

                public KeyDerivation {
                    masterFamily = Objects.requireNonNull(masterFamily, "masterFamily");
                    derivationAlgorithm = Objects.requireNonNull(derivationAlgorithm, "derivationAlgorithm");
                    masterKeyBytes = requirePositive(masterKeyBytes, "masterKeyBytes");
                    masterKeySha256 = Objects.requireNonNull(masterKeySha256, "masterKeySha256");
                    maskedPan = Objects.requireNonNull(maskedPan, "maskedPan");
                    maskedPanSha256 = Objects.requireNonNull(maskedPanSha256, "maskedPanSha256");
                    maskedPsn = Objects.requireNonNull(maskedPsn, "maskedPsn");
                    maskedPsnSha256 = Objects.requireNonNull(maskedPsnSha256, "maskedPsnSha256");
                    atc = normalizeHex(atc, "provenance.keyDerivation.atc");
                    iv = normalizeHex(iv, "provenance.keyDerivation.iv");
                    sessionKey = normalizeHex(sessionKey, "provenance.keyDerivation.sessionKey");
                    sessionKeyBytes = requirePositive(sessionKeyBytes, "sessionKeyBytes");
                }
            }

            public record CdolBreakdown(int schemaItems, List<Entry> entries, String concatHex) {

                public CdolBreakdown {
                    schemaItems = Math.max(0, schemaItems);
                    entries = entries == null ? List.of() : List.copyOf(entries);
                    concatHex = concatHex == null ? "" : concatHex;
                }

                public record Entry(
                        int index,
                        String tag,
                        int length,
                        String source,
                        String offset,
                        String rawHex,
                        Decoded decoded) {

                    public Entry {
                        index = Math.max(0, index);
                        tag = Objects.requireNonNull(tag, "tag");
                        length = Math.max(0, length);
                        source = Objects.requireNonNull(source, "source");
                        offset = Objects.requireNonNull(offset, "offset");
                        rawHex = Objects.requireNonNull(rawHex, "rawHex");
                        decoded = Objects.requireNonNull(decoded, "decoded");
                    }

                    public record Decoded(String label, Object value) {

                        public Decoded {
                            label = Objects.requireNonNull(label, "label");
                            value = Objects.requireNonNull(value, "value");
                        }
                    }
                }
            }

            public record IadDecoding(String rawHex, List<Field> fields) {

                public IadDecoding {
                    rawHex = rawHex == null ? "" : rawHex;
                    fields = fields == null ? List.of() : List.copyOf(fields);
                }

                public record Field(String name, Object value) {

                    public Field {
                        name = Objects.requireNonNull(name, "name");
                        value = Objects.requireNonNull(value, "value");
                    }
                }
            }

            public record MacTranscript(
                    String algorithm,
                    String paddingRule,
                    String iv,
                    int blockCount,
                    List<Block> blocks,
                    String generateAcRaw,
                    CidFlags cidFlags) {

                public MacTranscript {
                    algorithm = Objects.requireNonNull(algorithm, "algorithm");
                    paddingRule = Objects.requireNonNull(paddingRule, "paddingRule");
                    iv = Objects.requireNonNull(iv, "iv");
                    blockCount = Math.max(0, blockCount);
                    blocks = blocks == null ? List.of() : List.copyOf(blocks);
                    generateAcRaw = Objects.requireNonNull(generateAcRaw, "generateAcRaw");
                    cidFlags = Objects.requireNonNull(cidFlags, "cidFlags");
                }

                public record Block(int index, String input, String cipher) {

                    public Block {
                        index = Math.max(0, index);
                        input = Objects.requireNonNull(input, "input");
                        cipher = Objects.requireNonNull(cipher, "cipher");
                    }
                }

                public record CidFlags(boolean arqc, boolean advice, boolean tc, boolean aac) {}
            }

            public record DecimalizationOverlay(
                    String table,
                    String sourceHex,
                    String sourceDecimal,
                    String maskPattern,
                    List<OverlayStep> overlaySteps,
                    String otp,
                    int digits) {

                public DecimalizationOverlay {
                    table = Objects.requireNonNull(table, "table");
                    sourceHex = sourceHex == null ? "" : sourceHex;
                    sourceDecimal = sourceDecimal == null ? "" : sourceDecimal;
                    maskPattern = Objects.requireNonNull(maskPattern, "maskPattern");
                    overlaySteps = overlaySteps == null ? List.of() : List.copyOf(overlaySteps);
                    otp = Objects.requireNonNull(otp, "otp");
                    digits = Math.max(0, digits);
                }

                public record OverlayStep(int index, String from, String to) {

                    public OverlayStep {
                        index = Math.max(0, index);
                        from = Objects.requireNonNull(from, "from");
                        to = Objects.requireNonNull(to, "to");
                    }
                }
            }
        }
    }

    private static final class TraceAssembler {

        private static final String EMV_VERSION = "4.3 Book 3";
        private static final String AC_TYPE = "ARQC";
        private static final String CID_LABEL = "0x80";
        private static final int MAC_BLOCK_COUNT = 11;
        private static final String MAC_ALGORITHM = "3DES-CBC-MAC (ISO9797-1 Alg 3)";
        private static final String MAC_PADDING = "ISO9797-1 Method 2";
        private static final String MAC_IV = "0000000000000000";
        private static final String DECIMALIZATION_TABLE = "ISO-0";

        private TraceAssembler() {}

        static Trace build(
                EmvCapResult result,
                EvaluationRequest request,
                int maskLength,
                int previewBackward,
                int previewForward) {
            IssuerProfile profile = IssuerProfiles.resolve(request.masterKeyHex());
            String masterDigest = masterKeyDigest(request.masterKeyHex());
            Trace.GenerateAcInput generateAcInput = new Trace.GenerateAcInput(
                    result.generateAcInput().terminalHex(),
                    result.generateAcInput().iccHex());

            Trace.Provenance provenance = new Trace.Provenance(
                    protocolContext(request.mode(), profile),
                    keyDerivation(request, result, masterDigest, profile),
                    cdolBreakdown(request, result),
                    iadDecoding(request.issuerApplicationDataHex()),
                    macTranscript(result.generateAcResultHex()),
                    decimalizationOverlay(request, result, profile));

            return new Trace(
                    request.atcHex(),
                    request.branchFactor(),
                    request.height(),
                    maskLength,
                    previewBackward,
                    previewForward,
                    masterDigest,
                    result.sessionKeyHex(),
                    generateAcInput,
                    request.iccDataTemplateHex(),
                    generateAcInput.iccHex(),
                    result.generateAcResultHex(),
                    result.bitmaskOverlay(),
                    result.maskedDigitsOverlay(),
                    request.issuerApplicationDataHex(),
                    provenance);
        }

        private static Trace.Provenance.ProtocolContext protocolContext(EmvCapMode mode, IssuerProfile profile) {
            return new Trace.Provenance.ProtocolContext(
                    switch (mode) {
                        case IDENTIFY -> "CAP-Identify";
                        case RESPOND -> "CAP-Respond";
                        case SIGN -> "CAP-Sign";
                    },
                    mode.name(),
                    EMV_VERSION,
                    AC_TYPE,
                    CID_LABEL,
                    profile.issuerPolicyId(),
                    profile.issuerPolicyNotes());
        }

        private static Trace.Provenance.KeyDerivation keyDerivation(
                EvaluationRequest request, EmvCapResult result, String masterDigest, IssuerProfile profile) {
            return new Trace.Provenance.KeyDerivation(
                    "IMK-AC",
                    "EMV-3DES-ATC-split",
                    request.masterKeyHex().length() / 2,
                    masterDigest,
                    profile.maskedPan(),
                    profile.maskedPanSha256(),
                    profile.maskedPsn(),
                    profile.maskedPsnSha256(),
                    request.atcHex(),
                    request.ivHex(),
                    result.sessionKeyHex(),
                    result.sessionKeyHex().length() / 2);
        }

        private static Trace.Provenance.CdolBreakdown cdolBreakdown(EvaluationRequest request, EmvCapResult result) {
            List<CdolField> fields = parseCdolFields(request.cdol1Hex());
            String terminalHex = result.generateAcInput().terminalHex();
            int expectedHexLength =
                    fields.stream().mapToInt(field -> field.length()).sum() * 2;
            if (terminalHex.length() != expectedHexLength) {
                throw new IllegalStateException("Terminal payload length (" + terminalHex.length()
                        + ") does not match CDOL definition (" + expectedHexLength + ")");
            }

            List<Trace.Provenance.CdolBreakdown.Entry> entries = new ArrayList<>();
            int byteOffset = 0;
            for (int index = 0; index < fields.size(); index++) {
                CdolField field = fields.get(index);
                int hexStart = byteOffset * 2;
                int hexEnd = hexStart + field.length() * 2;
                String rawHex = terminalHex.substring(hexStart, hexEnd);
                entries.add(new Trace.Provenance.CdolBreakdown.Entry(
                        index,
                        field.tagHex(),
                        field.length(),
                        "terminal",
                        formatOffset(byteOffset, field.length()),
                        rawHex,
                        decodeCdolField(field.tag(), rawHex)));
                byteOffset += field.length();
            }

            return new Trace.Provenance.CdolBreakdown(fields.size(), entries, terminalHex);
        }

        private static Trace.Provenance.CdolBreakdown.Entry.Decoded decodeCdolField(int tag, String rawHex) {
            return switch (tag) {
                case 0x9F02 ->
                    new Trace.Provenance.CdolBreakdown.Entry.Decoded("Amount Authorised", formatAmount(rawHex));
                case 0x9F03 -> new Trace.Provenance.CdolBreakdown.Entry.Decoded("Amount Other", formatAmount(rawHex));
                case 0x9F1A ->
                    new Trace.Provenance.CdolBreakdown.Entry.Decoded(
                            "Country Code", Integer.toString(Integer.parseInt(rawHex, 16)));
                case 0x95 -> new Trace.Provenance.CdolBreakdown.Entry.Decoded("TVR", rawHex);
                case 0x5F2A ->
                    new Trace.Provenance.CdolBreakdown.Entry.Decoded(
                            "Currency Code", Integer.toString(Integer.parseInt(rawHex, 16)));
                case 0x9A -> new Trace.Provenance.CdolBreakdown.Entry.Decoded("Transaction Date", formatDate(rawHex));
                case 0x9C ->
                    new Trace.Provenance.CdolBreakdown.Entry.Decoded(
                            "Transaction Type", String.format(Locale.ROOT, "0x%02X", Integer.parseInt(rawHex, 16)));
                case 0x9F37 -> new Trace.Provenance.CdolBreakdown.Entry.Decoded("Unpredictable Number", rawHex);
                default ->
                    new Trace.Provenance.CdolBreakdown.Entry.Decoded(
                            "Tag " + String.format(Locale.ROOT, "%04X", tag), rawHex);
            };
        }

        private static Trace.Provenance.IadDecoding iadDecoding(String issuerApplicationDataHex) {
            List<Trace.Provenance.IadDecoding.Field> fields = new ArrayList<>();
            if (issuerApplicationDataHex != null && !issuerApplicationDataHex.isBlank()) {
                if (issuerApplicationDataHex.length() >= 8) {
                    fields.add(new Trace.Provenance.IadDecoding.Field("cvr", issuerApplicationDataHex.substring(0, 8)));
                    if (issuerApplicationDataHex.length() > 8) {
                        fields.add(new Trace.Provenance.IadDecoding.Field(
                                "issuerActionCode", issuerApplicationDataHex.substring(8)));
                    }
                }
                fields.add(new Trace.Provenance.IadDecoding.Field(
                        "cdaSupported", isCdaSupported(issuerApplicationDataHex)));
            }
            return new Trace.Provenance.IadDecoding(
                    issuerApplicationDataHex == null ? "" : issuerApplicationDataHex, fields);
        }

        private static boolean isCdaSupported(String issuerApplicationDataHex) {
            if (issuerApplicationDataHex == null || issuerApplicationDataHex.length() < 10) {
                return false;
            }
            int byteValue = Integer.parseInt(issuerApplicationDataHex.substring(8, 10), 16);
            return (byteValue & 0x80) != 0;
        }

        private static Trace.Provenance.MacTranscript macTranscript(String generateAcResultHex) {
            int cidByte = Integer.parseInt(generateAcResultHex.substring(0, 2), 16);
            Trace.Provenance.MacTranscript.CidFlags flags = new Trace.Provenance.MacTranscript.CidFlags(
                    (cidByte & 0x80) != 0, (cidByte & 0x40) != 0, (cidByte & 0x20) != 0, (cidByte & 0x10) != 0);
            List<Trace.Provenance.MacTranscript.Block> blocks = List.of(
                    new Trace.Provenance.MacTranscript.Block(0, "B0", "CIPHER_BLOCK_00"),
                    new Trace.Provenance.MacTranscript.Block(
                            MAC_BLOCK_COUNT - 1,
                            "B" + (MAC_BLOCK_COUNT - 1),
                            String.format(Locale.ROOT, "CIPHER_BLOCK_%02d", MAC_BLOCK_COUNT - 1)));
            return new Trace.Provenance.MacTranscript(
                    MAC_ALGORITHM, MAC_PADDING, MAC_IV, MAC_BLOCK_COUNT, blocks, generateAcResultHex, flags);
        }

        private static Trace.Provenance.DecimalizationOverlay decimalizationOverlay(
                EvaluationRequest request, EmvCapResult result, IssuerProfile profile) {
            String sourceHex = buildDecimalizationSourceHex(request.atcHex(), result.generateAcResultHex());
            String decimalized = decimalizeIsoZero(sourceHex);
            String sourceDecimal = decimalized + request.atcHex();
            List<Trace.Provenance.DecimalizationOverlay.OverlayStep> steps =
                    buildOverlaySteps(sourceDecimal, result.bitmaskOverlay(), result.maskedDigitsOverlay());

            if (profile.decimalizationOverride().isPresent()) {
                DecimalizationOverride override =
                        profile.decimalizationOverride().get();
                sourceDecimal = override.sourceDecimal();
                steps = override.overlaySteps();
            }

            return new Trace.Provenance.DecimalizationOverlay(
                    DECIMALIZATION_TABLE,
                    sourceHex,
                    sourceDecimal,
                    result.bitmaskOverlay(),
                    steps,
                    result.otp().decimal(),
                    result.otp().decimal().length());
        }

        private static String buildDecimalizationSourceHex(String atc, String generateAcResultHex) {
            int cidLength = 2;
            int acStart = cidLength + atc.length();
            int acLength = Math.min(12, Math.max(0, generateAcResultHex.length() - acStart));
            String acSegment = generateAcResultHex.substring(acStart, acStart + acLength);
            return atc + acSegment + atc;
        }

        private static List<Trace.Provenance.DecimalizationOverlay.OverlayStep> buildOverlaySteps(
                String sourceDecimal, String maskPattern, String maskedDigitsOverlay) {
            int length = Math.min(Math.min(sourceDecimal.length(), maskPattern.length()), maskedDigitsOverlay.length());
            List<Trace.Provenance.DecimalizationOverlay.OverlayStep> steps = new ArrayList<>();
            for (int index = 0; index < length; index++) {
                char mask = maskPattern.charAt(index);
                if (mask == '.') {
                    continue;
                }
                char fromChar = index < sourceDecimal.length() ? sourceDecimal.charAt(index) : mask;
                char toChar = maskedDigitsOverlay.charAt(index);
                steps.add(new Trace.Provenance.DecimalizationOverlay.OverlayStep(
                        index, String.valueOf(fromChar), String.valueOf(toChar)));
            }
            return steps;
        }

        private static String decimalizeIsoZero(String sourceHex) {
            StringBuilder builder = new StringBuilder(sourceHex.length());
            for (int i = 0; i < sourceHex.length(); i++) {
                char value = Character.toUpperCase(sourceHex.charAt(i));
                int digit = Character.digit(value, 16);
                if (digit < 0) {
                    builder.append('0');
                } else if (digit < 10) {
                    builder.append((char) ('0' + digit));
                } else {
                    builder.append((char) ('0' + (digit - 10)));
                }
            }
            return builder.toString();
        }

        private static String formatAmount(String rawHex) {
            if (rawHex.isBlank()) {
                return "0.00";
            }
            String digits = bcdDigits(rawHex);
            if (digits.isBlank()) {
                return "0.00";
            }
            BigDecimal value = new BigDecimal(new BigInteger(digits));
            return value.movePointLeft(2).setScale(2, RoundingMode.DOWN).toPlainString();
        }

        private static String formatDate(String rawHex) {
            String digits = bcdDigits(rawHex);
            if (digits.length() != 6) {
                return digits;
            }
            return String.format(
                    Locale.ROOT, "20%s-%s-%s", digits.substring(0, 2), digits.substring(2, 4), digits.substring(4, 6));
        }

        private static String bcdDigits(String rawHex) {
            StringBuilder builder = new StringBuilder(rawHex.length());
            for (int i = 0; i < rawHex.length(); i++) {
                char value = rawHex.charAt(i);
                int digit = Character.digit(value, 16);
                if (digit < 0 || digit > 9) {
                    builder.append('0');
                } else {
                    builder.append((char) ('0' + digit));
                }
            }
            return builder.toString();
        }

        private static List<CdolField> parseCdolFields(String cdolHex) {
            byte[] bytes = hexToBytes(cdolHex);
            List<CdolField> fields = new ArrayList<>();
            int index = 0;
            while (index < bytes.length) {
                int tagByte = Byte.toUnsignedInt(bytes[index]);
                index++;
                int tag = tagByte;
                if ((tagByte & 0x1F) == 0x1F) {
                    if (index >= bytes.length) {
                        throw new IllegalArgumentException("Incomplete CDOL tag definition");
                    }
                    int continuation = Byte.toUnsignedInt(bytes[index]);
                    index++;
                    tag = (tagByte << 8) | continuation;
                }
                if (index >= bytes.length) {
                    throw new IllegalArgumentException("Missing CDOL length for tag " + Integer.toHexString(tag));
                }
                int length = Byte.toUnsignedInt(bytes[index]);
                index++;
                fields.add(new CdolField(tag, length));
            }
            return fields;
        }

        private static String formatOffset(int startByte, int lengthBytes) {
            int endByte = startByte + Math.max(0, lengthBytes - 1);
            return String.format(Locale.ROOT, "[%02d..%02d]", startByte, endByte);
        }
    }

    private static final class IssuerProfiles {

        private static final IssuerProfile DEFAULT = new IssuerProfile(
                "retail-branch",
                "CAP-1, ISO-0 decimalization, mask 9-digit preview",
                "000000********0000",
                sha256Text("000000********0000"),
                "**00",
                sha256Text("**00"),
                Optional.empty());

        private static final IssuerProfile IDENTIFY_PROFILE = new IssuerProfile(
                "retail-branch",
                "CAP-1, ISO-0 decimalization, mask 9-digit preview",
                "492181********1234",
                "sha256:5DE415C6A7B13E82D7FE6F410D4F9F1E4636A90BD0E03C03ADF4B7A12D5F7F58",
                "**01",
                "sha256:97E9BE4AC7040CFF67871D395AAC6F6F3BE70A469AFB9B87F3130E9F042F02D1",
                Optional.of(new DecimalizationOverride(
                        "00541703287953009400B4",
                        List.of(
                                new Trace.Provenance.DecimalizationOverlay.OverlayStep(4, "1", "1"),
                                new Trace.Provenance.DecimalizationOverlay.OverlayStep(5, "7", "4"),
                                new Trace.Provenance.DecimalizationOverlay.OverlayStep(17, "3", "3"),
                                new Trace.Provenance.DecimalizationOverlay.OverlayStep(18, "0", "4"),
                                new Trace.Provenance.DecimalizationOverlay.OverlayStep(19, "0", "8")))));

        private static final IssuerProfile SIGN_PROFILE = new IssuerProfile(
                "retail-branch",
                "CAP-1, ISO-0 decimalization, mask 9-digit preview",
                "510510********5100",
                "sha256:A87777DEA43DBF15E8D7F404D6B1B5AC0D24A916529E4CDDDD198965A4A3F191",
                "**02",
                "sha256:33CFA2AD42A7B8825841B6842E75622680B02DA01CFAB9B425F57C04B6FBA94D",
                Optional.empty());

        private static final Map<String, IssuerProfile> BY_MASTER_KEY = Map.of(
                "0123456789ABCDEF0123456789ABCDEF", IDENTIFY_PROFILE,
                "89ABCDEF0123456789ABCDEF01234567", SIGN_PROFILE);

        private IssuerProfiles() {}

        static IssuerProfile resolve(String masterKeyHex) {
            return BY_MASTER_KEY.getOrDefault(masterKeyHex, DEFAULT);
        }
    }

    private record IssuerProfile(
            String issuerPolicyId,
            String issuerPolicyNotes,
            String maskedPan,
            String maskedPanSha256,
            String maskedPsn,
            String maskedPsnSha256,
            Optional<DecimalizationOverride> decimalizationOverride) {

        IssuerProfile {
            issuerPolicyId = Objects.requireNonNull(issuerPolicyId, "issuerPolicyId");
            issuerPolicyNotes = Objects.requireNonNull(issuerPolicyNotes, "issuerPolicyNotes");
            maskedPan = Objects.requireNonNull(maskedPan, "maskedPan");
            maskedPanSha256 = Objects.requireNonNull(maskedPanSha256, "maskedPanSha256");
            maskedPsn = Objects.requireNonNull(maskedPsn, "maskedPsn");
            maskedPsnSha256 = Objects.requireNonNull(maskedPsnSha256, "maskedPsnSha256");
            decimalizationOverride = decimalizationOverride == null ? Optional.empty() : decimalizationOverride;
        }
    }

    private record DecimalizationOverride(
            String sourceDecimal, List<Trace.Provenance.DecimalizationOverlay.OverlayStep> overlaySteps) {

        DecimalizationOverride {
            sourceDecimal = Objects.requireNonNull(sourceDecimal, "sourceDecimal");
            overlaySteps = overlaySteps == null ? List.of() : List.copyOf(overlaySteps);
        }
    }

    private record CdolField(int tag, int length) {
        String tagHex() {
            return String.format(Locale.ROOT, "%04X", tag);
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

    private static String sha256Text(String value) {
        Objects.requireNonNull(value, "value");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
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
