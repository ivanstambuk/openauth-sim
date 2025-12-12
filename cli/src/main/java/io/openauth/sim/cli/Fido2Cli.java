package io.openauth.sim.cli;

import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationCommand;
import io.openauth.sim.application.fido2.WebAuthnAssertionGenerationApplicationService.GenerationResult;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationGenerationApplicationService.GenerationCommand.InputSource;
import io.openauth.sim.application.fido2.WebAuthnAttestationReplayApplicationService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedCommand;
import io.openauth.sim.application.fido2.WebAuthnAttestationSeedService.SeedResult;
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
import io.openauth.sim.cli.support.JsonPrinter;
import io.openauth.sim.cli.support.TelemetryJson;
import io.openauth.sim.cli.support.VerboseTraceMapper;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures;
import io.openauth.sim.core.fido2.WebAuthnJsonVectorFixtures.WebAuthnJsonVector;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.support.ProjectPaths;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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
            Fido2Cli.EvaluateCommand.class,
            Fido2Cli.ReplayStoredCommand.class,
            Fido2Cli.AttestCommand.class,
            Fido2Cli.AttestReplayCommand.class,
            Fido2Cli.SeedAttestationsCommand.class,
            Fido2Cli.VectorsCommand.class
        })
public final class Fido2Cli implements java.util.concurrent.Callable<Integer> {

    private static final String EVENT_PREFIX = "cli.fido2.";
    private static final String DEFAULT_DATABASE_FILE = "credentials.db";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private static final Fido2TelemetryAdapter EVALUATION_TELEMETRY = new Fido2TelemetryAdapter("fido2.evaluate");
    private static final Fido2TelemetryAdapter REPLAY_TELEMETRY = new Fido2TelemetryAdapter("fido2.replay");
    private static final Fido2TelemetryAdapter ATTEST_TELEMETRY = new Fido2TelemetryAdapter("fido2.attest");
    private static final Fido2TelemetryAdapter ATTEST_REPLAY_TELEMETRY =
            new Fido2TelemetryAdapter("fido2.attestReplay");
    private static final Fido2TelemetryAdapter VECTORS_TELEMETRY = new Fido2TelemetryAdapter("fido2.vectors");

    private Map<String, WebAuthnJsonVector> jsonVectorIndex;
    private List<WebAuthnJsonVector> jsonVectorList;
    private List<WebAuthnAttestationVector> attestationVectorList;
    private Map<String, Sample> generatorSampleIndex;

    private final WebAuthnTrustAnchorResolver trustAnchorResolver = new WebAuthnTrustAnchorResolver();

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = "--output-json",
            scope = CommandLine.ScopeType.INHERIT,
            description = "Emit a single JSON object instead of text output")
    boolean outputJsonFlag;

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

    boolean outputJson() {
        return outputJsonFlag;
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
        Map<String, WebAuthnJsonVector> loaded = WebAuthnJsonVectorFixtures.loadAll()
                .collect(Collectors.toMap(
                        WebAuthnJsonVector::vectorId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        if (loaded.isEmpty()) {
            WebAuthnFixtures.WebAuthnFixture fallback = WebAuthnFixtures.loadPackedEs256();
            WebAuthnJsonVector vector = new WebAuthnJsonVector(
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
                .collect(Collectors.toMap(Sample::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
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
        return Optional.ofNullable(jsonVectorIndex().get(vectorId));
    }

    private Optional<Sample> findPreset(String presetId) {
        if (!hasText(presetId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(generatorSampleIndex().get(presetId));
    }

    private void printVectorSummaries(PrintWriter writer) {
        List<WebAuthnJsonVector> vectors = jsonVectorList();
        List<WebAuthnAttestationVector> attestationVectors = attestationVectorList();
        if (vectors.isEmpty() && attestationVectors.isEmpty()) {
            writer.println("No WebAuthn fixture vectors available.");
            return;
        }
        if (!vectors.isEmpty()) {
            writer.println("vectorId\talgorithm\tuvRequired\trelyingPartyId\torigin");
            vectors.forEach(vector -> writer.printf(
                    "%s\t%s\t%s\t%s\t%s%n",
                    vector.vectorId(),
                    vector.algorithm().label(),
                    vector.storedCredential().userVerificationRequired(),
                    vector.storedCredential().relyingPartyId(),
                    vector.assertionRequest().origin()));
        }
        if (!attestationVectors.isEmpty()) {
            if (!vectors.isEmpty()) {
                writer.println();
            }
            writer.println("attestationId\tformat\talgorithm\trelyingPartyId\torigin\tsection");
            attestationVectors.forEach(vector -> writer.printf(
                    "%s\t%s\t%s\t%s\t%s\t%s%n",
                    vector.vectorId(),
                    vector.format().label(),
                    vector.algorithm().label(),
                    vector.relyingPartyId(),
                    vector.origin(),
                    vector.w3cSection()));
        }
    }

    private Map<String, WebAuthnJsonVector> jsonVectorIndex() {
        if (jsonVectorIndex == null) {
            jsonVectorIndex = loadVectorIndex();
        }
        return jsonVectorIndex;
    }

    private List<WebAuthnJsonVector> jsonVectorList() {
        if (jsonVectorList == null) {
            jsonVectorList = jsonVectorIndex().values().stream()
                    .sorted(Comparator.comparing(WebAuthnJsonVector::vectorId, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toUnmodifiableList());
        }
        return jsonVectorList;
    }

    private List<WebAuthnAttestationVector> attestationVectorList() {
        if (attestationVectorList == null) {
            attestationVectorList = List.copyOf(WebAuthnAttestationSamples.vectors());
        }
        return attestationVectorList;
    }

    private Map<String, Sample> generatorSampleIndex() {
        if (generatorSampleIndex == null) {
            generatorSampleIndex = loadGeneratorSampleIndex();
        }
        return generatorSampleIndex;
    }

    private void applyStoredPresetDefaults(EvaluateCommand command, Sample sample) {
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
        if (command.signatureCounter == null) {
            command.signatureCounter = sample.signatureCounter();
        }
        if (command.userVerificationRequired == null) {
            command.userVerificationRequired = sample.userVerificationRequired();
        }
    }

    private void applyInlinePresetDefaults(EvaluateCommand command, Sample sample) {
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
        if (!hasText(command.inlineCredentialId)) {
            command.inlineCredentialId = sample.credentialIdBase64Url();
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

    String generateInlineCredentialId() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
            throw new IllegalArgumentException("Provide either --private-key or --private-key-file, not both.");
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

    private static String formatPublicKeyCredentialJson(GenerationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"public-key\",");
        builder.append("\"id\":\"").append(encodeBase64(result.credentialId())).append("\",");
        builder.append("\"rawId\":\"")
                .append(encodeBase64(result.credentialId()))
                .append("\",");
        builder.append("\"response\":{");
        builder.append("\"clientDataJSON\":\"")
                .append(encodeBase64(result.clientDataJson()))
                .append("\",");
        builder.append("\"authenticatorData\":\"")
                .append(encodeBase64(result.authenticatorData()))
                .append("\",");
        builder.append("\"signature\":\"")
                .append(encodeBase64(result.signature()))
                .append("\"},");
        builder.append("\"signature\":\"")
                .append(encodeBase64(result.signature()))
                .append("\"}}");
        return builder.toString();
    }

    private static Map<String, Object> generatorTelemetryFields(GenerationResult result, String credentialSource) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", credentialSource);
        fields.put("credentialReference", result.credentialReference());
        fields.put("credentialId", encodeBase64(result.credentialId()));
        fields.put("relyingPartyId", result.relyingPartyId());
        fields.put("origin", result.origin());
        fields.put("algorithm", result.algorithm().label());
        fields.put("userVerificationRequired", result.userVerificationRequired());
        fields.put("signatureCounter", result.signatureCounter());
        fields.put("signatureCounterDerived", result.signatureCounterDerived());
        return fields;
    }

    private CredentialStore openStore() throws Exception {
        return CredentialStoreFactory.openFileStore(databasePath());
    }

    private WebAuthnEvaluationApplicationService createEvaluationService(CredentialStore store) {
        return WebAuthnEvaluationApplicationService.usingDefaults(store);
    }

    private WebAuthnAssertionGenerationApplicationService createGeneratorService(CredentialStore store) {
        return WebAuthnAssertionGenerationApplicationService.usingDefaults(store);
    }

    private WebAuthnAssertionGenerationApplicationService createInlineGeneratorService() {
        return new WebAuthnAssertionGenerationApplicationService();
    }

    private WebAuthnAttestationGenerationApplicationService createAttestationGenerationService() {
        return new WebAuthnAttestationGenerationApplicationService();
    }

    private WebAuthnAttestationGenerationApplicationService createAttestationGenerationService(CredentialStore store) {
        return WebAuthnAttestationGenerationApplicationService.usingDefaults(store, ATTEST_TELEMETRY);
    }

    private WebAuthnAttestationReplayApplicationService createAttestationReplayService() {
        return new WebAuthnAttestationReplayApplicationService();
    }

    private static TelemetryFrame mergeTelemetryFrame(TelemetryFrame frame, Map<String, Object> additionalFields) {
        if (additionalFields == null || additionalFields.isEmpty()) {
            return frame;
        }
        Map<String, Object> merged = new LinkedHashMap<>(frame.fields());
        merged.putAll(additionalFields);
        return new TelemetryFrame(frame.event(), frame.status(), frame.sanitized(), merged);
    }

    private WebAuthnTrustAnchorResolver.Resolution resolveTrustAnchors(
            String attestationId, WebAuthnAttestationFormat format, List<String> metadataAnchors, List<Path> files) {
        List<String> metadataIds = metadataAnchors == null
                ? List.of()
                : metadataAnchors.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();

        List<String> pemBlocks = new ArrayList<>();
        if (files != null) {
            for (Path path : files) {
                if (path == null) {
                    continue;
                }
                try {
                    String data = Files.readString(path);
                    if (data != null && !data.isBlank()) {
                        pemBlocks.add(data);
                    }
                } catch (IOException ex) {
                    throw new IllegalArgumentException(
                            "Unable to read trust anchor file " + path + ": " + ex.getMessage(), ex);
                }
            }
        }

        return trustAnchorResolver.resolve(attestationId, format, metadataIds, pemBlocks);
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

    private int emitGenerationSuccess(
            GenerationResult result, String credentialSource, VerboseTrace verboseTrace, boolean outputJson) {
        Map<String, Object> fields = generatorTelemetryFields(result, credentialSource);
        TelemetryFrame frame =
                EVALUATION_TELEMETRY.status("success", nextTelemetryId(), "generated", true, null, fields);

        if (outputJson()) {
            Map<String, Object> data = buildAssertionResponse(result, credentialSource, verboseTrace);
            JsonPrinter.print(out(), TelemetryJson.response(event("evaluate"), frame, data), true);
            return CommandLine.ExitCode.OK;
        }

        out().println(formatPublicKeyCredentialJson(result));
        writeFrame(out(), event("evaluate"), frame);
        if (verboseTrace != null) {
            VerboseTracePrinter.print(out(), verboseTrace);
        }
        return CommandLine.ExitCode.OK;
    }

    private Map<String, Object> buildAssertionResponse(
            GenerationResult result, String credentialSource, VerboseTrace verboseTrace) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("credentialReference", result.credentialReference());
        data.put("credentialSource", credentialSource);
        data.put("credentialId", encodeBase64(result.credentialId()));
        data.put("relyingPartyId", result.relyingPartyId());
        data.put("origin", result.origin());
        data.put("algorithm", result.algorithm().label());
        data.put("signatureCounter", result.signatureCounter());
        data.put("signatureCounterDerived", result.signatureCounterDerived());
        data.put("userVerificationRequired", result.userVerificationRequired());
        data.put(
                "assertion",
                Map.of(
                        "type", "public-key",
                        "id", encodeBase64(result.credentialId()),
                        "rawId", encodeBase64(result.credentialId()),
                        "response",
                                Map.of(
                                        "clientDataJSON", encodeBase64(result.clientDataJson()),
                                        "authenticatorData", encodeBase64(result.authenticatorData()),
                                        "signature", encodeBase64(result.signature()))));
        if (result.publicKeyCose() != null && result.publicKeyCose().length > 0) {
            data.put("publicKeyCose", encodeBase64(result.publicKeyCose()));
        }
        if (verboseTrace != null) {
            data.put("trace", VerboseTraceMapper.toMap(verboseTrace));
        } else {
            result.verboseTrace().ifPresent(trace -> data.put("trace", VerboseTraceMapper.toMap(trace)));
        }
        return data;
    }

    private Map<String, Object> buildReplayResponse(ReplayResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("credentialReference", result.credentialReference());
        data.put("credentialId", result.credentialId());
        data.put("credentialSource", result.credentialSource());
        data.put("match", result.match());
        data.putAll(result.supplementalFields());
        result.error().ifPresent(error -> data.put("error", error.name().toLowerCase(Locale.US)));
        result.verboseTrace().ifPresent(trace -> data.put("trace", VerboseTraceMapper.toMap(trace)));
        return data;
    }

    private Map<String, Object> buildAttestationReplayResponse(
            WebAuthnAttestationReplayApplicationService.ReplayResult result, Map<String, Object> baseFields) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (baseFields != null && !baseFields.isEmpty()) {
            data.putAll(baseFields);
        }
        data.put("valid", result.valid());
        data.put("anchorProvided", result.anchorProvided());
        data.put("anchorMode", result.anchorMode());
        data.put("trustAnchorsCached", result.trustAnchorsCached());
        data.put("selfAttestedFallback", result.selfAttestedFallback());
        if (!result.anchorWarnings().isEmpty()) {
            data.put("anchorWarnings", result.anchorWarnings());
        }
        result.error().ifPresent(error -> data.put("error", error.name().toLowerCase(Locale.US)));
        result.attestedCredential().ifPresent(credential -> {
            Map<String, Object> credentialMap = new LinkedHashMap<>();
            credentialMap.put("relyingPartyId", credential.relyingPartyId());
            credentialMap.put("credentialId", credential.credentialId());
            credentialMap.put("algorithm", credential.algorithm().label());
            credentialMap.put("userVerificationRequired", credential.userVerificationRequired());
            credentialMap.put("aaguid", credential.aaguid());
            credentialMap.put("signatureCounter", credential.signatureCounter());
            data.put("attestedCredential", credentialMap);
        });
        return data;
    }

    private int failValidation(
            String event, String reasonCode, String message, Map<String, Object> fields, boolean outputJson) {
        TelemetryFrame frame =
                EVALUATION_TELEMETRY.invalid(nextTelemetryId(), reasonCode, sanitizeMessage(message), fields);
        if (outputJson()) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.USAGE;
    }

    private int failValidation(
            String event,
            Fido2TelemetryAdapter adapter,
            String reasonCode,
            String message,
            Map<String, Object> fields,
            boolean outputJson) {
        TelemetryFrame frame = adapter.invalid(nextTelemetryId(), reasonCode, sanitizeMessage(message), fields);
        if (outputJson()) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.USAGE;
    }

    private int failValidation(String event, TelemetrySignal signal, Map<String, Object> fields, boolean outputJson) {
        TelemetryFrame emitted = signal.emit(EVALUATION_TELEMETRY, nextTelemetryId());
        Map<String, Object> merged = new LinkedHashMap<>(emitted.fields());
        merged.putAll(fields);
        TelemetryFrame frame = new TelemetryFrame(emitted.event(), emitted.status(), emitted.sanitized(), merged);
        if (outputJson()) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.USAGE;
    }

    private int failUnexpected(String event, Map<String, Object> fields, String message, boolean outputJson) {
        TelemetryFrame frame = EVALUATION_TELEMETRY.status(
                "error", nextTelemetryId(), "unexpected_error", false, sanitizeMessage(message), fields);
        if (outputJson()) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
        return CommandLine.ExitCode.SOFTWARE;
    }

    private int failUnexpected(
            String event,
            Fido2TelemetryAdapter adapter,
            Map<String, Object> fields,
            String message,
            boolean outputJson) {
        TelemetryFrame frame =
                adapter.status("error", nextTelemetryId(), "unexpected_error", false, sanitizeMessage(message), fields);
        if (outputJson()) {
            JsonPrinter.print(out(), TelemetryJson.response(event, frame, fields), true);
        } else {
            writeFrame(err(), event, frame);
        }
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

    private static VerboseTrace buildGenerationTrace(
            String operation, Consumer<VerboseTrace.Builder> metadataConfigurer, Optional<VerboseTrace> source) {
        VerboseTrace.Builder builder = VerboseTrace.builder(operation);
        source.ifPresent(trace -> appendTrace(builder, trace));
        if (metadataConfigurer != null) {
            metadataConfigurer.accept(builder);
        }
        return builder.build();
    }

    private static void appendTrace(VerboseTrace.Builder builder, VerboseTrace source) {
        if (builder == null || source == null) {
            return;
        }
        for (VerboseTrace.TraceStep step : source.steps()) {
            builder.addStep(copy -> {
                copy.id(step.id());
                if (hasText(step.summary())) {
                    copy.summary(step.summary());
                }
                if (hasText(step.detail())) {
                    copy.detail(step.detail());
                }
                if (hasText(step.specAnchor())) {
                    copy.spec(step.specAnchor());
                }
                step.typedAttributes()
                        .forEach(attribute -> copy.attribute(attribute.type(), attribute.name(), attribute.value()));
                step.notes().forEach(copy::note);
            });
        }
    }

    @CommandLine.Command(
            name = "evaluate",
            description =
                    "Generate a WebAuthn assertion from a stored credential (with --credential-id) or inline parameters.")
    static final class EvaluateCommand extends AbstractFido2Command {

        @CommandLine.Option(
                names = "--preset-id",
                paramLabel = "<preset>",
                description = "Load default values from a generator preset")
        String presetId;

        @CommandLine.Option(
                names = "--credential-id",
                defaultValue = "",
                paramLabel = "<id>",
                description = "When set, evaluate the stored credential; omit for inline mode")
        String credentialId;

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
                names = "--challenge",
                defaultValue = "",
                paramLabel = "<base64url>",
                description = "Challenge to sign in Base64URL form")
        String challenge;

        @CommandLine.Option(
                names = "--signature-counter",
                paramLabel = "<counter>",
                description = "Signature counter value (inline) or override (stored)")
        Long signatureCounter;

        @CommandLine.Option(
                names = "--user-verification-required",
                paramLabel = "<bool>",
                arity = "0..1",
                fallbackValue = "true",
                description = "Whether the credential requires user verification (true/false)")
        Boolean userVerificationRequired;

        @CommandLine.Option(names = "--verbose", description = "Emit a detailed verbose trace of the generation steps")
        boolean verbose;

        // Inline-only options
        @CommandLine.Option(
                names = "--inline-credential-id",
                paramLabel = "<base64url>",
                description = "Credential ID for inline mode (defaults to a random value)")
        String inlineCredentialId;

        @CommandLine.Option(
                names = "--credential-name",
                paramLabel = "<name>",
                defaultValue = "cli-fido2-inline",
                description = "Display name for the inline credential (telemetry only)")
        String credentialName;

        @CommandLine.Option(
                names = "--algorithm",
                defaultValue = "",
                paramLabel = "<name>",
                description = "Signature algorithm label (e.g., ES256, RS256)")
        String algorithm;

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
            boolean storedMode = hasText(credentialId);
            Map<String, Object> baseFields = new LinkedHashMap<>();
            baseFields.put("credentialSource", storedMode ? "stored" : "inline");
            baseFields.put("credentialReference", storedMode);
            Long matchedCounter =
                    parent.spec.commandLine().getParseResult().matchedOptionValue("signature-counter", null);
            Long effectiveSignatureCounter = matchedCounter != null ? matchedCounter : signatureCounter;

            if (hasText(presetId)) {
                baseFields.put("presetId", presetId);
                Optional<Sample> preset = parent.findPreset(presetId);
                if (preset.isEmpty()) {
                    return failValidation(
                            event("evaluate"),
                            "preset_not_found",
                            "Unknown generator preset " + presetId,
                            Map.copyOf(baseFields));
                }
                if (storedMode) {
                    parent.applyStoredPresetDefaults(this, preset.get());
                } else {
                    parent.applyInlinePresetDefaults(this, preset.get());
                }
            }

            if (storedMode) {
                return handleStored(baseFields, effectiveSignatureCounter);
            }
            return handleInline(baseFields, effectiveSignatureCounter);
        }

        private Integer handleStored(Map<String, Object> baseFields, Long effectiveSignatureCounter) {
            baseFields.put("credentialId", credentialId);
            if (!hasText(credentialId)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--credential-id is required", Map.copyOf(baseFields));
            }
            if (!hasText(origin)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--origin is required", Map.copyOf(baseFields));
            }
            if (!hasText(expectedType)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--type is required", Map.copyOf(baseFields));
            }
            if (!hasText(challenge)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--challenge is required", Map.copyOf(baseFields));
            }

            byte[] challengeBytes;
            try {
                challengeBytes = decodeBase64Url("challenge", challenge);
            } catch (IllegalArgumentException ex) {
                return failValidation(event("evaluate"), "invalid_payload", ex.getMessage(), Map.copyOf(baseFields));
            }

            try (CredentialStore store = parent.openStore()) {
                WebAuthnAssertionGenerationApplicationService generator = parent.createGeneratorService(store);
                GenerationResult result = generator.generate(
                        new GenerationCommand.Stored(
                                credentialId,
                                relyingPartyId,
                                origin,
                                expectedType,
                                challengeBytes,
                                null,
                                effectiveSignatureCounter,
                                userVerificationRequired),
                        verbose);
                VerboseTrace verboseTrace = verbose
                        ? buildGenerationTrace(
                                "fido2.assertion.evaluate.stored",
                                builder -> {
                                    builder.withMetadata("mode", "stored");
                                    builder.withMetadata("credentialReference", "true");
                                    builder.withMetadata("credentialId", credentialId);
                                },
                                result.verboseTrace())
                        : null;
                return parent.emitGenerationSuccess(result, "stored", verboseTrace, outputJson());
            } catch (IllegalArgumentException ex) {
                String message = ex.getMessage() == null ? "Generation failed" : ex.getMessage();
                String reason = message.toLowerCase(Locale.US).contains("private key")
                        ? "private_key_invalid"
                        : "generation_failed";
                return failValidation(event("evaluate"), reason, message, Map.copyOf(baseFields));
            } catch (Exception ex) {
                return failUnexpected(
                        event("evaluate"),
                        Map.copyOf(baseFields),
                        "Generation failed: " + sanitizeMessage(ex.getMessage()));
            }
        }

        private Integer handleInline(Map<String, Object> baseFields, Long effectiveSignatureCounter) {
            if (!hasText(relyingPartyId) || !hasText(origin)) {
                return failValidation(
                        event("evaluate"),
                        "missing_option",
                        "--relying-party-id and --origin are required",
                        Map.copyOf(baseFields));
            }
            if (!hasText(expectedType)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--type is required", Map.copyOf(baseFields));
            }
            if (!hasText(algorithm)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--algorithm is required", Map.copyOf(baseFields));
            }
            if (!hasText(challenge)) {
                return failValidation(
                        event("evaluate"), "missing_option", "--challenge is required", Map.copyOf(baseFields));
            }

            String resolvedPrivateKey;
            try {
                resolvedPrivateKey = extractPrivateKey(privateKey, privateKeyFile);
            } catch (IllegalArgumentException | IOException ex) {
                return failValidation(
                        event("evaluate"), "private_key_invalid", ex.getMessage(), Map.copyOf(baseFields));
            }
            if (!hasText(resolvedPrivateKey)) {
                return failValidation(
                        event("evaluate"),
                        "private_key_required",
                        "Provide --private-key or --private-key-file",
                        Map.copyOf(baseFields));
            }

            byte[] credentialIdBytes;
            byte[] challengeBytes;
            WebAuthnSignatureAlgorithm parsedAlgorithm;

            try {
                String inlineId =
                        hasText(inlineCredentialId) ? inlineCredentialId : parent.generateInlineCredentialId();
                credentialIdBytes = decodeBase64Url("inline-credential-id", inlineId);
                challengeBytes = decodeBase64Url("challenge", challenge);
                parsedAlgorithm = WebAuthnSignatureAlgorithm.fromLabel(algorithm);
                baseFields.put("credentialId", inlineId);
            } catch (IllegalArgumentException ex) {
                return failValidation(event("evaluate"), "invalid_payload", ex.getMessage(), Map.copyOf(baseFields));
            }

            boolean requireUv = userVerificationRequired != null && userVerificationRequired;

            try {
                WebAuthnAssertionGenerationApplicationService generator = parent.createInlineGeneratorService();
                GenerationResult result = generator.generate(
                        new GenerationCommand.Inline(
                                credentialName,
                                credentialIdBytes,
                                parsedAlgorithm,
                                relyingPartyId,
                                origin,
                                expectedType,
                                effectiveSignatureCounter,
                                requireUv,
                                challengeBytes,
                                resolvedPrivateKey),
                        verbose);
                VerboseTrace verboseTrace = verbose
                        ? buildGenerationTrace(
                                "fido2.assertion.evaluate.inline",
                                builder -> {
                                    builder.withMetadata("mode", "inline");
                                    builder.withMetadata("credentialReference", "false");
                                    builder.withMetadata("credentialName", credentialName);
                                },
                                result.verboseTrace())
                        : null;
                return parent.emitGenerationSuccess(result, "inline", verboseTrace, outputJson());
            } catch (IllegalArgumentException ex) {
                String message = ex.getMessage() == null ? "Generation failed" : ex.getMessage();
                String reason = message.toLowerCase(Locale.US).contains("private key")
                        ? "private_key_invalid"
                        : "generation_failed";
                return failValidation(event("evaluate"), reason, message, Map.copyOf(baseFields));
            } catch (Exception ex) {
                return failUnexpected(
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

        @CommandLine.Option(
                names = "--verbose",
                description = "Emit a detailed verbose trace of the verification steps")
        boolean verbose;

        @Override
        public Integer call() {
            Map<String, Object> baseFields = new LinkedHashMap<>();
            baseFields.put("credentialReference", true);
            if (hasText(vectorId)) {
                baseFields.put("vectorId", vectorId);
                Optional<WebAuthnJsonVector> vector = parent.findVector(vectorId);
                if (vector.isEmpty()) {
                    return failValidation(
                            event("replay"),
                            "vector_not_found",
                            "Unknown JSON vector id " + vectorId,
                            Map.copyOf(baseFields));
                }
                parent.applyReplayVectorDefaults(this, vector.get());
            }

            if (!hasText(credentialId)) {
                return failValidation(
                        event("replay"), "missing_option", "--credential-id is required", Map.copyOf(baseFields));
            }
            baseFields.put("credentialId", credentialId);
            if (!hasText(relyingPartyId) || !hasText(origin)) {
                return failValidation(
                        event("replay"),
                        "missing_option",
                        "--relying-party-id and --origin are required",
                        Map.copyOf(baseFields));
            }
            if (!hasText(expectedType)) {
                return failValidation(event("replay"), "missing_option", "--type is required", Map.copyOf(baseFields));
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
                return failValidation(event("replay"), "invalid_payload", ex.getMessage(), Map.copyOf(baseFields));
            }

            try (CredentialStore store = parent.openStore()) {
                WebAuthnEvaluationApplicationService evaluationService = parent.createEvaluationService(store);
                WebAuthnReplayApplicationService replayService =
                        new WebAuthnReplayApplicationService(evaluationService);

                ReplayResult result = replayService.replay(
                        new ReplayCommand.Stored(
                                credentialId,
                                relyingPartyId,
                                origin,
                                expectedType,
                                challengeBytes,
                                clientDataBytes,
                                authenticatorDataBytes,
                                signatureBytes),
                        verbose);
                return handleResult(result);
            } catch (IllegalArgumentException ex) {
                return failValidation(event("replay"), "validation_error", ex.getMessage(), Map.copyOf(baseFields));
            } catch (Exception ex) {
                return failUnexpected(
                        event("replay"), Map.copyOf(baseFields), "Replay failed: " + sanitizeMessage(ex.getMessage()));
            }
        }

        private Integer handleResult(ReplayResult result) {
            TelemetrySignal signal = result.telemetry();
            Map<String, Object> baseFields =
                    Map.of("credentialReference", result.credentialReference(), "credentialId", result.credentialId());

            TelemetryFrame frame =
                    parent.augmentReplayFrame(result, result.replayFrame(REPLAY_TELEMETRY, nextTelemetryId()));

            return switch (signal.status()) {
                case SUCCESS -> {
                    if (outputJson()) {
                        JsonPrinter.print(
                                out(),
                                TelemetryJson.response(event("replay"), frame, parent.buildReplayResponse(result)),
                                true);
                        yield CommandLine.ExitCode.OK;
                    }
                    var writer = parent.out();
                    writeFrame(writer, event("replay"), frame);
                    result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(writer, trace));
                    yield CommandLine.ExitCode.OK;
                }
                case INVALID -> {
                    if (outputJson()) {
                        TelemetryFrame jsonFrame =
                                new TelemetryFrame(frame.event(), "success", frame.sanitized(), frame.fields());
                        JsonPrinter.print(
                                out(),
                                TelemetryJson.response(event("replay"), jsonFrame, parent.buildReplayResponse(result)),
                                true);
                        yield CommandLine.ExitCode.USAGE;
                    }
                    TelemetryFrame successFrame =
                            new TelemetryFrame(frame.event(), "success", frame.sanitized(), frame.fields());
                    writeFrame(parent.out(), event("replay"), successFrame);
                    result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(parent.out(), trace));
                    yield CommandLine.ExitCode.USAGE;
                }
                case ERROR -> {
                    if (outputJson()) {
                        JsonPrinter.print(
                                out(),
                                TelemetryJson.response(event("replay"), frame, parent.buildReplayResponse(result)),
                                true);
                        yield CommandLine.ExitCode.SOFTWARE;
                    }
                    int exit = failUnexpected(
                            event("replay"),
                            baseFields,
                            Optional.ofNullable(signal.reason()).orElse("WebAuthn replay failed"));
                    result.verboseTrace().ifPresent(trace -> VerboseTracePrinter.print(parent.err(), trace));
                    yield exit;
                }
            };
        }
    }

    @CommandLine.Command(
            name = "attest",
            description = "Generate a WebAuthn attestation payload using deterministic fixtures.")
    static final class AttestCommand extends AbstractFido2Command {

        @CommandLine.Option(
                names = "--input-source",
                defaultValue = "preset",
                paramLabel = "<source>",
                description = "Input source (preset, manual, or stored)")
        String inputSource;

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
                names = "--seed-preset-id",
                defaultValue = "",
                paramLabel = "<id>",
                description = "Optional preset identifier to record when manual overrides are applied")
        String seedPresetId;

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
                names = "--credential-id",
                defaultValue = "",
                paramLabel = "<id>",
                description = "Stored credential identifier (required for stored mode)")
        String credentialId;

        @CommandLine.Option(
                names = "--credential-private-key",
                defaultValue = "",
                paramLabel = "<key>",
                description = "Credential private key (JWK or PEM/PKCS#8)")
        String credentialPrivateKey;

        @CommandLine.Option(
                names = "--attestation-private-key",
                defaultValue = "",
                paramLabel = "<key>",
                description = "Attestation private key (JWK or PEM/PKCS#8)")
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

        @CommandLine.Option(
                names = "--override",
                paramLabel = "<field>",
                description = "Override field name to record for telemetry (repeat for multiple overrides)")
        List<String> overrideFields;

        @Override
        public Integer call() {
            Map<String, Object> baseFields = new LinkedHashMap<>();
            if (hasText(attestationId)) {
                baseFields.put("attestationId", attestationId);
            }
            if (hasText(format)) {
                baseFields.put("format", format);
            }

            InputSource source;
            try {
                source = parseInputSource(inputSource);
            } catch (IllegalArgumentException ex) {
                return failValidation(
                        event("attest"),
                        ATTEST_TELEMETRY,
                        "input_source_invalid",
                        ex.getMessage(),
                        Map.copyOf(baseFields));
            }
            baseFields.put("inputSource", source.name().toLowerCase(Locale.ROOT));

            if (!hasText(format)) {
                return failValidation(
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
                return failValidation(
                        event("attest"), ATTEST_TELEMETRY, "invalid_format", ex.getMessage(), Map.copyOf(baseFields));
            }

            WebAuthnAttestationGenerationApplicationService.GenerationResult result;

            if (source == InputSource.STORED) {
                if (!hasText(credentialId)) {
                    return failValidation(
                            event("attest"),
                            ATTEST_TELEMETRY,
                            "credential_id_required",
                            "--credential-id is required for stored mode",
                            Map.copyOf(baseFields));
                }
                String storedId = credentialId.trim();
                baseFields.put("credentialId", storedId);

                if (!hasText(challenge)) {
                    return failValidation(
                            event("attest"),
                            ATTEST_TELEMETRY,
                            "challenge_required",
                            "--challenge is required for stored mode",
                            Map.copyOf(baseFields));
                }

                byte[] challengeBytes;
                try {
                    challengeBytes = decodeBase64Url("--challenge", challenge);
                } catch (IllegalArgumentException ex) {
                    return failValidation(
                            event("attest"),
                            ATTEST_TELEMETRY,
                            "invalid_payload",
                            ex.getMessage(),
                            Map.copyOf(baseFields));
                }

                String requestedRp = hasText(relyingPartyId) ? relyingPartyId.trim() : "";
                if (!requestedRp.isEmpty()) {
                    baseFields.put("relyingPartyId", requestedRp);
                }
                String requestedOrigin = hasText(origin) ? origin.trim() : "";
                if (!requestedOrigin.isEmpty()) {
                    baseFields.put("origin", requestedOrigin);
                }

                try (CredentialStore store = parent.openStore()) {
                    WebAuthnAttestationGenerationApplicationService service =
                            parent.createAttestationGenerationService(store);
                    result = service.generate(
                            new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Stored(
                                    storedId, attestationFormat, requestedRp, requestedOrigin, challengeBytes));
                } catch (IllegalArgumentException ex) {
                    String message = ex.getMessage() == null ? "Stored attestation generation failed" : ex.getMessage();
                    String reason = mapStoredGenerationReason(message);
                    return failValidation(event("attest"), ATTEST_TELEMETRY, reason, message, Map.copyOf(baseFields));
                } catch (Exception ex) {
                    return failUnexpected(
                            event("attest"),
                            ATTEST_TELEMETRY,
                            Map.copyOf(baseFields),
                            "Attestation generation failed: " + sanitizeMessage(ex.getMessage()));
                }
            } else {
                if (!hasText(signingMode)) {
                    return failValidation(
                            event("attest"),
                            ATTEST_TELEMETRY,
                            "missing_signing_mode",
                            "--signing-mode is required",
                            Map.copyOf(baseFields));
                }

                WebAuthnAttestationGenerator.SigningMode mode;
                try {
                    mode = parseSigningMode(signingMode);
                } catch (IllegalArgumentException ex) {
                    return failValidation(
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
                    return failUnexpected(
                            event("attest"),
                            ATTEST_TELEMETRY,
                            Map.copyOf(baseFields),
                            "Unable to read custom root: " + sanitizeMessage(ex.getMessage()));
                }

                String customRootSource = customRoots.isEmpty() ? "" : "file";
                WebAuthnAttestationGenerationApplicationService service = parent.createAttestationGenerationService();

                if (source == InputSource.PRESET) {
                    if (!hasText(attestationId)
                            || !hasText(relyingPartyId)
                            || !hasText(origin)
                            || !hasText(challenge)
                            || !hasText(credentialPrivateKey)
                            || !hasText(attestationPrivateKey)
                            || !hasText(attestationSerial)) {
                        return failValidation(
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
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "invalid_payload",
                                ex.getMessage(),
                                Map.copyOf(baseFields));
                    }

                    try {
                        result = service.generate(
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
                                        customRootSource,
                                        InputSource.PRESET));
                    } catch (IllegalArgumentException ex) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "generation_failed",
                                ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
                                Map.copyOf(baseFields));
                    } catch (Exception ex) {
                        return failUnexpected(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                Map.copyOf(baseFields),
                                "Attestation generation failed: " + sanitizeMessage(ex.getMessage()));
                    }
                } else {
                    baseFields.remove("attestationId");
                    if (hasText(attestationId)) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "attestation_id_not_applicable",
                                "Manual input does not accept --attestation-id; use --seed-preset-id to annotate overrides",
                                Map.copyOf(baseFields));
                    }
                    if (!hasText(relyingPartyId)) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "relying_party_id_required",
                                "--relying-party-id is required for manual mode",
                                Map.copyOf(baseFields));
                    }
                    if (!hasText(origin)) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "origin_required",
                                "--origin is required for manual mode",
                                Map.copyOf(baseFields));
                    }
                    if (!hasText(challenge)) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "challenge_required",
                                "--challenge is required for manual mode",
                                Map.copyOf(baseFields));
                    }
                    byte[] challengeBytes;
                    try {
                        challengeBytes = decodeBase64Url("--challenge", challenge);
                    } catch (IllegalArgumentException ex) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "challenge_required",
                                "Invalid challenge (must be Base64URL)",
                                Map.copyOf(baseFields));
                    }

                    if (!hasText(credentialPrivateKey)) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "credential_private_key_required",
                                "Credential private key is required for manual mode",
                                Map.copyOf(baseFields));
                    }

                    String credentialKey = credentialPrivateKey.trim();
                    String attestationKey = hasText(attestationPrivateKey) ? attestationPrivateKey.trim() : "";
                    String attestationSerialValue = hasText(attestationSerial) ? attestationSerial.trim() : "";

                    if (mode != WebAuthnAttestationGenerator.SigningMode.UNSIGNED && attestationKey.isEmpty()) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "attestation_private_key_required",
                                "Attestation private key is required for manual signed modes",
                                Map.copyOf(baseFields));
                    }
                    if (mode != WebAuthnAttestationGenerator.SigningMode.UNSIGNED && attestationSerialValue.isEmpty()) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "attestation_serial_required",
                                "Attestation certificate serial is required for manual signed modes",
                                Map.copyOf(baseFields));
                    }
                    if (mode == WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT && customRoots.isEmpty()) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "custom_root_required",
                                "At least one custom root certificate is required for custom-root mode",
                                Map.copyOf(baseFields));
                    }

                    List<String> sanitizedOverrides = sanitizeOverrides(overrideFields);
                    String sanitizedSeedPresetId = hasText(seedPresetId) ? seedPresetId.trim() : "";
                    if (!sanitizedSeedPresetId.isEmpty()) {
                        baseFields.put("seedPresetId", sanitizedSeedPresetId);
                    }
                    if (!sanitizedOverrides.isEmpty()) {
                        baseFields.put("overrides", sanitizedOverrides);
                    }

                    try {
                        result = service.generate(
                                new WebAuthnAttestationGenerationApplicationService.GenerationCommand.Manual(
                                        attestationFormat,
                                        relyingPartyId.trim(),
                                        origin.trim(),
                                        challengeBytes,
                                        credentialKey,
                                        attestationKey.isEmpty() ? null : attestationKey,
                                        attestationSerialValue.isEmpty() ? null : attestationSerialValue,
                                        mode,
                                        customRoots,
                                        customRootSource,
                                        sanitizedSeedPresetId,
                                        sanitizedOverrides));
                    } catch (IllegalArgumentException ex) {
                        return failValidation(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                "generation_failed",
                                ex.getMessage() == null ? "Attestation generation failed" : ex.getMessage(),
                                Map.copyOf(baseFields));
                    } catch (Exception ex) {
                        return failUnexpected(
                                event("attest"),
                                ATTEST_TELEMETRY,
                                Map.copyOf(baseFields),
                                "Attestation generation failed: " + sanitizeMessage(ex.getMessage()));
                    }
                }
            }

            WebAuthnAttestationGenerationApplicationService.GeneratedAttestation attestation = result.attestation();

            WebAuthnAttestationGenerationApplicationService.TelemetrySignal telemetry = result.telemetry();
            TelemetryFrame frame =
                    mergeTelemetryFrame(telemetry.emit(ATTEST_TELEMETRY, nextTelemetryId()), Map.copyOf(baseFields));

            if (outputJson()) {
                Map<String, Object> data = new LinkedHashMap<>(baseFields);
                data.put("attestationId", attestation.attestationId());
                data.put("format", attestation.format().label());
                Map<String, Object> responsePayload = new LinkedHashMap<>();
                responsePayload.put("clientDataJSON", attestation.response().clientDataJson());
                responsePayload.put("attestationObject", attestation.response().attestationObject());
                Map<String, Object> attestationMap = new LinkedHashMap<>();
                attestationMap.put("type", attestation.type());
                attestationMap.put("id", attestation.id());
                attestationMap.put("rawId", attestation.rawId());
                attestationMap.put("attestationId", attestation.attestationId());
                attestationMap.put("format", attestation.format().label());
                attestationMap.put("response", responsePayload);
                data.put("attestation", attestationMap);
                if (!result.certificateChainPem().isEmpty()) {
                    data.put("certificateChainPem", result.certificateChainPem());
                }
                result.verboseTrace().ifPresent(trace -> data.put("trace", VerboseTraceMapper.toMap(trace)));
                JsonPrinter.print(out(), TelemetryJson.response(event("attest"), frame, data), true);
                return CommandLine.ExitCode.OK;
            }

            out().println("Generated attestation:");
            out().println("type=" + attestation.type());
            out().println("id=" + attestation.id());
            out().println("rawId=" + attestation.rawId());
            out().println("format=" + attestation.format().label());
            var responsePayload = attestation.response();
            out().println("response.clientDataJSON=" + responsePayload.clientDataJson());
            out().println("response.attestationObject=" + responsePayload.attestationObject());

            writeFrame(out(), event("attest"), frame);
            return CommandLine.ExitCode.OK;
        }

        private static InputSource parseInputSource(String value) {
            if (value == null || value.isBlank()) {
                return InputSource.PRESET;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "preset" -> InputSource.PRESET;
                case "manual" -> InputSource.MANUAL;
                case "stored" -> InputSource.STORED;
                default ->
                    throw new IllegalArgumentException(
                            "Unsupported input source: " + value + " (expected preset, manual, or stored)");
            };
        }

        private static WebAuthnAttestationGenerator.SigningMode parseSigningMode(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "self-signed", "self_signed" -> WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED;
                case "unsigned" -> WebAuthnAttestationGenerator.SigningMode.UNSIGNED;
                case "custom-root", "custom_root" -> WebAuthnAttestationGenerator.SigningMode.CUSTOM_ROOT;
                default ->
                    throw new IllegalArgumentException(
                            "Unsupported signing mode: " + value + " (expected self-signed, unsigned, or custom-root)");
            };
        }

        private static List<String> sanitizeOverrides(List<String> overrides) {
            if (overrides == null || overrides.isEmpty()) {
                return List.of();
            }
            return overrides.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toUnmodifiableList());
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

        private static String mapStoredGenerationReason(String message) {
            String normalized = message.toLowerCase(Locale.US);
            if (normalized.contains("not found")) {
                return "stored_credential_not_found";
            }
            if (normalized.contains("metadata")) {
                return "stored_attestation_required";
            }
            if (normalized.contains("relying party")) {
                return "stored_relying_party_mismatch";
            }
            return "generation_failed";
        }
    }

    @CommandLine.Command(
            name = "attest-replay",
            description = "Replay a WebAuthn attestation verification with optional trust anchors.")
    static final class AttestReplayCommand extends AbstractFido2Command {

        @CommandLine.Option(
                names = "--input-source",
                defaultValue = "inline",
                paramLabel = "<source>",
                description = "Input source (inline or stored)")
        String inputSource;

        @CommandLine.Option(
                names = "--format",
                defaultValue = "",
                paramLabel = "<format>",
                description = "Attestation statement format (packed, fido-u2f, tpm, android-key)")
        String format;

        @CommandLine.Option(
                names = "--credential-id",
                defaultValue = "",
                paramLabel = "<id>",
                description = "Stored credential identifier (required for stored mode)")
        String credentialId;

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

        @CommandLine.Option(
                names = "--metadata-anchor",
                paramLabel = "<entryId>",
                description = "Curated metadata entry identifier supplying trust anchors (repeat for multiple entries)")
        List<String> metadataAnchorIds;

        @Override
        public Integer call() {
            Map<String, Object> baseFields = new LinkedHashMap<>();
            if (hasText(attestationId)) {
                baseFields.put("attestationId", attestationId);
            }
            if (hasText(format)) {
                baseFields.put("format", format);
            }
            if (metadataAnchorIds != null && !metadataAnchorIds.isEmpty()) {
                baseFields.put("metadataAnchorIds", metadataAnchorIds);
            }

            InputSource source;
            try {
                source = parseInputSource(inputSource);
            } catch (IllegalArgumentException ex) {
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "input_source_invalid",
                        ex.getMessage(),
                        Map.copyOf(baseFields));
            }
            baseFields.put("inputSource", source.name().toLowerCase(Locale.ROOT));

            if (!hasText(format)) {
                return failValidation(
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
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "invalid_format",
                        ex.getMessage(),
                        Map.copyOf(baseFields));
            }

            return switch (source) {
                case STORED -> replayStored(attestationFormat, baseFields);
                case INLINE -> replayInline(attestationFormat, baseFields);
            };
        }

        private Integer replayStored(WebAuthnAttestationFormat attestationFormat, Map<String, Object> baseFields) {
            if (metadataAnchorIds != null && !metadataAnchorIds.isEmpty()) {
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "stored_metadata_anchor_unsupported",
                        "Stored attestation replay relies on persisted certificate chains; remove --metadata-anchor.",
                        Map.copyOf(baseFields));
            }
            if (trustAnchorFiles != null && !trustAnchorFiles.isEmpty()) {
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "stored_trust_anchor_unsupported",
                        "Stored attestation replay relies on persisted certificate chains; remove --trust-anchor-file.",
                        Map.copyOf(baseFields));
            }

            if (!hasText(credentialId)) {
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "credential_id_required",
                        "--credential-id is required for stored mode",
                        Map.copyOf(baseFields));
            }

            String storedId = credentialId.trim();
            baseFields.put("storedCredentialId", storedId);
            baseFields.put("credentialId", storedId);

            try (CredentialStore store = parent.openStore()) {
                WebAuthnAttestationReplayApplicationService service =
                        WebAuthnAttestationReplayApplicationService.usingDefaults(store, ATTEST_REPLAY_TELEMETRY);

                WebAuthnAttestationReplayApplicationService.ReplayResult result;
                try {
                    result = service.replay(new WebAuthnAttestationReplayApplicationService.ReplayCommand.Stored(
                            storedId, attestationFormat));
                } catch (IllegalArgumentException ex) {
                    String rawMessage = ex.getMessage() == null ? "Stored attestation replay failed" : ex.getMessage();
                    String message = sanitizeMessage(rawMessage);
                    String reason = mapStoredReplayReason(rawMessage);
                    return failValidation(
                            event("attestReplay"), ATTEST_REPLAY_TELEMETRY, reason, message, Map.copyOf(baseFields));
                } catch (Exception ex) {
                    return failUnexpected(
                            event("attestReplay"),
                            ATTEST_REPLAY_TELEMETRY,
                            Map.copyOf(baseFields),
                            "Attestation replay failed: " + sanitizeMessage(ex.getMessage()));
                }

                WebAuthnAttestationReplayApplicationService.TelemetrySignal telemetry = result.telemetry();
                TelemetryFrame frame = mergeTelemetryFrame(
                        telemetry.emit(ATTEST_REPLAY_TELEMETRY, nextTelemetryId()), Map.copyOf(baseFields));

                return switch (telemetry.status()) {
                    case SUCCESS -> {
                        if (outputJson()) {
                            JsonPrinter.print(
                                    out(),
                                    TelemetryJson.response(
                                            event("attestReplay"),
                                            frame,
                                            parent.buildAttestationReplayResponse(result, baseFields)),
                                    true);
                            yield CommandLine.ExitCode.OK;
                        }
                        writeFrame(out(), event("attestReplay"), frame);
                        yield CommandLine.ExitCode.OK;
                    }
                    case INVALID -> {
                        if (outputJson()) {
                            JsonPrinter.print(
                                    out(),
                                    TelemetryJson.response(
                                            event("attestReplay"),
                                            frame,
                                            parent.buildAttestationReplayResponse(result, baseFields)),
                                    true);
                            yield CommandLine.ExitCode.USAGE;
                        }
                        writeFrame(err(), event("attestReplay"), frame);
                        yield CommandLine.ExitCode.USAGE;
                    }
                    case ERROR -> {
                        if (outputJson()) {
                            JsonPrinter.print(
                                    out(),
                                    TelemetryJson.response(
                                            event("attestReplay"),
                                            frame,
                                            parent.buildAttestationReplayResponse(result, baseFields)),
                                    true);
                            yield CommandLine.ExitCode.SOFTWARE;
                        }
                        writeFrame(err(), event("attestReplay"), frame);
                        yield CommandLine.ExitCode.SOFTWARE;
                    }
                };
            } catch (Exception ex) {
                return failUnexpected(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        Map.copyOf(baseFields),
                        "Attestation replay failed: " + sanitizeMessage(ex.getMessage()));
            }
        }

        private Integer replayInline(WebAuthnAttestationFormat attestationFormat, Map<String, Object> baseFields) {

            if (!hasText(relyingPartyId) || !hasText(origin)) {
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "missing_option",
                        "--relying-party-id and --origin are required",
                        Map.copyOf(baseFields));
            }

            if (!hasText(attestationObject) || !hasText(clientDataJson) || !hasText(expectedChallenge)) {
                return failValidation(
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
                return failValidation(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        "invalid_payload",
                        ex.getMessage(),
                        Map.copyOf(baseFields));
            }

            WebAuthnTrustAnchorResolver.Resolution anchorResolution =
                    parent.resolveTrustAnchors(attestationId, attestationFormat, metadataAnchorIds, trustAnchorFiles);
            parent.emitTrustAnchorWarnings(anchorResolution.warnings());
            List<X509Certificate> trustAnchors = anchorResolution.anchors();

            if (!trustAnchors.isEmpty()) {
                baseFields.put("trustAnchorCount", trustAnchors.size());
                baseFields.put("trustAnchorMode", anchorResolution.cached() ? "cached" : "fresh");
                if (!anchorResolution.metadataEntryIds().isEmpty()) {
                    baseFields.put("metadataAnchorIds", anchorResolution.metadataEntryIds());
                    baseFields.put(
                            "anchorMetadataEntry",
                            anchorResolution.metadataEntryIds().get(0));
                }
            }

            WebAuthnAttestationReplayApplicationService service = parent.createAttestationReplayService();

            WebAuthnAttestationReplayApplicationService.ReplayResult result;
            try {
                result = service.replay(new WebAuthnAttestationReplayApplicationService.ReplayCommand.Inline(
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
                        anchorResolution.metadataEntryIds(),
                        anchorResolution.warnings()));
            } catch (Exception ex) {
                return failUnexpected(
                        event("attestReplay"),
                        ATTEST_REPLAY_TELEMETRY,
                        Map.copyOf(baseFields),
                        "Attestation replay failed: " + sanitizeMessage(ex.getMessage()));
            }

            WebAuthnAttestationReplayApplicationService.TelemetrySignal telemetry = result.telemetry();
            TelemetryFrame frame = mergeTelemetryFrame(
                    telemetry.emit(ATTEST_REPLAY_TELEMETRY, nextTelemetryId()), Map.copyOf(baseFields));

            switch (telemetry.status()) {
                case SUCCESS -> {
                    if (outputJson()) {
                        JsonPrinter.print(
                                out(),
                                TelemetryJson.response(
                                        event("attestReplay"),
                                        frame,
                                        parent.buildAttestationReplayResponse(result, baseFields)),
                                true);
                        return CommandLine.ExitCode.OK;
                    }
                    writeFrame(out(), event("attestReplay"), frame);
                    return CommandLine.ExitCode.OK;
                }
                case INVALID -> {
                    if (outputJson()) {
                        TelemetryFrame jsonFrame =
                                new TelemetryFrame(frame.event(), "success", frame.sanitized(), frame.fields());
                        JsonPrinter.print(
                                out(),
                                TelemetryJson.response(
                                        event("attestReplay"),
                                        jsonFrame,
                                        parent.buildAttestationReplayResponse(result, baseFields)),
                                true);
                        return CommandLine.ExitCode.USAGE;
                    }
                    TelemetryFrame successFrame =
                            new TelemetryFrame(frame.event(), "success", frame.sanitized(), frame.fields());
                    writeFrame(out(), event("attestReplay"), successFrame);
                    return CommandLine.ExitCode.USAGE;
                }
                case ERROR -> {
                    if (outputJson()) {
                        JsonPrinter.print(
                                out(),
                                TelemetryJson.response(
                                        event("attestReplay"),
                                        frame,
                                        parent.buildAttestationReplayResponse(result, baseFields)),
                                true);
                        return CommandLine.ExitCode.SOFTWARE;
                    }
                    writeFrame(err(), event("attestReplay"), frame);
                    return CommandLine.ExitCode.SOFTWARE;
                }
            }
            return CommandLine.ExitCode.SOFTWARE;
        }

        private static InputSource parseInputSource(String value) {
            if (value == null || value.isBlank()) {
                return InputSource.INLINE;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "inline" -> InputSource.INLINE;
                case "stored" -> InputSource.STORED;
                default ->
                    throw new IllegalArgumentException(
                            "Unsupported input source: " + value + " (expected inline or stored)");
            };
        }

        private static String mapStoredReplayReason(String message) {
            if (message == null || message.isBlank()) {
                return "replay_failed";
            }
            String normalized = message.toLowerCase(Locale.US);
            if (normalized.contains("not found")) {
                return "stored_credential_not_found";
            }
            if (normalized.contains("attestation metadata")) {
                return "stored_attestation_required";
            }
            if (normalized.contains("missing required attribute")) {
                return "stored_attestation_missing_attribute";
            }
            if (normalized.contains("base64")) {
                return "stored_attestation_invalid";
            }
            if (normalized.contains("credential store")) {
                return "stored_attestation_required";
            }
            return "replay_failed";
        }

        private enum InputSource {
            INLINE,
            STORED
        }
    }

    @CommandLine.Command(
            name = "seed-attestations",
            description = "Seed curated stored WebAuthn attestation credentials into the credential store.")
    static final class SeedAttestationsCommand extends AbstractFido2Command {

        private final WebAuthnAttestationSeedService seedService = new WebAuthnAttestationSeedService();

        @Override
        public Integer call() {
            List<WebAuthnAttestationVector> vectors = selectCanonicalVectors();
            List<SeedCommand> commands;
            try {
                commands = buildSeedCommands(vectors);
            } catch (RuntimeException ex) {
                return failUnexpected(
                        event("seed-attestations"),
                        Map.of(),
                        "Unable to prepare attestation seeds: " + sanitizeMessage(ex.getMessage()));
            }

            try (CredentialStore store = parent.openStore()) {
                SeedResult result = seedService.seed(commands, store);
                if (outputJson()) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("addedCount", result.addedCount());
                    data.put("addedCredentialIds", result.addedCredentialIds());
                    TelemetryFrame frame =
                            ATTEST_TELEMETRY.status("success", nextTelemetryId(), "success", true, null, Map.of());
                    JsonPrinter.print(out(), TelemetryJson.response(event("seed-attestations"), frame, data), true);
                    return CommandLine.ExitCode.OK;
                }
                if (result.addedCount() == 0) {
                    parent.out().println("All stored attestation credentials are already present.");
                } else {
                    parent.out()
                            .println("Seeded stored attestation credentials (" + result.addedCount() + "): "
                                    + String.join(", ", result.addedCredentialIds()));
                }
                return CommandLine.ExitCode.OK;
            } catch (Exception ex) {
                return failUnexpected(
                        event("seed-attestations"),
                        Map.of(),
                        "Seeding stored credentials failed: " + sanitizeMessage(ex.getMessage()));
            }
        }

        private static List<WebAuthnAttestationVector> selectCanonicalVectors() {
            WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
            Set<WebAuthnSignatureAlgorithm> algorithms = new LinkedHashSet<>();
            List<WebAuthnAttestationVector> selected = new ArrayList<>();
            for (WebAuthnAttestationVector vector : WebAuthnAttestationSamples.vectors()) {
                if (algorithms.add(vector.algorithm())) {
                    Optional<WebAuthnFixtures.WebAuthnFixture> fixture = findFixture(vector);
                    if (fixture.isEmpty() && resolveGeneratorSample(vector).isEmpty()) {
                        algorithms.remove(vector.algorithm());
                        continue;
                    }
                    if (!isInlineCompatible(generator, vector)) {
                        algorithms.remove(vector.algorithm());
                        continue;
                    }
                    selected.add(vector);
                }
            }
            return List.copyOf(selected);
        }

        private static List<SeedCommand> buildSeedCommands(List<WebAuthnAttestationVector> vectors) {
            WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
            List<SeedCommand> commands = new ArrayList<>(vectors.size());
            for (WebAuthnAttestationVector vector : vectors) {
                Optional<WebAuthnFixtures.WebAuthnFixture> fixture = findFixture(vector);
                WebAuthnCredentialDescriptor credentialDescriptor;
                if (fixture.isPresent()) {
                    WebAuthnFixtures.WebAuthnFixture resolved = fixture.get();
                    credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                            .name(vector.vectorId())
                            .relyingPartyId(resolved.storedCredential().relyingPartyId())
                            .credentialId(resolved.storedCredential().credentialId())
                            .publicKeyCose(resolved.storedCredential().publicKeyCose())
                            .signatureCounter(resolved.storedCredential().signatureCounter())
                            .userVerificationRequired(
                                    resolved.storedCredential().userVerificationRequired())
                            .algorithm(resolved.algorithm())
                            .build();
                } else {
                    Sample sample = resolveGeneratorSample(vector)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Missing fixture or generator sample for " + vector.vectorId()));
                    credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                            .name(vector.vectorId())
                            .relyingPartyId(sample.relyingPartyId())
                            .credentialId(sample.credentialId())
                            .publicKeyCose(sample.publicKeyCose())
                            .signatureCounter(sample.signatureCounter())
                            .userVerificationRequired(sample.userVerificationRequired())
                            .algorithm(sample.algorithm())
                            .build();
                }

                WebAuthnAttestationGenerator.GenerationResult generationResult =
                        generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                                vector.vectorId(),
                                vector.format(),
                                vector.relyingPartyId(),
                                vector.origin(),
                                vector.registration().challenge(),
                                vector.keyMaterial().credentialPrivateKeyBase64Url(),
                                vector.keyMaterial().attestationPrivateKeyBase64Url(),
                                vector.keyMaterial().attestationCertificateSerialBase64Url(),
                                WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED,
                                List.of()));

                WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                        .name(vector.vectorId())
                        .format(vector.format())
                        .signingMode(WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED)
                        .credentialDescriptor(credentialDescriptor)
                        .relyingPartyId(vector.relyingPartyId())
                        .origin(vector.origin())
                        .attestationId(vector.vectorId())
                        .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                        .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                        .attestationCertificateSerialBase64Url(
                                vector.keyMaterial().attestationCertificateSerialBase64Url())
                        .certificateChainPem(generationResult.certificateChainPem())
                        .customRootCertificatesPem(List.of())
                        .build();

                commands.add(new SeedCommand(
                        descriptor,
                        generationResult.attestationObject(),
                        generationResult.clientDataJson(),
                        vector.registration().challenge()));
            }
            return List.copyOf(commands);
        }

        private static Optional<WebAuthnFixtures.WebAuthnFixture> findFixture(WebAuthnAttestationVector vector) {
            Optional<WebAuthnFixtures.WebAuthnFixture> exact = WebAuthnFixtures.w3cFixtures().stream()
                    .filter(candidate -> candidate.id().equals(vector.vectorId()))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
            return WebAuthnFixtures.w3cFixtures().stream()
                    .filter(candidate -> candidate.algorithm().equals(vector.algorithm()))
                    .findFirst();
        }

        private static Optional<Sample> resolveGeneratorSample(WebAuthnAttestationVector vector) {
            return WebAuthnGeneratorSamples.samples().stream()
                    .filter(sample -> Arrays.equals(
                            sample.credentialId(), vector.registration().credentialId()))
                    .findFirst()
                    .or(() -> WebAuthnGeneratorSamples.samples().stream()
                            .filter(sample -> sample.algorithm() == vector.algorithm())
                            .findFirst());
        }

        private static boolean isInlineCompatible(
                WebAuthnAttestationGenerator generator, WebAuthnAttestationVector vector) {
            try {
                generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().challenge(),
                        vector.keyMaterial().credentialPrivateKeyBase64Url(),
                        vector.keyMaterial().attestationPrivateKeyBase64Url(),
                        vector.keyMaterial().attestationCertificateSerialBase64Url(),
                        WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED,
                        List.of()));
                return true;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
    }

    @CommandLine.Command(name = "vectors", description = "List available WebAuthn JSON bundle sample vectors.")
    static final class VectorsCommand extends AbstractFido2Command {

        @Override
        public Integer call() {
            List<WebAuthnJsonVector> vectors = parent.jsonVectorList();
            List<WebAuthnAttestationVector> attestationVectors = parent.attestationVectorList();

            if (outputJson()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put(
                        "vectors",
                        vectors.stream()
                                .map(vector -> Map.of(
                                        "vectorId", vector.vectorId(),
                                        "algorithm", vector.algorithm().label(),
                                        "uvRequired", vector.storedCredential().userVerificationRequired(),
                                        "relyingPartyId",
                                                vector.storedCredential().relyingPartyId(),
                                        "origin", vector.assertionRequest().origin()))
                                .toList());
                data.put(
                        "attestationVectors",
                        attestationVectors.stream()
                                .map(vector -> Map.of(
                                        "attestationId", vector.vectorId(),
                                        "format", vector.format().label(),
                                        "algorithm", vector.algorithm().label(),
                                        "relyingPartyId", vector.relyingPartyId(),
                                        "origin", vector.origin(),
                                        "section", vector.w3cSection()))
                                .toList());
                data.put("count", vectors.size());
                data.put("attestationCount", attestationVectors.size());
                TelemetryFrame frame =
                        VECTORS_TELEMETRY.status("success", nextTelemetryId(), "success", true, null, Map.of());
                JsonPrinter.print(out(), TelemetryJson.response(event("vectors"), frame, data), true);
                return CommandLine.ExitCode.OK;
            }

            parent.printVectorSummaries(out());
            return CommandLine.ExitCode.OK;
        }
    }

    private abstract static class AbstractFido2Command implements java.util.concurrent.Callable<Integer> {

        @CommandLine.ParentCommand
        Fido2Cli parent;

        protected PrintWriter out() {
            return parent.out();
        }

        protected PrintWriter err() {
            return parent.err();
        }

        protected Path databasePath() {
            return parent.databasePath();
        }

        protected boolean outputJson() {
            return parent.outputJson();
        }

        protected int failValidation(String event, String reasonCode, String message, Map<String, Object> fields) {
            return parent.failValidation(event, reasonCode, message, fields, parent.outputJson());
        }

        protected int failValidation(
                String event,
                Fido2TelemetryAdapter adapter,
                String reasonCode,
                String message,
                Map<String, Object> fields) {
            return parent.failValidation(event, adapter, reasonCode, message, fields, parent.outputJson());
        }

        protected int failValidation(String event, TelemetrySignal signal, Map<String, Object> fields) {
            return parent.failValidation(event, signal, fields, parent.outputJson());
        }

        protected int failUnexpected(String event, Map<String, Object> fields, String message) {
            return parent.failUnexpected(event, fields, message, parent.outputJson());
        }

        protected int failUnexpected(
                String event, Fido2TelemetryAdapter adapter, Map<String, Object> fields, String message) {
            return parent.failUnexpected(event, adapter, fields, message, parent.outputJson());
        }
    }
}
