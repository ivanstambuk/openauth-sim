package io.openauth.sim.cli;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayResult;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService.SeedResult;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.application.telemetry.EmvCapTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.emv.cap.EmvCapMode;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/** Picocli facade for EMV/CAP evaluation flows. */
@CommandLine.Command(
        name = "emv",
        mixinStandardHelpOptions = true,
        description = "Evaluate EMV/CAP flows and (in future increments) manage stored credentials.",
        subcommands = {EmvCli.CapCommand.class})
public final class EmvCli implements java.util.concurrent.Callable<Integer> {

    private static final String EVENT_PREFIX = "cli.emv.cap.";
    private static final String REPLAY_EVENT = EVENT_PREFIX + "replay";
    private static final String TELEMETRY_PREFIX = "cli-emv-cap-";

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = {"-d", "--database"},
            paramLabel = "<path>",
            scope = CommandLine.ScopeType.INHERIT,
            description = "Path to the credential store database (default: data/credentials.db)")
    private Path database;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return CommandLine.ExitCode.USAGE;
    }

    private PrintWriter out() {
        return spec.commandLine().getOut();
    }

    private PrintWriter err() {
        return spec.commandLine().getErr();
    }

    void overrideDatabase(Path database) {
        this.database = database;
    }

    private Path databasePath() {
        if (database != null) {
            return database.toAbsolutePath();
        }
        return ProjectPaths.resolveDataFile("credentials.db");
    }

    private CredentialStore openStore() throws Exception {
        return CredentialStoreFactory.openFileStore(databasePath());
    }

    private static String nextTelemetryId() {
        return TELEMETRY_PREFIX + UUID.randomUUID();
    }

    private static String nextReplayTelemetryId() {
        return TELEMETRY_PREFIX + "replay-" + UUID.randomUUID();
    }

    private static String sanitizeMessage(String message) {
        if (message == null) {
            return "unspecified";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static void writeFrame(PrintWriter writer, String event, TelemetryFrame frame) {
        StringBuilder builder =
                new StringBuilder("event=").append(event).append(" status=").append(frame.status());
        frame.fields().forEach((key, value) -> {
            if ("telemetryId".equals(key)) {
                return;
            }
            builder.append(' ').append(key).append('=').append(value);
        });
        writer.println(builder);
    }

    private static String eventForMode(EmvCapMode mode) {
        return EVENT_PREFIX + mode.name().toLowerCase(Locale.ROOT);
    }

    private static EmvCapTelemetryAdapter adapterForMode(EmvCapMode mode) {
        return switch (mode) {
            case IDENTIFY -> TelemetryContracts.emvCapIdentifyAdapter();
            case RESPOND -> TelemetryContracts.emvCapRespondAdapter();
            case SIGN -> TelemetryContracts.emvCapSignAdapter();
        };
    }

    private static EmvCapTelemetryAdapter replayAdapterForMode(EmvCapMode mode) {
        return switch (mode) {
            case IDENTIFY -> TelemetryContracts.emvCapReplayIdentifyAdapter();
            case RESPOND -> TelemetryContracts.emvCapReplayRespondAdapter();
            case SIGN -> TelemetryContracts.emvCapReplaySignAdapter();
        };
    }

    /** Stub parent command for EMV operations. */
    @CommandLine.Command(
            name = "cap",
            description = "Derive EMV/CAP OTP values for Identify, Respond, and Sign modes.",
            subcommands = {
                EmvCli.CapCommand.EvaluateCommand.class,
                EmvCli.CapCommand.ReplayCommand.class,
                EmvCli.CapCommand.SeedCommand.class
            })
    static final class CapCommand implements java.util.concurrent.Callable<Integer> {

        @CommandLine.ParentCommand
        private EmvCli parent;

        @Override
        public Integer call() {
            parent.spec.commandLine().getOut().println(parent.spec.commandLine().getUsageMessage());
            return CommandLine.ExitCode.USAGE;
        }

        private PrintWriter out() {
            return parent.out();
        }

        private PrintWriter err() {
            return parent.err();
        }

        private CredentialStore openStore() throws Exception {
            return parent.openStore();
        }

        private static String sanitizeMessage(String message) {
            return EmvCli.sanitizeMessage(message);
        }

        private static void writeFrame(PrintWriter writer, String event, TelemetryFrame frame) {
            EmvCli.writeFrame(writer, event, frame);
        }

        private static String eventForMode(EmvCapMode mode) {
            return EmvCli.eventForMode(mode);
        }

        private static EmvCapTelemetryAdapter adapterForMode(EmvCapMode mode) {
            return EmvCli.adapterForMode(mode);
        }

        private static String nextTelemetryId() {
            return EmvCli.nextTelemetryId();
        }

        /** Seed canonical EMV/CAP credentials into the configured store. */
        @CommandLine.Command(name = "seed", description = "Seed canonical EMV/CAP credentials into the store.")
        static final class SeedCommand implements Callable<Integer> {

            private final EmvCapSeedApplicationService seedService = new EmvCapSeedApplicationService();

            @CommandLine.ParentCommand
            private CapCommand parent;

            @Override
            public Integer call() {
                try (CredentialStore store = parent.openStore()) {
                    List<SeedSample> samples = EmvCapSeedSamples.samples();
                    SeedResult result = seedService.seed(
                            samples.stream().map(SeedSample::toSeedCommand).toList(), store);

                    Map<String, Object> fields = new LinkedHashMap<>();
                    fields.put("addedCount", result.addedCredentialIds().size());
                    fields.put("canonicalCount", samples.size());
                    if (!result.addedCredentialIds().isEmpty()) {
                        fields.put("addedCredentialIds", result.addedCredentialIds());
                    }

                    boolean seeded = !result.addedCredentialIds().isEmpty();
                    TelemetryFrame frame = TelemetryContracts.emvCapSeedingAdapter()
                            .status(
                                    seeded ? "seeded" : "noop",
                                    nextTelemetryId(),
                                    seeded ? "seeded" : "noop",
                                    true,
                                    null,
                                    fields);

                    writeFrame(parent.out(), EVENT_PREFIX + "seed", frame);
                    if (seeded) {
                        parent.out()
                                .println("Seeded EMV/CAP credentials: "
                                        + String.join(", ", result.addedCredentialIds()));
                    } else {
                        parent.out().println("All EMV/CAP credentials are already present.");
                    }
                    return CommandLine.ExitCode.OK;
                } catch (Exception ex) {
                    Map<String, Object> fields =
                            Map.of("exception", ex.getClass().getSimpleName());
                    TelemetryFrame frame = TelemetryContracts.emvCapSeedingAdapter()
                            .status(
                                    "error",
                                    nextTelemetryId(),
                                    "unexpected_error",
                                    false,
                                    sanitizeMessage(ex.getMessage()),
                                    fields);
                    writeFrame(parent.err(), EVENT_PREFIX + "seed", frame);
                    parent.err().println("error=" + sanitizeMessage(ex.getMessage()));
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
        }

        /** Validate supplied EMV/CAP OTPs against stored or inline credentials. */
        @CommandLine.Command(
                name = "replay",
                description = "Replay an EMV/CAP OTP against stored or inline credentials, including verbose traces.")
        static final class ReplayCommand implements Callable<Integer> {

            private final EmvCapEvaluationApplicationService evaluationService =
                    new EmvCapEvaluationApplicationService();

            @CommandLine.ParentCommand
            private CapCommand parent;

            @CommandLine.Option(
                    names = "--credential-id",
                    paramLabel = "<id>",
                    description = "Identifier of a stored EMV/CAP credential to replay against.")
            String credentialId;

            @CommandLine.Option(
                    names = "--mode",
                    required = true,
                    paramLabel = "<mode>",
                    description = "EMV/CAP mode: IDENTIFY, RESPOND, or SIGN")
            String mode;

            @CommandLine.Option(
                    names = "--otp",
                    required = true,
                    paramLabel = "<digits>",
                    description = "Operator-supplied OTP to verify (digits only)")
            String otp;

            @CommandLine.Option(
                    names = "--search-backward",
                    paramLabel = "<int>",
                    defaultValue = "0",
                    description = "Preview window backward search (default: 0)")
            int searchBackward;

            @CommandLine.Option(
                    names = "--search-forward",
                    paramLabel = "<int>",
                    defaultValue = "0",
                    description = "Preview window forward search (default: 0)")
            int searchForward;

            @CommandLine.Option(
                    names = "--master-key",
                    paramLabel = "<hex>",
                    description = "Override ICC master key for inline replay (hexadecimal)")
            String masterKeyHex;

            @CommandLine.Option(
                    names = "--atc",
                    paramLabel = "<hex>",
                    description = "Override Application Transaction Counter for inline replay (hexadecimal)")
            String atcHex;

            @CommandLine.Option(
                    names = "--branch-factor",
                    paramLabel = "<int>",
                    description = "Session key derivation branch factor (required for inline replay)")
            Integer branchFactor;

            @CommandLine.Option(
                    names = "--height",
                    paramLabel = "<int>",
                    description = "Session key derivation height (required for inline replay)")
            Integer height;

            @CommandLine.Option(
                    names = "--iv",
                    paramLabel = "<hex>",
                    description = "Initialization vector for Generate AC (hexadecimal, 16 bytes)")
            String ivHex;

            @CommandLine.Option(
                    names = "--cdol1",
                    paramLabel = "<hex>",
                    description = "CDOL1 descriptor payload (hexadecimal)")
            String cdol1Hex;

            @CommandLine.Option(
                    names = "--issuer-proprietary-bitmap",
                    paramLabel = "<hex>",
                    description = "Issuer Proprietary Bitmap selecting OTP digits (hexadecimal)")
            String issuerProprietaryBitmapHex;

            @CommandLine.Option(
                    names = "--challenge",
                    paramLabel = "<digits>",
                    description = "Numeric challenge (required for RESPOND/SIGN inline replay)")
            String challenge;

            @CommandLine.Option(
                    names = "--reference",
                    paramLabel = "<digits>",
                    description = "Reference value (required for SIGN inline replay)")
            String reference;

            @CommandLine.Option(
                    names = "--amount",
                    paramLabel = "<digits>",
                    description = "Amount value (required for SIGN inline replay)")
            String amount;

            @CommandLine.Option(
                    names = "--terminal-data",
                    paramLabel = "<hex>",
                    description = "Override terminal transaction payload (hexadecimal)")
            String terminalDataHex;

            @CommandLine.Option(
                    names = "--icc-template",
                    paramLabel = "<hex>",
                    description = "ICC data template with ATC placeholders (required for inline replay)")
            String iccTemplateHex;

            @CommandLine.Option(
                    names = "--icc-data",
                    paramLabel = "<hex>",
                    description = "Override resolved ICC payload (hexadecimal)")
            String iccDataOverrideHex;

            @CommandLine.Option(
                    names = "--issuer-application-data",
                    paramLabel = "<hex>",
                    description = "Issuer Application Data payload (required for inline replay)")
            String issuerApplicationDataHex;

            @CommandLine.Option(
                    names = "--include-trace",
                    paramLabel = "<true|false>",
                    defaultValue = "true",
                    description = "Include verbose trace payload (true/false, default: true)")
            boolean includeTrace;

            @CommandLine.Option(
                    names = "--output-json",
                    description = "Pretty-print a REST-equivalent JSON response instead of text output")
            boolean outputJson;

            @Override
            public Integer call() {
                EmvCapMode modeValue;
                try {
                    modeValue = EmvCapMode.fromLabel(requireText(mode, "mode"));
                } catch (Exception ex) {
                    parent.err().println("error=invalid_mode message=Mode must be IDENTIFY, RESPOND, or SIGN");
                    return CommandLine.ExitCode.USAGE;
                }

                io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayCommand replayCommand;
                try {
                    replayCommand = buildReplayCommand(modeValue);
                } catch (IllegalArgumentException ex) {
                    return failValidation(modeValue, errorFields(modeValue), ex.getMessage());
                }

                try (CredentialStore store = parent.openStore()) {
                    EmvCapReplayApplicationService service =
                            new EmvCapReplayApplicationService(store, evaluationService);
                    ReplayResult result;
                    try {
                        result = service.replay(replayCommand, includeTrace);
                    } catch (IllegalArgumentException ex) {
                        return failValidation(modeValue, errorFields(modeValue), ex.getMessage());
                    } catch (RuntimeException ex) {
                        Map<String, Object> fields = errorFields(modeValue);
                        fields.put("exception", ex.getClass().getSimpleName());
                        return failUnexpected(modeValue, fields, ex.getMessage());
                    }

                    String telemetrySeed = nextReplayTelemetryId();
                    TelemetryFrame frame = result.telemetryFrame(telemetrySeed);
                    String telemetryId = resolveTelemetryId(frame, telemetrySeed);
                    Map<String, Object> metadata = metadataFor(result, frame, telemetryId);
                    return handleResult(modeValue, result, frame, metadata);
                } catch (Exception ex) {
                    Map<String, Object> fields = errorFields(modeValue);
                    fields.put("exception", ex.getClass().getSimpleName());
                    return failUnexpected(modeValue, fields, ex.getMessage());
                }
            }

            private Integer handleResult(
                    EmvCapMode mode, ReplayResult result, TelemetryFrame frame, Map<String, Object> metadata) {
                TelemetrySignal signal = result.telemetry();
                return switch (signal.status()) {
                    case SUCCESS -> onMatch(signal, result, frame, metadata);
                    case INVALID -> {
                        if ("otp_mismatch".equals(signal.reasonCode())) {
                            yield onMismatch(signal, result, frame, metadata);
                        }
                        yield failValidation(mode, metadata, signal.reason());
                    }
                    case ERROR -> failUnexpected(mode, metadata, signal.reason());
                };
            }

            private Integer onMatch(
                    TelemetrySignal signal, ReplayResult result, TelemetryFrame frame, Map<String, Object> metadata) {
                if (outputJson) {
                    parent.out().println(renderJsonResponse("match", signal, metadata, result));
                    return CommandLine.ExitCode.OK;
                }
                PrintWriter writer = parent.out();
                writeFrame(writer, REPLAY_EVENT, frame);
                printSummary(writer, "match", signal.reasonCode(), metadata);
                if (includeTrace) {
                    result.traceOptional().ifPresent(trace -> result.effectiveRequest()
                            .ifPresent(request -> printTrace(writer, trace, request)));
                }
                return CommandLine.ExitCode.OK;
            }

            private Integer onMismatch(
                    TelemetrySignal signal, ReplayResult result, TelemetryFrame frame, Map<String, Object> metadata) {
                if (outputJson) {
                    parent.out().println(renderJsonResponse("mismatch", signal, metadata, result));
                    return CommandLine.ExitCode.OK;
                }
                PrintWriter writer = parent.out();
                writeFrame(writer, REPLAY_EVENT, frame);
                printSummary(writer, "mismatch", signal.reasonCode(), metadata);
                return CommandLine.ExitCode.OK;
            }

            private io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService.ReplayCommand buildReplayCommand(
                    EmvCapMode modeValue) {
                String normalizedOtp = requireOtp(otp);
                if (hasText(credentialId)) {
                    Optional<EvaluationRequest> override =
                            overridesProvided() ? Optional.of(buildEvaluationRequest(modeValue)) : Optional.empty();
                    return new EmvCapReplayApplicationService.ReplayCommand.Stored(
                            credentialId.trim(), modeValue, normalizedOtp, searchBackward, searchForward, override);
                }
                EvaluationRequest request = buildEvaluationRequest(modeValue);
                return new EmvCapReplayApplicationService.ReplayCommand.Inline(
                        request, normalizedOtp, searchBackward, searchForward);
            }

            private EvaluationRequest buildEvaluationRequest(EmvCapMode modeValue) {
                String masterKey = requireHex(masterKeyHex, "masterKey");
                String atc = requireHex(atcHex, "atc");
                int branch = requirePositive(branchFactor, "branchFactor");
                int heightValue = requirePositive(height, "height");
                String iv = requireHex(ivHex, "iv");
                String cdol1 = requireHex(cdol1Hex, "cdol1");
                String issuerBitmap = requireHex(issuerProprietaryBitmapHex, "issuerProprietaryBitmap");
                String iccTemplate = requireTemplate(iccTemplateHex, "iccTemplate");
                String issuerApplicationData = requireHex(issuerApplicationDataHex, "issuerApplicationData");

                CustomerInputs customerInputs = new CustomerInputs(challenge, reference, amount);
                TransactionData transactionData =
                        new TransactionData(optionalOf(terminalDataHex), optionalOf(iccDataOverrideHex));

                return new EvaluationRequest(
                        modeValue,
                        masterKey,
                        atc,
                        branch,
                        heightValue,
                        iv,
                        cdol1,
                        issuerBitmap,
                        customerInputs,
                        transactionData,
                        iccTemplate,
                        issuerApplicationData);
            }

            private Map<String, Object> metadataFor(ReplayResult result, TelemetryFrame frame, String telemetryId) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("credentialSource", result.credentialSource());
                result.credentialId().ifPresent(id -> metadata.put("credentialId", id));
                metadata.put("mode", result.mode().name());
                result.matchedDelta().ifPresent(delta -> metadata.put("matchedDelta", delta));
                metadata.put("driftBackward", result.driftBackward());
                metadata.put("driftForward", result.driftForward());

                Map<String, Object> fields = frame.fields();
                EvaluationRequest effective = result.effectiveRequest().orElse(null);

                Integer branchFactorValue =
                        effective != null ? effective.branchFactor() : valueAsInteger(fields, "branchFactor");
                if (branchFactorValue != null) {
                    metadata.put("branchFactor", branchFactorValue);
                }

                Integer heightValue = effective != null ? effective.height() : valueAsInteger(fields, "height");
                if (heightValue != null) {
                    metadata.put("height", heightValue);
                }

                Integer ipbMaskLength = effective != null
                        ? effective.issuerProprietaryBitmapHex().length() / 2
                        : valueAsInteger(fields, "ipbMaskLength");
                if (ipbMaskLength != null) {
                    metadata.put("ipbMaskLength", ipbMaskLength);
                }

                Integer suppliedOtpLength = valueAsInteger(fields, "suppliedOtpLength");
                if (suppliedOtpLength != null) {
                    metadata.put("suppliedOtpLength", suppliedOtpLength);
                }

                metadata.put("telemetryId", telemetryId);
                return metadata;
            }

            private static String resolveTelemetryId(TelemetryFrame frame, String fallback) {
                Object value = frame.fields().get("telemetryId");
                if (value == null) {
                    return fallback;
                }
                String text = value.toString().trim();
                return text.isEmpty() ? fallback : text;
            }

            private static Integer valueAsInteger(Map<String, Object> fields, String key) {
                if (fields == null) {
                    return null;
                }
                Object value = fields.get(key);
                if (value instanceof Number number) {
                    return number.intValue();
                }
                if (value != null) {
                    try {
                        return Integer.parseInt(value.toString());
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
                return null;
            }

            private String renderJsonResponse(
                    String statusText, TelemetrySignal signal, Map<String, Object> metadata, ReplayResult result) {
                StringBuilder builder = new StringBuilder();
                builder.append("{\n");
                builder.append("  \"status\":\"").append(escapeJson(statusText)).append("\",\n");
                builder.append("  \"reasonCode\":\"")
                        .append(escapeJson(signal.reasonCode()))
                        .append("\",\n");
                builder.append("  \"metadata\":{\n");
                Iterator<Map.Entry<String, Object>> iterator =
                        metadata.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> entry = iterator.next();
                    builder.append("    \"")
                            .append(escapeJson(entry.getKey()))
                            .append("\":")
                            .append(formatJsonValue(entry.getValue()));
                    if (iterator.hasNext()) {
                        builder.append(",");
                    }
                    builder.append("\n");
                }
                builder.append("  }");
                if (includeTrace
                        && result.traceOptional().isPresent()
                        && result.effectiveRequest().isPresent()) {
                    appendTraceJson(
                            builder,
                            result.traceOptional().get(),
                            result.effectiveRequest().get());
                }
                builder.append("\n}");
                return builder.toString();
            }

            private void printSummary(
                    PrintWriter writer, String statusText, String reasonCode, Map<String, Object> metadata) {
                Map<String, Object> remaining = new LinkedHashMap<>(metadata);
                writer.printf(Locale.ROOT, "status=%s%n", statusText);
                writer.printf(Locale.ROOT, "reasonCode=%s%n", reasonCode);
                Object source = remaining.remove("credentialSource");
                if (source != null) {
                    writer.printf(Locale.ROOT, "credentialSource=%s%n", source);
                }
                Object credential = remaining.remove("credentialId");
                if (credential != null) {
                    writer.printf(Locale.ROOT, "credentialId=%s%n", credential);
                }
                Object modeValue = remaining.remove("mode");
                if (modeValue != null) {
                    writer.printf(Locale.ROOT, "mode=%s%n", modeValue);
                }
                Object backward = remaining.remove("driftBackward");
                if (backward != null) {
                    writer.printf(Locale.ROOT, "driftBackward=%s%n", backward);
                }
                Object forward = remaining.remove("driftForward");
                if (forward != null) {
                    writer.printf(Locale.ROOT, "driftForward=%s%n", forward);
                }
                Object matched = remaining.remove("matchedDelta");
                writer.printf(Locale.ROOT, "matchedDelta=%s%n", matched != null ? matched : "n/a");
                Object telemetryId = remaining.remove("telemetryId");
                if (telemetryId != null) {
                    writer.printf(Locale.ROOT, "telemetryId=%s%n", telemetryId);
                }
                remaining.forEach((key, value) -> writer.printf(Locale.ROOT, "metadata.%s=%s%n", key, value));
            }

            private int failValidation(EmvCapMode modeValue, Map<String, Object> fields, String message) {
                TelemetryFrame frame = replayAdapterForMode(modeValue)
                        .status("invalid", nextReplayTelemetryId(), "invalid_input", true, message, fields);
                writeFrame(parent.err(), REPLAY_EVENT, frame);
                parent.err().println("error=" + sanitizeMessage(message));
                return CommandLine.ExitCode.USAGE;
            }

            private int failUnexpected(EmvCapMode modeValue, Map<String, Object> fields, String message) {
                TelemetryFrame frame = replayAdapterForMode(modeValue)
                        .status("error", nextReplayTelemetryId(), "unexpected_error", false, message, fields);
                writeFrame(parent.err(), REPLAY_EVENT, frame);
                parent.err().println("error=" + sanitizeMessage(message));
                return CommandLine.ExitCode.SOFTWARE;
            }

            private Map<String, Object> errorFields(EmvCapMode modeValue) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("mode", modeValue.name());
                fields.put("credentialSource", hasText(credentialId) ? "stored" : "inline");
                if (hasText(credentialId)) {
                    fields.put("credentialId", credentialId.trim());
                }
                if (otp != null && !otp.trim().isEmpty()) {
                    fields.put("suppliedOtpLength", otp.trim().length());
                }
                fields.put("driftBackward", searchBackward);
                fields.put("driftForward", searchForward);
                if (branchFactor != null) {
                    fields.put("branchFactor", branchFactor);
                }
                if (height != null) {
                    fields.put("height", height);
                }
                return fields;
            }

            private boolean overridesProvided() {
                return hasText(masterKeyHex)
                        || hasText(atcHex)
                        || branchFactor != null
                        || height != null
                        || hasText(ivHex)
                        || hasText(cdol1Hex)
                        || hasText(issuerProprietaryBitmapHex)
                        || hasText(iccTemplateHex)
                        || hasText(issuerApplicationDataHex);
            }

            private static Optional<String> optionalOf(String value) {
                if (value == null) {
                    return Optional.empty();
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(trimmed);
            }

            private static String requireHex(String value, String field) {
                String text = requireText(value, field).toUpperCase(Locale.ROOT);
                if ((text.length() & 1) == 1) {
                    throw new IllegalArgumentException(field + " must contain an even number of hex characters");
                }
                if (!text.matches("[0-9A-F]+")) {
                    throw new IllegalArgumentException(field + " must be hexadecimal");
                }
                return text;
            }

            private static String requireTemplate(String value, String field) {
                String text = requireText(value, field).toUpperCase(Locale.ROOT);
                if ((text.length() & 1) == 1) {
                    throw new IllegalArgumentException(field + " must contain an even number of characters");
                }
                if (!text.matches("[0-9A-FX]+")) {
                    throw new IllegalArgumentException(
                            field + " must contain hexadecimal characters or 'X' placeholders");
                }
                return text;
            }

            private static int requirePositive(Integer value, String field) {
                if (value == null) {
                    throw new IllegalArgumentException(field + " must be provided");
                }
                if (value <= 0) {
                    throw new IllegalArgumentException(field + " must be positive");
                }
                return value;
            }

            private static String requireOtp(String candidate) {
                String text = requireText(candidate, "otp");
                if (!text.matches("\\d+")) {
                    throw new IllegalArgumentException("otp must contain only digits");
                }
                return text;
            }

            private static boolean hasText(String value) {
                return value != null && !value.trim().isEmpty();
            }

            private static String requireText(String value, String field) {
                if (value == null) {
                    throw new IllegalArgumentException(field + " must be provided");
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException(field + " must not be empty");
                }
                return trimmed;
            }
        }

        /** Execute the EMV/CAP evaluate flow from Picocli inputs. */
        @CommandLine.Command(name = "evaluate", description = "Derive an EMV/CAP OTP for the supplied parameters.")
        static final class EvaluateCommand implements java.util.concurrent.Callable<Integer> {

            private final EmvCapEvaluationApplicationService service = new EmvCapEvaluationApplicationService();

            @CommandLine.ParentCommand
            private CapCommand parent;

            @CommandLine.Option(
                    names = "--mode",
                    required = true,
                    paramLabel = "<mode>",
                    description = "EMV/CAP mode: IDENTIFY, RESPOND, or SIGN")
            String mode;

            @CommandLine.Option(
                    names = "--master-key",
                    required = true,
                    paramLabel = "<hex>",
                    description = "ICC master key in hexadecimal")
            String masterKeyHex;

            @CommandLine.Option(
                    names = "--atc",
                    required = true,
                    paramLabel = "<hex>",
                    description = "Application Transaction Counter (hexadecimal, 2 bytes)")
            String atcHex;

            @CommandLine.Option(
                    names = "--branch-factor",
                    required = true,
                    paramLabel = "<int>",
                    description = "Session key derivation branch factor")
            int branchFactor;

            @CommandLine.Option(
                    names = "--height",
                    required = true,
                    paramLabel = "<int>",
                    description = "Session key derivation height")
            int height;

            @CommandLine.Option(
                    names = "--iv",
                    required = true,
                    paramLabel = "<hex>",
                    description = "Initialization vector (16 bytes hex)")
            String ivHex;

            @CommandLine.Option(
                    names = "--cdol1",
                    required = true,
                    paramLabel = "<hex>",
                    description = "CDOL1 descriptor payload")
            String cdol1Hex;

            @CommandLine.Option(
                    names = "--issuer-proprietary-bitmap",
                    required = true,
                    paramLabel = "<hex>",
                    description = "Issuer Proprietary Bitmap selecting OTP digits (hex)")
            String issuerProprietaryBitmapHex;

            @CommandLine.Option(
                    names = "--challenge",
                    paramLabel = "<digits>",
                    description = "Numeric challenge (required for RESPOND/SIGN modes)")
            String challenge;

            @CommandLine.Option(
                    names = "--reference",
                    paramLabel = "<digits>",
                    description = "Reference value (required for SIGN mode)")
            String reference;

            @CommandLine.Option(
                    names = "--amount",
                    paramLabel = "<digits>",
                    description = "Amount value (required for SIGN mode)")
            String amount;

            @CommandLine.Option(
                    names = "--terminal-data",
                    paramLabel = "<hex>",
                    description = "Override terminal transaction payload (hex)")
            String terminalDataHex;

            @CommandLine.Option(
                    names = "--icc-template",
                    required = true,
                    paramLabel = "<hex>",
                    description = "ICC data template with ATC placeholder (required if not using stored presets)")
            String iccTemplateHex;

            @CommandLine.Option(
                    names = "--icc-data",
                    paramLabel = "<hex>",
                    description = "Override resolved ICC payload (hex)")
            String iccDataOverrideHex;

            @CommandLine.Option(
                    names = "--issuer-application-data",
                    required = true,
                    paramLabel = "<hex>",
                    description = "Issuer Application Data payload (hex)")
            String issuerApplicationDataHex;

            @CommandLine.Option(
                    names = "--include-trace",
                    paramLabel = "<true|false>",
                    defaultValue = "true",
                    description = "Include verbose trace payload (true/false, default: true)")
            boolean includeTrace;

            @CommandLine.Option(
                    names = "--output-json",
                    description = "Pretty-print a REST-equivalent JSON response instead of text output")
            boolean outputJson;

            @Override
            public Integer call() {
                EmvCapMode modeValue;
                try {
                    modeValue = EmvCapMode.fromLabel(requireText(mode, "mode"));
                } catch (Exception ex) {
                    parent.err().println("error=invalid_mode message=Mode must be IDENTIFY, RESPOND, or SIGN");
                    return CommandLine.ExitCode.USAGE;
                }

                EvaluationRequest request;
                try {
                    request = buildRequest(modeValue);
                } catch (IllegalArgumentException ex) {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    fields.put("mode", modeValue.name());
                    fields.put("reason", sanitizeMessage(ex.getMessage()));
                    return failValidation(modeValue, fields, ex.getMessage());
                }

                EvaluationResult result;
                try {
                    result = service.evaluate(request, includeTrace);
                } catch (RuntimeException ex) {
                    Map<String, Object> fields = baseFields(request);
                    fields.put("exception", ex.getClass().getSimpleName());
                    return failUnexpected(modeValue, fields, ex.getMessage());
                }

                return handleResult(modeValue, request, result);
            }

            private EvaluationRequest buildRequest(EmvCapMode modeValue) {
                CustomerInputs inputs = new CustomerInputs(challenge, reference, amount);
                TransactionData transactionData =
                        new TransactionData(optionalOf(terminalDataHex), optionalOf(iccDataOverrideHex));
                return new EvaluationRequest(
                        modeValue,
                        requireText(masterKeyHex, "masterKey"),
                        requireText(atcHex, "atc"),
                        branchFactor,
                        height,
                        requireText(ivHex, "iv"),
                        requireText(cdol1Hex, "cdol1"),
                        requireText(issuerProprietaryBitmapHex, "issuerProprietaryBitmap"),
                        inputs,
                        transactionData,
                        requireText(iccTemplateHex, "iccTemplate"),
                        requireText(issuerApplicationDataHex, "issuerApplicationData"));
            }

            private Integer handleResult(EmvCapMode modeValue, EvaluationRequest request, EvaluationResult result) {
                TelemetrySignal signal = result.telemetry();
                TelemetryFrame frame = signal.emit(adapterForMode(modeValue), nextTelemetryId());
                String event = eventForMode(modeValue);
                return switch (signal.status()) {
                    case SUCCESS -> onSuccess(event, frame, request, result);
                    case INVALID -> onInvalid(event, frame, signal);
                    case ERROR -> onError(event, frame, signal);
                };
            }

            private Integer onSuccess(
                    String event, TelemetryFrame frame, EvaluationRequest request, EvaluationResult result) {
                if (outputJson) {
                    parent.out().println(renderJsonResponse(result, request, frame));
                    return CommandLine.ExitCode.OK;
                }

                PrintWriter writer = parent.out();
                writeFrame(writer, event, frame);
                writer.printf(Locale.ROOT, "otp=%s%n", result.otp());
                writer.printf(Locale.ROOT, "maskLength=%d%n", result.maskLength());
                if (includeTrace) {
                    result.traceOptional().ifPresent(trace -> printTrace(writer, trace, request));
                }
                return CommandLine.ExitCode.OK;
            }

            private Integer onInvalid(String event, TelemetryFrame frame, TelemetrySignal signal) {
                writeFrame(parent.err(), event, frame);
                if (signal.reason() != null && !signal.reason().isBlank()) {
                    parent.err().println("error=" + sanitizeMessage(signal.reason()));
                }
                return CommandLine.ExitCode.USAGE;
            }

            private Integer onError(String event, TelemetryFrame frame, TelemetrySignal signal) {
                writeFrame(parent.err(), event, frame);
                if (signal.reason() != null && !signal.reason().isBlank()) {
                    parent.err().println("error=" + sanitizeMessage(signal.reason()));
                }
                return CommandLine.ExitCode.SOFTWARE;
            }

            private int failValidation(EmvCapMode modeValue, Map<String, Object> fields, String message) {
                TelemetryFrame frame = adapterForMode(modeValue)
                        .status("invalid", nextTelemetryId(), "invalid_input", true, message, fields);
                writeFrame(parent.err(), eventForMode(modeValue), frame);
                parent.err().println("error=" + sanitizeMessage(message));
                return CommandLine.ExitCode.USAGE;
            }

            private int failUnexpected(EmvCapMode modeValue, Map<String, Object> fields, String message) {
                TelemetryFrame frame = adapterForMode(modeValue)
                        .status("error", nextTelemetryId(), "unexpected_error", false, message, fields);
                writeFrame(parent.err(), eventForMode(modeValue), frame);
                parent.err().println("error=" + sanitizeMessage(message));
                return CommandLine.ExitCode.SOFTWARE;
            }

            private static Map<String, Object> baseFields(EvaluationRequest request) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("mode", request.mode().name());
                fields.put("atc", request.atcHex());
                fields.put("branchFactor", request.branchFactor());
                fields.put("height", request.height());
                fields.put("ipbMaskLength", request.issuerProprietaryBitmapHex().length() / 2);
                return fields;
            }

            private static Optional<String> optionalOf(String value) {
                if (value == null) {
                    return Optional.empty();
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(trimmed);
            }

            private static String requireText(String value, String field) {
                if (value == null) {
                    throw new IllegalArgumentException(field + " must be provided");
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    throw new IllegalArgumentException(field + " must not be empty");
                }
                return trimmed;
            }

            private static String renderJsonResponse(
                    EvaluationResult result, EvaluationRequest request, TelemetryFrame frame) {
                StringBuilder builder = new StringBuilder();
                builder.append("{\n");
                builder.append("  \"otp\": \"").append(escapeJson(result.otp())).append("\",\n");
                builder.append("  \"maskLength\": ").append(result.maskLength());
                result.traceOptional().ifPresent(trace -> appendTraceJson(builder, trace, request));
                builder.append(",\n  \"telemetry\": {\n");
                builder.append("    \"event\": \"")
                        .append(escapeJson(frame.event()))
                        .append("\",\n");
                builder.append("    \"status\": \"")
                        .append(escapeJson(frame.status()))
                        .append("\",\n");
                builder.append("    \"reasonCode\": \"")
                        .append(escapeJson(result.telemetry().reasonCode()))
                        .append("\",\n");
                builder.append("    \"sanitized\": ").append(frame.sanitized()).append(",\n");
                builder.append("    \"fields\": {\n");
                Iterator<Map.Entry<String, Object>> iterator =
                        frame.fields().entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Object> entry = iterator.next();
                    builder.append("      \"")
                            .append(escapeJson(entry.getKey()))
                            .append("\": ")
                            .append(formatJsonValue(entry.getValue()));
                    if (iterator.hasNext()) {
                        builder.append(",");
                    }
                    builder.append("\n");
                }
                builder.append("    }\n  }\n}");
                return builder.toString();
            }
        }
    }

    private static void appendTraceJson(StringBuilder builder, Trace trace, EvaluationRequest request) {
        builder.append(",\n  \"trace\": {\n");
        builder.append("    \"sessionKey\": \"")
                .append(escapeJson(trace.sessionKey()))
                .append("\",\n");
        builder.append("    \"generateAcInput\": {\n");
        builder.append("      \"terminal\": \"")
                .append(escapeJson(trace.generateAcInput().terminalHex()))
                .append("\",\n");
        builder.append("      \"icc\": \"")
                .append(escapeJson(trace.generateAcInput().iccHex()))
                .append("\"\n");
        builder.append("    },\n");
        builder.append("    \"generateAcResult\": \"")
                .append(escapeJson(trace.generateAcResult()))
                .append("\",\n");
        builder.append("    \"bitmask\": \"")
                .append(escapeJson(trace.bitmask()))
                .append("\",\n");
        builder.append("    \"maskedDigitsOverlay\": \"")
                .append(escapeJson(trace.maskedDigits()))
                .append("\",\n");
        builder.append("    \"issuerApplicationData\": \"")
                .append(escapeJson(trace.issuerApplicationData()))
                .append("\",\n");
        builder.append("    \"iccPayloadTemplate\": \"")
                .append(escapeJson(request.iccDataTemplateHex()))
                .append("\",\n");
        builder.append("    \"iccPayloadResolved\": \"")
                .append(escapeJson(trace.generateAcInput().iccHex()))
                .append("\"\n");
        builder.append("  }");
    }

    private static void printTrace(PrintWriter writer, Trace trace, EvaluationRequest request) {
        writer.println("trace.sessionKey=" + trace.sessionKey());
        writer.println(
                "trace.generateAcInput.terminal=" + trace.generateAcInput().terminalHex());
        writer.println("trace.generateAcInput.icc=" + trace.generateAcInput().iccHex());
        writer.println("trace.generateAcResult=" + trace.generateAcResult());
        writer.println("trace.bitmask=" + trace.bitmask());
        writer.println("trace.maskedDigitsOverlay=" + trace.maskedDigits());
        writer.println("trace.issuerApplicationData=" + trace.issuerApplicationData());
        writer.println("trace.iccPayloadTemplate=" + request.iccDataTemplateHex());
        writer.println("trace.iccPayloadResolved=" + trace.generateAcInput().iccHex());
    }

    private static String formatJsonValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04X", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
