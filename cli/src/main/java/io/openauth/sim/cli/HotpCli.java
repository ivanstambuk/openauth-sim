package io.openauth.sim.cli;

import io.openauth.sim.application.hotp.HotpCredentialDirectoryApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.IssuanceCommand;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.IssuanceResult;
import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.cli.support.EphemeralCredentialStore;
import io.openauth.sim.cli.support.JsonPrinter;
import io.openauth.sim.cli.support.TelemetryJson;
import io.openauth.sim.cli.support.VerboseTraceMapper;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/** CLI facade for HOTP credential lifecycle operations. */
@CommandLine.Command(
        name = "hotp",
        mixinStandardHelpOptions = true,
        description = "Manage HOTP credentials and evaluate responses.",
        subcommands = {HotpCli.ImportCommand.class, HotpCli.ListCommand.class, HotpCli.EvaluateCommand.class})
public final class HotpCli implements Callable<Integer> {

    private static final String EVENT_PREFIX = "cli.hotp.";
    private static final HotpTelemetryAdapter EVALUATION_TELEMETRY = TelemetryContracts.hotpEvaluationAdapter();
    private static final HotpTelemetryAdapter ISSUANCE_TELEMETRY = TelemetryContracts.hotpIssuanceAdapter();
    private static final String DEFAULT_DATABASE_FILE = "credentials.db";

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = {"-d", "--database"},
            paramLabel = "<path>",
            scope = CommandLine.ScopeType.INHERIT,
            description = "Path to the credential store database (default: data/credentials.db)")
    private Path database;

    void overrideDatabase(Path database) {
        this.database = database;
    }

    Path configuredDatabase() {
        return database;
    }

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

    Path databasePath() {
        if (database != null) {
            return database.toAbsolutePath();
        }
        return ProjectPaths.resolveDataFile(DEFAULT_DATABASE_FILE);
    }

    private static String event(String suffix) {
        return EVENT_PREFIX + suffix;
    }

    static String sanitizeMessage(String message) {
        if (message == null) {
            return "unspecified";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String nextTelemetryId() {
        return "cli-" + UUID.randomUUID();
    }

    private int failValidation(
            String event,
            HotpTelemetryAdapter adapter,
            Map<String, Object> fields,
            String message,
            boolean outputJson) {
        TelemetryFrame frame = adapter.validationFailure(
                nextTelemetryId(), "validation_error", sanitizeMessage(message), true, fields);
        if (outputJson) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.USAGE;
    }

    private int failUnexpected(
            String event,
            HotpTelemetryAdapter adapter,
            Map<String, Object> fields,
            String message,
            boolean outputJson) {
        TelemetryFrame frame =
                adapter.error(nextTelemetryId(), "unexpected_error", sanitizeMessage(message), false, fields);
        if (outputJson) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.SOFTWARE;
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

    abstract static class AbstractHotpCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        HotpCli parent;

        protected PrintWriter out() {
            return parent.out();
        }

        protected PrintWriter err() {
            return parent.err();
        }

        protected Path databasePath() {
            return parent.databasePath();
        }

        protected CredentialStore openStore() throws Exception {
            return CredentialStoreFactory.openFileStore(databasePath());
        }
    }

    @CommandLine.Command(name = "import", description = "Import an HOTP credential.")
    static final class ImportCommand extends AbstractHotpCommand {

        @CommandLine.Option(
                names = "--credential-id",
                paramLabel = "<id>",
                required = true,
                description = "Logical identifier for the credential")
        String credentialId;

        @CommandLine.Option(
                names = "--secret",
                paramLabel = "<hex>",
                required = true,
                description = "Shared secret in hex")
        String secretHex;

        @CommandLine.Option(
                names = "--digits",
                paramLabel = "<digits>",
                defaultValue = "6",
                description = "Number of output digits")
        int digits;

        @CommandLine.Option(
                names = "--counter",
                paramLabel = "<counter>",
                defaultValue = "0",
                description = "Initial moving factor counter")
        long counter;

        @CommandLine.Option(
                names = "--algorithm",
                paramLabel = "<name>",
                defaultValue = "SHA1",
                description = "HOTP hash algorithm (e.g. SHA1)")
        String algorithm;

        @CommandLine.Option(
                names = "--metadata",
                paramLabel = "key=value",
                split = ",",
                description = "Optional metadata entries",
                mapFallbackValue = "")
        Map<String, String> metadata;

        @CommandLine.Option(names = "--output-json", description = "Emit a single JSON object instead of text output")
        boolean outputJson;

        @Override
        public Integer call() {
            String event = event("issue");
            try (CredentialStore store = openStore()) {
                HotpIssuanceApplicationService service = new HotpIssuanceApplicationService(store);
                Map<String, String> normalizedMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
                HotpHashAlgorithm hashAlgorithm =
                        HotpHashAlgorithm.valueOf(algorithm.trim().toUpperCase(Locale.ROOT));
                IssuanceCommand command = new IssuanceCommand(
                        credentialId.trim(), secretHex.trim(), hashAlgorithm, digits, counter, normalizedMetadata);

                IssuanceResult result = service.issue(command);
                TelemetryFrame frame = result.telemetry().emit(ISSUANCE_TELEMETRY, nextTelemetryId());
                HotpIssuanceApplicationService.TelemetryStatus status =
                        result.telemetry().status();
                if (outputJson) {
                    JsonPrinter.print(
                            out(),
                            TelemetryJson.response(event, frame, Map.of("credentialId", credentialId.trim())),
                            true);
                    return switch (status) {
                        case SUCCESS -> CommandLine.ExitCode.OK;
                        case INVALID -> CommandLine.ExitCode.USAGE;
                        case ERROR -> CommandLine.ExitCode.SOFTWARE;
                    };
                }
                PrintWriter writer = status == HotpIssuanceApplicationService.TelemetryStatus.SUCCESS ? out() : err();
                writeFrame(writer, event, frame);
                return switch (status) {
                    case SUCCESS -> CommandLine.ExitCode.OK;
                    case INVALID -> CommandLine.ExitCode.USAGE;
                    case ERROR -> CommandLine.ExitCode.SOFTWARE;
                };
            } catch (IllegalArgumentException ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("credentialId", credentialId);
                return parent.failValidation(event, ISSUANCE_TELEMETRY, fields, ex.getMessage(), outputJson);
            } catch (Exception ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("credentialId", credentialId);
                return parent.failUnexpected(event, ISSUANCE_TELEMETRY, fields, ex.getMessage(), outputJson);
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List HOTP credentials.")
    static final class ListCommand extends AbstractHotpCommand {

        @CommandLine.Option(names = "--output-json", description = "Emit a single JSON object instead of text output")
        boolean outputJson;

        @Override
        public Integer call() {
            try (CredentialStore store = openStore()) {
                HotpCredentialDirectoryApplicationService directoryService =
                        new HotpCredentialDirectoryApplicationService(store);
                List<HotpCredentialDirectoryApplicationService.Summary> credentials = directoryService.list();

                if (outputJson) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("count", credentials.size());
                    data.put(
                            "credentials",
                            credentials.stream()
                                    .map(summary -> Map.of(
                                            "credentialId",
                                            summary.credentialId(),
                                            "algorithm",
                                            summary.algorithm(),
                                            "digits",
                                            summary.digits(),
                                            "counter",
                                            summary.counter()))
                                    .toList());
                    Map<String, Object> telemetryFields = Map.of("count", credentials.size());
                    TelemetryFrame frame = ISSUANCE_TELEMETRY.status(
                            "success", nextTelemetryId(), "success", true, null, telemetryFields);
                    JsonPrinter.print(out(), TelemetryJson.response(event("list"), frame, data), true);
                } else {
                    out().printf(
                                    Locale.ROOT,
                                    "event=%s status=success reasonCode=success sanitized=true count=%d%n",
                                    event("list"),
                                    credentials.size());

                    credentials.forEach(summary -> out().printf(
                                    Locale.ROOT,
                                    "credentialId=%s algorithm=%s digits=%s counter=%s%n",
                                    summary.credentialId(),
                                    summary.algorithm(),
                                    summary.digits(),
                                    summary.counter()));
                }
                return CommandLine.ExitCode.OK;
            } catch (IllegalArgumentException ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                return parent.failValidation(event("list"), ISSUANCE_TELEMETRY, fields, ex.getMessage(), outputJson);
            } catch (Exception ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                return parent.failUnexpected(event("list"), ISSUANCE_TELEMETRY, fields, ex.getMessage(), outputJson);
            }
        }
    }

    @CommandLine.Command(name = "evaluate", description = "Validate HOTP responses.")
    static final class EvaluateCommand extends AbstractHotpCommand {

        @CommandLine.Option(
                names = "--credential-id",
                paramLabel = "<id>",
                defaultValue = "",
                description = "When set, evaluate the stored credential; omit for inline mode")
        String credentialId;

        @CommandLine.Option(names = "--secret", paramLabel = "<hex>", description = "Shared secret in hex (inline)")
        String secretHex;

        @CommandLine.Option(
                names = "--digits",
                paramLabel = "<digits>",
                defaultValue = "6",
                description = "Number of digits (inline; ignored for stored)")
        int digits;

        @CommandLine.Option(
                names = "--counter",
                paramLabel = "<counter>",
                description = "Moving factor counter (required for inline)")
        Long counter;

        @CommandLine.Option(
                names = "--algorithm",
                paramLabel = "<name>",
                defaultValue = "SHA1",
                description = "HOTP hash algorithm for inline mode (e.g. SHA1, SHA256)")
        String algorithm;

        @CommandLine.Option(
                names = "--metadata",
                paramLabel = "key=value",
                split = ",",
                description = "Optional metadata entries for inline mode",
                mapFallbackValue = "")
        Map<String, String> metadata;

        @CommandLine.Option(
                names = "--window-backward",
                paramLabel = "<steps>",
                defaultValue = "0",
                description = "Preview window size before the evaluated OTP")
        int windowBackward;

        @CommandLine.Option(
                names = "--window-forward",
                paramLabel = "<steps>",
                defaultValue = "0",
                description = "Preview window size after the evaluated OTP")
        int windowForward;

        @CommandLine.Option(names = "--verbose", description = "Emit a detailed verbose trace of the evaluation steps")
        boolean verbose;

        @CommandLine.Option(names = "--output-json", description = "Emit a single JSON object instead of text output")
        boolean outputJson;

        @Override
        public Integer call() {
            String event = event("evaluate");
            boolean storedMode = hasText(credentialId);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("credentialReference", storedMode);
            if (storedMode) {
                fields.put("credentialId", credentialId);
                if (hasInlineInputs()) {
                    return parent.failValidation(
                            event,
                            EVALUATION_TELEMETRY,
                            fields,
                            "Inline inputs cannot be combined with --credential-id",
                            outputJson);
                }
                return handleStored(event, fields);
            }
            return handleInline(event, fields);
        }

        private Integer handleStored(String event, Map<String, Object> fields) {
            try (CredentialStore store = openStore()) {
                HotpEvaluationApplicationService service = new HotpEvaluationApplicationService(store);
                EvaluationCommand command =
                        new EvaluationCommand.Stored(credentialId.trim(), windowBackward, windowForward);
                EvaluationResult result = service.evaluate(command, verbose);
                TelemetryFrame frame = result.telemetry().emit(EVALUATION_TELEMETRY, nextTelemetryId());
                HotpEvaluationApplicationService.TelemetryStatus status =
                        result.telemetry().status();
                if (outputJson) {
                    JsonPrinter.print(out(), buildResponse(event, frame, result), true);
                    return exitCode(status);
                }
                PrintWriter writer = status == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS ? out() : err();
                writeFrame(writer, event, frame);
                if (status == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
                    OtpPreviewTableFormatter.print(writer, result.previews());
                    writer.printf(Locale.ROOT, "generatedOtp=%s%n", result.otp());
                }
                result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(writer, trace));
                return exitCode(status);
            } catch (IllegalArgumentException ex) {
                return parent.failValidation(event, EVALUATION_TELEMETRY, fields, ex.getMessage(), outputJson);
            } catch (Exception ex) {
                return parent.failUnexpected(event, EVALUATION_TELEMETRY, fields, ex.getMessage(), outputJson);
            }
        }

        private Integer handleInline(String event, Map<String, Object> fields) {
            if (!hasText(secretHex)) {
                return parent.failValidation(
                        event, EVALUATION_TELEMETRY, fields, "--secret is required for inline", outputJson);
            }
            if (counter == null) {
                return parent.failValidation(
                        event, EVALUATION_TELEMETRY, fields, "--counter is required for inline", outputJson);
            }

            try (CredentialStore store = new EphemeralCredentialStore()) {
                HotpEvaluationApplicationService service = new HotpEvaluationApplicationService(store);
                HotpHashAlgorithm hashAlgorithm =
                        HotpHashAlgorithm.valueOf(algorithm.trim().toUpperCase(Locale.ROOT));
                Map<String, String> normalizedMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
                EvaluationCommand command = new EvaluationCommand.Inline(
                        secretHex.trim(),
                        hashAlgorithm,
                        digits,
                        counter,
                        normalizedMetadata,
                        windowBackward,
                        windowForward);
                EvaluationResult result = service.evaluate(command, verbose);
                TelemetryFrame frame = result.telemetry().emit(EVALUATION_TELEMETRY, nextTelemetryId());
                HotpEvaluationApplicationService.TelemetryStatus status =
                        result.telemetry().status();
                if (outputJson) {
                    JsonPrinter.print(out(), buildResponse(event, frame, result), true);
                    return exitCode(status);
                }
                PrintWriter writer = status == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS ? out() : err();
                writeFrame(writer, event, frame);
                if (status == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
                    OtpPreviewTableFormatter.print(writer, result.previews());
                    writer.printf(Locale.ROOT, "generatedOtp=%s%n", result.otp());
                }
                result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(writer, trace));
                return exitCode(status);
            } catch (IllegalArgumentException ex) {
                return parent.failValidation(event, EVALUATION_TELEMETRY, fields, ex.getMessage(), outputJson);
            } catch (Exception ex) {
                return parent.failUnexpected(event, EVALUATION_TELEMETRY, fields, ex.getMessage(), outputJson);
            }
        }

        private Map<String, Object> buildResponse(String event, TelemetryFrame frame, EvaluationResult result) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reasonCode", result.telemetry().reasonCode());
            data.put("credentialReference", result.credentialReference());
            if (hasText(result.credentialId())) {
                data.put("credentialId", result.credentialId());
            }
            data.put("algorithm", result.algorithm().name());
            if (result.digits() != null) {
                data.put("digits", result.digits());
            }
            data.put("previousCounter", result.previousCounter());
            data.put("nextCounter", result.nextCounter());
            if (result.otp() != null) {
                data.put("otp", result.otp());
            }
            if (result.samplePresetKey() != null) {
                data.put("samplePresetKey", result.samplePresetKey());
            }
            if (result.samplePresetLabel() != null) {
                data.put("samplePresetLabel", result.samplePresetLabel());
            }
            if (result.previews() != null && !result.previews().isEmpty()) {
                data.put(
                        "previews",
                        result.previews().stream().map(this::mapPreview).toList());
            }
            result.verboseTrace().ifPresent(trace -> data.put("trace", VerboseTraceMapper.toMap(trace)));
            return TelemetryJson.response(event, frame, data);
        }

        private Map<String, Object> mapPreview(OtpPreview preview) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (preview.counter() != null) {
                map.put("counter", preview.counter());
            }
            map.put("delta", preview.delta());
            map.put("otp", preview.otp());
            return map;
        }

        private static int exitCode(HotpEvaluationApplicationService.TelemetryStatus status) {
            return switch (status) {
                case SUCCESS -> CommandLine.ExitCode.OK;
                case INVALID -> CommandLine.ExitCode.USAGE;
                case ERROR -> CommandLine.ExitCode.SOFTWARE;
            };
        }

        private boolean hasInlineInputs() {
            return hasText(secretHex) || counter != null || metadata != null;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
