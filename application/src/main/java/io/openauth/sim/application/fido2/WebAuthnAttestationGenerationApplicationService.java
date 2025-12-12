package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.CborDecoder;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnAuthenticatorDataParser;
import io.openauth.sim.core.fido2.WebAuthnAuthenticatorDataParser.ParsedAuthenticatorData;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnRelyingPartyId;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Application service that generates WebAuthn attestation payloads with sanitized telemetry. */
public final class WebAuthnAttestationGenerationApplicationService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String ATTR_ATTESTATION_ENABLED_KEY = "fido2.attestation.enabled";

    private final WebAuthnAttestationGenerator generator;
    private final Fido2TelemetryAdapter telemetryAdapter;
    private final CredentialStore credentialStore;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;

    public WebAuthnAttestationGenerationApplicationService(
            WebAuthnAttestationGenerator generator,
            Fido2TelemetryAdapter telemetryAdapter,
            CredentialStore credentialStore,
            WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
        if ((credentialStore == null) != (persistenceAdapter == null)) {
            throw new IllegalArgumentException(
                    "credentialStore and persistenceAdapter must both be provided or both be null");
        }
        this.credentialStore = credentialStore;
        this.persistenceAdapter = persistenceAdapter;
    }

    public WebAuthnAttestationGenerationApplicationService() {
        this(new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"), null, null);
    }

    public WebAuthnAttestationGenerationApplicationService(
            WebAuthnAttestationGenerator generator, Fido2TelemetryAdapter telemetryAdapter) {
        this(generator, telemetryAdapter, null, null);
    }

    /** Convenience factory for facades that need stored attestation generation without core instantiation. */
    public static WebAuthnAttestationGenerationApplicationService usingDefaults(
            CredentialStore credentialStore, Fido2TelemetryAdapter telemetryAdapter) {
        return new WebAuthnAttestationGenerationApplicationService(
                new WebAuthnAttestationGenerator(),
                telemetryAdapter,
                credentialStore,
                new WebAuthnCredentialPersistenceAdapter());
    }

    public GenerationResult generate(GenerationCommand command) {
        return generate(command, false);
    }

    public GenerationResult generate(GenerationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.attestation.generate");
        metadata(trace, "inputSource", command.inputSource().name().toLowerCase(Locale.ROOT));
        metadata(trace, "format", command.format().label());
        metadata(trace, "attestationId", command.attestationId());
        metadata(trace, "relyingPartyId", command.relyingPartyId());
        WebAuthnAttestationGenerator.GenerationResult generatorResult;
        boolean manualSource;
        GenerationCommand telemetryCommand = command;
        WebAuthnAttestationCredentialDescriptor storedDescriptor = null;

        GenerationCommand.InputSource inputSource = command.inputSource();
        String telemetryAttestationId = command.attestationId();
        WebAuthnAttestationFormat telemetryFormat = command.format();
        SigningMode telemetrySigningMode = command.signingMode();
        List<String> telemetryCustomRoots = command.customRootCertificatesPem();
        String telemetryCustomRootSource = command.customRootSource();
        String telemetrySeedPresetId = "";
        List<String> telemetryOverrides = List.of();
        String telemetryStoredCredentialId = null;

        String effectiveRelyingPartyId = command.relyingPartyId();
        String effectiveOrigin = command.origin();
        byte[] effectiveChallenge = command.challenge().clone();
        WebAuthnSignatureAlgorithm traceAlgorithm = null;
        String traceAttestationPrivateKey = command.attestationPrivateKeyBase64Url();

        if (command instanceof GenerationCommand.Stored stored) {
            metadata(trace, "mode", "stored");
            if (credentialStore == null || persistenceAdapter == null) {
                throw new IllegalStateException("Stored attestation generation requires a credential store");
            }

            Credential credential = credentialStore
                    .findByName(stored.credentialName())
                    .orElseThrow(() -> new IllegalArgumentException("Stored credential not found"));
            var record = VersionedCredentialRecordMapper.toRecord(credential);
            if (!"true".equals(record.attributes().get(ATTR_ATTESTATION_ENABLED_KEY))) {
                throw new IllegalArgumentException("Stored credential does not contain attestation metadata");
            }

            WebAuthnAttestationCredentialDescriptor descriptor = persistenceAdapter.deserializeAttestation(record);

            storedDescriptor = descriptor;

            telemetryStoredCredentialId = descriptor.name();
            telemetryAttestationId = descriptor.attestationId();
            telemetryFormat = descriptor.format();
            telemetrySigningMode = descriptor.signingMode();
            telemetryCustomRoots = descriptor.customRootCertificatesPem();
            telemetryCustomRootSource = "stored";
            inputSource = GenerationCommand.InputSource.STORED;

            String descriptorRelyingParty = descriptor.credentialDescriptor().relyingPartyId();
            if (stored.relyingPartyId() != null && !stored.relyingPartyId().isBlank()) {
                String requested = stored.relyingPartyId().trim();
                if (!descriptorRelyingParty.equals(requested)) {
                    throw new IllegalArgumentException("Stored credential relying party mismatch");
                }
                effectiveRelyingPartyId = requested;
            } else {
                effectiveRelyingPartyId = descriptorRelyingParty;
            }

            if (stored.origin() == null || stored.origin().isBlank()) {
                effectiveOrigin = descriptor.origin();
            } else {
                effectiveOrigin = stored.origin().trim();
            }

            if (effectiveChallenge.length == 0) {
                throw new IllegalArgumentException("challenge must not be empty for stored attestation");
            }

            String credentialKey = descriptor.credentialPrivateKeyBase64Url();
            String attestationKey = descriptor.attestationPrivateKeyBase64Url();
            if (attestationKey == null) {
                attestationKey = "";
            }
            String certificateSerial = descriptor.attestationCertificateSerialBase64Url();
            if (certificateSerial == null) {
                certificateSerial = "";
            }

            WebAuthnAttestationGenerator.GenerationCommand.Inline coreCommand =
                    new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                            descriptor.attestationId(),
                            descriptor.format(),
                            effectiveRelyingPartyId,
                            effectiveOrigin,
                            effectiveChallenge,
                            credentialKey,
                            attestationKey,
                            certificateSerial,
                            descriptor.signingMode(),
                            descriptor.customRootCertificatesPem());

            generatorResult = generator.generate(coreCommand);
            manualSource = false;

            telemetryCommand = new StoredTelemetryCommand(
                    telemetryAttestationId,
                    telemetryFormat,
                    effectiveRelyingPartyId,
                    effectiveOrigin,
                    effectiveChallenge,
                    telemetrySigningMode,
                    telemetryCustomRoots,
                    telemetryCustomRootSource,
                    inputSource);
            traceAlgorithm = descriptor.credentialDescriptor().algorithm();
            traceAttestationPrivateKey = stored.attestationPrivateKeyBase64Url();
        } else if (command instanceof GenerationCommand.Inline inline) {
            metadata(trace, "mode", "preset");
            manualSource = false;
            WebAuthnAttestationVector vector = WebAuthnAttestationSamples.require(inline.attestationId());
            WebAuthnSignatureAlgorithm algorithm = vector.algorithm();
            WebAuthnSignatureAlgorithm attestationAlgorithm =
                    vector.keyMaterial().attestationPrivateKeyAlgorithm() == null
                            ? algorithm
                            : vector.keyMaterial().attestationPrivateKeyAlgorithm();
            WebAuthnPrivateKeyParser.ParsedKey credentialKey;
            WebAuthnPrivateKeyParser.ParsedKey attestationKey;
            try {
                credentialKey = WebAuthnPrivateKeyParser.parse(inline.credentialPrivateKeyBase64Url(), algorithm);

                String attestationKeyInput = inline.attestationPrivateKeyBase64Url();
                attestationKey = attestationKeyInput == null
                                || attestationKeyInput.trim().isEmpty()
                        ? null
                        : WebAuthnPrivateKeyParser.parse(attestationKeyInput, attestationAlgorithm);
            } catch (GeneralSecurityException ex) {
                throw new IllegalArgumentException("Unable to parse attestation key material", ex);
            }

            WebAuthnAttestationGenerator.GenerationCommand.Inline coreCommand =
                    new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                            inline.attestationId(),
                            inline.format(),
                            inline.relyingPartyId(),
                            inline.origin(),
                            inline.challenge(),
                            credentialKey.canonicalBase64Url(),
                            attestationKey == null ? null : attestationKey.canonicalBase64Url(),
                            inline.attestationCertificateSerialBase64Url(),
                            inline.signingMode(),
                            inline.customRootCertificatesPem());
            generatorResult = generator.generate(coreCommand);
            effectiveRelyingPartyId = inline.relyingPartyId();
            effectiveOrigin = inline.origin();
            effectiveChallenge = inline.challenge().clone();
            traceAlgorithm = algorithm;
            traceAttestationPrivateKey = inline.attestationPrivateKeyBase64Url();
        } else {
            manualSource = true;
            GenerationCommand.Manual manual = (GenerationCommand.Manual) command;
            metadata(trace, "mode", "manual");
            WebAuthnPrivateKeyParser.ParsedKey credentialKey;
            try {
                WebAuthnSignatureAlgorithm inferredAlgorithm =
                        WebAuthnPrivateKeyParser.inferAlgorithm(manual.credentialPrivateKeyBase64Url());
                credentialKey =
                        WebAuthnPrivateKeyParser.parse(manual.credentialPrivateKeyBase64Url(), inferredAlgorithm);
            } catch (GeneralSecurityException ex) {
                throw new IllegalArgumentException("Unable to parse credential private key", ex);
            }

            String attestationCanonical = null;
            String attestationInput = manual.attestationPrivateKeyBase64Url();
            if (attestationInput != null && !attestationInput.trim().isEmpty()) {
                try {
                    WebAuthnSignatureAlgorithm attestationAlgorithm =
                            WebAuthnPrivateKeyParser.inferAlgorithm(attestationInput);
                    WebAuthnPrivateKeyParser.ParsedKey attestationKey =
                            WebAuthnPrivateKeyParser.parse(attestationInput, attestationAlgorithm);
                    attestationCanonical = attestationKey.canonicalBase64Url();
                } catch (GeneralSecurityException ex) {
                    throw new IllegalArgumentException("Unable to parse attestation private key", ex);
                }
            }
            WebAuthnAttestationGenerator.GenerationCommand.Manual coreCommand =
                    new WebAuthnAttestationGenerator.GenerationCommand.Manual(
                            manual.format(),
                            manual.relyingPartyId(),
                            manual.origin(),
                            manual.challenge(),
                            credentialKey.canonicalBase64Url(),
                            attestationCanonical,
                            manual.attestationCertificateSerialBase64Url(),
                            manual.signingMode(),
                            manual.customRootCertificatesPem());
            generatorResult = generator.generate(coreCommand);
            telemetrySeedPresetId = normalize(manual.seedPresetId());
            telemetryOverrides = manual.overrides() == null ? List.of() : List.copyOf(manual.overrides());
            effectiveRelyingPartyId = manual.relyingPartyId();
            effectiveOrigin = manual.origin();
            effectiveChallenge = manual.challenge().clone();
            traceAttestationPrivateKey = manual.attestationPrivateKeyBase64Url();
            try {
                traceAlgorithm = WebAuthnPrivateKeyParser.inferAlgorithm(manual.credentialPrivateKeyBase64Url());
            } catch (GeneralSecurityException ex) {
                traceAlgorithm = null;
            }
        }

        if (trace != null) {
            metadata(trace, "signingMode", telemetrySigningMode.name().toLowerCase(Locale.ROOT));
            metadata(trace, "signatureIncluded", Boolean.toString(generatorResult.signatureIncluded()));
            populateAttestationTrace(
                    trace,
                    effectiveRelyingPartyId,
                    effectiveOrigin,
                    generatorResult,
                    traceAlgorithm,
                    telemetrySigningMode,
                    traceAttestationPrivateKey);
        }

        GeneratedAttestation.Response responsePayload = new GeneratedAttestation.Response(
                encode(generatorResult.clientDataJson()), encode(generatorResult.attestationObject()));

        String credentialIdBase64 = encode(generatorResult.credentialId());

        List<String> certificateChain = generatorResult.certificateChainPem() == null
                ? List.of()
                : List.copyOf(generatorResult.certificateChainPem());
        if (storedDescriptor != null && !storedDescriptor.certificateChainPem().isEmpty()) {
            certificateChain = List.copyOf(storedDescriptor.certificateChainPem());
        }
        int certificateChainCount = certificateChain.size();

        GeneratedAttestation attestation = new GeneratedAttestation(
                "public-key",
                credentialIdBase64,
                credentialIdBase64,
                telemetryAttestationId,
                telemetryFormat,
                responsePayload);

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("attestationId", telemetryAttestationId);
        telemetryFields.put("attestationFormat", telemetryFormat.label());
        telemetryFields.put("generationMode", telemetryMode(telemetrySigningMode));
        telemetryFields.put("signatureIncluded", generatorResult.signatureIncluded());
        telemetryFields.put("customRootCount", telemetryCustomRoots.size());
        telemetryFields.put("certificateChainCount", certificateChainCount);
        telemetryFields.put("inputSource", inputSourceLabel(inputSource));
        if (telemetryStoredCredentialId != null) {
            telemetryFields.put("storedCredentialId", telemetryStoredCredentialId);
        }

        TelemetryObservations observations = observeTelemetry(telemetryCommand, generatorResult);
        if (!observations.relyingPartyId().isBlank()) {
            telemetryFields.put("relyingPartyId", observations.relyingPartyId());
        }
        if (!observations.aaguid().isBlank()) {
            telemetryFields.put("aaguid", observations.aaguid());
        }
        if (!observations.certificateFingerprint().isBlank()) {
            telemetryFields.put("certificateFingerprint", observations.certificateFingerprint());
        }

        if (!telemetryCustomRoots.isEmpty()) {
            String source = normalize(telemetryCustomRootSource);
            telemetryFields.put("customRootSource", source.isBlank() ? "inline" : source);
        }

        if (manualSource) {
            if (!telemetrySeedPresetId.isBlank()) {
                telemetryFields.put("seedPresetId", telemetrySeedPresetId);
            }
            if (!telemetryOverrides.isEmpty()) {
                telemetryFields.put("overrides", telemetryOverrides);
            }
        }

        TelemetrySignal telemetry = new SuccessTelemetrySignal(Map.copyOf(telemetryFields), telemetryAdapter);

        VerboseTrace builtTrace = buildTrace(trace);
        return new GenerationResult(attestation, telemetry, certificateChain, Optional.ofNullable(builtTrace));
    }

    /** Marker interface for generation commands. */
    public sealed interface GenerationCommand
            permits GenerationCommand.Inline,
                    GenerationCommand.Manual,
                    GenerationCommand.Stored,
                    StoredTelemetryCommand {
        String attestationId();

        WebAuthnAttestationFormat format();

        String relyingPartyId();

        String origin();

        byte[] challenge();

        String credentialPrivateKeyBase64Url();

        String attestationPrivateKeyBase64Url();

        String attestationCertificateSerialBase64Url();

        SigningMode signingMode();

        List<String> customRootCertificatesPem();

        String customRootSource();

        InputSource inputSource();

        /** Origin of the input parameters for attestation generation. */
        enum InputSource {
            PRESET,
            MANUAL,
            STORED
        }

        /** Inline command carrier. */
        record Inline(
                String attestationId,
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] challenge,
                String credentialPrivateKeyBase64Url,
                String attestationPrivateKeyBase64Url,
                String attestationCertificateSerialBase64Url,
                SigningMode signingMode,
                List<String> customRootCertificatesPem,
                String customRootSource,
                InputSource inputSource)
                implements GenerationCommand {

            public Inline {
                Objects.requireNonNull(attestationId, "attestationId");
                Objects.requireNonNull(format, "format");
                Objects.requireNonNull(relyingPartyId, "relyingPartyId");
                Objects.requireNonNull(origin, "origin");
                Objects.requireNonNull(challenge, "challenge");
                Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
                Objects.requireNonNull(signingMode, "signingMode");
                inputSource = inputSource == null ? InputSource.PRESET : inputSource;
                if (inputSource == InputSource.MANUAL) {
                    throw new IllegalArgumentException("Inline commands require PRESET input source");
                }
                customRootCertificatesPem =
                        customRootCertificatesPem == null ? List.of() : List.copyOf(customRootCertificatesPem);
                customRootSource = normalize(customRootSource);
            }
        }

        /** Manual command carrier. */
        record Manual(
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] challenge,
                String credentialPrivateKeyBase64Url,
                String attestationPrivateKeyBase64Url,
                String attestationCertificateSerialBase64Url,
                SigningMode signingMode,
                List<String> customRootCertificatesPem,
                String customRootSource,
                String seedPresetId,
                List<String> overrides)
                implements GenerationCommand {

            public Manual {
                Objects.requireNonNull(format, "format");
                Objects.requireNonNull(relyingPartyId, "relyingPartyId");
                Objects.requireNonNull(origin, "origin");
                Objects.requireNonNull(challenge, "challenge");
                Objects.requireNonNull(credentialPrivateKeyBase64Url, "credentialPrivateKeyBase64Url");
                Objects.requireNonNull(signingMode, "signingMode");
                customRootCertificatesPem =
                        customRootCertificatesPem == null ? List.of() : List.copyOf(customRootCertificatesPem);
                customRootSource = normalize(customRootSource);
                seedPresetId = normalize(seedPresetId);
                overrides = overrides == null ? List.of() : List.copyOf(overrides);
            }

            @Override
            public String attestationId() {
                return "manual";
            }

            @Override
            public InputSource inputSource() {
                return InputSource.MANUAL;
            }
        }

        /** Stored command carrier. */
        record Stored(
                String credentialName,
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] challenge)
                implements GenerationCommand {

            public Stored {
                credentialName =
                        Objects.requireNonNull(credentialName, "credentialName").trim();
                if (credentialName.isEmpty()) {
                    throw new IllegalArgumentException("credentialName must not be blank");
                }
                format = Objects.requireNonNull(format, "format");
                relyingPartyId = relyingPartyId == null ? "" : relyingPartyId.trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                challenge = challenge == null ? new byte[0] : challenge.clone();
            }

            @Override
            public String attestationId() {
                return credentialName;
            }

            @Override
            public WebAuthnAttestationFormat format() {
                return format;
            }

            @Override
            public String relyingPartyId() {
                return relyingPartyId;
            }

            @Override
            public String origin() {
                return origin;
            }

            @Override
            public byte[] challenge() {
                return challenge.clone();
            }

            @Override
            public String credentialPrivateKeyBase64Url() {
                return "";
            }

            @Override
            public String attestationPrivateKeyBase64Url() {
                return "";
            }

            @Override
            public String attestationCertificateSerialBase64Url() {
                return "";
            }

            @Override
            public SigningMode signingMode() {
                return SigningMode.SELF_SIGNED;
            }

            @Override
            public List<String> customRootCertificatesPem() {
                return List.of();
            }

            @Override
            public String customRootSource() {
                return "";
            }

            @Override
            public InputSource inputSource() {
                return InputSource.STORED;
            }
        }
    }

    /** Result payload for attestation generation requests. */
    public record GenerationResult(
            GeneratedAttestation attestation,
            TelemetrySignal telemetry,
            List<String> certificateChainPem,
            Optional<VerboseTrace> verboseTrace) {
        public GenerationResult {
            certificateChainPem = certificateChainPem == null ? List.of() : List.copyOf(certificateChainPem);
            verboseTrace = verboseTrace == null ? Optional.empty() : verboseTrace;
        }
    }

    private static final class StoredTelemetryCommand implements GenerationCommand {
        private final String attestationId;
        private final WebAuthnAttestationFormat format;
        private final String relyingPartyId;
        private final String origin;
        private final byte[] challenge;
        private final SigningMode signingMode;
        private final List<String> customRoots;
        private final String customRootSource;
        private final InputSource inputSource;

        StoredTelemetryCommand(
                String attestationId,
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] challenge,
                SigningMode signingMode,
                List<String> customRoots,
                String customRootSource,
                InputSource inputSource) {
            this.attestationId = attestationId;
            this.format = format;
            this.relyingPartyId = relyingPartyId;
            this.origin = origin;
            this.challenge = challenge.clone();
            this.signingMode = signingMode;
            this.customRoots = customRoots == null ? List.of() : List.copyOf(customRoots);
            this.customRootSource = customRootSource;
            this.inputSource = inputSource;
        }

        @Override
        public String attestationId() {
            return attestationId;
        }

        @Override
        public WebAuthnAttestationFormat format() {
            return format;
        }

        @Override
        public String relyingPartyId() {
            return relyingPartyId;
        }

        @Override
        public String origin() {
            return origin;
        }

        @Override
        public byte[] challenge() {
            return challenge.clone();
        }

        @Override
        public String credentialPrivateKeyBase64Url() {
            return "";
        }

        @Override
        public String attestationPrivateKeyBase64Url() {
            return "";
        }

        @Override
        public String attestationCertificateSerialBase64Url() {
            return "";
        }

        @Override
        public SigningMode signingMode() {
            return signingMode;
        }

        @Override
        public List<String> customRootCertificatesPem() {
            return customRoots;
        }

        @Override
        public String customRootSource() {
            return customRootSource;
        }

        @Override
        public InputSource inputSource() {
            return inputSource;
        }
    }

    /** Generated attestation payload details. */
    public record GeneratedAttestation(
            String type,
            String id,
            String rawId,
            String attestationId,
            WebAuthnAttestationFormat format,
            Response response) {

        /** Nested attestation response mirroring WebAuthn assertions. */
        public record Response(String clientDataJson, String attestationObject) {
            // Canonical response view for JSON mapping.
        }
    }

    /** Telemetry signal for attestation generation outcomes. */
    public interface TelemetrySignal {
        TelemetryStatus status();

        String reasonCode();

        Map<String, Object> fields();

        TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId);
    }

    /** Telemetry status indicator. */
    public enum TelemetryStatus {
        SUCCESS,
        ERROR
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static String telemetryMode(SigningMode signingMode) {
        return switch (signingMode) {
            case SELF_SIGNED -> "self_signed";
            case UNSIGNED -> "unsigned";
            case CUSTOM_ROOT -> "custom_root";
        };
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && hasText(key) && hasText(value)) {
            trace.withMetadata(key, value);
        }
    }

    private static void addStep(
            VerboseTrace.Builder trace, java.util.function.Consumer<VerboseTrace.TraceStep.Builder> configurer) {
        if (trace != null) {
            trace.addStep(configurer);
        }
    }

    private static VerboseTrace buildTrace(VerboseTrace.Builder trace) {
        return trace == null ? null : trace.build();
    }

    private static void populateAttestationTrace(
            VerboseTrace.Builder trace,
            String relyingPartyId,
            String origin,
            WebAuthnAttestationGenerator.GenerationResult generatorResult,
            WebAuthnSignatureAlgorithm algorithm,
            SigningMode signingMode,
            String attestationPrivateKeyRaw) {
        if (trace == null || generatorResult == null) {
            return;
        }
        byte[] clientDataJson = safeBytes(generatorResult.clientDataJson());
        byte[] expectedChallenge = safeBytes(generatorResult.expectedChallenge());
        addAttestationClientDataStep(trace, clientDataJson, expectedChallenge, origin);

        AttestationComponents components = extractAttestationComponents(generatorResult.attestationObject());
        addAttestationAuthenticatorDataStep(trace, relyingPartyId, components.authenticatorData());
        addSignatureBaseStep(trace, components.authenticatorData(), clientDataJson);
        addAttestationSignatureStep(
                trace,
                algorithm,
                signingMode,
                attestationPrivateKeyRaw,
                generatorResult.signatureIncluded(),
                components.signature());
        addComposeAttestationStep(trace, generatorResult.format(), generatorResult.attestationObject());
    }

    private static void addAttestationClientDataStep(
            VerboseTrace.Builder trace, byte[] clientDataJson, byte[] challenge, String origin) {
        if (trace == null) {
            return;
        }
        byte[] safeClientData = safeBytes(clientDataJson);
        String json = new String(safeClientData, StandardCharsets.UTF_8);
        Map<String, Object> parsed = parseJsonObject(json);
        String type = stringValue(parsed.get("type"));
        String resolvedOrigin = hasText(origin) ? origin : stringValue(parsed.get("origin"));
        boolean tokenBindingPresent =
                parsed.get("tokenBinding") instanceof Map<?, ?> tokenBinding && !tokenBinding.isEmpty();

        String challengeBase64 = base64Url(challenge);
        int computedLength;
        try {
            computedLength =
                    challengeBase64.isEmpty() ? 0 : Base64.getUrlDecoder().decode(challengeBase64).length;
        } catch (IllegalArgumentException ex) {
            computedLength = 0;
        }
        final int challengeLength = computedLength;

        addStep(trace, step -> step.id("build.clientData")
                .summary("Construct clientDataJSON payload")
                .detail("clientDataJSON")
                .spec("webauthn§7.1")
                .attribute("type", type)
                .attribute(AttributeType.BASE64URL, "challenge.b64u", challengeBase64)
                .attribute(AttributeType.INT, "challenge.decoded.len", challengeLength)
                .attribute("origin", resolvedOrigin)
                .attribute(AttributeType.JSON, "clientData.json", json)
                .attribute("clientData.sha256", sha256Digest(safeClientData))
                .attribute(AttributeType.BOOL, "tokenBinding.present", tokenBindingPresent));
    }

    private static Map<String, Object> parseJsonObject(String json) {
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            Object parsed = SimpleJson.parse(json);
            if (parsed instanceof Map<?, ?> raw) {
                Map<String, Object> result = new LinkedHashMap<>();
                raw.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        } catch (Exception ignored) {
            // ignore malformed JSON for trace purposes
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void addAttestationAuthenticatorDataStep(
            VerboseTrace.Builder trace, String relyingPartyId, byte[] authenticatorData) {
        if (trace == null) {
            return;
        }
        byte[] safeAuthData = safeBytes(authenticatorData);
        ParsedAuthenticatorData parsed = WebAuthnAuthenticatorDataParser.parse(safeAuthData);
        String calculatedCanonical;
        try {
            calculatedCanonical = WebAuthnRelyingPartyId.canonicalize(relyingPartyId);
        } catch (IllegalArgumentException ex) {
            calculatedCanonical = relyingPartyId == null ? "" : relyingPartyId.trim();
        }
        final String canonicalRpId = calculatedCanonical;
        byte[] expectedHashBytes = sha256(canonicalRpId.getBytes(StandardCharsets.UTF_8));
        String expectedHash = sha256Hex(expectedHashBytes);
        boolean hashesMatch = Arrays.equals(parsed.rpIdHash(), expectedHashBytes);

        addStep(trace, step -> {
            step.id("build.authenticatorData")
                    .summary("Construct authenticator data")
                    .detail("authenticatorData")
                    .spec("webauthn§6.5.4")
                    .attribute(AttributeType.INT, "authenticatorData.len.bytes", safeAuthData.length)
                    .attribute(AttributeType.HEX, "authenticatorData.hex", safeAuthData)
                    .attribute(AttributeType.HEX, "rpIdHash.hex", parsed.rpIdHash())
                    .attribute("rpId.canonical", canonicalRpId)
                    .attribute("rpIdHash.expected", expectedHash)
                    .attribute(AttributeType.BOOL, "rpIdHash.match", hashesMatch)
                    .attribute("flags.byte", formatByte(parsed.flags()))
                    .attribute(AttributeType.BOOL, "flags.bits.UP", (parsed.flags() & 0x01) != 0)
                    .attribute(AttributeType.BOOL, "flags.bits.UV", (parsed.flags() & 0x04) != 0)
                    .attribute(AttributeType.BOOL, "flags.bits.AT", (parsed.flags() & 0x40) != 0)
                    .attribute(AttributeType.BOOL, "flags.bits.ED", (parsed.flags() & 0x80) != 0)
                    .attribute(AttributeType.INT, "counter", parsed.counter());
        });
    }

    private static void addSignatureBaseStep(
            VerboseTrace.Builder trace, byte[] authenticatorData, byte[] clientDataJson) {
        if (trace == null) {
            return;
        }
        byte[] safeAuthData = safeBytes(authenticatorData);
        byte[] clientHash = sha256(clientDataJson);
        byte[] signedPayload = concat(safeAuthData, clientHash);

        addStep(trace, step -> step.id("build.signatureBase")
                .summary("Concatenate authenticator data and clientData hash")
                .detail("authenticatorData || SHA-256(clientData)")
                .attribute(AttributeType.INT, "authenticatorData.len.bytes", safeAuthData.length)
                .attribute(AttributeType.INT, "clientDataHash.len.bytes", clientHash.length)
                .attribute("clientDataHash.sha256", sha256Hex(clientHash))
                .attribute("signedBytes.sha256", sha256Digest(signedPayload))
                .attribute("signedBytes.preview", previewHex(signedPayload)));
    }

    private static void addAttestationSignatureStep(
            VerboseTrace.Builder trace,
            WebAuthnSignatureAlgorithm algorithm,
            SigningMode signingMode,
            String attestationPrivateKeyRaw,
            boolean signatureIncluded,
            byte[] signature) {
        if (trace == null) {
            return;
        }
        addStep(trace, step -> {
            step.id("generate.signature")
                    .summary("Sign attestation statement")
                    .detail("Attestation signing")
                    .attribute("signingMode", signingMode.name().toLowerCase(Locale.ROOT))
                    .attribute(AttributeType.BOOL, "signatureIncluded", signatureIncluded);
            if (algorithm != null) {
                step.attribute("alg", algorithm.name());
                step.attribute("alg.label", algorithm.label());
            }
            if (hasText(attestationPrivateKeyRaw)) {
                step.attribute(
                        "privateKey.sha256", sha256Digest(attestationPrivateKeyRaw.getBytes(StandardCharsets.UTF_8)));
            }
            step.attribute(AttributeType.INT, "signature.len.bytes", signature == null ? 0 : signature.length);
        });
    }

    private static void addComposeAttestationStep(
            VerboseTrace.Builder trace, WebAuthnAttestationFormat format, byte[] attestationObject) {
        if (trace == null) {
            return;
        }
        byte[] safeObject = safeBytes(attestationObject);
        addStep(trace, step -> step.id("compose.attestationObject")
                .summary("Compose attestation object")
                .detail("CBOR encode attestation")
                .spec("webauthn§6.5.2")
                .attribute("fmt", format.label())
                .attribute(AttributeType.INT, "attObj.len.bytes", safeObject.length)
                .attribute("attObj.sha256", sha256Digest(safeObject)));
    }

    private static AttestationComponents extractAttestationComponents(byte[] attestationObject) {
        byte[] safeObject = safeBytes(attestationObject);
        if (safeObject.length == 0) {
            return AttestationComponents.empty();
        }
        try {
            Object decoded = CborDecoder.decode(safeObject);
            if (!(decoded instanceof Map<?, ?> rawMap)) {
                return AttestationComponents.empty();
            }
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));
            byte[] authData = toByteArray(map.get("authData"));
            Map<String, Object> attStmt = toMap(map.get("attStmt"));
            byte[] signature = toByteArray(attStmt.get("sig"));
            return new AttestationComponents(authData, signature);
        } catch (GeneralSecurityException ex) {
            return AttestationComponents.empty();
        }
    }

    private static byte[] toByteArray(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        return new byte[0];
    }

    private static Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = new LinkedHashMap<>();
            raw.forEach((key, entry) -> map.put(String.valueOf(key), entry));
            return map;
        }
        return Map.of();
    }

    private static byte[] safeBytes(byte[] input) {
        return input == null ? new byte[0] : input.clone();
    }

    private static String base64Url(byte[] input) {
        byte[] safe = safeBytes(input);
        if (safe.length == 0) {
            return "";
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(safe);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input == null ? new byte[0] : input.clone());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sha256Digest(byte[] input) {
        return "sha256:" + hex(sha256(input));
    }

    private static String sha256Hex(byte[] digestBytes) {
        return "sha256:" + hex(safeBytes(digestBytes));
    }

    private static String previewHex(byte[] bytes) {
        byte[] safe = safeBytes(bytes);
        if (safe.length <= 32) {
            return hex(safe);
        }
        int preview = Math.min(16, safe.length);
        byte[] head = Arrays.copyOfRange(safe, 0, preview);
        byte[] tail = Arrays.copyOfRange(safe, safe.length - preview, safe.length);
        return hex(head) + "…" + hex(tail);
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] safeLeft = safeBytes(left);
        byte[] safeRight = safeBytes(right);
        byte[] combined = Arrays.copyOf(safeLeft, safeLeft.length + safeRight.length);
        System.arraycopy(safeRight, 0, combined, safeLeft.length, safeRight.length);
        return combined;
    }

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record AttestationComponents(byte[] authenticatorData, byte[] signature) {
        private static AttestationComponents empty() {
            return new AttestationComponents(new byte[0], new byte[0]);
        }

        public byte[] authenticatorData() {
            return safeBytes(authenticatorData);
        }

        public byte[] signature() {
            return safeBytes(signature);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String inputSourceLabel(GenerationCommand.InputSource inputSource) {
        return switch (inputSource) {
            case PRESET -> "preset";
            case MANUAL -> "manual";
            case STORED -> "stored";
        };
    }

    private static TelemetryObservations observeTelemetry(
            GenerationCommand command, WebAuthnAttestationGenerator.GenerationResult generatorResult) {
        String relyingPartyId = normalize(command.relyingPartyId());
        try {
            WebAuthnAttestationVerifier verifier = new WebAuthnAttestationVerifier();
            WebAuthnAttestationVerification verification = verifier.verify(new WebAuthnAttestationRequest(
                    generatorResult.format(),
                    generatorResult.attestationObject().clone(),
                    generatorResult.clientDataJson().clone(),
                    command.challenge().clone(),
                    command.relyingPartyId(),
                    command.origin()));
            if (!verification.result().success()) {
                return new TelemetryObservations(relyingPartyId, "", "");
            }
            String aaguid = formatAaguid(verification.aaguid());
            List<X509Certificate> chain = verification.certificateChain();
            String certificateFingerprint = chain == null || chain.isEmpty() ? "" : fingerprint(chain.get(0));
            return new TelemetryObservations(relyingPartyId, aaguid, certificateFingerprint);
        } catch (RuntimeException ex) {
            return new TelemetryObservations(relyingPartyId, "", "");
        }
    }

    private static String formatAaguid(byte[] aaguid) {
        if (aaguid == null || aaguid.length != 16) {
            return "";
        }
        String hex = hex(aaguid);
        return "%s-%s-%s-%s-%s"
                .formatted(
                        hex.substring(0, 8),
                        hex.substring(8, 12),
                        hex.substring(12, 16),
                        hex.substring(16, 20),
                        hex.substring(20));
    }

    private static String fingerprint(X509Certificate certificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(certificate.getEncoded());
            return hex(hash);
        } catch (CertificateEncodingException | NoSuchAlgorithmException ex) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", value));
        }
        return builder.toString();
    }

    private record TelemetryObservations(String relyingPartyId, String aaguid, String certificateFingerprint) {
        // Value carrier capturing derived telemetry attributes.
    }

    private static final class SuccessTelemetrySignal implements TelemetrySignal {

        private final Map<String, Object> fields;
        private final Fido2TelemetryAdapter fallbackAdapter;

        private SuccessTelemetrySignal(Map<String, Object> fields, Fido2TelemetryAdapter fallbackAdapter) {
            this.fields = fields;
            this.fallbackAdapter = fallbackAdapter;
        }

        @Override
        public TelemetryStatus status() {
            return TelemetryStatus.SUCCESS;
        }

        @Override
        public String reasonCode() {
            return "generated";
        }

        @Override
        public Map<String, Object> fields() {
            return fields;
        }

        @Override
        public TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId) {
            Fido2TelemetryAdapter target = adapter != null ? adapter : fallbackAdapter;
            return target.status("success", telemetryId, reasonCode(), true, null, fields);
        }
    }
}
