package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public GenerationResult generate(GenerationCommand command) {
        Objects.requireNonNull(command, "command");
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

        if (command instanceof GenerationCommand.Stored stored) {
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
        } else if (command instanceof GenerationCommand.Inline inline) {
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
        } else {
            manualSource = true;
            GenerationCommand.Manual manual = (GenerationCommand.Manual) command;
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

        return new GenerationResult(attestation, telemetry, certificateChain);
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
            GeneratedAttestation attestation, TelemetrySignal telemetry, List<String> certificateChainPem) {
        public GenerationResult {
            certificateChainPem = certificateChainPem == null ? List.of() : List.copyOf(certificateChainPem);
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
