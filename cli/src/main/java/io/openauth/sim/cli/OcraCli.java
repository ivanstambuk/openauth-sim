package io.openauth.sim.cli;

import io.openauth.sim.application.ocra.OcraCredentialResolvers;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService.EvaluationValidationException;
import io.openauth.sim.application.ocra.OcraEvaluationRequests;
import io.openauth.sim.application.ocra.OcraInlineIdentifiers;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationCommand;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationReason;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationResult;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService.VerificationValidationException;
import io.openauth.sim.application.ocra.OcraVerificationRequests;
import io.openauth.sim.application.telemetry.OcraTelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryContracts;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/** CLI facade for OCRA credential lifecycle operations. */
@CommandLine.Command(
    name = "ocra",
    mixinStandardHelpOptions = true,
    description = "Manage OCRA credentials and evaluate responses.",
    subcommands = {
      OcraCli.ImportCommand.class,
      OcraCli.ListCommand.class,
      OcraCli.DeleteCommand.class,
      OcraCli.EvaluateCommand.class,
      OcraCli.VerifyCommand.class
    })
public final class OcraCli implements Callable<Integer> {

  private static final String EVENT_PREFIX = "cli.ocra.";
  private static final OcraTelemetryAdapter EVALUATION_TELEMETRY =
      TelemetryContracts.ocraEvaluationAdapter();
  private static final OcraTelemetryAdapter VERIFICATION_TELEMETRY =
      TelemetryContracts.ocraVerificationAdapter();

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  private static final String DEFAULT_DATABASE_FILE = "credentials.db";

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

  private Path databasePath() {
    if (database != null) {
      return database.toAbsolutePath();
    }
    return ProjectPaths.resolveDataFile(DEFAULT_DATABASE_FILE);
  }

  private static String event(String suffix) {
    return EVENT_PREFIX + suffix;
  }

  static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  static String sanitizeMessage(String message) {
    if (message == null) {
      return "unspecified";
    }
    return message.replace('\n', ' ').replace('\r', ' ').trim();
  }

  static Map<String, String> mapOf(String key, String value) {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  void emit(
      PrintWriter writer,
      String event,
      String status,
      String reasonCode,
      boolean sanitized,
      Map<String, String> fields) {
    TelemetryFrame frame = buildFrame(adapterFor(event), status, reasonCode, sanitized, fields);
    writeFrame(writer, event, frame);
  }

  private static OcraTelemetryAdapter adapterFor(String event) {
    return event.endsWith(".verify") ? VERIFICATION_TELEMETRY : EVALUATION_TELEMETRY;
  }

  private static TelemetryFrame buildFrame(
      OcraTelemetryAdapter adapter,
      String status,
      String reasonCode,
      boolean sanitized,
      Map<String, String> fields) {
    Map<String, Object> payload = new LinkedHashMap<>();
    String message = null;
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      String value = entry.getValue();
      if (!hasText(value)) {
        continue;
      }
      if ("reason".equals(entry.getKey())) {
        message = value.trim();
      }
      payload.put(entry.getKey(), value.trim());
    }

    String telemetryId = nextTelemetryId();
    if ("invalid".equals(status)) {
      return adapter.validationFailure(telemetryId, reasonCode, message, sanitized, payload);
    }
    if ("error".equals(status)) {
      return adapter.error(telemetryId, reasonCode, message, sanitized, payload);
    }
    return adapter.status(status, telemetryId, reasonCode, sanitized, message, payload);
  }

  private static void writeFrame(PrintWriter writer, String event, TelemetryFrame frame) {
    StringBuilder builder =
        new StringBuilder("event=").append(event).append(" status=").append(frame.status());

    Object reasonCode = frame.fields().get("reasonCode");
    if (reasonCode != null) {
      builder.append(' ').append("reasonCode=").append(reasonCode);
    }

    Object sanitized = frame.fields().get("sanitized");
    if (sanitized != null) {
      builder.append(' ').append("sanitized=").append(sanitized);
    }

    frame
        .fields()
        .forEach(
            (key, value) -> {
              if ("telemetryId".equals(key)) {
                return;
              }
              if ("reasonCode".equals(key) || "sanitized".equals(key)) {
                return;
              }
              builder.append(' ').append(key).append('=').append(value);
            });

    writer.println(builder);
  }

  private static String nextTelemetryId() {
    return "cli-" + UUID.randomUUID();
  }

  private int failValidation(String event, String reasonCode, String message) {
    Map<String, String> fields = mapOf("reason", sanitizeMessage(message));
    emit(err(), event, "invalid", reasonCode, true, fields);
    return CommandLine.ExitCode.USAGE;
  }

  private int failUnexpected(String event, String message) {
    Map<String, String> fields = mapOf("reason", sanitizeMessage(message));
    emit(err(), event, "error", "unexpected_error", false, fields);
    return CommandLine.ExitCode.SOFTWARE;
  }

  abstract static sealed class AbstractOcraCommand implements Callable<Integer>
      permits ImportCommand, ListCommand, DeleteCommand, EvaluateCommand, VerifyCommand {

    @CommandLine.ParentCommand OcraCli parent;

    private final OcraCredentialFactory credentialFactory = new OcraCredentialFactory();
    private OcraCredentialPersistenceAdapter persistenceAdapter =
        new OcraCredentialPersistenceAdapter();

    protected PrintWriter out() {
      return parent.out();
    }

    protected PrintWriter err() {
      return parent.err();
    }

    protected Path databasePath() {
      return parent.databasePath();
    }

    protected OcraCredentialFactory credentialFactory() {
      return credentialFactory;
    }

    protected OcraCredentialPersistenceAdapter persistenceAdapter() {
      return persistenceAdapter;
    }

    OcraCredentialPersistenceAdapter swapPersistenceAdapter(
        OcraCredentialPersistenceAdapter adapter) {
      OcraCredentialPersistenceAdapter previous = persistenceAdapter;
      persistenceAdapter = adapter;
      return previous;
    }

    protected int failValidation(String event, String reasonCode, String message) {
      return parent.failValidation(event, reasonCode, message);
    }

    protected int failUnexpected(String event, String message) {
      return parent.failUnexpected(event, message);
    }

    protected void emitSuccess(String event, String reasonCode, Map<String, String> fields) {
      parent.emit(parent.out(), event, "success", reasonCode, true, fields);
    }

    protected void emitSummary(
        String event, String status, String reasonCode, Map<String, String> fields) {
      parent.emit(parent.out(), event, status, reasonCode, true, fields);
    }

    protected CredentialStore openStore() throws IOException {
      Path database = databasePath();
      if (database == null) {
        throw new CommandLine.ExecutionException(
            parent.spec.commandLine(), "--database=<path> is required");
      }
      return CredentialStoreFactory.openFileStore(database);
    }

    protected Optional<OcraCredentialDescriptor> resolveDescriptor(
        CredentialStore store, String id) {
      return store
          .findByName(id)
          .filter(credential -> credential.type() == CredentialType.OATH_OCRA)
          .map(VersionedCredentialRecordMapper::toRecord)
          .map(persistenceAdapter()::deserialize);
    }

    protected OcraCredentialDescriptor createDescriptor(
        String name,
        String suite,
        String sharedSecretHex,
        Long counter,
        String pinHashHex,
        Duration allowedDrift,
        Map<String, String> metadata) {
      OcraCredentialRequest request =
          new OcraCredentialRequest(
              name,
              suite,
              sharedSecretHex,
              SecretEncoding.HEX,
              counter,
              pinHashHex,
              allowedDrift,
              metadata);
      return credentialFactory().createDescriptor(request);
    }
  }

  @CommandLine.Command(
      name = "import",
      description = "Persist an OCRA credential descriptor into the store.")
  static final class ImportCommand extends AbstractOcraCommand {

    @CommandLine.Option(
        names = {"--credential-id"},
        paramLabel = "<id>",
        required = true,
        description = "Logical identifier for the credential")
    String credentialId;

    @CommandLine.Option(
        names = {"--suite"},
        paramLabel = "<ocra-suite>",
        required = true,
        description = "OCRA suite string (e.g. OCRA-1:HOTP-SHA1-6:QA08)")
    String suite;

    @CommandLine.Option(
        names = {"--secret"},
        paramLabel = "<hex>",
        required = true,
        description = "Shared secret material in hexadecimal")
    String sharedSecretHex;

    @CommandLine.Option(
        names = {"--counter"},
        paramLabel = "<value>",
        description = "Initial counter value (when suite requires a counter)")
    Long counter;

    @CommandLine.Option(
        names = {"--pin-hash"},
        paramLabel = "<hex>",
        description = "Optional PIN hash material (hexadecimal)")
    String pinHashHex;

    @CommandLine.Option(
        names = {"--drift-seconds"},
        paramLabel = "<seconds>",
        description = "Override allowed timestamp drift (seconds)")
    Long allowedDriftSeconds;

    @Override
    public Integer call() {
      String event = event("import");
      try (CredentialStore store = openStore()) {
        Duration allowedDrift =
            allowedDriftSeconds == null ? null : Duration.ofSeconds(allowedDriftSeconds);
        OcraCredentialDescriptor descriptor =
            createDescriptor(
                credentialId.trim(),
                suite.trim(),
                sharedSecretHex.replace(" ", "").trim(),
                counter,
                hasText(pinHashHex) ? pinHashHex.trim() : null,
                allowedDrift,
                Map.of("source", "cli"));

        store.save(
            VersionedCredentialRecordMapper.toCredential(
                persistenceAdapter().serialize(descriptor)));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("credentialId", descriptor.name());
        fields.put("suite", descriptor.suite().value());
        emitSuccess(event, "created", fields);
        return CommandLine.ExitCode.OK;
      } catch (IllegalArgumentException ex) {
        return failValidation(event, "validation_error", ex.getMessage());
      } catch (Exception ex) {
        return failUnexpected(event, ex.getMessage());
      }
    }
  }

  @CommandLine.Command(name = "list", description = "List stored OCRA credentials.")
  static final class ListCommand extends AbstractOcraCommand {

    @CommandLine.Option(
        names = {"--verbose"},
        description = "Include extended metadata in the output")
    boolean verbose;

    @Override
    public Integer call() {
      String event = event("list");
      try (CredentialStore store = openStore()) {
        List<OcraCredentialDescriptor> descriptors = new ArrayList<>();
        for (Credential credential : store.findAll()) {
          if (credential.type() != CredentialType.OATH_OCRA) {
            continue;
          }
          descriptors.add(
              persistenceAdapter()
                  .deserialize(VersionedCredentialRecordMapper.toRecord(credential)));
        }
        descriptors.sort(Comparator.comparing(OcraCredentialDescriptor::name));

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("count", Integer.toString(descriptors.size()));
        emitSummary(event, "success", "success", summary);

        for (OcraCredentialDescriptor descriptor : descriptors) {
          String line =
              String.format(
                  Locale.ROOT,
                  "credentialId=%s suite=%s hasCounter=%s hasPin=%s hasDrift=%s",
                  descriptor.name(),
                  descriptor.suite().value(),
                  descriptor.counter().isPresent(),
                  descriptor.pinHash().isPresent(),
                  descriptor.allowedTimestampDrift().isPresent());
          out().println(line);
          if (verbose && !descriptor.metadata().isEmpty()) {
            descriptor
                .metadata()
                .forEach(
                    (key, value) ->
                        out()
                            .println(
                                String.format(
                                    Locale.ROOT,
                                    "  metadata.%s=%s",
                                    key,
                                    value.replace('\n', ' '))));
          }
        }
        return CommandLine.ExitCode.OK;
      } catch (IllegalArgumentException ex) {
        return failValidation(event, "validation_error", ex.getMessage());
      } catch (Exception ex) {
        return failUnexpected(event, ex.getMessage());
      }
    }
  }

  @CommandLine.Command(name = "delete", description = "Delete a stored OCRA credential.")
  static final class DeleteCommand extends AbstractOcraCommand {

    @CommandLine.Option(
        names = {"--credential-id"},
        paramLabel = "<id>",
        required = true,
        description = "Logical identifier for the credential")
    String credentialId;

    @Override
    public Integer call() {
      String event = event("delete");
      try (CredentialStore store = openStore()) {
        boolean removed = store.delete(credentialId.trim());
        if (!removed) {
          return failValidation(
              event, "credential_not_found", "credentialId " + credentialId + " not found");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("credentialId", credentialId.trim());
        emitSuccess(event, "deleted", fields);
        return CommandLine.ExitCode.OK;
      } catch (IllegalArgumentException ex) {
        return failValidation(event, "validation_error", ex.getMessage());
      } catch (Exception ex) {
        return failUnexpected(event, ex.getMessage());
      }
    }
  }

  @CommandLine.Command(name = "evaluate", description = "Generate an OTP using OCRA.")
  static final class EvaluateCommand extends AbstractOcraCommand {

    @CommandLine.Option(
        names = {"--credential-id"},
        paramLabel = "<id>",
        description = "Evaluate using a stored credential")
    String credentialId;

    @CommandLine.Option(
        names = {"--suite"},
        paramLabel = "<ocra-suite>",
        description = "Suite to evaluate when not referencing a stored credential")
    String suite;

    @CommandLine.Option(
        names = {"--secret"},
        paramLabel = "<hex>",
        description = "Shared secret material in hexadecimal")
    String sharedSecretHex;

    @CommandLine.Option(
        names = {"--challenge"},
        paramLabel = "<value>",
        description = "Challenge question input")
    String challenge;

    @CommandLine.Option(
        names = {"--client-challenge"},
        paramLabel = "<value>",
        description = "Client-side component of a split challenge")
    String clientChallenge;

    @CommandLine.Option(
        names = {"--server-challenge"},
        paramLabel = "<value>",
        description = "Server-side component of a split challenge")
    String serverChallenge;

    @CommandLine.Option(
        names = {"--session"},
        paramLabel = "<hex>",
        description = "Session information payload for suite data inputs")
    String session;

    @CommandLine.Option(
        names = {"--timestamp"},
        paramLabel = "<hex>",
        description = "Timestamp payload when required by the suite")
    String timestamp;

    @CommandLine.Option(
        names = {"--counter"},
        paramLabel = "<value>",
        description = "Counter value to use when the suite has a counter input")
    Long counter;

    @CommandLine.Option(
        names = {"--pin-hash"},
        paramLabel = "<hex>",
        description = "PIN hash material if required")
    String pinHashHex;

    @Override
    public Integer call() {
      String event = event("evaluate");
      boolean hasCredential = hasText(credentialId);
      boolean hasSecret = hasText(sharedSecretHex);

      if (hasCredential && hasSecret) {
        return failValidation(
            event,
            "credential_conflict",
            "Provide either credentialId or sharedSecretHex, not both");
      }
      if (!hasCredential && !hasSecret) {
        return failValidation(
            event, "credential_missing", "credentialId or sharedSecretHex must be provided");
      }

      try {
        if (hasCredential) {
          try (CredentialStore store = openStore()) {
            OcraEvaluationApplicationService service =
                new OcraEvaluationApplicationService(
                    Clock.systemUTC(), OcraCredentialResolvers.forStore(store));
            EvaluationCommand command =
                OcraEvaluationRequests.stored(
                    new OcraEvaluationRequests.StoredInputs(
                        credentialId,
                        challenge,
                        session,
                        clientChallenge,
                        serverChallenge,
                        pinHashHex,
                        timestamp,
                        counter));

            EvaluationResult result = service.evaluate(command);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("credentialId", credentialId.trim());
            fields.put("suite", result.suite());
            fields.put("otp", result.otp());
            emitSuccess(event, "success", fields);
            return CommandLine.ExitCode.OK;
          }
        }

        if (!hasText(suite)) {
          return failValidation(event, "suite_missing", "suite is required for inline mode");
        }
        OcraEvaluationApplicationService service =
            new OcraEvaluationApplicationService(
                Clock.systemUTC(), OcraCredentialResolvers.emptyResolver());
        EvaluationCommand command =
            OcraEvaluationRequests.inline(
                new OcraEvaluationRequests.InlineInputs(
                    OcraInlineIdentifiers.sharedIdentifier(suite, sharedSecretHex),
                    suite,
                    sharedSecretHex,
                    challenge,
                    session,
                    clientChallenge,
                    serverChallenge,
                    pinHashHex,
                    timestamp,
                    counter,
                    null));

        EvaluationResult result = service.evaluate(command);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mode", "inline");
        fields.put("suite", result.suite());
        fields.put("otp", result.otp());
        emitSuccess(event, "success", fields);
        return CommandLine.ExitCode.OK;
      } catch (EvaluationValidationException ex) {
        return failValidation(event, ex.reasonCode(), ex.getMessage());
      } catch (Exception ex) {
        return failUnexpected(event, ex.getMessage());
      }
    }
  }

  @CommandLine.Command(name = "verify", description = "Verify a supplied OTP using OCRA.")
  static final class VerifyCommand extends AbstractOcraCommand {

    private static final int EXIT_STRICT_MISMATCH = 2;

    @CommandLine.Option(
        names = {"--credential-id"},
        paramLabel = "<id>",
        description = "Verify using a stored credential")
    String credentialId;

    @CommandLine.Option(
        names = {"--suite"},
        paramLabel = "<ocra-suite>",
        description = "Suite to verify when not referencing a stored credential")
    String suite;

    @CommandLine.Option(
        names = {"--secret"},
        paramLabel = "<hex>",
        description = "Shared secret material in hexadecimal for inline verification")
    String sharedSecretHex;

    @CommandLine.Option(
        names = {"--otp"},
        paramLabel = "<value>",
        description = "OTP value supplied by the operator")
    String otp;

    @CommandLine.Option(
        names = {"--challenge"},
        paramLabel = "<value>",
        description = "Challenge question input")
    String challenge;

    @CommandLine.Option(
        names = {"--client-challenge"},
        paramLabel = "<value>",
        description = "Client-side component of a split challenge")
    String clientChallenge;

    @CommandLine.Option(
        names = {"--server-challenge"},
        paramLabel = "<value>",
        description = "Server-side component of a split challenge")
    String serverChallenge;

    @CommandLine.Option(
        names = {"--session"},
        paramLabel = "<hex>",
        description = "Session information payload for suite data inputs")
    String session;

    @CommandLine.Option(
        names = {"--timestamp"},
        paramLabel = "<hex>",
        description = "Timestamp payload when required by the suite")
    String timestamp;

    @CommandLine.Option(
        names = {"--counter"},
        paramLabel = "<value>",
        description = "Counter value to use when the suite has a counter input")
    Long counter;

    @CommandLine.Option(
        names = {"--pin-hash"},
        paramLabel = "<hex>",
        description = "PIN hash material if required")
    String pinHashHex;

    @Override
    public Integer call() {
      String event = event("verify");

      if (!hasText(otp)) {
        return failValidation(event, "otp_missing", "otp is required for verification");
      }

      boolean hasCredential = hasText(credentialId);
      boolean hasSecret = hasText(sharedSecretHex);

      if (hasCredential && hasSecret) {
        return failValidation(
            event,
            "credential_conflict",
            "Provide either credentialId or sharedSecretHex, not both");
      }
      if (!hasCredential && !hasSecret) {
        return failValidation(
            event, "credential_missing", "credentialId or sharedSecretHex must be provided");
      }

      try {
        if (hasCredential) {
          String descriptorId = credentialId.trim();
          try (CredentialStore store = openStore()) {
            OcraVerificationApplicationService service =
                new OcraVerificationApplicationService(
                    OcraCredentialResolvers.forVerificationStore(store), store);
            VerificationCommand command =
                OcraVerificationRequests.stored(
                    new OcraVerificationRequests.StoredInputs(
                        descriptorId,
                        otp,
                        challenge,
                        clientChallenge,
                        serverChallenge,
                        session,
                        pinHashHex,
                        timestamp,
                        counter));
            VerificationResult result = service.verify(command);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("credentialSource", "stored");
            fields.put("credentialId", result.credentialId());
            fields.put("suite", result.suite());
            return handleResult(event, result, fields);
          }
        }

        // Inline verification path
        if (!hasText(suite)) {
          return failValidation(event, "suite_missing", "suite is required for inline mode");
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("credentialSource", "inline");
        fields.put("suite", suite.trim());

        OcraVerificationApplicationService service =
            new OcraVerificationApplicationService(
                OcraCredentialResolvers.emptyVerificationResolver(), null);
        VerificationCommand command =
            OcraVerificationRequests.inline(
                new OcraVerificationRequests.InlineInputs(
                    OcraInlineIdentifiers.sharedIdentifier(suite, sharedSecretHex),
                    suite,
                    sharedSecretHex,
                    otp,
                    challenge,
                    clientChallenge,
                    serverChallenge,
                    session,
                    pinHashHex,
                    timestamp,
                    counter,
                    null));

        VerificationResult result = service.verify(command);
        fields.put("suite", result.suite());
        fields.put("descriptor", result.credentialId());
        return handleResult(event, result, fields);

      } catch (IllegalArgumentException ex) {
        return failValidation(event, "validation_error", ex.getMessage());
      } catch (VerificationValidationException ex) {
        return failValidation(event, ex.reasonCode(), ex.getMessage());
      } catch (Exception ex) {
        return failUnexpected(event, ex.getMessage());
      }
    }

    private int handleResult(String event, VerificationResult result, Map<String, String> fields) {
      String reasonCode = reasonCodeFor(result.reason());
      return switch (result.status()) {
        case MATCH -> {
          emitSummary(event, "match", reasonCode, fields);
          yield CommandLine.ExitCode.OK;
        }
        case MISMATCH -> {
          emitSummary(event, "mismatch", reasonCode, fields);
          yield EXIT_STRICT_MISMATCH;
        }
        case INVALID -> handleInvalid(event, result.reason(), fields);
      };
    }

    int handleInvalid(String event, VerificationReason reason, Map<String, String> fields) {
      return switch (reason) {
        case VALIDATION_FAILURE ->
            failValidation(event, "validation_error", "Verification inputs failed validation");
        case CREDENTIAL_NOT_FOUND ->
            failValidation(event, "credential_not_found", "credentialId not found");
        case UNEXPECTED_ERROR -> failUnexpected(event, "Unexpected error during verification");
        case MATCH, STRICT_MISMATCH -> failUnexpected(event, "Unexpected verification state");
      };
    }

    static String reasonCodeFor(VerificationReason reason) {
      return switch (reason) {
        case MATCH -> "match";
        case STRICT_MISMATCH -> "strict_mismatch";
        case VALIDATION_FAILURE -> "validation_error";
        case CREDENTIAL_NOT_FOUND -> "credential_not_found";
        case UNEXPECTED_ERROR -> "unexpected_error";
      };
    }
  }
}
