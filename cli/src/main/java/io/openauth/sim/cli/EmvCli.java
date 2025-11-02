package io.openauth.sim.cli;

import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.CustomerInputs;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationRequest;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.Trace;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService.TransactionData;
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

    /** Stub parent command for EMV operations. */
    @CommandLine.Command(
            name = "cap",
            description = "Derive EMV/CAP OTP values for Identify, Respond, and Sign modes.",
            subcommands = {EmvCli.CapCommand.EvaluateCommand.class, EmvCli.CapCommand.SeedCommand.class})
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
                writer.println("trace.generateAcInput.terminal="
                        + trace.generateAcInput().terminalHex());
                writer.println(
                        "trace.generateAcInput.icc=" + trace.generateAcInput().iccHex());
                writer.println("trace.generateAcResult=" + trace.generateAcResult());
                writer.println("trace.bitmask=" + trace.bitmask());
                writer.println("trace.maskedDigitsOverlay=" + trace.maskedDigits());
                writer.println("trace.issuerApplicationData=" + trace.issuerApplicationData());
                writer.println("trace.iccPayloadTemplate=" + request.iccDataTemplateHex());
                writer.println(
                        "trace.iccPayloadResolved=" + trace.generateAcInput().iccHex());
            }
        }
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
