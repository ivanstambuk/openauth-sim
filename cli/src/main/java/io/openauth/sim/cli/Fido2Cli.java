package io.openauth.sim.cli;

import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationCommand;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.EvaluationResult;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService.ReplayResult;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import picocli.CommandLine;

/** CLI facade for WebAuthn assertion verification and replay diagnostics. */
@CommandLine.Command(
    name = "fido2",
    mixinStandardHelpOptions = true,
    description = "Evaluate stored and inline WebAuthn assertions and replay diagnostics.",
    subcommands = {
      Fido2Cli.EvaluateStoredCommand.class,
      Fido2Cli.EvaluateInlineCommand.class,
      Fido2Cli.ReplayStoredCommand.class
    })
public final class Fido2Cli implements java.util.concurrent.Callable<Integer> {

  private static final String EVENT_PREFIX = "cli.fido2.";
  private static final String DEFAULT_DATABASE_FILE = "fido2-credentials.db";
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private static final Fido2TelemetryAdapter EVALUATION_TELEMETRY =
      new Fido2TelemetryAdapter("fido2.evaluate");
  private static final Fido2TelemetryAdapter REPLAY_TELEMETRY =
      new Fido2TelemetryAdapter("fido2.replay");

  private final WebAuthnAssertionVerifier verifier = new WebAuthnAssertionVerifier();
  private final WebAuthnCredentialPersistenceAdapter persistenceAdapter =
      new WebAuthnCredentialPersistenceAdapter();

  @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(
      names = {"-d", "--database"},
      paramLabel = "<path>",
      scope = CommandLine.ScopeType.INHERIT,
      description = "Path to the credential store database (default: data/fido2-credentials.db)")
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

  private static String nextTelemetryId() {
    return "cli-fido2-" + UUID.randomUUID();
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

  private CredentialStore openStore() throws Exception {
    return CredentialStoreFactory.openFileStore(databasePath());
  }

  private WebAuthnEvaluationApplicationService createEvaluationService(CredentialStore store) {
    return new WebAuthnEvaluationApplicationService(store, verifier, persistenceAdapter);
  }

  private int failValidation(
      String event, String reasonCode, String message, Map<String, Object> fields) {
    TelemetryFrame frame =
        EVALUATION_TELEMETRY.invalid(
            nextTelemetryId(), reasonCode, sanitizeMessage(message), fields);
    writeFrame(err(), event, frame);
    return CommandLine.ExitCode.USAGE;
  }

  private int failValidation(String event, TelemetrySignal signal, Map<String, Object> fields) {
    TelemetryFrame emitted = signal.emit(EVALUATION_TELEMETRY, nextTelemetryId());
    Map<String, Object> merged = new LinkedHashMap<>(emitted.fields());
    merged.putAll(fields);
    TelemetryFrame frame =
        new TelemetryFrame(emitted.event(), emitted.status(), emitted.sanitized(), merged);
    writeFrame(err(), event, frame);
    return CommandLine.ExitCode.USAGE;
  }

  private int failUnexpected(String event, Map<String, Object> fields, String message) {
    TelemetryFrame frame =
        EVALUATION_TELEMETRY.status(
            "error",
            nextTelemetryId(),
            "unexpected_error",
            false,
            sanitizeMessage(message),
            fields);
    writeFrame(err(), event, frame);
    return CommandLine.ExitCode.SOFTWARE;
  }

  private static byte[] decodeBase64Url(String label, String value) {
    try {
      return URL_DECODER.decode(Objects.requireNonNull(value, label + " required"));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(label + " must be Base64URL encoded", ex);
    }
  }

  private TelemetryFrame augmentEvaluationFrame(
      TelemetryFrame frame, EvaluationResult result, boolean credentialReference) {
    Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
    merged.put("credentialReference", credentialReference);
    merged.put("valid", result.valid());
    if (result.credentialId() != null) {
      merged.put("credentialId", result.credentialId());
    }
    if (result.relyingPartyId() != null) {
      merged.put("relyingPartyId", result.relyingPartyId());
    }
    if (result.algorithm() != null) {
      merged.put("algorithm", result.algorithm().label());
    }
    merged.put("userVerificationRequired", result.userVerificationRequired());
    result.error().ifPresent(error -> merged.put("error", error.name().toLowerCase(Locale.US)));
    return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
  }

  private TelemetryFrame augmentReplayFrame(ReplayResult result, TelemetryFrame frame) {
    Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
    merged.put("match", result.match());
    merged.put("credentialReference", result.credentialReference());
    if (result.credentialId() != null) {
      merged.put("credentialId", result.credentialId());
    }
    merged.put("credentialSource", result.credentialSource());
    merged.putAll(result.supplementalFields());
    result.error().ifPresent(error -> merged.put("error", error.name().toLowerCase(Locale.US)));
    return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
  }

  @CommandLine.Command(name = "evaluate", description = "Evaluate a stored WebAuthn credential.")
  static final class EvaluateStoredCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--credential-id",
        required = true,
        paramLabel = "<id>",
        description = "Stored credential identifier")
    String credentialId;

    @CommandLine.Option(
        names = "--relying-party-id",
        required = true,
        paramLabel = "<rpId>",
        description = "Expected relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        required = true,
        paramLabel = "<origin>",
        description = "Expected client data origin")
    String origin;

    @CommandLine.Option(
        names = "--type",
        required = true,
        paramLabel = "<type>",
        description = "Expected client data type (e.g., webauthn.get)")
    String expectedType;

    @CommandLine.Option(
        names = "--expected-challenge",
        required = true,
        paramLabel = "<base64url>",
        description = "Expected challenge in Base64URL form")
    String expectedChallenge;

    @CommandLine.Option(
        names = "--client-data",
        required = true,
        paramLabel = "<base64url>",
        description = "ClientDataJSON payload in Base64URL form")
    String clientData;

    @CommandLine.Option(
        names = "--authenticator-data",
        required = true,
        paramLabel = "<base64url>",
        description = "Authenticator data in Base64URL form")
    String authenticatorData;

    @CommandLine.Option(
        names = "--signature",
        required = true,
        paramLabel = "<base64url>",
        description = "Authenticator signature in Base64URL form")
    String signature;

    @Override
    public Integer call() {
      byte[] challengeBytes;
      byte[] clientDataBytes;
      byte[] authenticatorDataBytes;
      byte[] signatureBytes;
      try {
        challengeBytes = decodeBase64Url("expected-challenge", expectedChallenge);
        clientDataBytes = decodeBase64Url("client-data", clientData);
        authenticatorDataBytes = decodeBase64Url("authenticator-data", authenticatorData);
        signatureBytes = decodeBase64Url("signature", signature);
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields =
            Map.of("credentialReference", true, "credentialId", credentialId);
        return parent.failValidation(event("evaluate"), "invalid_payload", ex.getMessage(), fields);
      }

      try (CredentialStore store = parent.openStore()) {
        WebAuthnEvaluationApplicationService service = parent.createEvaluationService(store);
        EvaluationResult result =
            service.evaluate(
                new EvaluationCommand.Stored(
                    credentialId,
                    relyingPartyId,
                    origin,
                    expectedType,
                    challengeBytes,
                    clientDataBytes,
                    authenticatorDataBytes,
                    signatureBytes));
        return handleResult(result);
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields =
            Map.of("credentialReference", true, "credentialId", credentialId);
        return parent.failValidation(
            event("evaluate"), "validation_error", ex.getMessage(), fields);
      } catch (Exception ex) {
        Map<String, Object> fields =
            Map.of("credentialReference", true, "credentialId", credentialId);
        return parent.failUnexpected(
            event("evaluate"), fields, "Evaluation failed: " + sanitizeMessage(ex.getMessage()));
      }
    }

    private Integer handleResult(EvaluationResult result) {
      TelemetrySignal signal = result.telemetry();
      Map<String, Object> baseFields =
          Map.of("credentialReference", true, "credentialId", result.credentialId());

      return switch (signal.status()) {
        case SUCCESS -> {
          TelemetryFrame frame =
              parent.augmentEvaluationFrame(
                  result.evaluationFrame(EVALUATION_TELEMETRY, nextTelemetryId()), result, true);
          writeFrame(parent.out(), event("evaluate"), frame);
          yield CommandLine.ExitCode.OK;
        }
        case INVALID -> parent.failValidation(event("evaluate"), signal, baseFields);
        case ERROR ->
            parent.failUnexpected(
                event("evaluate"),
                baseFields,
                Optional.ofNullable(signal.reason()).orElse("WebAuthn evaluation failed"));
      };
    }
  }

  @CommandLine.Command(
      name = "evaluate-inline",
      description = "Evaluate an inline WebAuthn assertion without referencing stored credentials.")
  static final class EvaluateInlineCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--credential-name",
        paramLabel = "<name>",
        defaultValue = "cli-fido2-inline",
        description = "Display name for the inline credential (used for telemetry only)")
    String credentialName;

    @CommandLine.Option(
        names = "--relying-party-id",
        required = true,
        paramLabel = "<rpId>",
        description = "Expected relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        required = true,
        paramLabel = "<origin>",
        description = "Expected client data origin")
    String origin;

    @CommandLine.Option(
        names = "--type",
        required = true,
        paramLabel = "<type>",
        description = "Expected client data type (e.g., webauthn.get)")
    String expectedType;

    @CommandLine.Option(
        names = "--credential-id",
        required = true,
        paramLabel = "<base64url>",
        description = "Credential ID in Base64URL form")
    String credentialId;

    @CommandLine.Option(
        names = "--public-key",
        required = true,
        paramLabel = "<base64url>",
        description = "COSE public key in Base64URL form")
    String publicKey;

    @CommandLine.Option(
        names = "--signature-counter",
        required = true,
        paramLabel = "<counter>",
        description = "Stored signature counter value")
    long signatureCounter;

    @CommandLine.Option(
        names = "--user-verification-required",
        paramLabel = "<bool>",
        arity = "0..1",
        fallbackValue = "true",
        defaultValue = "false",
        description = "Whether the credential requires user verification (true/false)")
    boolean userVerificationRequired;

    @CommandLine.Option(
        names = "--algorithm",
        required = true,
        paramLabel = "<name>",
        description = "Signature algorithm label (e.g., ES256, RS256)")
    String algorithm;

    @CommandLine.Option(
        names = "--expected-challenge",
        required = true,
        paramLabel = "<base64url>",
        description = "Expected challenge in Base64URL form")
    String expectedChallenge;

    @CommandLine.Option(
        names = "--client-data",
        required = true,
        paramLabel = "<base64url>",
        description = "ClientDataJSON payload in Base64URL form")
    String clientData;

    @CommandLine.Option(
        names = "--authenticator-data",
        required = true,
        paramLabel = "<base64url>",
        description = "Authenticator data in Base64URL form")
    String authenticatorData;

    @CommandLine.Option(
        names = "--signature",
        required = true,
        paramLabel = "<base64url>",
        description = "Authenticator signature in Base64URL form")
    String signature;

    @Override
    public Integer call() {
      byte[] credentialIdBytes;
      byte[] publicKeyBytes;
      byte[] challengeBytes;
      byte[] clientDataBytes;
      byte[] authenticatorDataBytes;
      byte[] signatureBytes;
      WebAuthnSignatureAlgorithm parsedAlgorithm;

      try {
        credentialIdBytes = decodeBase64Url("credential-id", credentialId);
        publicKeyBytes = decodeBase64Url("public-key", publicKey);
        challengeBytes = decodeBase64Url("expected-challenge", expectedChallenge);
        clientDataBytes = decodeBase64Url("client-data", clientData);
        authenticatorDataBytes = decodeBase64Url("authenticator-data", authenticatorData);
        signatureBytes = decodeBase64Url("signature", signature);
        parsedAlgorithm = WebAuthnSignatureAlgorithm.fromLabel(algorithm);
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields = Map.of("credentialReference", false);
        return parent.failValidation(event("evaluate"), "invalid_payload", ex.getMessage(), fields);
      }

      try (CredentialStore store = parent.openStore()) {
        WebAuthnEvaluationApplicationService service = parent.createEvaluationService(store);
        EvaluationResult result =
            service.evaluate(
                new EvaluationCommand.Inline(
                    credentialName,
                    relyingPartyId,
                    origin,
                    expectedType,
                    credentialIdBytes,
                    publicKeyBytes,
                    signatureCounter,
                    userVerificationRequired,
                    parsedAlgorithm,
                    challengeBytes,
                    clientDataBytes,
                    authenticatorDataBytes,
                    signatureBytes));
        return handleResult(result);
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields = Map.of("credentialReference", false);
        return parent.failValidation(
            event("evaluate"), "validation_error", ex.getMessage(), fields);
      } catch (Exception ex) {
        Map<String, Object> fields = Map.of("credentialReference", false);
        return parent.failUnexpected(
            event("evaluate"), fields, "Evaluation failed: " + sanitizeMessage(ex.getMessage()));
      }
    }

    private Integer handleResult(EvaluationResult result) {
      TelemetrySignal signal = result.telemetry();
      Map<String, Object> baseFields = new LinkedHashMap<>(signal.fields());
      baseFields.put("credentialReference", false);

      return switch (signal.status()) {
        case SUCCESS -> {
          TelemetryFrame frame =
              parent.augmentEvaluationFrame(
                  result.evaluationFrame(EVALUATION_TELEMETRY, nextTelemetryId()), result, false);
          writeFrame(parent.out(), event("evaluate"), frame);
          yield CommandLine.ExitCode.OK;
        }
        case INVALID -> parent.failValidation(event("evaluate"), signal, baseFields);
        case ERROR ->
            parent.failUnexpected(
                event("evaluate"),
                Map.of("credentialReference", false),
                Optional.ofNullable(signal.reason()).orElse("WebAuthn evaluation failed"));
      };
    }
  }

  @CommandLine.Command(
      name = "replay",
      description = "Replay a stored WebAuthn assertion without mutating credential state.")
  static final class ReplayStoredCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--credential-id",
        required = true,
        paramLabel = "<id>",
        description = "Stored credential identifier")
    String credentialId;

    @CommandLine.Option(
        names = "--relying-party-id",
        required = true,
        paramLabel = "<rpId>",
        description = "Expected relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        required = true,
        paramLabel = "<origin>",
        description = "Expected client data origin")
    String origin;

    @CommandLine.Option(
        names = "--type",
        required = true,
        paramLabel = "<type>",
        description = "Expected client data type (e.g., webauthn.get)")
    String expectedType;

    @CommandLine.Option(
        names = "--expected-challenge",
        required = true,
        paramLabel = "<base64url>",
        description = "Expected challenge in Base64URL form")
    String expectedChallenge;

    @CommandLine.Option(
        names = "--client-data",
        required = true,
        paramLabel = "<base64url>",
        description = "ClientDataJSON payload in Base64URL form")
    String clientData;

    @CommandLine.Option(
        names = "--authenticator-data",
        required = true,
        paramLabel = "<base64url>",
        description = "Authenticator data in Base64URL form")
    String authenticatorData;

    @CommandLine.Option(
        names = "--signature",
        required = true,
        paramLabel = "<base64url>",
        description = "Authenticator signature in Base64URL form")
    String signature;

    @Override
    public Integer call() {
      byte[] challengeBytes;
      byte[] clientDataBytes;
      byte[] authenticatorDataBytes;
      byte[] signatureBytes;
      try {
        challengeBytes = decodeBase64Url("expected-challenge", expectedChallenge);
        clientDataBytes = decodeBase64Url("client-data", clientData);
        authenticatorDataBytes = decodeBase64Url("authenticator-data", authenticatorData);
        signatureBytes = decodeBase64Url("signature", signature);
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields =
            Map.of("credentialReference", true, "credentialId", credentialId);
        return parent.failValidation(event("replay"), "invalid_payload", ex.getMessage(), fields);
      }

      try (CredentialStore store = parent.openStore()) {
        WebAuthnEvaluationApplicationService evaluationService =
            parent.createEvaluationService(store);
        WebAuthnReplayApplicationService replayService =
            new WebAuthnReplayApplicationService(evaluationService);

        ReplayResult result =
            replayService.replay(
                new ReplayCommand.Stored(
                    credentialId,
                    relyingPartyId,
                    origin,
                    expectedType,
                    challengeBytes,
                    clientDataBytes,
                    authenticatorDataBytes,
                    signatureBytes));
        return handleResult(result);
      } catch (IllegalArgumentException ex) {
        Map<String, Object> fields =
            Map.of("credentialReference", true, "credentialId", credentialId);
        return parent.failValidation(event("replay"), "validation_error", ex.getMessage(), fields);
      } catch (Exception ex) {
        Map<String, Object> fields =
            Map.of("credentialReference", true, "credentialId", credentialId);
        return parent.failUnexpected(
            event("replay"), fields, "Replay failed: " + sanitizeMessage(ex.getMessage()));
      }
    }

    private Integer handleResult(ReplayResult result) {
      TelemetrySignal signal = result.telemetry();
      Map<String, Object> baseFields =
          Map.of(
              "credentialReference",
              result.credentialReference(),
              "credentialId",
              result.credentialId());

      return switch (signal.status()) {
        case SUCCESS -> {
          TelemetryFrame frame =
              parent.augmentReplayFrame(
                  result, result.replayFrame(REPLAY_TELEMETRY, nextTelemetryId()));
          writeFrame(parent.out(), event("replay"), frame);
          yield CommandLine.ExitCode.OK;
        }
        case INVALID -> parent.failValidation(event("replay"), signal, baseFields);
        case ERROR ->
            parent.failUnexpected(
                event("replay"),
                baseFields,
                Optional.ofNullable(signal.reason()).orElse("WebAuthn replay failed"));
      };
    }
  }

  private abstract static class AbstractFido2Command
      implements java.util.concurrent.Callable<Integer> {

    @CommandLine.ParentCommand Fido2Cli parent;

    protected PrintWriter out() {
      return parent.out();
    }

    protected PrintWriter err() {
      return parent.err();
    }

    protected Path databasePath() {
      return parent.databasePath();
    }
  }
}
