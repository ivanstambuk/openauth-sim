package io.openauth.sim.cli;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraInlineVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraStoredVerificationRequest;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationContext;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationReason;
import io.openauth.sim.core.credentials.ocra.OcraReplayVerifier.OcraVerificationResult;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator.OcraExecutionContext;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.MapDbCredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.support.ProjectPaths;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  private static final String DEFAULT_DATABASE_FILE = "ocra-credentials.db";

  @CommandLine.Option(
      names = {"-d", "--database"},
      paramLabel = "<path>",
      scope = CommandLine.ScopeType.INHERIT,
      description = "Path to the credential store database (default: data/ocra-credentials.db)")
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

  static void ensureParentDirectory(Path path) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
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
    StringBuilder builder =
        new StringBuilder("event=").append(event).append(" status=").append(status).append(' ');
    if (reasonCode != null && !reasonCode.isBlank()) {
      builder.append("reasonCode=").append(reasonCode).append(' ');
    }
    builder.append("sanitized=").append(sanitized);
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      String value = entry.getValue();
      if (!hasText(value)) {
        continue;
      }
      builder.append(' ').append(entry.getKey()).append('=').append(value.trim());
    }
    writer.println(builder);
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

  abstract static class AbstractOcraCommand implements Callable<Integer> {

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

    protected MapDbCredentialStore openStore() throws IOException {
      Path database = databasePath();
      if (database == null) {
        throw new CommandLine.ExecutionException(
            parent.spec.commandLine(), "--database=<path> is required");
      }
      ensureParentDirectory(database);
      return MapDbCredentialStore.file(database).open();
    }

    protected Optional<OcraCredentialDescriptor> resolveDescriptor(
        MapDbCredentialStore store, String id) {
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
      try (MapDbCredentialStore store = openStore()) {
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
      try (MapDbCredentialStore store = openStore()) {
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
      try (MapDbCredentialStore store = openStore()) {
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
      try {
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

        OcraCredentialDescriptor descriptor;
        boolean credentialReference = hasCredential;

        if (hasCredential) {
          try (MapDbCredentialStore store = openStore()) {
            descriptor =
                resolveDescriptor(store, credentialId.trim())
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "credentialId " + credentialId + " not found"));
          }
        } else {
          if (!hasText(suite)) {
            return failValidation(event, "suite_missing", "suite is required for inline mode");
          }
          descriptor =
              createDescriptor(
                  "cli-inline-" + Integer.toHexString(Objects.hash(suite)),
                  suite.trim(),
                  sharedSecretHex.replace(" ", "").trim(),
                  counter,
                  hasText(pinHashHex) ? pinHashHex.trim() : null,
                  null,
                  Map.of("source", "cli-inline"));
        }

        // Validate auxiliary inputs before execution.
        credentialFactory().validateChallenge(descriptor, challenge);
        credentialFactory().validateSessionInformation(descriptor, session);

        OcraExecutionContext context =
            new OcraExecutionContext(
                counter,
                challenge,
                session,
                clientChallenge,
                serverChallenge,
                pinHashHex,
                timestamp);

        String otp = OcraResponseCalculator.generate(descriptor, context);

        Map<String, String> fields = new LinkedHashMap<>();
        if (credentialReference) {
          fields.put("credentialId", credentialId.trim());
        } else {
          fields.put("mode", "inline");
        }
        fields.put("suite", descriptor.suite().value());
        fields.put("otp", otp);
        emitSuccess(event, "success", fields);
        return CommandLine.ExitCode.OK;
      } catch (IllegalArgumentException ex) {
        return failValidation(event, "validation_error", ex.getMessage());
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

      String normalizedOtp = otp.trim();
      OcraVerificationContext context =
          new OcraVerificationContext(
              counter,
              normalize(challenge),
              normalize(session),
              normalize(clientChallenge),
              normalize(serverChallenge),
              normalize(pinHashHex),
              normalize(timestamp));

      try {
        if (hasCredential) {
          String descriptorId = credentialId.trim();
          try (MapDbCredentialStore store = openStore()) {
            Optional<OcraCredentialDescriptor> descriptorOptional =
                resolveDescriptor(store, descriptorId);
            if (descriptorOptional.isEmpty()) {
              return failValidation(
                  event, "credential_not_found", "credentialId " + descriptorId + " not found");
            }

            OcraCredentialDescriptor descriptor = descriptorOptional.get();
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("credentialSource", "stored");
            fields.put("credentialId", descriptor.name());
            fields.put("suite", descriptor.suite().value());

            Integer shortCircuit = ensureChallengeProvided(event, descriptor, context);
            if (shortCircuit != null) {
              return shortCircuit;
            }

            OcraReplayVerifier verifier = new OcraReplayVerifier(store);
            OcraVerificationResult result =
                verifier.verifyStored(
                    new OcraStoredVerificationRequest(descriptor.name(), normalizedOtp, context));
            return handleResult(event, result, fields);
          }
        }

        // Inline verification path
        if (!hasText(suite)) {
          return failValidation(event, "suite_missing", "suite is required for inline mode");
        }

        String sanitizedSecret = sharedSecretHex.replace(" ", "").trim();
        Map<String, String> metadata = Map.of("source", "cli-inline");
        OcraCredentialDescriptor descriptor =
            createDescriptor(
                "cli-inline-" + Integer.toHexString(Objects.hash(suite.trim(), sanitizedSecret)),
                suite.trim(),
                sanitizedSecret,
                counter,
                normalize(pinHashHex),
                null,
                metadata);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("credentialSource", "inline");
        fields.put("suite", descriptor.suite().value());
        fields.put("descriptor", descriptor.name());

        Integer shortCircuit = ensureChallengeProvided(event, descriptor, context);
        if (shortCircuit != null) {
          return shortCircuit;
        }

        OcraReplayVerifier verifier = new OcraReplayVerifier(null);
        OcraInlineVerificationRequest request =
            new OcraInlineVerificationRequest(
                descriptor.name(),
                descriptor.suite().value(),
                sanitizedSecret,
                SecretEncoding.HEX,
                normalizedOtp,
                context,
                descriptor.metadata());

        OcraVerificationResult result = verifier.verifyInline(request);
        return handleResult(event, result, fields);

      } catch (IllegalArgumentException ex) {
        return failValidation(event, "validation_error", ex.getMessage());
      } catch (Exception ex) {
        return failUnexpected(event, ex.getMessage());
      }
    }

    private Integer ensureChallengeProvided(
        String event, OcraCredentialDescriptor descriptor, OcraVerificationContext context) {
      boolean requiresChallenge = descriptor.suite().dataInput().challengeQuestion().isPresent();
      if (!requiresChallenge) {
        return null;
      }

      if (hasText(context.challenge())
          || hasText(context.clientChallenge())
          || hasText(context.serverChallenge())) {
        return null;
      }

      return failValidation(
          event,
          "validation_error",
          "challengeQuestion required for suite: " + descriptor.suite().value());
    }

    private int handleResult(
        String event, OcraVerificationResult result, Map<String, String> fields) {
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

    int handleInvalid(String event, OcraVerificationReason reason, Map<String, String> fields) {
      return switch (reason) {
        case VALIDATION_FAILURE ->
            failValidation(event, "validation_error", "Verification inputs failed validation");
        case CREDENTIAL_NOT_FOUND ->
            failValidation(event, "credential_not_found", "credentialId not found");
        case UNEXPECTED_ERROR -> failUnexpected(event, "Unexpected error during verification");
        case MATCH, STRICT_MISMATCH -> failUnexpected(event, "Unexpected verification state");
      };
    }

    static String reasonCodeFor(OcraVerificationReason reason) {
      return switch (reason) {
        case MATCH -> "match";
        case STRICT_MISMATCH -> "strict_mismatch";
        case VALIDATION_FAILURE -> "validation_error";
        case CREDENTIAL_NOT_FOUND -> "credential_not_found";
        case UNEXPECTED_ERROR -> "unexpected_error";
      };
    }

    static String normalize(String value) {
      if (value == null) {
        return null;
      }
      String trimmed = value.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }
  }
}
