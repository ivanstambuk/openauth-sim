package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.WebAuthnAssertionRequest;
import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnRelyingPartyId;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.fido2.WebAuthnVerificationResult;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Application-level coordinator for WebAuthn assertion verification + telemetry emission. */
public final class WebAuthnEvaluationApplicationService {

    private final CredentialStore credentialStore;
    private final WebAuthnAssertionVerifier verifier;
    private final WebAuthnCredentialPersistenceAdapter persistenceAdapter;
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final Pattern JSON_FIELD_PATTERN =
            Pattern.compile("\\\"(?<key>[^\\\"]+)\\\"\\s*:\\s*\\\"(?<value>[^\\\"]*)\\\"");
    private static final Pattern TOKEN_BINDING_PATTERN = Pattern.compile("\\\"tokenBinding\\\"\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern TOKEN_BINDING_FIELD_PATTERN =
            Pattern.compile("\\\"(status|id)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    public WebAuthnEvaluationApplicationService(
            CredentialStore credentialStore,
            WebAuthnAssertionVerifier verifier,
            WebAuthnCredentialPersistenceAdapter persistenceAdapter) {
        this.credentialStore = Objects.requireNonNull(credentialStore, "credentialStore");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.persistenceAdapter = Objects.requireNonNull(persistenceAdapter, "persistenceAdapter");
    }

    public EvaluationResult evaluate(EvaluationCommand command) {
        return evaluate(command, false);
    }

    public EvaluationResult evaluate(EvaluationCommand command, boolean verbose) {
        Objects.requireNonNull(command, "command");

        if (command instanceof EvaluationCommand.Stored stored) {
            return evaluateStored(stored, verbose);
        }
        if (command instanceof EvaluationCommand.Inline inline) {
            return evaluateInline(inline, verbose);
        }

        throw new IllegalArgumentException("Unsupported WebAuthn evaluation command: " + command);
    }

    private EvaluationResult evaluateStored(EvaluationCommand.Stored command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.assertion.evaluate.stored");
        metadata(trace, "protocol", "FIDO2");
        metadata(trace, "mode", "stored");
        metadata(trace, "credentialName", command.credentialId());
        String submittedRpId = command.relyingPartyId();
        String canonicalRpId = WebAuthnRelyingPartyId.canonicalize(submittedRpId);
        try {
            Optional<Credential> credential = credentialStore.findByName(command.credentialId());
            if (credential.isEmpty()) {
                addStep(trace, step -> step.id("resolve.credential")
                        .summary("Resolve stored credential")
                        .detail("CredentialStore.findByName")
                        .attribute("credentialId", command.credentialId())
                        .attribute("found", false));
                return credentialNotFound(command, buildTrace(trace));
            }

            addStep(trace, step -> step.id("resolve.credential")
                    .summary("Resolve stored credential")
                    .detail("CredentialStore.findByName")
                    .attribute("credentialId", command.credentialId())
                    .attribute("found", true));

            WebAuthnCredentialDescriptor descriptor =
                    persistenceAdapter.deserialize(VersionedCredentialRecordMapper.toRecord(credential.get()));
            addStep(trace, step -> step.id("deserialize.descriptor")
                    .summary("Deserialize stored descriptor")
                    .detail("WebAuthnCredentialPersistenceAdapter.deserialize")
                    .attribute("credentialId", descriptor.name())
                    .attribute("relyingPartyId", descriptor.relyingPartyId())
                    .attribute("alg", descriptor.algorithm().name())
                    .attribute("cose.alg", descriptor.algorithm().coseIdentifier()));

            WebAuthnStoredCredential storedCredential = descriptor.toStoredCredential();
            WebAuthnAssertionRequest request = toRequest(command);
            TraceClientData clientData = trace == null ? null : traceClientData(command.clientDataJson());
            TraceAuthenticatorData authenticatorData =
                    trace == null ? null : traceAuthenticatorData(command.authenticatorData());

            if (trace != null) {
                addParseClientDataStep(
                        trace, clientData, command.expectedType(), command.expectedChallenge(), command.origin());
                addParseAuthenticatorDataStep(
                        trace, authenticatorData, submittedRpId, canonicalRpId, storedCredential.signatureCounter());
                addEvaluateCounterStep(trace, storedCredential.signatureCounter(), authenticatorData.counter());
            }

            WebAuthnVerificationResult verification = verifier.verify(storedCredential, request);

            if (trace != null) {
                addSignatureBaseStep(trace, authenticatorData.raw(), clientData.hash());
                addVerifySignatureStep(trace, descriptor.algorithm(), verification.success());
            }

            return buildVerificationResult(
                    verification,
                    true,
                    descriptor.name(),
                    descriptor.relyingPartyId(),
                    command.origin(),
                    descriptor.algorithm(),
                    descriptor.userVerificationRequired(),
                    "stored",
                    trace);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("deserialize.descriptor")
                    .summary("Deserialize stored descriptor")
                    .detail("WebAuthnCredentialPersistenceAdapter.deserialize")
                    .note("failure", ex.getMessage()));
            return metadataFailure(command, ex.getMessage(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during stored evaluation")
                    .detail(ex.getClass().getName())
                    .note("message", ex.getMessage()));
            return unexpectedError(command.origin(), "stored", ex, buildTrace(trace));
        }
    }

    private EvaluationResult evaluateInline(EvaluationCommand.Inline command, boolean verbose) {
        VerboseTrace.Builder trace = newTrace(verbose, "fido2.assertion.evaluate.inline");
        metadata(trace, "protocol", "FIDO2");
        metadata(trace, "mode", "inline");
        metadata(trace, "credentialName", command.credentialName());
        String submittedRpId = command.relyingPartyId();
        String canonicalRpId = WebAuthnRelyingPartyId.canonicalize(submittedRpId);
        try {
            WebAuthnStoredCredential storedCredential = new WebAuthnStoredCredential(
                    canonicalRpId,
                    command.credentialId(),
                    command.publicKeyCose(),
                    command.signatureCounter(),
                    command.userVerificationRequired(),
                    command.algorithm());

            TraceClientData clientData = trace == null ? null : traceClientData(command.clientDataJson());
            TraceAuthenticatorData authenticatorData =
                    trace == null ? null : traceAuthenticatorData(command.authenticatorData());

            if (trace != null) {
                addParseClientDataStep(
                        trace, clientData, command.expectedType(), command.expectedChallenge(), command.origin());
                addParseAuthenticatorDataStep(
                        trace, authenticatorData, submittedRpId, canonicalRpId, command.signatureCounter());
                addConstructCredentialStep(
                        trace,
                        canonicalRpId,
                        command.algorithm(),
                        command.publicKeyCose(),
                        command.userVerificationRequired());
                addEvaluateCounterStep(trace, command.signatureCounter(), authenticatorData.counter());
            }

            WebAuthnVerificationResult verification = verifier.verify(storedCredential, toRequest(command));

            if (trace != null) {
                addSignatureBaseStep(trace, authenticatorData.raw(), clientData.hash());
                addVerifySignatureStep(trace, command.algorithm(), verification.success());
            }

            return buildVerificationResult(
                    verification,
                    false,
                    null,
                    command.relyingPartyId(),
                    command.origin(),
                    command.algorithm(),
                    command.userVerificationRequired(),
                    "inline",
                    trace);
        } catch (IllegalArgumentException ex) {
            addStep(trace, step -> step.id("construct.credential")
                    .summary("Construct inline credential")
                    .detail("WebAuthnStoredCredential")
                    .note("failure", ex.getMessage()));
            return inlineValidationFailure(command, ex.getMessage(), buildTrace(trace));
        } catch (RuntimeException ex) {
            addStep(trace, step -> step.id("error")
                    .summary("Unexpected error during inline evaluation")
                    .detail(ex.getClass().getName())
                    .note("message", ex.getMessage()));
            return unexpectedError(command.origin(), "inline", ex, buildTrace(trace));
        }
    }

    private EvaluationResult credentialNotFound(EvaluationCommand.Stored command, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(
                "stored", false, null, command.relyingPartyId(), command.origin(), null, false, Optional.empty());

        TelemetrySignal telemetry = new TelemetrySignal(
                TelemetryStatus.INVALID, "credential_not_found", "Credential not found", true, fields);

        return new EvaluationResult(
                telemetry, false, false, null, command.relyingPartyId(), null, false, Optional.empty(), trace);
    }

    private EvaluationResult metadataFailure(EvaluationCommand.Stored command, String reason, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(
                "stored",
                true,
                command.credentialId(),
                command.relyingPartyId(),
                command.origin(),
                null,
                false,
                Optional.empty());

        TelemetrySignal telemetry =
                new TelemetrySignal(TelemetryStatus.ERROR, "credential_invalid", reason, true, fields);

        return new EvaluationResult(
                telemetry,
                false,
                true,
                command.credentialId(),
                command.relyingPartyId(),
                null,
                false,
                Optional.empty(),
                trace);
    }

    private EvaluationResult inlineValidationFailure(
            EvaluationCommand.Inline command, String reason, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(
                "inline",
                false,
                null,
                command.relyingPartyId(),
                command.origin(),
                command.algorithm(),
                command.userVerificationRequired(),
                Optional.empty());

        TelemetrySignal telemetry =
                new TelemetrySignal(TelemetryStatus.INVALID, "inline_invalid", reason, true, fields);

        return new EvaluationResult(
                telemetry,
                false,
                false,
                null,
                command.relyingPartyId(),
                command.algorithm(),
                command.userVerificationRequired(),
                Optional.empty(),
                trace);
    }

    private EvaluationResult unexpectedError(String origin, String source, RuntimeException ex, VerboseTrace trace) {
        Map<String, Object> fields = telemetryFields(source, false, null, null, origin, null, false, Optional.empty());

        TelemetrySignal telemetry =
                new TelemetrySignal(TelemetryStatus.ERROR, "unexpected_error", ex.getMessage(), true, fields);

        return new EvaluationResult(telemetry, false, false, null, null, null, false, Optional.empty(), trace);
    }

    private EvaluationResult buildVerificationResult(
            WebAuthnVerificationResult verification,
            boolean credentialReference,
            String credentialId,
            String relyingPartyId,
            String origin,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            String credentialSource,
            VerboseTrace.Builder trace) {

        boolean success = verification.success();
        Optional<WebAuthnVerificationError> error = verification.error();

        String reasonCode = success
                ? "match"
                : error.map(err -> err.name().toLowerCase(Locale.US)).orElse("verification_failed");

        Map<String, Object> fields = telemetryFields(
                credentialSource,
                credentialReference,
                credentialId,
                relyingPartyId,
                origin,
                algorithm,
                userVerificationRequired,
                error);

        TelemetrySignal telemetry = new TelemetrySignal(
                success ? TelemetryStatus.SUCCESS : TelemetryStatus.INVALID,
                reasonCode,
                success ? null : verification.message(),
                true,
                fields);

        addStep(trace, step -> {
            step.id("verify.assertion")
                    .summary("Verify WebAuthn assertion")
                    .detail("WebAuthnAssertionVerifier.verify")
                    .spec("webauthn§7.2")
                    .attribute("credentialSource", credentialSource)
                    .attribute("valid", success);
            error.map(Enum::name).ifPresent(err -> step.note("error", err));
        });

        return new EvaluationResult(
                telemetry,
                success,
                credentialReference,
                credentialReference ? credentialId : null,
                relyingPartyId,
                algorithm,
                userVerificationRequired,
                error,
                buildTrace(trace));
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
            VerboseTrace.Builder trace,
            TraceClientData clientData,
            String expectedType,
            byte[] expectedChallenge,
            String origin) {
        if (trace == null || clientData == null) {
            return;
        }
        String expectedTypeValue = sanitize(expectedType);
        String type = clientData.type().isEmpty() ? expectedTypeValue : clientData.type();
        String challenge = clientData.challengeBase64().isEmpty() && expectedChallenge != null
                ? base64Url(expectedChallenge)
                : clientData.challengeBase64();
        String expectedOriginValue = sanitize(origin);
        String resolvedOrigin = clientData.origin().isEmpty() ? expectedOriginValue : clientData.origin();
        boolean typeMatches = expectedTypeValue.isEmpty() || expectedTypeValue.equals(type);
        boolean originMatches = expectedOriginValue.isEmpty() || expectedOriginValue.equals(resolvedOrigin);

        addStep(trace, step -> {
            step.id("parse.clientData")
                    .summary("Parse client data JSON")
                    .detail("clientDataJSON")
                    .spec("webauthn§6.5.1")
                    .attribute("type", type)
                    .attribute("challenge.b64u", challenge)
                    .attribute("challenge.decoded.len", clientData.challengeLength())
                    .attribute("origin", resolvedOrigin)
                    .attribute(AttributeType.JSON, "clientData.json", clientData.json())
                    .attribute("clientDataHash.sha256", sha256Label(clientData.hash()))
                    .attribute(AttributeType.BOOL, "tokenBinding.present", clientData.tokenBindingPresent());
            if (!expectedTypeValue.isEmpty()) {
                step.attribute("expected.type", expectedTypeValue);
            }
            step.attribute(AttributeType.BOOL, "type.match", typeMatches);
            if (!expectedOriginValue.isEmpty()) {
                step.attribute("origin.expected", expectedOriginValue);
            }
            step.attribute(AttributeType.BOOL, "origin.match", originMatches);
            clientData.tokenBindingStatusOptional().ifPresent(status -> step.attribute("tokenBinding.status", status));
            clientData.tokenBindingIdOptional().ifPresent(id -> step.attribute("tokenBinding.id", id));
            if (clientData.challengeDecodeFailed()) {
                step.note("challenge.decode", "invalid_base64url");
            }
        });
    }

    private static void addParseAuthenticatorDataStep(
            VerboseTrace.Builder trace,
            TraceAuthenticatorData authenticatorData,
            String relyingPartyId,
            String canonicalRpId,
            long storedCounter) {
        if (trace == null || authenticatorData == null) {
            return;
        }
        String expectedHash = sha256Digest(canonicalRpId.getBytes(StandardCharsets.UTF_8));
        String actualHash = sha256Label(authenticatorData.rpIdHash());
        boolean hashesMatch = expectedHash.equals(actualHash);

        addStep(trace, step -> step.id("parse.authenticatorData")
                .summary("Parse authenticator data")
                .detail("authenticatorData")
                .spec("webauthn§6.5.4")
                .attribute("rpIdHash.hex", hex(authenticatorData.rpIdHash()))
                .attribute("rpId.canonical", canonicalRpId)
                .attribute("rpIdHash.expected", expectedHash)
                .attribute(AttributeType.BOOL, "rpIdHash.match", hashesMatch)
                .attribute("rpId.expected.sha256", expectedHash)
                .attribute("flags.byte", formatByte(authenticatorData.flags()))
                .attribute("flags.userPresence", authenticatorData.userPresence())
                .attribute("flags.userVerification", authenticatorData.userVerification())
                .attribute("flags.attestedCredentialData", authenticatorData.attestedCredentialData())
                .attribute("flags.extensionDataIncluded", authenticatorData.extensionDataIncluded())
                .attribute("counter.stored", storedCounter)
                .attribute("counter.reported", authenticatorData.counter()));
    }

    private static void addEvaluateCounterStep(VerboseTrace.Builder trace, long previousCounter, long reportedCounter) {
        if (trace == null) {
            return;
        }
        addStep(trace, step -> step.id("evaluate.counter")
                .summary("Evaluate authenticator counter")
                .detail("counter comparison")
                .spec("webauthn§6.5.4")
                .attribute("counter.previous", previousCounter)
                .attribute("counter.reported", reportedCounter)
                .attribute("counter.incremented", reportedCounter > previousCounter));
    }

    private static void addConstructCredentialStep(
            VerboseTrace.Builder trace,
            String canonicalRpId,
            WebAuthnSignatureAlgorithm algorithm,
            byte[] publicKeyCose,
            boolean userVerificationRequired) {
        if (trace == null) {
            return;
        }
        addStep(trace, step -> step.id("construct.credential")
                .summary("Construct inline credential")
                .detail("WebAuthnStoredCredential")
                .spec("webauthn§6.1")
                .attribute("rpId.canonical", canonicalRpId)
                .attribute("alg", algorithm.name())
                .attribute("cose.alg", algorithm.coseIdentifier())
                .attribute("publicKey.cose.hex", hex(publicKeyCose))
                .attribute("userVerificationRequired", userVerificationRequired));
    }

    private static void addSignatureBaseStep(
            VerboseTrace.Builder trace, byte[] authenticatorData, byte[] clientDataHash) {
        if (trace == null) {
            return;
        }
        byte[] payload = concat(authenticatorData, clientDataHash);
        addStep(trace, step -> step.id("build.signatureBase")
                .summary("Build signature payload")
                .detail("authenticatorData || SHA-256(clientData)")
                .spec("webauthn§6.5.5")
                .attribute("authenticatorData.hex", hex(authenticatorData))
                .attribute("clientDataHash.sha256", sha256Label(clientDataHash))
                .attribute("signedBytes.sha256", sha256Digest(payload)));
    }

    private static void addVerifySignatureStep(
            VerboseTrace.Builder trace, WebAuthnSignatureAlgorithm algorithm, boolean valid) {
        if (trace == null) {
            return;
        }
        addStep(trace, step -> step.id("verify.signature")
                .summary("Verify signature")
                .detail("WebAuthnAssertionVerifier.verify")
                .spec("webauthn§6.5.5")
                .attribute("alg", algorithm.name())
                .attribute("cose.alg", algorithm.coseIdentifier())
                .attribute("valid", valid));
    }

    private static TraceClientData traceClientData(byte[] clientDataJson) {
        byte[] jsonBytes = clientDataJson == null ? new byte[0] : clientDataJson.clone();
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        Map<String, String> values = extractJsonValues(json);
        String type = values.getOrDefault("type", "");
        String challenge = values.getOrDefault("challenge", "");
        String origin = values.getOrDefault("origin", "");
        TokenBinding tokenBinding = parseTokenBinding(json);
        DecodedChallenge decodedChallenge = decodeChallenge(challenge);
        byte[] hash = sha256(jsonBytes);
        return new TraceClientData(
                json,
                type,
                challenge,
                decodedChallenge.bytes(),
                decodedChallenge.failed(),
                origin,
                hash,
                tokenBinding.present(),
                tokenBinding.status(),
                tokenBinding.id());
    }

    private static TraceAuthenticatorData traceAuthenticatorData(byte[] authenticatorData) {
        byte[] raw = authenticatorData == null ? new byte[0] : authenticatorData.clone();
        if (raw.length < 37) {
            return new TraceAuthenticatorData(raw, new byte[0], 0, 0L);
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        byte[] rpIdHash = new byte[32];
        buffer.get(rpIdHash);
        int flags = buffer.get() & 0xFF;
        long counter = buffer.getInt() & 0xFFFFFFFFL;
        return new TraceAuthenticatorData(raw, rpIdHash, flags, counter);
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

    private static TokenBinding parseTokenBinding(String json) {
        if (json == null || json.isEmpty()) {
            return new TokenBinding(false, "", "");
        }
        Matcher matcher = TOKEN_BINDING_PATTERN.matcher(json);
        if (!matcher.find()) {
            return new TokenBinding(false, "", "");
        }
        String body = matcher.group(1);
        Matcher fieldMatcher = TOKEN_BINDING_FIELD_PATTERN.matcher(body);
        String status = "";
        String id = "";
        while (fieldMatcher.find()) {
            if ("status".equals(fieldMatcher.group(1))) {
                status = fieldMatcher.group(2);
            } else if ("id".equals(fieldMatcher.group(1))) {
                id = fieldMatcher.group(2);
            }
        }
        return new TokenBinding(true, status, id);
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

    private static DecodedChallenge decodeChallenge(String challenge) {
        if (challenge == null || challenge.isBlank()) {
            return new DecodedChallenge(new byte[0], false);
        }
        String trimmed = challenge.trim();
        try {
            String padded = padBase64Url(trimmed);
            return new DecodedChallenge(BASE64_URL_DECODER.decode(padded), false);
        } catch (IllegalArgumentException ex) {
            return new DecodedChallenge(new byte[0], true);
        }
    }

    private static String padBase64Url(String value) {
        int remainder = value.length() % 4;
        if (remainder == 0) {
            return value;
        }
        return value + "====".substring(remainder);
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] safeLeft = left == null ? new byte[0] : left;
        byte[] safeRight = right == null ? new byte[0] : right;
        byte[] combined = Arrays.copyOf(safeLeft, safeLeft.length + safeRight.length);
        System.arraycopy(safeRight, 0, combined, safeLeft.length, safeRight.length);
        return combined;
    }

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    private record DecodedChallenge(byte[] bytes, boolean failed) {

        private DecodedChallenge {
            bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record TokenBinding(boolean present, String status, String id) {

        private TokenBinding {
            status = status == null ? "" : status;
            id = id == null ? "" : id;
        }
    }

    private record TraceClientData(
            String json,
            String type,
            String challengeBase64,
            byte[] challengeBytes,
            boolean challengeDecodeFailed,
            String origin,
            byte[] hash,
            boolean tokenBindingPresent,
            String tokenBindingStatus,
            String tokenBindingId) {

        private TraceClientData {
            json = json == null ? "" : json;
            type = type == null ? "" : type;
            challengeBase64 = challengeBase64 == null ? "" : challengeBase64;
            challengeBytes = challengeBytes == null ? new byte[0] : challengeBytes.clone();
            origin = origin == null ? "" : origin;
            hash = hash == null ? new byte[0] : hash.clone();
            tokenBindingStatus = tokenBindingStatus == null ? "" : tokenBindingStatus;
            tokenBindingId = tokenBindingId == null ? "" : tokenBindingId;
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }

        public int challengeLength() {
            return challengeDecodeFailed ? -1 : challengeBytes.length;
        }

        public boolean challengeDecodeFailed() {
            return challengeDecodeFailed;
        }

        public boolean tokenBindingPresent() {
            return tokenBindingPresent;
        }

        public Optional<String> tokenBindingStatusOptional() {
            return tokenBindingStatus.isBlank() ? Optional.empty() : Optional.of(tokenBindingStatus);
        }

        public Optional<String> tokenBindingIdOptional() {
            return tokenBindingId.isBlank() ? Optional.empty() : Optional.of(tokenBindingId);
        }
    }

    private record TraceAuthenticatorData(byte[] raw, byte[] rpIdHash, int flags, long counter) {

        private TraceAuthenticatorData {
            raw = raw == null ? new byte[0] : raw.clone();
            rpIdHash = rpIdHash == null ? new byte[0] : rpIdHash.clone();
        }

        @Override
        public byte[] raw() {
            return raw.clone();
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
    }

    private static WebAuthnAssertionRequest toRequest(EvaluationCommand command) {
        if (command instanceof EvaluationCommand.Stored stored) {
            String canonicalRpId = WebAuthnRelyingPartyId.canonicalize(stored.relyingPartyId());
            return new WebAuthnAssertionRequest(
                    canonicalRpId,
                    stored.origin(),
                    stored.expectedChallenge(),
                    stored.clientDataJson(),
                    stored.authenticatorData(),
                    stored.signature(),
                    stored.expectedType());
        }
        if (command instanceof EvaluationCommand.Inline inline) {
            String canonicalRpId = WebAuthnRelyingPartyId.canonicalize(inline.relyingPartyId());
            return new WebAuthnAssertionRequest(
                    canonicalRpId,
                    inline.origin(),
                    inline.expectedChallenge(),
                    inline.clientDataJson(),
                    inline.authenticatorData(),
                    inline.signature(),
                    inline.expectedType());
        }
        throw new IllegalArgumentException("Unsupported WebAuthn assertion command: " + command);
    }

    private static Map<String, Object> telemetryFields(
            String credentialSource,
            boolean credentialReference,
            String credentialId,
            String relyingPartyId,
            String origin,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            Optional<WebAuthnVerificationError> error) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("credentialSource", credentialSource);
        fields.put("credentialReference", credentialReference);

        if (credentialReference && hasText(credentialId)) {
            fields.put("credentialId", credentialId);
        }
        if (hasText(relyingPartyId)) {
            fields.put("relyingPartyId", relyingPartyId);
        }
        if (hasText(origin)) {
            fields.put("origin", origin);
        }
        if (algorithm != null) {
            fields.put("algorithm", algorithm.label());
        }
        fields.put("userVerificationRequired", userVerificationRequired);
        error.ifPresent(err -> fields.put("error", err.name().toLowerCase(Locale.US)));
        return fields;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public sealed interface EvaluationCommand permits EvaluationCommand.Stored, EvaluationCommand.Inline {

        record Stored(
                String credentialId,
                String relyingPartyId,
                String origin,
                String expectedType,
                byte[] expectedChallenge,
                byte[] clientDataJson,
                byte[] authenticatorData,
                byte[] signature)
                implements EvaluationCommand {

            public Stored {
                credentialId =
                        Objects.requireNonNull(credentialId, "credentialId").trim();
                relyingPartyId =
                        Objects.requireNonNull(relyingPartyId, "relyingPartyId").trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                expectedType =
                        Objects.requireNonNull(expectedType, "expectedType").trim();
                expectedChallenge = expectedChallenge == null ? new byte[0] : expectedChallenge.clone();
                clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
                authenticatorData = authenticatorData == null ? new byte[0] : authenticatorData.clone();
                signature = signature == null ? new byte[0] : signature.clone();
            }

            @Override
            public byte[] expectedChallenge() {
                return expectedChallenge.clone();
            }

            @Override
            public byte[] clientDataJson() {
                return clientDataJson.clone();
            }

            @Override
            public byte[] authenticatorData() {
                return authenticatorData.clone();
            }

            @Override
            public byte[] signature() {
                return signature.clone();
            }
        }

        record Inline(
                String credentialName,
                String relyingPartyId,
                String origin,
                String expectedType,
                byte[] credentialId,
                byte[] publicKeyCose,
                long signatureCounter,
                boolean userVerificationRequired,
                WebAuthnSignatureAlgorithm algorithm,
                byte[] expectedChallenge,
                byte[] clientDataJson,
                byte[] authenticatorData,
                byte[] signature)
                implements EvaluationCommand {

            public Inline {
                credentialName = credentialName == null ? "webAuthn-inline" : credentialName.trim();
                relyingPartyId =
                        Objects.requireNonNull(relyingPartyId, "relyingPartyId").trim();
                origin = Objects.requireNonNull(origin, "origin").trim();
                expectedType =
                        Objects.requireNonNull(expectedType, "expectedType").trim();
                credentialId = credentialId == null ? new byte[0] : credentialId.clone();
                publicKeyCose = publicKeyCose == null ? new byte[0] : publicKeyCose.clone();
                algorithm = Objects.requireNonNull(algorithm, "algorithm");
                expectedChallenge = expectedChallenge == null ? new byte[0] : expectedChallenge.clone();
                clientDataJson = clientDataJson == null ? new byte[0] : clientDataJson.clone();
                authenticatorData = authenticatorData == null ? new byte[0] : authenticatorData.clone();
                signature = signature == null ? new byte[0] : signature.clone();
            }

            @Override
            public byte[] credentialId() {
                return credentialId.clone();
            }

            @Override
            public byte[] publicKeyCose() {
                return publicKeyCose.clone();
            }

            @Override
            public byte[] expectedChallenge() {
                return expectedChallenge.clone();
            }

            @Override
            public byte[] clientDataJson() {
                return clientDataJson.clone();
            }

            @Override
            public byte[] authenticatorData() {
                return authenticatorData.clone();
            }

            @Override
            public byte[] signature() {
                return signature.clone();
            }
        }
    }

    public record EvaluationResult(
            TelemetrySignal telemetry,
            boolean valid,
            boolean credentialReference,
            String credentialId,
            String relyingPartyId,
            WebAuthnSignatureAlgorithm algorithm,
            boolean userVerificationRequired,
            Optional<WebAuthnVerificationError> error,
            VerboseTrace trace) {

        public EvaluationResult {
            Objects.requireNonNull(telemetry, "telemetry");
            error = error == null ? Optional.empty() : error;
        }

        public TelemetryFrame evaluationFrame(Fido2TelemetryAdapter adapter, String telemetryId) {
            return telemetry.emit(adapter, telemetryId);
        }

        public Optional<VerboseTrace> verboseTrace() {
            return Optional.ofNullable(trace);
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
}
