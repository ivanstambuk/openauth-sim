package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator.SigningMode;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
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

    private final WebAuthnAttestationGenerator generator;
    private final Fido2TelemetryAdapter telemetryAdapter;

    public WebAuthnAttestationGenerationApplicationService(
            WebAuthnAttestationGenerator generator, Fido2TelemetryAdapter telemetryAdapter) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
    }

    public WebAuthnAttestationGenerationApplicationService() {
        this(new WebAuthnAttestationGenerator(), new Fido2TelemetryAdapter("fido2.attest"));
    }

    public GenerationResult generate(GenerationCommand command) {
        Objects.requireNonNull(command, "command");
        WebAuthnAttestationGenerator.GenerationResult generatorResult;
        boolean manualSource;
        if (command instanceof GenerationCommand.Inline inline) {
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
        }

        GeneratedAttestation.Response responsePayload = new GeneratedAttestation.Response(
                encode(generatorResult.clientDataJson()), encode(generatorResult.attestationObject()));

        String credentialIdBase64 = encode(generatorResult.credentialId());

        List<String> certificateChain = generatorResult.certificateChainPem() == null
                ? List.of()
                : List.copyOf(generatorResult.certificateChainPem());

        GeneratedAttestation attestation = new GeneratedAttestation(
                "public-key",
                credentialIdBase64,
                credentialIdBase64,
                generatorResult.attestationId(),
                generatorResult.format(),
                responsePayload);

        Map<String, Object> telemetryFields = new LinkedHashMap<>();
        telemetryFields.put("attestationId", command.attestationId());
        telemetryFields.put("attestationFormat", command.format().label());
        telemetryFields.put("generationMode", telemetryMode(command.signingMode()));
        telemetryFields.put("signatureIncluded", generatorResult.signatureIncluded());
        telemetryFields.put(
                "customRootCount", command.customRootCertificatesPem().size());
        telemetryFields.put(
                "certificateChainCount", generatorResult.certificateChainPem().size());
        telemetryFields.put("inputSource", inputSourceLabel(command.inputSource()));

        TelemetryObservations observations = observeTelemetry(command, generatorResult);
        if (!observations.relyingPartyId().isBlank()) {
            telemetryFields.put("relyingPartyId", observations.relyingPartyId());
        }
        if (!observations.aaguid().isBlank()) {
            telemetryFields.put("aaguid", observations.aaguid());
        }
        if (!observations.certificateFingerprint().isBlank()) {
            telemetryFields.put("certificateFingerprint", observations.certificateFingerprint());
        }

        if (!command.customRootCertificatesPem().isEmpty()) {
            String source = normalize(command.customRootSource());
            telemetryFields.put("customRootSource", source.isBlank() ? "inline" : source);
        }

        if (manualSource) {
            GenerationCommand.Manual manual = (GenerationCommand.Manual) command;
            if (!normalize(manual.seedPresetId()).isBlank()) {
                telemetryFields.put("seedPresetId", normalize(manual.seedPresetId()));
            }
            if (manual.overrides() != null && !manual.overrides().isEmpty()) {
                telemetryFields.put("overrides", List.copyOf(manual.overrides()));
            }
        }

        TelemetrySignal telemetry = new SuccessTelemetrySignal(Map.copyOf(telemetryFields), telemetryAdapter);

        return new GenerationResult(attestation, telemetry, certificateChain);
    }

    /** Marker interface for generation commands. */
    public sealed interface GenerationCommand permits GenerationCommand.Inline, GenerationCommand.Manual {
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
            MANUAL
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
    }

    /** Result payload for attestation generation requests. */
    public record GenerationResult(
            GeneratedAttestation attestation, TelemetrySignal telemetry, List<String> certificateChainPem) {
        public GenerationResult {
            certificateChainPem = certificateChainPem == null ? List.of() : List.copyOf(certificateChainPem);
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
