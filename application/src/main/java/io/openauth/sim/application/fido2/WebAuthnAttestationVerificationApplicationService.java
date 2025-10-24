package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.CborDecoder;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Application-layer coordinator for WebAuthn attestation verification telemetry. */
public final class WebAuthnAttestationVerificationApplicationService {

    private final WebAuthnAttestationVerifier verifier;
    private final Fido2TelemetryAdapter telemetryAdapter;
    private static final Pattern JSON_FIELD_PATTERN =
            Pattern.compile("\\\"(?<key>[^\\\"]+)\\\"\\s*:\\s*\\\"(?<value>[^\\\"]*)\\\"");
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    public WebAuthnAttestationVerificationApplicationService(
            WebAuthnAttestationVerifier verifier, Fido2TelemetryAdapter telemetryAdapter) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
    }

    public VerificationResult verify(VerificationCommand command) {
        return verify(command, false);
    }

    public VerificationResult verify(VerificationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        VerboseTrace.Builder trace = newTrace(verbose, "fido2.attestation.verify");
        metadata(trace, "protocol", "FIDO2");
        metadata(trace, "format", command.format().label());
        metadata(trace, "attestationId", command.attestationId());

        addStep(trace, step -> step.id("parse.request")
                .summary("Prepare attestation verification request")
                .detail("VerificationCommand")
                .attribute("relyingPartyId", command.relyingPartyId())
                .attribute("origin", command.origin())
                .attribute("trustAnchors", command.trustAnchors().size())
                .attribute("trustAnchorsCached", command.trustAnchorsCached())
                .attribute("trustAnchorSource", command.trustAnchorSource().name()));

        TraceClientData clientData = trace == null ? null : traceClientData(command.clientDataJson());
        AttestationComponents attestationComponents =
                trace == null ? AttestationComponents.empty() : traceAttestation(command.attestationObject());

        if (trace != null) {
            addParseClientDataStep(trace, clientData, command.expectedChallenge(), command.origin());
            addParseAuthenticatorDataStep(trace, attestationComponents, command.relyingPartyId());
        }

        WebAuthnAttestationServiceSupport.Outcome outcome = WebAuthnAttestationServiceSupport.process(
                verifier,
                command.format(),
                command.attestationId(),
                command.relyingPartyId(),
                command.origin(),
                command.attestationObject(),
                command.clientDataJson(),
                command.expectedChallenge(),
                command.trustAnchors(),
                command.trustAnchorSource(),
                command.trustAnchorsCached(),
                command.trustAnchorMetadataEntryId());

        TelemetrySignal telemetry = new TelemetrySignal(
                toTelemetryStatus(outcome.status()),
                outcome.reasonCode(),
                outcome.reason(),
                true,
                outcome.telemetryFields());

        Optional<AttestedCredential> attestedCredential = outcome.credential()
                .map(data -> new AttestedCredential(
                        data.relyingPartyId(),
                        data.credentialId(),
                        data.algorithm(),
                        data.userVerificationRequired(),
                        outcome.aaguid(),
                        data.signatureCounter()));

        if (trace != null) {
            addExtractAttestedCredentialStep(
                    trace, attestationComponents, attestedCredential, command.relyingPartyId());
            byte[] clientDataHash = clientData == null ? sha256(command.clientDataJson()) : clientData.hash();
            addSignatureBaseStep(trace, attestationComponents.authData(), clientDataHash);
            addVerifySignatureStep(
                    trace, resolveAlgorithm(attestedCredential, attestationComponents), outcome.success());
        }

        addStep(trace, step -> {
            step.id("verify.attestation")
                    .summary("Verify WebAuthn attestation object")
                    .detail("WebAuthnAttestationServiceSupport.process")
                    .spec("webauthn§7.2")
                    .attribute("status", outcome.status().name())
                    .attribute("valid", outcome.success());
            outcome.error().map(Enum::name).ifPresent(err -> step.note("error", err));
        });

        addValidateMetadataStep(trace, outcome, command.trustAnchors().size());

        addStep(trace, step -> {
            step.id("assemble.result")
                    .summary("Assemble attestation verification result")
                    .detail("VerificationResult")
                    .attribute("valid", outcome.success())
                    .attribute("anchorMode", outcome.anchorMode())
                    .attribute("attestedCredential", attestedCredential.isPresent());
        });

        return new VerificationResult(
                telemetry,
                outcome.success(),
                outcome.error(),
                attestedCredential,
                outcome.anchorProvided(),
                outcome.selfAttestedFallback(),
                outcome.anchorMode(),
                command.trustAnchorsCached(),
                command.trustAnchorWarnings(),
                buildTrace(trace));
    }

    public sealed interface VerificationCommand permits VerificationCommand.Inline {

        String attestationId();

        WebAuthnAttestationFormat format();

        String relyingPartyId();

        String origin();

        byte[] attestationObject();

        byte[] clientDataJson();

        byte[] expectedChallenge();

        List<X509Certificate> trustAnchors();

        boolean trustAnchorsCached();

        WebAuthnTrustAnchorResolver.Source trustAnchorSource();

        String trustAnchorMetadataEntryId();

        List<String> trustAnchorWarnings();

        record Inline(
                String attestationId,
                WebAuthnAttestationFormat format,
                String relyingPartyId,
                String origin,
                byte[] attestationObject,
                byte[] clientDataJson,
                byte[] expectedChallenge,
                List<X509Certificate> trustAnchors,
                boolean trustAnchorsCached,
                WebAuthnTrustAnchorResolver.Source trustAnchorSource,
                String trustAnchorMetadataEntryId,
                List<String> trustAnchorWarnings)
                implements VerificationCommand {

            public Inline {
                attestationId = sanitize(attestationId);
                format = Objects.requireNonNull(format, "format");
                relyingPartyId = sanitize(relyingPartyId);
                origin = sanitize(origin);
                attestationObject = attestationObject == null ? new byte[0] : attestationObject.clone();
                clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
                expectedChallenge = expectedChallenge == null ? new byte[0] : expectedChallenge.clone();
                trustAnchors = List.copyOf(trustAnchors == null ? List.of() : trustAnchors);
                trustAnchorSource = Objects.requireNonNull(trustAnchorSource, "trustAnchorSource");
                trustAnchorMetadataEntryId = trustAnchorMetadataEntryId == null || trustAnchorMetadataEntryId.isBlank()
                        ? null
                        : trustAnchorMetadataEntryId.trim();
                trustAnchorWarnings = List.copyOf(trustAnchorWarnings == null ? List.of() : trustAnchorWarnings);
            }

            private static String sanitize(String value) {
                Objects.requireNonNull(value, "value");
                return value.trim();
            }

            @Override
            public byte[] attestationObject() {
                return attestationObject.clone();
            }

            @Override
            public byte[] clientDataJson() {
                return clientDataJson.clone();
            }

            @Override
            public byte[] expectedChallenge() {
                return expectedChallenge.clone();
            }
        }
    }

    private static VerboseTrace.Builder newTrace(boolean verbose, String operation) {
        return verbose ? VerboseTrace.builder(operation) : null;
    }

    private static void metadata(VerboseTrace.Builder trace, String key, String value) {
        if (trace != null && value != null) {
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

    private static void addParseClientDataStep(
            VerboseTrace.Builder trace, TraceClientData clientData, byte[] expectedChallenge, String origin) {
        if (trace == null || clientData == null) {
            return;
        }
        String type = clientData.type().isEmpty() ? "webauthn.create" : clientData.type();
        String challenge = clientData.challengeBase64().isEmpty() && expectedChallenge != null
                ? base64Url(expectedChallenge)
                : clientData.challengeBase64();
        String resolvedOrigin = clientData.origin().isEmpty() ? safe(origin) : clientData.origin();

        addStep(trace, step -> step.id("parse.clientData")
                .summary("Parse client data JSON")
                .detail("clientDataJSON")
                .spec("webauthn§6.5.1")
                .attribute("type", type)
                .attribute("challenge.base64url", challenge)
                .attribute("origin", resolvedOrigin)
                .attribute(AttributeType.JSON, "clientData.json", clientData.json())
                .attribute("clientData.sha256", sha256Label(clientData.hash())));
    }

    private static void addParseAuthenticatorDataStep(
            VerboseTrace.Builder trace, AttestationComponents components, String relyingPartyId) {
        if (trace == null || !components.hasAuthData()) {
            return;
        }
        String expectedHash = sha256Digest(safe(relyingPartyId).getBytes(StandardCharsets.UTF_8));

        addStep(trace, step -> step.id("parse.authenticatorData")
                .summary("Parse authenticator data")
                .detail("authenticatorData")
                .spec("webauthn§6.5.4")
                .attribute("rpId.hash.hex", hex(components.rpIdHash()))
                .attribute("rpId.expected.sha256", expectedHash)
                .attribute("flags.byte", formatByte(components.flags()))
                .attribute("flags.userPresence", components.userPresence())
                .attribute("flags.userVerification", components.userVerification())
                .attribute("flags.attestedCredentialData", components.attestedCredentialData())
                .attribute("flags.extensionDataIncluded", components.extensionDataIncluded())
                .attribute("counter.reported", components.counter()));
    }

    private static void addExtractAttestedCredentialStep(
            VerboseTrace.Builder trace,
            AttestationComponents components,
            Optional<AttestedCredential> attestedCredential,
            String relyingPartyId) {
        if (trace == null || !components.hasAuthData()) {
            return;
        }
        String rpId = attestedCredential
                .map(AttestedCredential::relyingPartyId)
                .filter(s -> !s.isBlank())
                .orElse(safe(relyingPartyId));
        String credentialId = attestedCredential
                .map(AttestedCredential::credentialId)
                .filter(s -> !s.isBlank())
                .orElseGet(() -> base64Url(components.credentialId()));
        WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(attestedCredential, components);
        long signatureCounter =
                attestedCredential.map(AttestedCredential::signatureCounter).orElse(components.counter());
        boolean userVerificationRequired = attestedCredential
                .map(AttestedCredential::userVerificationRequired)
                .orElse(components.userVerification());
        String aaguid = !safe(components.aaguidHex()).isEmpty()
                ? components.aaguidHex()
                : safe(attestedCredential.map(AttestedCredential::aaguid).orElse(""));

        addStep(trace, step -> {
            step.id("extract.attestedCredential")
                    .summary("Extract attested credential metadata")
                    .detail("AttestationObject")
                    .spec("webauthn§7.1")
                    .attribute("relyingPartyId", rpId)
                    .attribute("credentialId.base64url", credentialId)
                    .attribute("signatureCounter", signatureCounter)
                    .attribute("userVerificationRequired", userVerificationRequired)
                    .attribute("aaguid.hex", aaguid);
            if (algorithm != null) {
                step.attribute("algorithm", algorithm.name());
            }
        });
    }

    private static void addSignatureBaseStep(
            VerboseTrace.Builder trace, byte[] authenticatorData, byte[] clientDataHash) {
        if (trace == null || authenticatorData == null || authenticatorData.length == 0) {
            return;
        }
        byte[] safeClientHash = clientDataHash == null ? sha256(new byte[0]) : clientDataHash;
        byte[] payload = concat(authenticatorData, safeClientHash);
        addStep(trace, step -> step.id("build.signatureBase")
                .summary("Build signature payload")
                .detail("authenticatorData || SHA-256(clientData)")
                .spec("webauthn§6.5.5")
                .attribute("authenticatorData.hex", hex(authenticatorData))
                .attribute("clientData.hash.sha256", sha256Label(safeClientHash))
                .attribute("signature.base.sha256", sha256Digest(payload)));
    }

    private static void addVerifySignatureStep(
            VerboseTrace.Builder trace, WebAuthnSignatureAlgorithm algorithm, boolean valid) {
        if (trace == null) {
            return;
        }
        addStep(trace, step -> step.id("verify.signature")
                .summary("Verify attestation signature")
                .detail("WebAuthnAttestationVerifier")
                .spec("webauthn§6.5.5")
                .attribute("algorithm", algorithm == null ? "" : algorithm.name())
                .attribute("valid", valid));
    }

    private static void addValidateMetadataStep(
            VerboseTrace.Builder trace, WebAuthnAttestationServiceSupport.Outcome outcome, int trustAnchorCount) {
        if (trace == null) {
            return;
        }
        addStep(trace, step -> step.id("validate.metadata")
                .summary("Validate trust anchors and metadata")
                .detail("Trust anchor resolution")
                .spec("webauthn§7.2")
                .attribute("anchorProvided", outcome.anchorProvided())
                .attribute("selfAttested", outcome.selfAttestedFallback())
                .attribute("trustAnchors.provided", trustAnchorCount)
                .attribute("certificateChain.length", outcome.certificateChainLength())
                .attribute("anchorMode", outcome.anchorMode()));
    }

    private static TraceClientData traceClientData(byte[] clientDataJson) {
        byte[] jsonBytes = clientDataJson == null ? new byte[0] : clientDataJson.clone();
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        Map<String, String> values = extractJsonValues(json);
        String type = values.getOrDefault("type", "");
        String challenge = values.getOrDefault("challenge", "");
        String origin = values.getOrDefault("origin", "");
        byte[] hash = sha256(jsonBytes);
        return new TraceClientData(json, type, challenge, origin, hash);
    }

    private static AttestationComponents traceAttestation(byte[] attestationObject) {
        if (attestationObject == null || attestationObject.length == 0) {
            return AttestationComponents.empty();
        }
        try {
            Object decoded = CborDecoder.decode(attestationObject);
            if (!(decoded instanceof Map<?, ?> rawMap)) {
                return AttestationComponents.empty();
            }
            Map<String, Object> map = new LinkedHashMap<>(rawMap.size());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object authDataNode = map.get("authData");
            if (!(authDataNode instanceof byte[] authData)) {
                return AttestationComponents.empty();
            }
            return parseAuthData(authData);
        } catch (GeneralSecurityException ex) {
            return AttestationComponents.empty();
        }
    }

    private static AttestationComponents parseAuthData(byte[] authData) {
        if (authData == null || authData.length < 37) {
            return AttestationComponents.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(authData).order(ByteOrder.BIG_ENDIAN);
        byte[] rpIdHash = new byte[32];
        buffer.get(rpIdHash);
        int flags = buffer.get() & 0xFF;
        long counter = buffer.getInt() & 0xFFFFFFFFL;
        byte[] aaguid = new byte[16];
        byte[] credentialId;
        byte[] credentialPublicKey;

        if ((flags & 0x40) != 0) {
            if (buffer.remaining() < 18) {
                return new AttestationComponents(
                        authData, rpIdHash, flags, counter, new byte[0], new byte[0], new byte[0]);
            }
            buffer.get(aaguid);
            int credentialIdLength = Short.toUnsignedInt(buffer.getShort());
            if (buffer.remaining() < credentialIdLength) {
                return new AttestationComponents(authData, rpIdHash, flags, counter, aaguid, new byte[0], new byte[0]);
            }
            credentialId = new byte[credentialIdLength];
            buffer.get(credentialId);
            credentialPublicKey = new byte[buffer.remaining()];
            buffer.get(credentialPublicKey);
        } else {
            credentialId = new byte[0];
            credentialPublicKey = new byte[buffer.remaining()];
            buffer.get(credentialPublicKey);
        }

        return new AttestationComponents(authData, rpIdHash, flags, counter, aaguid, credentialId, credentialPublicKey);
    }

    private static WebAuthnSignatureAlgorithm resolveAlgorithm(
            Optional<AttestedCredential> attestedCredential, AttestationComponents components) {
        return attestedCredential.map(AttestedCredential::algorithm).orElseGet(() -> deriveAlgorithmFromCose(
                        components.credentialPublicKey())
                .orElse(null));
    }

    private static Optional<WebAuthnSignatureAlgorithm> deriveAlgorithmFromCose(byte[] credentialPublicKey) {
        if (credentialPublicKey == null || credentialPublicKey.length == 0) {
            return Optional.empty();
        }
        try {
            Object decoded = CborDecoder.decode(credentialPublicKey);
            if (!(decoded instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (key instanceof Number && ((Number) key).intValue() == 3) {
                    Object value = entry.getValue();
                    if (value instanceof Number algNumber) {
                        try {
                            return Optional.of(WebAuthnSignatureAlgorithm.fromCoseIdentifier(algNumber.intValue()));
                        } catch (IllegalArgumentException ignored) {
                            return Optional.empty();
                        }
                    }
                }
            }
        } catch (GeneralSecurityException ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Map<String, String> extractJsonValues(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        if (json == null || json.isEmpty()) {
            return values;
        }
        Matcher matcher = JSON_FIELD_PATTERN.matcher(json);
        while (matcher.find()) {
            values.put(matcher.group("key"), matcher.group("value"));
        }
        return values;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sha256Label(byte[] digest) {
        return "sha256:" + hex(digest);
    }

    private static String sha256Digest(byte[] input) {
        return sha256Label(sha256(input));
    }

    private static String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static String base64Url(byte[] input) {
        if (input == null || input.length == 0) {
            return "";
        }
        return BASE64_URL_ENCODER.encodeToString(input);
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] safeLeft = left == null ? new byte[0] : left;
        byte[] safeRight = right == null ? new byte[0] : right;
        byte[] combined = Arrays.copyOf(safeLeft, safeLeft.length + safeRight.length);
        System.arraycopy(safeRight, 0, combined, safeLeft.length, safeRight.length);
        return combined;
    }

    private static String formatByte(int value) {
        return String.format("0x%02x", value & 0xFF);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record TraceClientData(String json, String type, String challengeBase64, String origin, byte[] hash) {

        private TraceClientData {
            json = json == null ? "" : json;
            type = type == null ? "" : type;
            challengeBase64 = challengeBase64 == null ? "" : challengeBase64;
            origin = origin == null ? "" : origin;
            hash = hash == null ? new byte[0] : hash.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }

    private record AttestationComponents(
            byte[] authData,
            byte[] rpIdHash,
            int flags,
            long counter,
            byte[] aaguid,
            byte[] credentialId,
            byte[] credentialPublicKey) {

        private AttestationComponents {
            authData = authData == null ? new byte[0] : authData.clone();
            rpIdHash = rpIdHash == null ? new byte[0] : rpIdHash.clone();
            aaguid = aaguid == null ? new byte[0] : aaguid.clone();
            credentialId = credentialId == null ? new byte[0] : credentialId.clone();
            credentialPublicKey = credentialPublicKey == null ? new byte[0] : credentialPublicKey.clone();
        }

        static AttestationComponents empty() {
            return new AttestationComponents(new byte[0], new byte[0], 0, 0L, new byte[0], new byte[0], new byte[0]);
        }

        boolean hasAuthData() {
            return authData.length > 0;
        }

        @Override
        public byte[] authData() {
            return authData.clone();
        }

        @Override
        public byte[] rpIdHash() {
            return rpIdHash.clone();
        }

        boolean userPresence() {
            return (flags & 0x01) != 0;
        }

        boolean userVerification() {
            return (flags & 0x04) != 0;
        }

        boolean attestedCredentialData() {
            return (flags & 0x40) != 0;
        }

        boolean extensionDataIncluded() {
            return (flags & 0x80) != 0;
        }

        public byte[] credentialId() {
            return credentialId.clone();
        }

        public byte[] credentialPublicKey() {
            return credentialPublicKey.clone();
        }

        String aaguidHex() {
            return hex(aaguid);
        }
    }

    public record VerificationResult(
            TelemetrySignal telemetry,
            boolean valid,
            Optional<WebAuthnVerificationError> error,
            Optional<AttestedCredential> attestedCredential,
            boolean anchorProvided,
            boolean selfAttestedFallback,
            String anchorMode,
            boolean trustAnchorsCached,
            List<String> anchorWarnings,
            VerboseTrace trace) {

        public VerificationResult {
            telemetry = Objects.requireNonNull(telemetry, "telemetry");
            error = error == null ? Optional.empty() : error;
            attestedCredential = attestedCredential == null ? Optional.empty() : attestedCredential;
            anchorMode = anchorMode == null ? "" : anchorMode;
            anchorWarnings = List.copyOf(anchorWarnings == null ? List.of() : anchorWarnings);
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
        }
    }

    public record AttestedCredential(
            String relyingPartyId,
            String credentialId,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            String aaguid,
            long signatureCounter) {

        public AttestedCredential {
            relyingPartyId = sanitize(relyingPartyId);
            credentialId = sanitize(credentialId);
            algorithm = Objects.requireNonNull(algorithm, "algorithm");
            aaguid = aaguid == null ? "" : aaguid.trim();
        }

        private static String sanitize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record TelemetrySignal(
            TelemetryStatus status, String reasonCode, String reason, boolean sanitized, Map<String, Object> fields) {

        public TelemetrySignal {
            status = Objects.requireNonNull(status, "status");
            reasonCode = reasonCode == null ? "unspecified" : reasonCode;
            fields = Map.copyOf(new LinkedHashMap<>(fields == null ? Map.of() : fields));
        }

        public TelemetryFrame emit(Fido2TelemetryAdapter adapter, String telemetryId) {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(telemetryId, "telemetryId");
            String eventStatus =
                    switch (status) {
                        case SUCCESS -> "success";
                        case INVALID -> "invalid";
                        case ERROR -> "error";
                    };
            return adapter.status(eventStatus, telemetryId, reasonCode, sanitized, reason, fields);
        }
    }

    public enum TelemetryStatus {
        SUCCESS,
        INVALID,
        ERROR
    }

    public TelemetryFrame emitTelemetry(VerificationResult result, String telemetryId) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(telemetryId, "telemetryId");
        return result.telemetry().emit(telemetryAdapter, telemetryId);
    }

    private static TelemetryStatus toTelemetryStatus(WebAuthnAttestationServiceSupport.Status status) {
        return switch (status) {
            case SUCCESS -> TelemetryStatus.SUCCESS;
            case INVALID -> TelemetryStatus.INVALID;
            case ERROR -> TelemetryStatus.ERROR;
        };
    }
}
