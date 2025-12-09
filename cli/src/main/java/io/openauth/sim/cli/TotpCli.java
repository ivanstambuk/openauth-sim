package io.openauth.sim.cli;

import io.openauth.sim.application.preview.OtpPreview;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.telemetry.TotpTelemetryAdapter;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
import io.openauth.sim.cli.support.EphemeralCredentialStore;
import io.openauth.sim.cli.support.JsonPrinter;
import io.openauth.sim.cli.support.TelemetryJson;
import io.openauth.sim.cli.support.VerboseTraceMapper;
import io.openauth.sim.core.encoding.Base32SecretCodec;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.otp.totp.TotpPersistenceDefaults;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;

/** CLI facade for validating TOTP credentials. */
@CommandLine.Command(
        name = "totp",
        mixinStandardHelpOptions = true,
        description = "Validate TOTP credentials and inspect stored entries.",
        subcommands = {TotpCli.ListCommand.class, TotpCli.EvaluateCommand.class})
public final class TotpCli implements Callable<Integer> {

    private static final String EVENT_PREFIX = "cli.totp.";
    private static final TotpTelemetryAdapter EVALUATION_TELEMETRY = TelemetryContracts.totpEvaluationAdapter();
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

    private Path databasePath() {
        if (database != null) {
            return database.toAbsolutePath();
        }
        return ProjectPaths.resolveDataFile(DEFAULT_DATABASE_FILE);
    }

    private static String event(String suffix) {
        return EVENT_PREFIX + suffix;
    }

    private static String sanitizeMessage(String message) {
        if (message == null) {
            return "unspecified";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String nextTelemetryId() {
        return "cli-totp-" + UUID.randomUUID();
    }

    private int failValidation(
            String event, TelemetrySignal signal, Map<String, Object> fields, String message, boolean outputJson) {
        TelemetryFrame frame = EVALUATION_TELEMETRY.validationFailure(
                nextTelemetryId(), signal.reasonCode(), sanitizeMessage(message), true, fields);
        if (outputJson) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.USAGE;
    }

    private int failUnexpected(String event, Map<String, Object> fields, String message, boolean outputJson) {
        TelemetryFrame frame = EVALUATION_TELEMETRY.error(
                nextTelemetryId(), "unexpected_error", sanitizeMessage(message), false, fields);
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
                builder.append(' ').append(key).append('=').append(value);
                return;
            }
            builder.append(' ').append(key).append('=').append(value);
        });
        writer.println(builder);
    }

    private abstract static class AbstractTotpCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        TotpCli parent;

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

        protected int failUnexpected(String event, Map<String, Object> fields, String message, boolean outputJson) {
            return parent.failUnexpected(event, fields, message, outputJson);
        }

        protected int failValidation(
                String event, TelemetrySignal signal, Map<String, Object> fields, String message, boolean outputJson) {
            return parent.failValidation(event, signal, fields, message, outputJson);
        }
    }

    @CommandLine.Command(name = "list", description = "Show stored TOTP credentials.")
    static final class ListCommand extends AbstractTotpCommand {

        @CommandLine.Option(names = "--output-json", description = "Emit a single JSON object instead of text output")
        boolean outputJson;

        @Override
        public Integer call() {
            try (CredentialStore store = openStore()) {
                List<Credential> credentials = store.findAll().stream()
                        .filter(credential -> credential.type() == CredentialType.OATH_TOTP)
                        .sorted(Comparator.comparing(Credential::name))
                        .collect(Collectors.toList());

                if (outputJson) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("count", credentials.size());
                    payload.put(
                            "credentials",
                            credentials.stream()
                                    .map(credential -> {
                                        Map<String, String> attributes =
                                                TotpPersistenceDefaults.ensureDefaults(credential.attributes());
                                        return Map.of(
                                                "credentialId",
                                                credential.name(),
                                                "algorithm",
                                                attributes.get(TotpPersistenceDefaults.ALGORITHM_ATTRIBUTE),
                                                "digits",
                                                attributes.get(TotpPersistenceDefaults.DIGITS_ATTRIBUTE),
                                                "stepSeconds",
                                                attributes.get(TotpPersistenceDefaults.STEP_SECONDS_ATTRIBUTE),
                                                "driftBackwardSteps",
                                                attributes.get(TotpPersistenceDefaults.DRIFT_BACKWARD_ATTRIBUTE),
                                                "driftForwardSteps",
                                                attributes.get(TotpPersistenceDefaults.DRIFT_FORWARD_ATTRIBUTE));
                                    })
                                    .toList());
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("event", event("list"));
                    response.put("status", "success");
                    response.put("reasonCode", "success");
                    response.put("data", payload);
                    JsonPrinter.print(out(), response, true);
                } else {
                    out().println("event=" + event("list") + " status=success count=" + credentials.size());

                    for (Credential credential : credentials) {
                        Map<String, String> attributes =
                                TotpPersistenceDefaults.ensureDefaults(credential.attributes());
                        String line = "credentialId="
                                + credential.name()
                                + " algorithm="
                                + attributes.get(TotpPersistenceDefaults.ALGORITHM_ATTRIBUTE)
                                + " digits="
                                + attributes.get(TotpPersistenceDefaults.DIGITS_ATTRIBUTE)
                                + " stepSeconds="
                                + attributes.get(TotpPersistenceDefaults.STEP_SECONDS_ATTRIBUTE)
                                + " driftBackwardSteps="
                                + attributes.get(TotpPersistenceDefaults.DRIFT_BACKWARD_ATTRIBUTE)
                                + " driftForwardSteps="
                                + attributes.get(TotpPersistenceDefaults.DRIFT_FORWARD_ATTRIBUTE);
                        out().println(line);
                    }
                }

                return CommandLine.ExitCode.OK;
            } catch (Exception ex) {
                return failUnexpected(
                        event("list"),
                        Map.of(),
                        "Failed to list TOTP credentials: " + sanitizeMessage(ex.getMessage()),
                        outputJson);
            }
        }
    }

    @CommandLine.Command(
            name = "evaluate",
            description =
                    "Generate a TOTP code from a stored credential (with --credential-id) or inline parameters (without).")
    static final class EvaluateCommand extends AbstractTotpCommand {

        @CommandLine.Option(
                names = "--credential-id",
                defaultValue = "",
                paramLabel = "<id>",
                description = "Identifier of the stored credential; omit to evaluate inline parameters")
        String credentialId;

        @CommandLine.Option(names = "--secret", paramLabel = "<hex>", description = "Shared secret in hexadecimal")
        String secretHex;

        @CommandLine.Option(
                names = "--secret-base32",
                paramLabel = "<base32>",
                description = "Shared secret in Base32 (RFC 4648)")
        String secretBase32;

        @CommandLine.Option(
                names = "--algorithm",
                defaultValue = "SHA1",
                paramLabel = "<name>",
                description = "TOTP hash algorithm (e.g. SHA1, SHA256)")
        String algorithm;

        @CommandLine.Option(
                names = "--digits",
                defaultValue = "6",
                paramLabel = "<digits>",
                description = "Number of digits expected from the authenticator")
        int digits;

        @CommandLine.Option(
                names = "--step-seconds",
                defaultValue = "30",
                paramLabel = "<seconds>",
                description = "Time-step duration in seconds")
        long stepSeconds;

        @CommandLine.Option(
                names = "--timestamp",
                paramLabel = "<epochSeconds>",
                description = "Timestamp (Unix seconds) representing the evaluation time")
        Long timestamp;

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

        @CommandLine.Option(
                names = "--timestamp-override",
                paramLabel = "<epochSeconds>",
                description = "Override timestamp supplied by the authenticator")
        Long timestampOverride;

        @CommandLine.Option(names = "--verbose", description = "Emit a detailed verbose trace of the evaluation steps")
        boolean verbose;

        @CommandLine.Option(names = "--output-json", description = "Emit a single JSON object instead of text output")
        boolean outputJson;

        @Override
        public Integer call() {
            boolean storedMode = hasText(credentialId);
            TotpDriftWindow window = TotpDriftWindow.of(windowBackward, windowForward);
            Instant evaluationInstant = timestamp != null ? Instant.ofEpochSecond(timestamp) : null;
            Optional<Instant> override = timestampOverride != null
                    ? Optional.of(Instant.ofEpochSecond(timestampOverride))
                    : Optional.empty();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("credentialReference", storedMode);
            if (storedMode) {
                fields.put("credentialId", credentialId);
                if (hasInlineSecrets()) {
                    return failValidation(
                            event("evaluate"),
                            new TelemetrySignal(
                                    TelemetryStatus.INVALID,
                                    "validation_error",
                                    "Inline secrets cannot be combined with --credential-id",
                                    true,
                                    fields),
                            fields,
                            "Inline secrets cannot be combined with --credential-id",
                            outputJson);
                }
                return handleStored(window, evaluationInstant, override, fields);
            }
            return handleInline(window, evaluationInstant, override, fields);
        }

        private Integer handleStored(
                TotpDriftWindow window,
                Instant evaluationInstant,
                Optional<Instant> override,
                Map<String, Object> fields) {
            try (CredentialStore store = openStore()) {
                TotpEvaluationApplicationService service = new TotpEvaluationApplicationService(store);
                EvaluationResult result = service.evaluate(
                        new EvaluationCommand.Stored(credentialId, "", window, evaluationInstant, override), verbose);
                return handleResult(result, event("evaluate"), true, outputJson);
            } catch (Exception ex) {
                return failUnexpected(
                        event("evaluate"),
                        fields,
                        "Evaluation failed: " + sanitizeMessage(ex.getMessage()),
                        outputJson);
            }
        }

        private Integer handleResult(
                EvaluationResult result, String event, boolean credentialReference, boolean outputJson) {
            TelemetrySignal signal = result.telemetry();
            TelemetryFrame frame =
                    addResultFields(signal.emit(EVALUATION_TELEMETRY, nextTelemetryId()), credentialReference, result);
            switch (signal.status()) {
                case SUCCESS -> {
                    if (outputJson) {
                        JsonPrinter.print(out(), buildResponse(event, frame, result), true);
                        return CommandLine.ExitCode.OK;
                    }
                    PrintWriter writer = out();
                    writeFrame(writer, event, frame);
                    OtpPreviewTableFormatter.print(writer, result.previews());
                    result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(writer, trace));
                    return CommandLine.ExitCode.OK;
                }
                case INVALID -> {
                    if (outputJson) {
                        JsonPrinter.print(out(), buildResponse(event, frame, result), true);
                        return CommandLine.ExitCode.USAGE;
                    }
                    writeFrame(err(), event, frame);
                    result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(err(), trace));
                    return CommandLine.ExitCode.USAGE;
                }
                case ERROR -> {
                    if (outputJson) {
                        JsonPrinter.print(out(), buildResponse(event, frame, result), true);
                        return CommandLine.ExitCode.SOFTWARE;
                    }
                    writeFrame(err(), event, frame);
                    result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(err(), trace));
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
            throw new IllegalStateException("Unhandled telemetry status: " + signal.status());
        }

        private TelemetryFrame addResultFields(
                TelemetryFrame frame, boolean credentialReference, EvaluationResult result) {
            Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
            merged.put("credentialReference", credentialReference);
            merged.put("valid", result.valid());
            merged.put("matchedSkewSteps", result.matchedSkewSteps());
            merged.put("reasonCode", result.telemetry().reasonCode());
            if (result.credentialId() != null && !result.credentialId().isBlank()) {
                merged.put("credentialId", result.credentialId());
            }
            if (result.otp() != null && !result.otp().isBlank()) {
                merged.put("otp", result.otp());
            }
            return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
        }

        private Map<String, Object> buildResponse(String event, TelemetryFrame frame, EvaluationResult result) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reasonCode", result.telemetry().reasonCode());
            data.put("credentialReference", result.credentialReference());
            data.put("valid", result.valid());
            data.put("matchedSkewSteps", result.matchedSkewSteps());
            if (result.credentialId() != null && !result.credentialId().isBlank()) {
                data.put("credentialId", result.credentialId());
            }
            if (result.otp() != null && !result.otp().isBlank()) {
                data.put("otp", result.otp());
            }
            data.put("algorithm", result.algorithm().name());
            if (result.digits() != null) {
                data.put("digits", result.digits());
            }
            if (result.stepDuration() != null) {
                data.put("stepSeconds", result.stepDuration().toSeconds());
            }
            if (result.driftWindow() != null) {
                data.put("driftBackwardSteps", result.driftWindow().backwardSteps());
                data.put("driftForwardSteps", result.driftWindow().forwardSteps());
            }
            if (result.previews() != null && !result.previews().isEmpty()) {
                data.put(
                        "previews",
                        result.previews().stream().map(this::mapPreview).toList());
            }
            result.verboseTrace().ifPresent(trace -> data.put("trace", VerboseTraceMapper.toMap(trace)));
            if (result.telemetry().reason() != null) {
                data.put("reason", result.telemetry().reason());
            }
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

        private Integer handleInline(
                TotpDriftWindow window,
                Instant evaluationInstant,
                Optional<Instant> override,
                Map<String, Object> fields) {
            try {
                String resolvedSecretHex = resolveSecret(secretHex, secretBase32);
                TotpHashAlgorithm hashAlgorithm = TotpHashAlgorithm.valueOf(algorithm.toUpperCase(Locale.ROOT));
                try (CredentialStore store = new EphemeralCredentialStore()) {
                    TotpEvaluationApplicationService service = new TotpEvaluationApplicationService(store);
                    EvaluationResult result = service.evaluate(
                            new EvaluationCommand.Inline(
                                    resolvedSecretHex,
                                    hashAlgorithm,
                                    digits,
                                    Duration.ofSeconds(stepSeconds),
                                    "",
                                    window,
                                    evaluationInstant,
                                    override),
                            verbose);
                    return handleResult(result, event("evaluate"), false, outputJson);
                }
            } catch (IllegalArgumentException ex) {
                return failValidation(
                        event("evaluate"),
                        new TelemetrySignal(TelemetryStatus.INVALID, "validation_error", ex.getMessage(), true, fields),
                        fields,
                        ex.getMessage(),
                        outputJson);
            } catch (Exception ex) {
                return failUnexpected(
                        event("evaluate"),
                        fields,
                        "Evaluation failed: " + sanitizeMessage(ex.getMessage()),
                        outputJson);
            }
        }

        private boolean hasInlineSecrets() {
            return hasText(secretHex) || hasText(secretBase32);
        }

        private static String resolveSecret(String secretHex, String secretBase32) {
            boolean hasHex = hasText(secretHex);
            boolean hasBase32 = hasText(secretBase32);
            if (hasHex && hasBase32) {
                throw new IllegalArgumentException("Provide either --secret or --secret-base32");
            }
            if (!hasHex && !hasBase32) {
                throw new IllegalArgumentException("Provide either --secret or --secret-base32");
            }
            if (hasBase32) {
                return Base32SecretCodec.toUpperHex(secretBase32);
            }
            return secretHex.trim();
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
