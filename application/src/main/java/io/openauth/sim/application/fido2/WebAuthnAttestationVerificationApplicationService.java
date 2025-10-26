package io.openauth.sim.application.fido2;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.application.telemetry.TelemetryFrame;
import io.openauth.sim.core.fido2.CborDecoder;
import io.openauth.sim.core.fido2.CoseKeyInspector;
import io.openauth.sim.core.fido2.CoseKeyInspector.CoseKeyDetails;
import io.openauth.sim.core.fido2.SignatureInspector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnAuthenticatorDataParser;
import io.openauth.sim.core.fido2.WebAuthnAuthenticatorDataParser.AttestedCredentialData;
import io.openauth.sim.core.fido2.WebAuthnAuthenticatorDataParser.ParsedAuthenticatorData;
import io.openauth.sim.core.fido2.WebAuthnExtensionDecoder;
import io.openauth.sim.core.fido2.WebAuthnExtensionDecoder.ParsedExtensions;
import io.openauth.sim.core.fido2.WebAuthnRelyingPartyId;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.core.trace.VerboseTrace.AttributeType;
import java.math.BigInteger;
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
    private final WebAuthnSignaturePolicy signaturePolicy;
    private static final Base64.Encoder BASE64_URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final Pattern JSON_FIELD_PATTERN =
            Pattern.compile("\\\"(?<key>[^\\\"]+)\\\"\\s*:\\s*\\\"(?<value>[^\\\"]*)\\\"");
    private static final Pattern TOKEN_BINDING_PATTERN = Pattern.compile("\\\"tokenBinding\\\"\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern TOKEN_BINDING_FIELD_PATTERN =
            Pattern.compile("\\\"(status|id)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    public WebAuthnAttestationVerificationApplicationService(
            WebAuthnAttestationVerifier verifier, Fido2TelemetryAdapter telemetryAdapter) {
        this(verifier, telemetryAdapter, WebAuthnSignaturePolicy.observeOnly());
    }

    public WebAuthnAttestationVerificationApplicationService(
            WebAuthnAttestationVerifier verifier,
            Fido2TelemetryAdapter telemetryAdapter,
            WebAuthnSignaturePolicy signaturePolicy) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.telemetryAdapter = Objects.requireNonNull(telemetryAdapter, "telemetryAdapter");
        this.signaturePolicy = Objects.requireNonNull(signaturePolicy, "signaturePolicy");
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
        String submittedRpId = command.relyingPartyId();
        String canonicalRpId = WebAuthnRelyingPartyId.canonicalize(submittedRpId);

        addStep(trace, step -> step.id("parse.request")
                .summary("Prepare attestation verification request")
                .detail("VerificationCommand")
                .attribute("relyingPartyId", submittedRpId)
                .attribute("rpId.canonical", canonicalRpId)
                .attribute("origin", command.origin())
                .attribute("trustAnchors", command.trustAnchors().size())
                .attribute("trustAnchorsCached", command.trustAnchorsCached())
                .attribute("trustAnchorSource", command.trustAnchorSource().name()));

        TraceClientData clientData = trace == null ? null : traceClientData(command.clientDataJson());
        AttestationComponents attestationComponents =
                trace == null ? AttestationComponents.empty() : traceAttestation(command.attestationObject());

        if (trace != null) {
            addParseClientDataStep(trace, clientData, command.expectedChallenge(), command.origin());
        }

        WebAuthnAttestationServiceSupport.Outcome outcome = WebAuthnAttestationServiceSupport.process(
                verifier,
                command.format(),
                command.attestationId(),
                canonicalRpId,
                command.origin(),
                command.attestationObject(),
                command.clientDataJson(),
                command.expectedChallenge(),
                command.trustAnchors(),
                command.trustAnchorSource(),
                command.trustAnchorsCached(),
                command.trustAnchorMetadataEntryId());

        Optional<AttestedCredential> attestedCredential = outcome.credential()
                .map(data -> new AttestedCredential(
                        data.relyingPartyId(),
                        data.credentialId(),
                        data.algorithm(),
                        data.userVerificationRequired(),
                        outcome.aaguid(),
                        data.signatureCounter()));

        WebAuthnSignatureAlgorithm algorithm = resolveAlgorithm(attestedCredential, attestationComponents);
        CoseKeyDetails keyDetails = addExtractAttestedCredentialStep(
                trace, attestationComponents, attestedCredential, submittedRpId, canonicalRpId, algorithm);
        byte[] signatureBytes = extractAttestationSignature(command.attestationObject());
        SignatureInspector.SignatureDetails signatureDetails = null;
        String signatureDecodeError = null;
        if (algorithm != null) {
            try {
                signatureDetails = SignatureInspector.inspect(algorithm, signatureBytes);
            } catch (IllegalArgumentException ex) {
                signatureDecodeError = ex.getMessage();
                signatureDetails = SignatureInspector.SignatureDetails.raw(signatureBytes);
            }
        }
        boolean algorithmMatches =
                algorithm == null || keyDetails == null || keyDetails.coseAlgorithm() == algorithm.coseIdentifier();
        Integer rsaKeyBits = rsaKeyBits(keyDetails);
        boolean lowSEnforced = signaturePolicy.enforceLowS();
        boolean lowSValid = signatureDetails == null
                || signatureDetails
                        .ecdsa()
                        .map(SignatureInspector.EcdsaSignatureDetails::lowS)
                        .orElse(true);

        if (trace != null) {
            Boolean userVerificationRequired = attestedCredential
                    .map(AttestedCredential::userVerificationRequired)
                    .orElse(null);
            addParseAuthenticatorDataStep(
                    trace, attestationComponents, submittedRpId, canonicalRpId, userVerificationRequired);
            addParseExtensionsStep(trace, attestationComponents);
            byte[] clientDataHash = clientData == null ? sha256(command.clientDataJson()) : clientData.hash();
            addSignatureBaseStep(trace, attestationComponents.authData(), clientDataHash);
        }

        boolean success = outcome.success();
        Optional<WebAuthnVerificationError> error = outcome.error();
        if (lowSEnforced && !lowSValid) {
            success = false;
            error = Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID);
        }

        TelemetrySignal telemetry = lowSEnforced && !lowSValid
                ? new TelemetrySignal(
                        TelemetryStatus.INVALID,
                        "signature_invalid",
                        "ECDSA signature violates low-S policy",
                        true,
                        outcome.telemetryFields())
                : new TelemetrySignal(
                        toTelemetryStatus(outcome.status()),
                        outcome.reasonCode(),
                        outcome.reason(),
                        true,
                        outcome.telemetryFields());

        boolean finalSuccess = success;
        Optional<WebAuthnVerificationError> finalError = error;

        if (trace != null && signatureDetails != null && algorithm != null) {
            addVerifySignatureStep(
                    trace,
                    algorithm,
                    signatureBytes,
                    signatureDetails,
                    signatureDecodeError,
                    success,
                    lowSEnforced,
                    algorithmMatches,
                    rsaKeyBits);
        }

        addStep(trace, step -> {
            step.id("verify.attestation")
                    .summary("Verify WebAuthn attestation object")
                    .detail("WebAuthnAttestationServiceSupport.process")
                    .spec("webauthn§7.2")
                    .attribute("status", outcome.status().name())
                    .attribute("valid", finalSuccess);
            finalError.map(Enum::name).ifPresent(err -> step.note("error", err));
        });

        addValidateMetadataStep(trace, outcome, command.trustAnchors().size());

        addStep(trace, step -> {
            step.id("assemble.result")
                    .summary("Assemble attestation verification result")
                    .detail("VerificationResult")
                    .attribute("valid", finalSuccess)
                    .attribute("anchorMode", outcome.anchorMode())
                    .attribute("attestedCredential", attestedCredential.isPresent());
        });

        return new VerificationResult(
                telemetry,
                success,
                error,
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
        String expectedType = "webauthn.create";
        String type = clientData.type().isEmpty() ? expectedType : clientData.type();
        String challenge = clientData.challengeBase64().isEmpty() && expectedChallenge != null
                ? base64Url(expectedChallenge)
                : clientData.challengeBase64();
        String expectedOrigin = safe(origin);
        String resolvedOrigin = clientData.origin().isEmpty() ? expectedOrigin : clientData.origin();
        boolean typeMatches = expectedType.equals(type);
        boolean originMatches = expectedOrigin.isEmpty() || expectedOrigin.equals(resolvedOrigin);

        addStep(trace, step -> {
            String tokenBindingStatus = clientData
                    .tokenBindingStatusOptional()
                    .orElseGet(() -> clientData.tokenBindingPresent() ? "" : "not_present");
            String tokenBindingId = clientData.tokenBindingIdOptional().orElse("");
            step.id("parse.clientData")
                    .summary("Parse client data JSON")
                    .detail("clientDataJSON")
                    .spec("webauthn§6.5.1")
                    .attribute("type", type)
                    .attribute("expected.type", expectedType)
                    .attribute(AttributeType.BOOL, "type.match", typeMatches)
                    .attribute("challenge.b64u", challenge)
                    .attribute("challenge.decoded.len", clientData.challengeLength())
                    .attribute("origin", resolvedOrigin)
                    .attribute("origin.expected", expectedOrigin)
                    .attribute(AttributeType.BOOL, "origin.match", originMatches)
                    .attribute(AttributeType.JSON, "clientData.json", clientData.json())
                    .attribute("clientDataHash.sha256", sha256Label(clientData.hash()))
                    .attribute(AttributeType.BOOL, "tokenBinding.present", clientData.tokenBindingPresent());
            step.attribute("tokenBinding.status", tokenBindingStatus);
            step.attribute("tokenBinding.id", tokenBindingId);
            if (clientData.challengeDecodeFailed()) {
                step.note("challenge.decode", "invalid_base64url");
            }
        });
    }

    private static void addParseAuthenticatorDataStep(
            VerboseTrace.Builder trace,
            AttestationComponents components,
            String relyingPartyId,
            String canonicalRpId,
            Boolean userVerificationRequired) {
        if (trace == null || !components.hasAuthData()) {
            return;
        }
        String expectedHash = sha256Digest(canonicalRpId.getBytes(StandardCharsets.UTF_8));
        String actualHash = sha256Label(components.rpIdHash());
        boolean hashesMatch = expectedHash.equals(actualHash);
        boolean userVerificationFlag = components.userVerification();

        addStep(trace, step -> {
            step.id("parse.authenticatorData")
                    .summary("Parse authenticator data")
                    .detail("authenticatorData")
                    .spec("webauthn§6.5.4")
                    .attribute("rpIdHash.hex", hex(components.rpIdHash()))
                    .attribute("rpId.canonical", canonicalRpId)
                    .attribute("rpIdHash.expected", expectedHash)
                    .attribute(AttributeType.BOOL, "rpIdHash.match", hashesMatch)
                    .attribute("rpId.expected.sha256", expectedHash)
                    .attribute("flags.byte", formatByte(components.flags()))
                    .attribute(AttributeType.BOOL, "flags.bits.UP", components.userPresence())
                    .attribute(AttributeType.BOOL, "flags.bits.RFU1", components.reservedBitRfu1())
                    .attribute(AttributeType.BOOL, "flags.bits.UV", userVerificationFlag)
                    .attribute(AttributeType.BOOL, "flags.bits.BE", components.backupEligible())
                    .attribute(AttributeType.BOOL, "flags.bits.BS", components.backupState())
                    .attribute(AttributeType.BOOL, "flags.bits.RFU2", components.reservedBitRfu2())
                    .attribute(AttributeType.BOOL, "flags.bits.AT", components.attestedCredentialData())
                    .attribute(AttributeType.BOOL, "flags.bits.ED", components.extensionDataIncluded())
                    .attribute("counter.reported", components.counter());
            if (userVerificationRequired != null) {
                step.attribute(AttributeType.BOOL, "userVerificationRequired", userVerificationRequired);
                step.attribute(AttributeType.BOOL, "uv.policy.ok", !userVerificationRequired || userVerificationFlag);
            }
        });
    }

    private static void addParseExtensionsStep(VerboseTrace.Builder trace, AttestationComponents components) {
        if (trace == null || !components.hasAuthData()) {
            return;
        }
        boolean extensionFlag = components.extensionDataIncluded();
        byte[] extensions = components.extensions();
        ParsedExtensions decoded = WebAuthnExtensionDecoder.decode(extensions);
        boolean present =
                extensionFlag && extensions.length > 0 && decoded.error().isEmpty();
        String extensionsHex = present ? hex(extensions) : "";

        addStep(trace, step -> {
            step.id("parse.extensions")
                    .summary("Decode authenticator extensions")
                    .detail("authenticator extensions")
                    .spec("webauthn§6.5.5")
                    .attribute(AttributeType.BOOL, "extensions.present", present)
                    .attribute("extensions.cbor.hex", extensionsHex);

            decoded.residentKey().ifPresent(value -> step.attribute(AttributeType.BOOL, "ext.credProps.rk", value));
            decoded.credProtectPolicy().ifPresent(value -> step.attribute("ext.credProtect.policy", value));
            decoded.largeBlobKeyBase64().ifPresent(value -> step.attribute("ext.largeBlobKey.b64u", value));
            decoded.hmacSecretState().ifPresent(value -> step.attribute("ext.hmac-secret", value));
            decoded.unknownEntries()
                    .forEach((key, value) -> step.attribute("extensions.unknown." + key, String.valueOf(value)));

            decoded.error().ifPresent(message -> step.note("extensions.decode.error", message));
            if (extensionFlag && !present && decoded.error().isEmpty()) {
                step.note("extensions.decode", "empty");
            }
            components.extensionParseError().ifPresent(message -> step.note("extensions.parse.error", message));
        });
    }

    private static CoseKeyDetails addExtractAttestedCredentialStep(
            VerboseTrace.Builder trace,
            AttestationComponents components,
            Optional<AttestedCredential> attestedCredential,
            String relyingPartyId,
            String canonicalRpId,
            WebAuthnSignatureAlgorithm algorithm) {
        CoseKeyDetails details = null;
        String decodeError = null;
        if (components.hasAuthData() && algorithm != null) {
            try {
                details = CoseKeyInspector.inspect(components.credentialPublicKey(), algorithm);
            } catch (GeneralSecurityException ex) {
                decodeError = ex.getMessage();
            }
        }
        if (trace == null || !components.hasAuthData()) {
            return details;
        }
        String rpId = attestedCredential
                .map(AttestedCredential::relyingPartyId)
                .filter(s -> !s.isBlank())
                .orElse(safe(relyingPartyId));
        String credentialId = attestedCredential
                .map(AttestedCredential::credentialId)
                .filter(s -> !s.isBlank())
                .orElseGet(() -> base64Url(components.credentialId()));
        long signatureCounter =
                attestedCredential.map(AttestedCredential::signatureCounter).orElse(components.counter());
        boolean userVerificationRequired = attestedCredential
                .map(AttestedCredential::userVerificationRequired)
                .orElse(components.userVerification());
        String aaguid = !safe(components.aaguidHex()).isEmpty()
                ? components.aaguidHex()
                : safe(attestedCredential.map(AttestedCredential::aaguid).orElse(""));

        CoseKeyDetails finalDetails = details;
        String finalDecodeError = decodeError;
        addStep(trace, step -> {
            step.id("extract.attestedCredential")
                    .summary("Extract attested credential metadata")
                    .detail("AttestationObject")
                    .spec("webauthn§7.1")
                    .attribute("relyingPartyId", rpId)
                    .attribute("rpId.canonical", canonicalRpId)
                    .attribute("credentialId.base64url", credentialId)
                    .attribute("signatureCounter", signatureCounter)
                    .attribute("userVerificationRequired", userVerificationRequired)
                    .attribute("aaguid.hex", aaguid);
            if (algorithm != null) {
                step.attribute("alg", algorithm.name());
                step.attribute("cose.alg", algorithm.coseIdentifier());
                step.attribute("cose.alg.name", algorithm.name());
            }
            if (finalDetails != null) {
                step.attribute("cose.kty", finalDetails.keyType());
                step.attribute("cose.kty.name", finalDetails.keyTypeName());
                finalDetails.curve().ifPresent(value -> step.attribute("cose.crv", value));
                finalDetails.curveName().ifPresent(value -> step.attribute("cose.crv.name", value));
                finalDetails
                        .xCoordinateBase64Url()
                        .ifPresent(value -> step.attribute(VerboseTrace.AttributeType.BASE64URL, "cose.x.b64u", value));
                finalDetails
                        .yCoordinateBase64Url()
                        .ifPresent(value -> step.attribute(VerboseTrace.AttributeType.BASE64URL, "cose.y.b64u", value));
                finalDetails
                        .modulusBase64Url()
                        .ifPresent(value -> step.attribute(VerboseTrace.AttributeType.BASE64URL, "cose.n.b64u", value));
                finalDetails
                        .exponentBase64Url()
                        .ifPresent(value -> step.attribute(VerboseTrace.AttributeType.BASE64URL, "cose.e.b64u", value));
                finalDetails
                        .jwkThumbprintSha256()
                        .ifPresent(value -> step.attribute(
                                VerboseTrace.AttributeType.BASE64URL, "publicKey.jwk.thumbprint.sha256", value));
            } else if (finalDecodeError != null) {
                step.note("coseDecodeError", finalDecodeError);
            }
            components
                    .attestedCredentialParseError()
                    .ifPresent(message -> step.note("attestedCredential.parse.error", message));
        });
        return details;
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
                .attribute(AttributeType.INT, "authenticatorData.len.bytes", authenticatorData.length)
                .attribute("clientDataHash.sha256", sha256Label(safeClientHash))
                .attribute(AttributeType.INT, "clientDataHash.len.bytes", safeClientHash.length)
                .attribute(AttributeType.HEX, "signedBytes.hex", hex(payload))
                .attribute(AttributeType.INT, "signedBytes.len.bytes", payload.length)
                .attribute("signedBytes.preview", previewHex(payload))
                .attribute("signedBytes.sha256", sha256Digest(payload)));
    }

    private static void addVerifySignatureStep(
            VerboseTrace.Builder trace,
            WebAuthnSignatureAlgorithm algorithm,
            byte[] signature,
            SignatureInspector.SignatureDetails details,
            String decodeError,
            boolean valid,
            boolean lowSEnforced,
            boolean algorithmMatches,
            Integer rsaKeyBits) {
        if (trace == null) {
            return;
        }
        byte[] safeSignature = signature == null ? new byte[0] : signature;
        addStep(trace, step -> {
            step.id("verify.signature")
                    .summary("Verify attestation signature")
                    .detail("WebAuthnAttestationVerifier")
                    .spec("webauthn§6.5.5")
                    .attribute("valid", valid)
                    .attribute("verify.ok", valid)
                    .attribute("policy.lowS.enforced", lowSEnforced);
            if (algorithm != null) {
                step.attribute("alg", algorithm.name());
                step.attribute("cose.alg", algorithm.coseIdentifier());
                step.attribute("cose.alg.name", algorithm.name());
            } else {
                step.attribute("alg", "");
            }
            boolean lowSValid = true;
            if (details != null) {
                String prefix = details.encoding() == SignatureInspector.SignatureEncoding.DER ? "sig.der" : "sig.raw";
                step.attribute(prefix + ".b64u", details.base64Url());
                step.attribute(AttributeType.INT, prefix + ".len", details.length());
                if (details.encoding() == SignatureInspector.SignatureEncoding.RAW) {
                    step.attribute(AttributeType.HEX, prefix + ".hex", hex(safeSignature));
                }
                var ecdsaDetails = details.ecdsa();
                lowSValid = ecdsaDetails
                        .map(SignatureInspector.EcdsaSignatureDetails::lowS)
                        .orElse(true);
                ecdsaDetails.ifPresent(ecdsa -> {
                    step.attribute("ecdsa.r.hex", ecdsa.rHex());
                    step.attribute("ecdsa.s.hex", ecdsa.sHex());
                    step.attribute("ecdsa.lowS", ecdsa.lowS());
                });
                details.rsa().ifPresent(rsa -> {
                    step.attribute("rsa.padding", rsa.padding());
                    step.attribute("rsa.hash", rsa.hash());
                    rsa.pssSaltLength().ifPresent(len -> step.attribute(AttributeType.INT, "rsa.pss.salt.len", len));
                    if (rsaKeyBits != null) {
                        step.attribute(AttributeType.INT, "key.bits", rsaKeyBits);
                    }
                });
            }
            if (lowSEnforced && !lowSValid) {
                step.note("error.lowS", "ECDSA signature violates low-S policy");
            }
            if (!algorithmMatches) {
                step.note("error.alg_mismatch", "COSE algorithm differs from attested metadata");
            }
            if (decodeError != null && !decodeError.isBlank()) {
                step.note("signature.decode.error", decodeError);
            }
        });
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

    private static byte[] extractAttestationSignature(byte[] attestationObject) {
        if (attestationObject == null || attestationObject.length == 0) {
            return new byte[0];
        }
        try {
            Object decoded = CborDecoder.decode(attestationObject);
            if (!(decoded instanceof Map<?, ?> rawMap)) {
                return new byte[0];
            }
            Map<String, Object> map = new LinkedHashMap<>(rawMap.size());
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Object attStmtNode = map.get("attStmt");
            if (!(attStmtNode instanceof Map<?, ?> rawAttStmt)) {
                return new byte[0];
            }
            for (Map.Entry<?, ?> entry : rawAttStmt.entrySet()) {
                if ("sig".equals(String.valueOf(entry.getKey())) && entry.getValue() instanceof byte[] bytes) {
                    return bytes;
                }
            }
            return new byte[0];
        } catch (GeneralSecurityException ex) {
            return new byte[0];
        }
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
        ParsedAuthenticatorData parsed = WebAuthnAuthenticatorDataParser.parse(authData);
        Optional<AttestedCredentialData> attested = parsed.attestedCredential();
        byte[] aaguid = attested.map(AttestedCredentialData::aaguid).orElse(new byte[0]);
        byte[] credentialId = attested.map(AttestedCredentialData::credentialId).orElse(new byte[0]);
        byte[] credentialPublicKey =
                attested.map(AttestedCredentialData::credentialPublicKey).orElse(new byte[0]);
        return new AttestationComponents(parsed, aaguid, credentialId, credentialPublicKey);
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

    private static String previewHex(byte[] bytes) {
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        if (safeBytes.length <= 32) {
            return hex(safeBytes);
        }
        int previewLength = Math.min(16, safeBytes.length);
        byte[] head = Arrays.copyOfRange(safeBytes, 0, previewLength);
        byte[] tail = Arrays.copyOfRange(safeBytes, safeBytes.length - previewLength, safeBytes.length);
        return hex(head) + "…" + hex(tail);
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

    private static Integer rsaKeyBits(CoseKeyDetails details) {
        if (details == null) {
            return null;
        }
        return details.modulusBase64Url()
                .map(BASE64_URL_DECODER::decode)
                .map(bytes -> new BigInteger(1, bytes).bitLength())
                .orElse(null);
    }

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
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

    private record AttestationComponents(
            ParsedAuthenticatorData parsed, byte[] aaguid, byte[] credentialId, byte[] credentialPublicKey) {

        private AttestationComponents {
            parsed = parsed == null ? WebAuthnAuthenticatorDataParser.parse(new byte[0]) : parsed;
            aaguid = aaguid == null ? new byte[0] : aaguid.clone();
            credentialId = credentialId == null ? new byte[0] : credentialId.clone();
            credentialPublicKey = credentialPublicKey == null ? new byte[0] : credentialPublicKey.clone();
        }

        static AttestationComponents empty() {
            return new AttestationComponents(
                    WebAuthnAuthenticatorDataParser.parse(new byte[0]), new byte[0], new byte[0], new byte[0]);
        }

        boolean hasAuthData() {
            return parsed.raw().length > 0;
        }

        public byte[] authData() {
            return parsed.raw().clone();
        }

        public byte[] rpIdHash() {
            return parsed.rpIdHash().clone();
        }

        int flags() {
            return parsed.flags();
        }

        long counter() {
            return parsed.counter();
        }

        boolean userPresence() {
            return parsed.userPresence();
        }

        boolean reservedBitRfu1() {
            return parsed.reservedBitRfu1();
        }

        boolean userVerification() {
            return parsed.userVerification();
        }

        boolean backupEligible() {
            return parsed.backupEligible();
        }

        boolean backupState() {
            return parsed.backupState();
        }

        boolean reservedBitRfu2() {
            return parsed.reservedBitRfu2();
        }

        boolean attestedCredentialData() {
            return parsed.attestedCredentialDataIncluded();
        }

        boolean extensionDataIncluded() {
            return parsed.extensionDataIncluded();
        }

        @Override
        public byte[] credentialId() {
            return credentialId.clone();
        }

        @Override
        public byte[] credentialPublicKey() {
            return credentialPublicKey.clone();
        }

        public byte[] extensions() {
            return parsed.extensions().clone();
        }

        Optional<String> extensionParseError() {
            return parsed.extensionsError();
        }

        Optional<String> attestedCredentialParseError() {
            return parsed.attestedCredentialError();
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
