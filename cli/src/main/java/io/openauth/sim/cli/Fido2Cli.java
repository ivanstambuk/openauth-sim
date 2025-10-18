package io.openauth.sim.cli;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnEvaluationApplicationService.TelemetrySignal;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService.ReplayCommand;
import io.openauth.sim.application.fido2.WebAuthnReplayApplicationService.ReplayResult;
import io.openauth.sim.application.fido2.WebAuthnTrustAnchorResolver;
import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import picocli.CommandLine;

/** CLI facade for WebAuthn assertion verification and replay diagnostics. */
@CommandLine.Command(
    name = "fido2",
    mixinStandardHelpOptions = true,
    description =
        "Evaluate stored/inline WebAuthn assertions, verify attestation payloads, and replay diagnostics.",
    subcommands = {
      Fido2Cli.EvaluateStoredCommand.class,
      Fido2Cli.EvaluateInlineCommand.class,
      Fido2Cli.ReplayStoredCommand.class,
      Fido2Cli.AttestCommand.class,
      Fido2Cli.AttestReplayCommand.class,
      Fido2Cli.VectorsCommand.class
    })
public final class Fido2Cli implements java.util.concurrent.Callable<Integer> {

  private static final String EVENT_PREFIX = "cli.fido2.";
  private static final String DEFAULT_DATABASE_FILE = "fido2-credentials.db";
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
  private static final Map<String, WebAuthnJsonVector> JSON_VECTOR_INDEX = loadVectorIndex();
  private static final List<WebAuthnJsonVector> JSON_VECTOR_LIST =
      JSON_VECTOR_INDEX.values().stream()
          .sorted(Comparator.comparing(WebAuthnJsonVector::vectorId, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toUnmodifiableList());
  private static final List<WebAuthnAttestationVector> ATTESTATION_VECTOR_LIST =
      WebAuthnAttestationSamples.vectors();
  private static final Map<String, Sample> GENERATOR_SAMPLE_INDEX = loadGeneratorSampleIndex();

  private static final Fido2TelemetryAdapter EVALUATION_TELEMETRY =
      new Fido2TelemetryAdapter("fido2.evaluate");
  private static final Fido2TelemetryAdapter REPLAY_TELEMETRY =
      new Fido2TelemetryAdapter("fido2.replay");
  private static final Fido2TelemetryAdapter ATTEST_TELEMETRY =
      new Fido2TelemetryAdapter("fido2.attest");
  private static final Fido2TelemetryAdapter ATTEST_REPLAY_TELEMETRY =
      new Fido2TelemetryAdapter("fido2.attestReplay");

  private final WebAuthnAssertionVerifier verifier = new WebAuthnAssertionVerifier();
  private final WebAuthnAttestationVerifier attestationVerifier = new WebAuthnAttestationVerifier();
  private final WebAuthnCredentialPersistenceAdapter persistenceAdapter =
      new WebAuthnCredentialPersistenceAdapter();
  private final WebAuthnTrustAnchorResolver trustAnchorResolver = new WebAuthnTrustAnchorResolver();

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

  private static Map<String, WebAuthnJsonVector> loadVectorIndex() {
    Map<String, WebAuthnJsonVector> loaded =
        WebAuthnJsonVectorFixtures.loadAll()
            .collect(
                Collectors.toMap(
                    WebAuthnJsonVector::vectorId,
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    if (loaded.isEmpty()) {
      WebAuthnFixtures.WebAuthnFixture fallback = WebAuthnFixtures.loadPackedEs256();
      WebAuthnJsonVector vector =
          new WebAuthnJsonVector(
              "packed-es256",
              WebAuthnSignatureAlgorithm.ES256,
              fallback.storedCredential(),
              fallback.request(),
              null);
      loaded = new LinkedHashMap<>();
      loaded.put(vector.vectorId(), vector);
    }
    return Map.copyOf(loaded);
  }

  private static Map<String, Sample> loadGeneratorSampleIndex() {
    return WebAuthnGeneratorSamples.samples().stream()
        .collect(
            Collectors.toMap(
                Sample::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
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

  private Optional<WebAuthnJsonVector> findVector(String vectorId) {
    if (!hasText(vectorId)) {
      return Optional.empty();
    }
    return Optional.ofNullable(JSON_VECTOR_INDEX.get(vectorId));
  }

  private Optional<Sample> findPreset(String presetId) {
    if (!hasText(presetId)) {
      return Optional.empty();
    }
    return Optional.ofNullable(GENERATOR_SAMPLE_INDEX.get(presetId));
  }

  private void printVectorSummaries(PrintWriter writer) {
    if (JSON_VECTOR_LIST.isEmpty() && ATTESTATION_VECTOR_LIST.isEmpty()) {
      writer.println("No WebAuthn fixture vectors available.");
      return;
    }
    if (!JSON_VECTOR_LIST.isEmpty()) {
      writer.println("vectorId\talgorithm\tuvRequired\trelyingPartyId\torigin");
      JSON_VECTOR_LIST.forEach(
          vector ->
              writer.printf(
                  "%s\t%s\t%s\t%s\t%s%n",
                  vector.vectorId(),
                  vector.algorithm().label(),
                  vector.storedCredential().userVerificationRequired(),
                  vector.storedCredential().relyingPartyId(),
                  vector.assertionRequest().origin()));
    }
    if (!ATTESTATION_VECTOR_LIST.isEmpty()) {
      if (!JSON_VECTOR_LIST.isEmpty()) {
        writer.println();
      }
      writer.println("attestationId\tformat\talgorithm\trelyingPartyId\torigin\tsection");
      ATTESTATION_VECTOR_LIST.forEach(
          vector ->
              writer.printf(
                  "%s\t%s\t%s\t%s\t%s\t%s%n",
                  vector.vectorId(),
                  vector.format().label(),
                  vector.algorithm().label(),
                  vector.relyingPartyId(),
                  vector.origin(),
                  vector.w3cSection()));
    }
  }

  private void applyStoredPresetDefaults(EvaluateStoredCommand command, Sample sample) {
    if (!hasText(command.credentialId)) {
      command.credentialId = sample.key();
    }
    if (!hasText(command.relyingPartyId)) {
      command.relyingPartyId = sample.relyingPartyId();
    }
    if (!hasText(command.origin)) {
      command.origin = sample.origin();
    }
    if (!hasText(command.expectedType)) {
      command.expectedType = sample.expectedType();
    }
    if (!hasText(command.challenge)) {
      command.challenge = sample.challengeBase64Url();
    }
    if (!hasText(command.privateKey)) {
      command.privateKey = sample.privateKeyJwk();
    }
    if (command.signatureCounter == null) {
      command.signatureCounter = sample.signatureCounter();
    }
    if (command.userVerificationRequired == null) {
      command.userVerificationRequired = sample.userVerificationRequired();
    }
  }

  private void applyInlinePresetDefaults(EvaluateInlineCommand command, Sample sample) {
    if (!hasText(command.credentialName) || "cli-fido2-inline".equals(command.credentialName)) {
      command.credentialName = sample.label();
    }
    if (!hasText(command.relyingPartyId)) {
      command.relyingPartyId = sample.relyingPartyId();
    }
    if (!hasText(command.origin)) {
      command.origin = sample.origin();
    }
    if (!hasText(command.expectedType)) {
      command.expectedType = sample.expectedType();
    }
    if (!hasText(command.credentialId)) {
      command.credentialId = sample.credentialIdBase64Url();
    }
    if (command.signatureCounter == null) {
      command.signatureCounter = sample.signatureCounter();
    }
    if (command.userVerificationRequired == null) {
      command.userVerificationRequired = sample.userVerificationRequired();
    }
    if (!hasText(command.algorithm)) {
      command.algorithm = sample.algorithm().label();
    }
    if (!hasText(command.challenge)) {
      command.challenge = sample.challengeBase64Url();
    }
    if (!hasText(command.privateKey)) {
      command.privateKey = sample.privateKeyJwk();
    }
  }

  private void applyReplayVectorDefaults(ReplayStoredCommand command, WebAuthnJsonVector vector) {
    if (!hasText(command.credentialId)) {
      command.credentialId = vector.vectorId();
    }
    if (!hasText(command.relyingPartyId)) {
      command.relyingPartyId = vector.storedCredential().relyingPartyId();
    }
    if (!hasText(command.origin)) {
      command.origin = vector.assertionRequest().origin();
    }
    if (!hasText(command.expectedType)) {
      command.expectedType = vector.assertionRequest().expectedType();
    }
    if (!hasText(command.expectedChallenge)) {
      command.expectedChallenge = encodeBase64(vector.assertionRequest().expectedChallenge());
    }
    if (!hasText(command.clientData)) {
      command.clientData = encodeBase64(vector.assertionRequest().clientDataJson());
    }
    if (!hasText(command.authenticatorData)) {
      command.authenticatorData = encodeBase64(vector.assertionRequest().authenticatorData());
    }
    if (!hasText(command.signature)) {
      command.signature = encodeBase64(vector.assertionRequest().signature());
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String encodeBase64(byte[] value) {
    return URL_ENCODER.encodeToString(value);
  }

  private static String extractPrivateKey(String inlineValue, Path fileValue) throws IOException {
    boolean hasInline = hasText(inlineValue);
    boolean hasFile = fileValue != null;
    if (hasInline && hasFile) {
      throw new IllegalArgumentException(
          "Provide either --private-key or --private-key-file, not both.");
    }
    if (hasFile) {
      String content = Files.readString(fileValue);
      if (!hasText(content)) {
        throw new IllegalArgumentException("Private key file is empty: " + fileValue);
      }
      return content.trim();
    }
    if (hasInline) {
      return inlineValue.trim();
    }
    return null;
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private static String formatPublicKeyCredentialJson(GenerationResult result) {
    StringBuilder builder = new StringBuilder();
    builder.append("{\"id\":\"").append(encodeBase64(result.credentialId())).append("\",");
    builder.append("\"rawId\":\"").append(encodeBase64(result.credentialId())).append("\",");
    builder.append("\"type\":\"public-key\",");
    builder.append("\"response\":{");
    builder
        .append("\"clientDataJSON\":\"")
        .append(encodeBase64(result.clientDataJson()))
        .append("\",");
    builder
        .append("\"authenticatorData\":\"")
        .append(encodeBase64(result.authenticatorData()))
        .append("\",");
    builder.append("\"signature\":\"").append(encodeBase64(result.signature())).append("\"},");
    builder
        .append("\"relyingPartyId\":\"")
        .append(escapeJson(result.relyingPartyId()))
        .append("\",");
    builder.append("\"origin\":\"").append(escapeJson(result.origin())).append("\",");
    builder.append("\"algorithm\":\"").append(result.algorithm().label()).append("\",");
    builder
        .append("\"userVerificationRequired\":")
        .append(result.userVerificationRequired())
        .append(',');
    builder.append("\"signatureCounter\":").append(result.signatureCounter()).append('}');
    return builder.toString();
  }

  private static Map<String, Object> generatorTelemetryFields(
      GenerationResult result, String credentialSource) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("credentialSource", credentialSource);
    fields.put("credentialReference", result.credentialReference());
    fields.put("credentialId", encodeBase64(result.credentialId()));
    fields.put("relyingPartyId", result.relyingPartyId());
    fields.put("origin", result.origin());
    fields.put("algorithm", result.algorithm().label());
    fields.put("userVerificationRequired", result.userVerificationRequired());
    fields.put("signatureCounter", result.signatureCounter());
    return fields;
  }

  private CredentialStore openStore() throws Exception {
    return CredentialStoreFactory.openFileStore(databasePath());
  }

  private WebAuthnEvaluationApplicationService createEvaluationService(CredentialStore store) {
    return new WebAuthnEvaluationApplicationService(store, verifier, persistenceAdapter);
  }

  private WebAuthnAssertionGenerationApplicationService createGeneratorService(
      CredentialStore store) {
    return new WebAuthnAssertionGenerationApplicationService(store, persistenceAdapter);
  }

  private WebAuthnAssertionGenerationApplicationService createInlineGeneratorService() {
    return new WebAuthnAssertionGenerationApplicationService();
  }

  private WebAuthnAttestationGenerationApplicationService createAttestationGenerationService() {
    return new WebAuthnAttestationGenerationApplicationService();
  }

  private WebAuthnAttestationReplayApplicationService createAttestationReplayService() {
    return new WebAuthnAttestationReplayApplicationService(
        attestationVerifier, ATTEST_REPLAY_TELEMETRY);
  }

  private static TelemetryFrame mergeTelemetryFrame(
      TelemetryFrame frame, Map<String, Object> additionalFields) {
    if (additionalFields == null || additionalFields.isEmpty()) {
      return frame;
    }
    Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
    merged.putAll(additionalFields);
    return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
  }

  private WebAuthnTrustAnchorResolver.Resolution resolveTrustAnchors(
      String attestationId, WebAuthnAttestationFormat format, List<Path> files) {
    if (files == null || files.isEmpty()) {
      return trustAnchorResolver.resolveFiles(attestationId, format, List.of());
    }
    return trustAnchorResolver.resolveFiles(attestationId, format, files);
  }

  private void emitTrustAnchorWarnings(List<String> warnings) {
    if (warnings == null || warnings.isEmpty()) {
      return;
    }
    PrintWriter writer = err();
    warnings.stream()
        .filter(message -> message != null && !message.isBlank())
        .map(Fido2Cli::sanitizeMessage)
        .forEach(message -> writer.println("warning=trust_anchor " + message));
  }

  private int emitGenerationSuccess(GenerationResult result, String credentialSource) {
    out().println(formatPublicKeyCredentialJson(result));
    Map<String, Object> fields = generatorTelemetryFields(result, credentialSource);
    TelemetryFrame frame =
        EVALUATION_TELEMETRY.status("success", nextTelemetryId(), "generated", true, null, fields);
    writeFrame(out(), event("evaluate"), frame);
    return CommandLine.ExitCode.OK;
  }

  private int failValidation(
      String event, String reasonCode, String message, Map<String, Object> fields) {
    TelemetryFrame frame =
        EVALUATION_TELEMETRY.invalid(
            nextTelemetryId(), reasonCode, sanitizeMessage(message), fields);
    writeFrame(err(), event, frame);
    return CommandLine.ExitCode.USAGE;
  }

  private int failValidation(
      String event,
      Fido2TelemetryAdapter adapter,
      String reasonCode,
      String message,
      Map<String, Object> fields) {
    TelemetryFrame frame =
        adapter.invalid(nextTelemetryId(), reasonCode, sanitizeMessage(message), fields);
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

  private int failUnexpected(
      String event, Fido2TelemetryAdapter adapter, Map<String, Object> fields, String message) {
    TelemetryFrame frame =
        adapter.status(
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

  @CommandLine.Command(
      name = "evaluate",
      description = "Generate a WebAuthn assertion using a stored credential.")
  static final class EvaluateStoredCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--preset-id",
        paramLabel = "<preset>",
        description = "Load default values from a generator preset")
    String presetId;

    @CommandLine.Option(
        names = "--credential-id",
        defaultValue = "",
        paramLabel = "<id>",
        description = "Stored credential identifier")
    String credentialId;

    @CommandLine.Option(
        names = "--relying-party-id",
        defaultValue = "",
        paramLabel = "<rpId>",
        description = "Override relying party identifier (defaults to stored value)")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        defaultValue = "",
        paramLabel = "<origin>",
        description = "Origin to embed in clientDataJSON")
    String origin;

    @CommandLine.Option(
        names = "--type",
        defaultValue = "",
        paramLabel = "<type>",
        description = "Client data type (for example, webauthn.get)")
    String expectedType;

    @CommandLine.Option(
        names = "--challenge",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Challenge to sign in Base64URL form")
    String challenge;

    @CommandLine.Option(
        names = "--signature-counter",
        paramLabel = "<counter>",
        description = "Override signature counter in the generated authenticator data")
    Long signatureCounter;

    @CommandLine.Option(
        names = "--user-verification-required",
        paramLabel = "<bool>",
        arity = "0..1",
        fallbackValue = "true",
        description = "Override user verification requirement (true/false)")
    Boolean userVerificationRequired;

    @CommandLine.Option(
        names = "--private-key",
        paramLabel = "<jwk-or-pem>",
        description = "Authenticator private key as JWK (preferred) or PEM/PKCS#8")
    String privateKey;

    @CommandLine.Option(
        names = "--private-key-file",
        paramLabel = "<path>",
        description = "Path to a file containing the authenticator private key (JWK or PEM/PKCS#8)")
    Path privateKeyFile;

    @Override
    public Integer call() {
      Map<String, Object> baseFields = new LinkedHashMap<>();
      baseFields.put("credentialSource", "stored");
      baseFields.put("credentialReference", true);
      if (hasText(presetId)) {
        baseFields.put("presetId", presetId);
        Optional<Sample> preset = parent.findPreset(presetId);
        if (preset.isEmpty()) {
          return parent.failValidation(
              event("evaluate"),
              "preset_not_found",
              "Unknown generator preset " + presetId,
              Map.copyOf(baseFields));
        }
        parent.applyStoredPresetDefaults(this, preset.get());
      }

      if (!hasText(credentialId)) {
        return parent.failValidation(
            event("evaluate"),
            "missing_option",
            "--credential-id is required",
            Map.copyOf(baseFields));
      }
      baseFields.put("credentialId", credentialId);
      if (!hasText(origin)) {
        return parent.failValidation(
            event("evaluate"), "missing_option", "--origin is required", Map.copyOf(baseFields));
      }
      if (!hasText(expectedType)) {
        return parent.failValidation(
            event("evaluate"), "missing_option", "--type is required", Map.copyOf(baseFields));
      }

      if (!hasText(challenge)) {
        return parent.failValidation(
            event("evaluate"), "missing_option", "--challenge is required", Map.copyOf(baseFields));
      }

      String resolvedPrivateKey;
      try {
        resolvedPrivateKey = extractPrivateKey(privateKey, privateKeyFile);
      } catch (IllegalArgumentException | IOException ex) {
        return parent.failValidation(
            event("evaluate"), "private_key_invalid", ex.getMessage(), Map.copyOf(baseFields));
      }

      if (!hasText(resolvedPrivateKey)) {
        return parent.failValidation(
            event("evaluate"),
            "private_key_required",
            "Provide --private-key or --private-key-file",
            Map.copyOf(baseFields));
      }

      byte[] challengeBytes;
      try {
        challengeBytes = decodeBase64Url("challenge", challenge);
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("evaluate"), "invalid_payload", ex.getMessage(), Map.copyOf(baseFields));
      }

      try (CredentialStore store = parent.openStore()) {
        WebAuthnAssertionGenerationApplicationService generator =
            parent.createGeneratorService(store);
        GenerationResult result =
            generator.generate(
                new GenerationCommand.Stored(
                    credentialId,
                    relyingPartyId,
                    origin,
                    expectedType,
                    challengeBytes,
                    resolvedPrivateKey,
                    signatureCounter,
                    userVerificationRequired));
        return parent.emitGenerationSuccess(result, "stored");
      } catch (IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Generation failed" : ex.getMessage();
        String reason =
            message.toLowerCase(Locale.US).contains("private key")
                ? "private_key_invalid"
                : "generation_failed";
        return parent.failValidation(event("evaluate"), reason, message, Map.copyOf(baseFields));
      } catch (Exception ex) {
        return parent.failUnexpected(
            event("evaluate"),
            Map.copyOf(baseFields),
            "Generation failed: " + sanitizeMessage(ex.getMessage()));
      }
    }
  }

  @CommandLine.Command(
      name = "evaluate-inline",
      description = "Generate a WebAuthn assertion using inline credential parameters.")
  static final class EvaluateInlineCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--preset-id",
        paramLabel = "<preset>",
        description = "Load default values from a generator preset")
    String presetId;

    @CommandLine.Option(
        names = "--credential-name",
        paramLabel = "<name>",
        defaultValue = "cli-fido2-inline",
        description = "Display name for the inline credential (used for telemetry only)")
    String credentialName;

    @CommandLine.Option(
        names = "--relying-party-id",
        defaultValue = "",
        paramLabel = "<rpId>",
        description = "Relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        defaultValue = "",
        paramLabel = "<origin>",
        description = "Origin to embed in clientDataJSON")
    String origin;

    @CommandLine.Option(
        names = "--type",
        defaultValue = "",
        paramLabel = "<type>",
        description = "Client data type (for example, webauthn.get)")
    String expectedType;

    @CommandLine.Option(
        names = "--credential-id",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Credential ID in Base64URL form")
    String credentialId;

    @CommandLine.Option(
        names = "--signature-counter",
        paramLabel = "<counter>",
        description = "Signature counter value")
    Long signatureCounter;

    @CommandLine.Option(
        names = "--user-verification-required",
        paramLabel = "<bool>",
        arity = "0..1",
        fallbackValue = "true",
        description = "Whether the credential requires user verification (true/false)")
    Boolean userVerificationRequired;

    @CommandLine.Option(
        names = "--algorithm",
        defaultValue = "",
        paramLabel = "<name>",
        description = "Signature algorithm label (e.g., ES256, RS256)")
    String algorithm;

    @CommandLine.Option(
        names = "--challenge",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Challenge to sign in Base64URL form")
    String challenge;

    @CommandLine.Option(
        names = "--private-key",
        paramLabel = "<jwk-or-pem>",
        description = "Authenticator private key as JWK (preferred) or PEM/PKCS#8")
    String privateKey;

    @CommandLine.Option(
        names = "--private-key-file",
        paramLabel = "<path>",
        description = "Path to a file containing the authenticator private key (JWK or PEM/PKCS#8)")
    Path privateKeyFile;

    @Override
    public Integer call() {
      Map<String, Object> baseFields = new LinkedHashMap<>();
      baseFields.put("credentialSource", "inline");
      baseFields.put("credentialReference", false);

      if (hasText(presetId)) {
        baseFields.put("presetId", presetId);
        Optional<Sample> preset = parent.findPreset(presetId);
        if (preset.isEmpty()) {
          return parent.failValidation(
              event("evaluate"),
              "preset_not_found",
              "Unknown generator preset " + presetId,
              Map.copyOf(baseFields));
        }
        parent.applyInlinePresetDefaults(this, preset.get());
      }

      if (!hasText(relyingPartyId) || !hasText(origin)) {
        return parent.failValidation(
            event("evaluate"),
            "missing_option",
            "--relying-party-id and --origin are required",
            Map.copyOf(baseFields));
      }
      if (!hasText(expectedType)) {
        return parent.failValidation(
            event("evaluate"), "missing_option", "--type is required", Map.copyOf(baseFields));
      }
      if (!hasText(credentialId)) {
        return parent.failValidation(
            event("evaluate"),
            "missing_option",
            "--credential-id is required",
            Map.copyOf(baseFields));
      }
      if (!hasText(algorithm)) {
        return parent.failValidation(
            event("evaluate"), "missing_option", "--algorithm is required", Map.copyOf(baseFields));
      }
      if (signatureCounter == null) {
        return parent.failValidation(
            event("evaluate"),
            "missing_option",
            "--signature-counter is required",
            Map.copyOf(baseFields));
      }
      if (!hasText(challenge)) {
        return parent.failValidation(
            event("evaluate"), "missing_option", "--challenge is required", Map.copyOf(baseFields));
      }

      String resolvedPrivateKey;
      try {
        resolvedPrivateKey = extractPrivateKey(privateKey, privateKeyFile);
      } catch (IllegalArgumentException | IOException ex) {
        return parent.failValidation(
            event("evaluate"), "private_key_invalid", ex.getMessage(), Map.copyOf(baseFields));
      }

      if (!hasText(resolvedPrivateKey)) {
        return parent.failValidation(
            event("evaluate"),
            "private_key_required",
            "Provide --private-key or --private-key-file",
            Map.copyOf(baseFields));
      }

      byte[] credentialIdBytes;
      byte[] challengeBytes;
      WebAuthnSignatureAlgorithm parsedAlgorithm;

      try {
        credentialIdBytes = decodeBase64Url("credential-id", credentialId);
        challengeBytes = decodeBase64Url("challenge", challenge);
        parsedAlgorithm = WebAuthnSignatureAlgorithm.fromLabel(algorithm);
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("evaluate"), "invalid_payload", ex.getMessage(), Map.copyOf(baseFields));
      }

      boolean requireUv = userVerificationRequired != null && userVerificationRequired;

      try {
        WebAuthnAssertionGenerationApplicationService generator =
            parent.createInlineGeneratorService();
        GenerationResult result =
            generator.generate(
                new GenerationCommand.Inline(
                    credentialName,
                    credentialIdBytes,
                    parsedAlgorithm,
                    relyingPartyId,
                    origin,
                    expectedType,
                    signatureCounter,
                    requireUv,
                    challengeBytes,
                    resolvedPrivateKey));
        return parent.emitGenerationSuccess(result, "inline");
      } catch (IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "Generation failed" : ex.getMessage();
        String reason =
            message.toLowerCase(Locale.US).contains("private key")
                ? "private_key_invalid"
                : "generation_failed";
        return parent.failValidation(event("evaluate"), reason, message, Map.copyOf(baseFields));
      } catch (Exception ex) {
        return parent.failUnexpected(
            event("evaluate"),
            Map.copyOf(baseFields),
            "Generation failed: " + sanitizeMessage(ex.getMessage()));
      }
    }
  }

  @CommandLine.Command(
      name = "replay",
      description = "Replay a stored WebAuthn assertion without mutating credential state.")
  static final class ReplayStoredCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--vector-id",
        paramLabel = "<vector>",
        description = "Load replay payloads from the JSON bundle vector")
    String vectorId;

    @CommandLine.Option(
        names = "--credential-id",
        defaultValue = "",
        paramLabel = "<id>",
        description = "Stored credential identifier")
    String credentialId;

    @CommandLine.Option(
        names = "--relying-party-id",
        defaultValue = "",
        paramLabel = "<rpId>",
        description = "Expected relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        defaultValue = "",
        paramLabel = "<origin>",
        description = "Expected client data origin")
    String origin;

    @CommandLine.Option(
        names = "--type",
        defaultValue = "",
        paramLabel = "<type>",
        description = "Expected client data type (e.g., webauthn.get)")
    String expectedType;

    @CommandLine.Option(
        names = "--expected-challenge",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Expected challenge in Base64URL form")
    String expectedChallenge;

    @CommandLine.Option(
        names = "--client-data",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "ClientDataJSON payload in Base64URL form")
    String clientData;

    @CommandLine.Option(
        names = "--authenticator-data",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Authenticator data in Base64URL form")
    String authenticatorData;

    @CommandLine.Option(
        names = "--signature",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Authenticator signature in Base64URL form")
    String signature;

    @Override
    public Integer call() {
      Map<String, Object> baseFields = new LinkedHashMap<>();
      baseFields.put("credentialReference", true);
      if (hasText(vectorId)) {
        baseFields.put("vectorId", vectorId);
        Optional<WebAuthnJsonVector> vector = parent.findVector(vectorId);
        if (vector.isEmpty()) {
          return parent.failValidation(
              event("replay"),
              "vector_not_found",
              "Unknown JSON vector id " + vectorId,
              Map.copyOf(baseFields));
        }
        parent.applyReplayVectorDefaults(this, vector.get());
      }

      if (!hasText(credentialId)) {
        return parent.failValidation(
            event("replay"),
            "missing_option",
            "--credential-id is required",
            Map.copyOf(baseFields));
      }
      baseFields.put("credentialId", credentialId);
      if (!hasText(relyingPartyId) || !hasText(origin)) {
        return parent.failValidation(
            event("replay"),
            "missing_option",
            "--relying-party-id and --origin are required",
            Map.copyOf(baseFields));
      }
      if (!hasText(expectedType)) {
        return parent.failValidation(
            event("replay"), "missing_option", "--type is required", Map.copyOf(baseFields));
      }

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
        return parent.failValidation(
            event("replay"), "invalid_payload", ex.getMessage(), Map.copyOf(baseFields));
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
        return parent.failValidation(
            event("replay"), "validation_error", ex.getMessage(), Map.copyOf(baseFields));
      } catch (Exception ex) {
        return parent.failUnexpected(
            event("replay"),
            Map.copyOf(baseFields),
            "Replay failed: " + sanitizeMessage(ex.getMessage()));
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

  @CommandLine.Command(
      name = "attest",
      description = "Generate a WebAuthn attestation payload using deterministic fixtures.")
  static final class AttestCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--format",
        defaultValue = "",
        paramLabel = "<format>",
        description = "Attestation statement format (packed, fido-u2f, tpm, android-key)")
    String format;

    @CommandLine.Option(
        names = "--attestation-id",
        defaultValue = "",
        paramLabel = "<id>",
        description = "Fixture identifier to generate (for example, w3c-packed-es256)")
    String attestationId;

    @CommandLine.Option(
        names = "--relying-party-id",
        defaultValue = "",
        paramLabel = "<rpId>",
        description = "Expected relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        defaultValue = "",
        paramLabel = "<origin>",
        description = "Expected client origin (for example, https://example.org)")
    String origin;

    @CommandLine.Option(
        names = "--challenge",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Challenge in Base64URL form")
    String challenge;

    @CommandLine.Option(
        names = "--credential-private-key",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Credential private key in Base64URL form")
    String credentialPrivateKey;

    @CommandLine.Option(
        names = "--attestation-private-key",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Attestation private key in Base64URL form")
    String attestationPrivateKey;

    @CommandLine.Option(
        names = "--attestation-serial",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Attestation certificate serial in Base64URL form")
    String attestationSerial;

    @CommandLine.Option(
        names = "--signing-mode",
        defaultValue = "",
        paramLabel = "<mode>",
        description = "Signing mode (self-signed, unsigned, custom-root)")
    String signingMode;

    @CommandLine.Option(
        names = "--custom-root-file",
        paramLabel = "<path>",
        description = "PEM encoded custom root certificate (repeat for multiple anchors)")
    List<Path> customRootFiles;

    @Override
    public Integer call() {
      Map<String, Object> baseFields = new LinkedHashMap<>();
      if (hasText(attestationId)) {
        baseFields.put("attestationId", attestationId);
      }
      if (hasText(format)) {
        baseFields.put("format", format);
      }

      if (!hasText(format)) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "missing_option",
            "--format is required",
            Map.copyOf(baseFields));
      }

      WebAuthnAttestationFormat attestationFormat;
      try {
        attestationFormat = WebAuthnAttestationFormat.fromLabel(format.trim());
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "invalid_format",
            ex.getMessage(),
            Map.copyOf(baseFields));
      }

      if (!hasText(signingMode)) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "missing_signing_mode",
            "--signing-mode is required",
            Map.copyOf(baseFields));
      }

      if (!hasText(attestationId)
          || !hasText(relyingPartyId)
          || !hasText(origin)
          || !hasText(challenge)
          || !hasText(credentialPrivateKey)
          || !hasText(attestationPrivateKey)
          || !hasText(attestationSerial)) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "missing_option",
            "--attestation-id, --relying-party-id, --origin, --challenge, "
                + "--credential-private-key, --attestation-private-key, and --attestation-serial are required",
            Map.copyOf(baseFields));
      }

      byte[] challengeBytes;
      try {
        challengeBytes = decodeBase64Url("--challenge", challenge);
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "invalid_payload",
            ex.getMessage(),
            Map.copyOf(baseFields));
      }

      WebAuthnAttestationGenerator.SigningMode mode;
      try {
        mode = parseSigningMode(signingMode);
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "invalid_signing_mode",
            ex.getMessage(),
            Map.copyOf(baseFields));
      }
      baseFields.put("signingMode", mode.name().toLowerCase(Locale.ROOT));

      List<String> customRoots;
      try {
        customRoots = loadCustomRoots(customRootFiles);
      } catch (IOException ex) {
        return parent.failUnexpected(
            event("attest"),
            ATTEST_TELEMETRY,
            Map.copyOf(baseFields),
            "Unable to read custom root: " + sanitizeMessage(ex.getMessage()));
      }

      String customRootSource = customRoots.isEmpty() ? "" : "file";

      WebAuthnAttestationGenerationApplicationService service =
          parent.createAttestationGenerationService();

      WebAuthnAttestationGenerationApplicationService.GenerationResult result;
      try {
        result =
            service.generate(
                new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Inline(
                    attestationId.trim(),
                    attestationFormat,
                    relyingPartyId.trim(),
                    origin.trim(),
                    challengeBytes,
                    credentialPrivateKey.trim(),
                    attestationPrivateKey.trim(),
                    attestationSerial.trim(),
                    mode,
                    customRoots,
                    customRootSource));
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("attest"),
            ATTEST_TELEMETRY,
            "generation_failed",
            ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
            Map.copyOf(baseFields));
      } catch (Exception ex) {
        return parent.failUnexpected(
            event("attest"),
            ATTEST_TELEMETRY,
            Map.copyOf(baseFields),
            "Attestation generation failed: " + sanitizeMessage(ex.getMessage()));
      }

      WebAuthnAttestationGenerationApplicationService.GeneratedAttestation attestation =
          result.attestation();
      out().println("Generated attestation:");
      out().println("type=" + attestation.type());
      out().println("id=" + attestation.id());
      out().println("rawId=" + attestation.rawId());
      out().println("attestationId=" + attestation.attestationId());
      out().println("format=" + attestation.format().label());
      var responsePayload = attestation.response();
      out().println("response.clientDataJSON=" + responsePayload.clientDataJson());
      out().println("response.attestationObject=" + responsePayload.attestationObject());

      WebAuthnAttestationGenerationApplicationService.TelemetrySignal telemetry =
          result.telemetry();
      TelemetryFrame frame =
          mergeTelemetryFrame(
              telemetry.emit(ATTEST_TELEMETRY, nextTelemetryId()), Map.copyOf(baseFields));
      writeFrame(out(), event("attest"), frame);
      return CommandLine.ExitCode.OK;
    }

    private static WebAuthnAttestationGenerator.SigningMode parseSigningMode(String value) {
      String normalized = value.trim().toLowerCase(Locale.ROOT);
      return switch (normalized) {
        case "self-signed", "self_signed" -> WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED;
        case "unsigned" -> WebAuthnAttestationGenerator.SigningMode.UNSIGNED;
        case "custom-root", "custom_root" -> WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT;
        default ->
            throw new IllegalArgumentException(
                "Unsupported signing mode: "
                    + value
                    + " (expected self-signed, unsigned, or custom-root)");
      };
    }

    private static List<String> loadCustomRoots(List<Path> files) throws IOException {
      if (files == null || files.isEmpty()) {
        return List.of();
      }
      List<String> roots = new java.util.ArrayList<>();
      for (Path file : files) {
        String content = Files.readString(file);
        if (content != null && !content.isBlank()) {
          roots.add(content.trim());
        }
      }
      return List.copyOf(roots);
    }
  }

  @CommandLine.Command(
      name = "attest-replay",
      description = "Replay a WebAuthn attestation verification with optional trust anchors.")
  static final class AttestReplayCommand extends AbstractFido2Command {

    @CommandLine.Option(
        names = "--format",
        defaultValue = "",
        paramLabel = "<format>",
        description = "Attestation statement format (packed, fido-u2f, tpm, android-key)")
    String format;

    @CommandLine.Option(
        names = "--attestation-id",
        defaultValue = "",
        paramLabel = "<id>",
        description = "Identifier to echo in telemetry outputs")
    String attestationId;

    @CommandLine.Option(
        names = "--relying-party-id",
        defaultValue = "",
        paramLabel = "<rpId>",
        description = "Expected relying party identifier")
    String relyingPartyId;

    @CommandLine.Option(
        names = "--origin",
        defaultValue = "",
        paramLabel = "<origin>",
        description = "Expected client origin (for example, https://example.org)")
    String origin;

    @CommandLine.Option(
        names = "--attestation-object",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Attestation object in Base64URL form")
    String attestationObject;

    @CommandLine.Option(
        names = "--client-data-json",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "ClientDataJSON payload in Base64URL form")
    String clientDataJson;

    @CommandLine.Option(
        names = "--expected-challenge",
        defaultValue = "",
        paramLabel = "<base64url>",
        description = "Expected challenge in Base64URL form")
    String expectedChallenge;

    @CommandLine.Option(
        names = "--trust-anchor-file",
        paramLabel = "<path>",
        description = "PEM encoded trust anchor certificate (repeat for multiple anchors)")
    List<Path> trustAnchorFiles;

    @Override
    public Integer call() {
      Map<String, Object> baseFields = new LinkedHashMap<>();
      if (hasText(attestationId)) {
        baseFields.put("attestationId", attestationId);
      }
      if (hasText(format)) {
        baseFields.put("format", format);
      }

      if (!hasText(format)) {
        return parent.failValidation(
            event("attestReplay"),
            ATTEST_REPLAY_TELEMETRY,
            "missing_option",
            "--format is required",
            Map.copyOf(baseFields));
      }

      WebAuthnAttestationFormat attestationFormat;
      try {
        attestationFormat = WebAuthnAttestationFormat.fromLabel(format.trim());
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("attestReplay"),
            ATTEST_REPLAY_TELEMETRY,
            "invalid_format",
            ex.getMessage(),
            Map.copyOf(baseFields));
      }

      if (!hasText(relyingPartyId) || !hasText(origin)) {
        return parent.failValidation(
            event("attestReplay"),
            ATTEST_REPLAY_TELEMETRY,
            "missing_option",
            "--relying-party-id and --origin are required",
            Map.copyOf(baseFields));
      }

      if (!hasText(attestationObject) || !hasText(clientDataJson) || !hasText(expectedChallenge)) {
        return parent.failValidation(
            event("attestReplay"),
            ATTEST_REPLAY_TELEMETRY,
            "missing_option",
            "--attestation-object, --client-data-json, and --expected-challenge are required",
            Map.copyOf(baseFields));
      }

      byte[] attestationObjectBytes;
      byte[] clientDataBytes;
      byte[] expectedChallengeBytes;
      try {
        attestationObjectBytes = decodeBase64Url("--attestation-object", attestationObject);
        clientDataBytes = decodeBase64Url("--client-data-json", clientDataJson);
        expectedChallengeBytes = decodeBase64Url("--expected-challenge", expectedChallenge);
      } catch (IllegalArgumentException ex) {
        return parent.failValidation(
            event("attestReplay"),
            ATTEST_REPLAY_TELEMETRY,
            "invalid_payload",
            ex.getMessage(),
            Map.copyOf(baseFields));
      }

      WebAuthnTrustAnchorResolver.Resolution anchorResolution =
          parent.resolveTrustAnchors(attestationId, attestationFormat, trustAnchorFiles);
      parent.emitTrustAnchorWarnings(anchorResolution.warnings());
      List<X509Certificate> trustAnchors = anchorResolution.anchors();

      if (!trustAnchors.isEmpty()) {
        baseFields.put("trustAnchorCount", trustAnchors.size());
        baseFields.put("trustAnchorMode", anchorResolution.cached() ? "cached" : "fresh");
        if (anchorResolution.metadataEntryId() != null) {
          baseFields.put("anchorMetadataEntry", anchorResolution.metadataEntryId());
        }
      }

      WebAuthnAttestationReplayApplicationService service = parent.createAttestationReplayService();

      WebAuthnAttestationReplayApplicationService.ReplayResult result;
      try {
        result =
            service.replay(
                new WebAuthnAttestationReplayApplicationService.ReplayCommand.Inline(
                    attestationId,
                    attestationFormat,
                    relyingPartyId,
                    origin,
                    attestationObjectBytes,
                    clientDataBytes,
                    expectedChallengeBytes,
                    trustAnchors,
                    anchorResolution.cached(),
                    anchorResolution.source(),
                    anchorResolution.metadataEntryId(),
                    anchorResolution.warnings()));
      } catch (Exception ex) {
        return parent.failUnexpected(
            event("attestReplay"),
            ATTEST_REPLAY_TELEMETRY,
            Map.copyOf(baseFields),
            "Attestation replay failed: " + sanitizeMessage(ex.getMessage()));
      }

      WebAuthnAttestationReplayApplicationService.TelemetrySignal telemetry = result.telemetry();
      TelemetryFrame frame =
          mergeTelemetryFrame(
              telemetry.emit(ATTEST_REPLAY_TELEMETRY, nextTelemetryId()), Map.copyOf(baseFields));

      switch (telemetry.status()) {
        case SUCCESS -> {
          writeFrame(out(), event("attestReplay"), frame);
          return CommandLine.ExitCode.OK;
        }
        case INVALID -> {
          writeFrame(err(), event("attestReplay"), frame);
          return CommandLine.ExitCode.USAGE;
        }
        case ERROR -> {
          writeFrame(err(), event("attestReplay"), frame);
          return CommandLine.ExitCode.SOFTWARE;
        }
      }
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  @CommandLine.Command(
      name = "vectors",
      description = "List available WebAuthn JSON bundle sample vectors.")
  static final class VectorsCommand extends AbstractFido2Command {

    @Override
    public Integer call() {
      parent.printVectorSummaries(out());
      return CommandLine.ExitCode.OK;
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
