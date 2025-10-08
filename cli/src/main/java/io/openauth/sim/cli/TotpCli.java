package io.openauth.sim.cli;

import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.application.telemetry.TotpTelemetryAdapter;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService.TelemetryStatus;
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
    subcommands = {
      TotpCli.ListCommand.class,
      TotpCli.EvaluateStoredCommand.class,
      TotpCli.EvaluateInlineCommand.class
    })
public final class TotpCli implements Callable<Integer> {

  private static final String EVENT_PREFIX = "cli.totp.";
  private static final TotpTelemetryAdapter EVALUATION_TELEMETRY =
      TelemetryContracts.totpEvaluationAdapter();
  private static final String DEFAULT_DATABASE_FILE = "totp-credentials.db";

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(
      names = {"-d", "--database"},
      paramLabel = "<path>",
      scope = CommandLine.ScopeType.INHERIT,
      description = "Path to the credential store database (default: data/totp-credentials.db)")
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
      String event, TelemetrySignal signal, Map<String, Object> fields, String message) {
    TelemetryFrame frame =
        EVALUATION_TELEMETRY.validationFailure(
            nextTelemetryId(), signal.reasonCode(), sanitizeMessage(message), true, fields);
    writeFrame(err(), event, frame);
    return CommandLine.ExitCode.USAGE;
  }

  private int failUnexpected(String event, Map<String, Object> fields, String message) {
    TelemetryFrame frame =
        EVALUATION_TELEMETRY.error(
            nextTelemetryId(), "unexpected_error", sanitizeMessage(message), false, fields);
    writeFrame(err(), event, frame);
    return CommandLine.ExitCode.SOFTWARE;
  }

  private static void writeFrame(PrintWriter writer, String event, TelemetryFrame frame) {
    StringBuilder builder =
        new StringBuilder("event=").append(event).append(" status=").append(frame.status());
    frame
        .fields()
        .forEach(
            (key, value) -> {
              if ("telemetryId".equals(key)) {
                builder.append(' ').append(key).append('=').append(value);
                return;
              }
              builder.append(' ').append(key).append('=').append(value);
            });
    writer.println(builder);
  }

  private abstract static class AbstractTotpCommand implements Callable<Integer> {

    @CommandLine.ParentCommand TotpCli parent;

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

    protected int failUnexpected(String event, Map<String, Object> fields, String message) {
      return parent.failUnexpected(event, fields, message);
    }

    protected int failValidation(
        String event, TelemetrySignal signal, Map<String, Object> fields, String message) {
      return parent.failValidation(event, signal, fields, message);
    }
  }

  @CommandLine.Command(name = "list", description = "Show stored TOTP credentials.")
  static final class ListCommand extends AbstractTotpCommand {

    @Override
    public Integer call() {
      try (CredentialStore store = openStore()) {
        List<Credential> credentials =
            store.findAll().stream()
                .filter(credential -> credential.type() == CredentialType.OATH_TOTP)
                .sorted(Comparator.comparing(Credential::name))
                .collect(Collectors.toList());

        out().println("event=" + event("list") + " status=success count=" + credentials.size());

        for (Credential credential : credentials) {
          Map<String, String> attributes =
              TotpPersistenceDefaults.ensureDefaults(credential.attributes());
          String line =
              "credentialId="
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

        return CommandLine.ExitCode.OK;
      } catch (Exception ex) {
        return failUnexpected(
            event("list"),
            Map.of(),
            "Failed to list TOTP credentials: " + sanitizeMessage(ex.getMessage()));
      }
    }
  }

  @CommandLine.Command(
      name = "evaluate",
      description = "Validate a stored TOTP credential using a supplied OTP.")
  static final class EvaluateStoredCommand extends AbstractTotpCommand {

    @CommandLine.Option(
        names = "--credential-id",
        required = true,
        paramLabel = "<id>",
        description = "Identifier of the stored credential")
    String credentialId;

    @CommandLine.Option(
        names = "--otp",
        required = true,
        paramLabel = "<code>",
        description = "One-time password provided by the operator")
    String otp;

    @CommandLine.Option(
        names = "--timestamp",
        paramLabel = "<epochSeconds>",
        description = "Timestamp (Unix seconds) representing the evaluation time")
    Long timestamp;

    @CommandLine.Option(
        names = "--drift-backward",
        paramLabel = "<steps>",
        defaultValue = "1",
        description = "Permitted backward time-step drift")
    int driftBackward;

    @CommandLine.Option(
        names = "--drift-forward",
        paramLabel = "<steps>",
        defaultValue = "1",
        description = "Permitted forward time-step drift")
    int driftForward;

    @CommandLine.Option(
        names = "--timestamp-override",
        paramLabel = "<epochSeconds>",
        description = "Override timestamp supplied by the authenticator")
    Long timestampOverride;

    @Override
    public Integer call() {
      TotpDriftWindow window = TotpDriftWindow.of(driftBackward, driftForward);
      Instant evaluationInstant = timestamp != null ? Instant.ofEpochSecond(timestamp) : null;
      Optional<Instant> override =
          timestampOverride != null
              ? Optional.of(Instant.ofEpochSecond(timestampOverride))
              : Optional.empty();

      try (CredentialStore store = openStore()) {
        TotpEvaluationApplicationService service = new TotpEvaluationApplicationService(store);
        EvaluationResult result =
            service.evaluate(
                new EvaluationCommand.Stored(
                    credentialId, otp, window, evaluationInstant, override));
        return handleResult(result, event("evaluate"), true);
      } catch (Exception ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialReference", true);
        fields.put("credentialId", credentialId);
        return failUnexpected(
            event("evaluate"), fields, "Evaluation failed: " + sanitizeMessage(ex.getMessage()));
      }
    }

    private Integer handleResult(
        EvaluationResult result, String event, boolean credentialReference) {
      TelemetrySignal signal = result.telemetry();
      switch (signal.status()) {
        case SUCCESS -> {
          TelemetryFrame frame = signal.emit(EVALUATION_TELEMETRY, nextTelemetryId());
          writeFrame(
              out(),
              event,
              addResultFields(
                  frame, credentialReference, result.valid(), result.matchedSkewSteps()));
          return CommandLine.ExitCode.OK;
        }
        case INVALID -> {
          Map<String, Object> fields = new LinkedHashMap<>();
          fields.put("credentialReference", credentialReference);
          fields.put("credentialId", result.credentialId());
          fields.put("matchedSkewSteps", result.matchedSkewSteps());
          return failValidation(
              event,
              signal,
              fields,
              Optional.ofNullable(signal.reason()).orElse(signal.reasonCode()));
        }
        case ERROR -> {
          Map<String, Object> fields = new LinkedHashMap<>();
          fields.put("credentialReference", credentialReference);
          fields.put("credentialId", result.credentialId());
          return failUnexpected(
              event, fields, Optional.ofNullable(signal.reason()).orElse("TOTP evaluation failed"));
        }
      }
      throw new IllegalStateException("Unhandled telemetry status: " + signal.status());
    }

    private TelemetryFrame addResultFields(
        TelemetryFrame frame, boolean credentialReference, boolean valid, int matchedSkew) {
      Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
      merged.put("credentialReference", credentialReference);
      merged.put("valid", valid);
      merged.put("matchedSkewSteps", matchedSkew);
      return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
    }
  }

  @CommandLine.Command(
      name = "evaluate-inline",
      description = "Validate an inline TOTP submission without referencing stored credentials.")
  static final class EvaluateInlineCommand extends AbstractTotpCommand {

    @CommandLine.Option(
        names = "--secret",
        required = true,
        paramLabel = "<hex>",
        description = "Shared secret in hex")
    String secretHex;

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
        names = "--drift-backward",
        defaultValue = "1",
        paramLabel = "<steps>",
        description = "Permitted backward drift steps")
    int driftBackward;

    @CommandLine.Option(
        names = "--drift-forward",
        defaultValue = "1",
        paramLabel = "<steps>",
        description = "Permitted forward drift steps")
    int driftForward;

    @CommandLine.Option(
        names = "--timestamp",
        paramLabel = "<epochSeconds>",
        description = "Timestamp (Unix seconds) for evaluation")
    Long timestamp;

    @CommandLine.Option(
        names = "--timestamp-override",
        paramLabel = "<epochSeconds>",
        description = "Authenticator-supplied timestamp override")
    Long timestampOverride;

    @CommandLine.Option(
        names = "--otp",
        required = true,
        paramLabel = "<code>",
        description = "One-time password to validate")
    String otp;

    @Override
    public Integer call() {
      TotpHashAlgorithm hashAlgorithm =
          TotpHashAlgorithm.valueOf(algorithm.toUpperCase(Locale.ROOT));
      TotpDriftWindow window = TotpDriftWindow.of(driftBackward, driftForward);
      Instant evaluationInstant = timestamp != null ? Instant.ofEpochSecond(timestamp) : null;
      Optional<Instant> override =
          timestampOverride != null
              ? Optional.of(Instant.ofEpochSecond(timestampOverride))
              : Optional.empty();

      try (CredentialStore store = openStore()) {
        TotpEvaluationApplicationService service = new TotpEvaluationApplicationService(store);
        EvaluationResult result =
            service.evaluate(
                new EvaluationCommand.Inline(
                    secretHex,
                    hashAlgorithm,
                    digits,
                    Duration.ofSeconds(stepSeconds),
                    otp,
                    window,
                    evaluationInstant,
                    override));
        return handleResult(result, event("evaluate"));
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields = Map.of("credentialReference", false);
        return failValidation(
            event("evaluate"),
            new TelemetrySignal(
                TelemetryStatus.INVALID, "validation_error", ex.getMessage(), true, fields),
            fields,
            ex.getMessage());
      } catch (Exception ex) {
        Map<String, Object> fields = Map.of("credentialReference", false);
        return failUnexpected(
            event("evaluate"), fields, "Evaluation failed: " + sanitizeMessage(ex.getMessage()));
      }
    }

    private Integer handleResult(EvaluationResult result, String event) {
      TelemetrySignal signal = result.telemetry();
      switch (signal.status()) {
        case SUCCESS -> {
          TelemetryFrame frame = signal.emit(EVALUATION_TELEMETRY, nextTelemetryId());
          writeFrame(
              out(), event, addResultFields(frame, result.valid(), result.matchedSkewSteps()));
          return CommandLine.ExitCode.OK;
        }
        case INVALID -> {
          Map<String, Object> fields = new LinkedHashMap<>(signal.fields());
          fields.put("credentialReference", false);
          fields.put("matchedSkewSteps", result.matchedSkewSteps());
          return failValidation(
              event,
              signal,
              fields,
              Optional.ofNullable(signal.reason()).orElse(signal.reasonCode()));
        }
        case ERROR -> {
          Map<String, Object> fields = Map.of("credentialReference", false);
          return failUnexpected(
              event, fields, Optional.ofNullable(signal.reason()).orElse("TOTP evaluation failed"));
        }
      }
      throw new IllegalStateException("Unhandled telemetry status: " + signal.status());
    }

    private TelemetryFrame addResultFields(TelemetryFrame frame, boolean valid, int matchedSkew) {
      Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
      merged.put("credentialReference", false);
      merged.put("valid", valid);
      merged.put("matchedSkewSteps", matchedSkew);
      return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
    }
  }
}
