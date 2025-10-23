package io.openauth.sim.cli;

import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.IssuanceCommand;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService.IssuanceResult;
import io.openauth.sim.application.telemetry.HotpTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Comparator;
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

    private int failValidation(String event, HotpTelemetryAdapter adapter, Map<String, Object> fields, String message) {
        TelemetryFrame frame = adapter.validationFailure(
                nextTelemetryId(), "validation_error", sanitizeMessage(message), true, fields);
        writeFrame(err(), event, frame);
        return CommandLine.ExitCode.USAGE;
    }

    private int failUnexpected(String event, HotpTelemetryAdapter adapter, Map<String, Object> fields, String message) {
        TelemetryFrame frame =
                adapter.error(nextTelemetryId(), "unexpected_error", sanitizeMessage(message), false, fields);
        writeFrame(err(), event, frame);
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
                return parent.failValidation(event, ISSUANCE_TELEMETRY, fields, ex.getMessage());
            } catch (Exception ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("credentialId", credentialId);
                return parent.failUnexpected(event, ISSUANCE_TELEMETRY, fields, ex.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List HOTP credentials.")
    static final class ListCommand extends AbstractHotpCommand {

        @Override
        public Integer call() {
            try (CredentialStore store = openStore()) {
                List<Credential> credentials = store.findAll().stream()
                        .filter(credential -> credential.type() == CredentialType.OATH_HOTP)
                        .sorted(Comparator.comparing(Credential::name))
                        .toList();

                out().printf(
                                Locale.ROOT,
                                "event=%s status=success reasonCode=success sanitized=true count=%d%n",
                                event("list"),
                                credentials.size());

                for (Credential credential : credentials) {
                    String algorithm = credential.attributes().get("hotp.algorithm");
                    String digits = credential.attributes().get("hotp.digits");
                    String counter = credential.attributes().get("hotp.counter");
                    out().printf(
                                    Locale.ROOT,
                                    "credentialId=%s algorithm=%s digits=%s counter=%s%n",
                                    credential.name(),
                                    algorithm,
                                    digits,
                                    counter);
                }
                return CommandLine.ExitCode.OK;
            } catch (IllegalArgumentException ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                return parent.failValidation(event("list"), ISSUANCE_TELEMETRY, fields, ex.getMessage());
            } catch (Exception ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                return parent.failUnexpected(event("list"), ISSUANCE_TELEMETRY, fields, ex.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "evaluate", description = "Validate HOTP responses.")
    static final class EvaluateCommand extends AbstractHotpCommand {

        @CommandLine.Option(
                names = "--credential-id",
                paramLabel = "<id>",
                required = true,
                description = "Identifier for the stored credential")
        String credentialId;

        @CommandLine.Option(names = "--verbose", description = "Emit a detailed verbose trace of the evaluation steps")
        boolean verbose;

        @Override
        public Integer call() {
            String event = event("evaluate");
            try (CredentialStore store = openStore()) {
                HotpEvaluationApplicationService service = new HotpEvaluationApplicationService(store);
                EvaluationCommand command = new EvaluationCommand.Stored(credentialId.trim());
                EvaluationResult result = service.evaluate(command, verbose);
                TelemetryFrame frame = result.telemetry().emit(EVALUATION_TELEMETRY, nextTelemetryId());
                HotpEvaluationApplicationService.TelemetryStatus status =
                        result.telemetry().status();
                PrintWriter writer = status == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS ? out() : err();
                writeFrame(writer, event, frame);
                if (status == HotpEvaluationApplicationService.TelemetryStatus.SUCCESS) {
                    writer.printf(Locale.ROOT, "generatedOtp=%s%n", result.otp());
                }
                result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(writer, trace));
                return switch (status) {
                    case SUCCESS -> CommandLine.ExitCode.OK;
                    case INVALID -> CommandLine.ExitCode.USAGE;
                    case ERROR -> CommandLine.ExitCode.SOFTWARE;
                };
            } catch (IllegalArgumentException ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("credentialId", credentialId);
                return parent.failValidation(event, EVALUATION_TELEMETRY, fields, ex.getMessage());
            } catch (Exception ex) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("credentialId", credentialId);
                return parent.failUnexpected(event, EVALUATION_TELEMETRY, fields, ex.getMessage());
            }
        }
    }
}
