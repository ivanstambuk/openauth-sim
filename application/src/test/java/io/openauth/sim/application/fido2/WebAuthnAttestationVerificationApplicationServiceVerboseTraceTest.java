package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.telemetry.Fido2TelemetryAdapter;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.trace.VerboseTrace;
import java.math.BigInteger;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnAttestationVerificationApplicationServiceVerboseTraceTest {

    private static final BigInteger P256_ORDER =
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
    private static final BigInteger P384_ORDER = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973", 16);
    private static final BigInteger P521_ORDER = new BigInteger(
            "01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            16);

    private WebAuthnAttestationVerificationApplicationService service;
    private WebAuthnAttestationVector vector;
    private WebAuthnAttestationVerification verification;
    private WebAuthnStoredCredential attestedCredential;
    private List<X509Certificate> trustAnchors;

    @BeforeEach
    void setUp() {
        service = new WebAuthnAttestationVerificationApplicationService(
                new WebAuthnAttestationVerifier(), new Fido2TelemetryAdapter("fido2.attest"));

        vector = WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                .findFirst()
                .orElseThrow();

        verification = new WebAuthnAttestationVerifier().verify(toRequest(vector));
        attestedCredential = verification
                .attestedCredential()
                .orElseThrow(() -> new IllegalStateException("Attested credential missing in fixture"));
        trustAnchors = verification.certificateChain().isEmpty()
                ? List.of()
                : List.of(verification
                        .certificateChain()
                        .get(verification.certificateChain().size() - 1));

        if (trustAnchors.isEmpty()) {
            throw new IllegalStateException("Attestation fixture missing trust anchor");
        }
    }

    private static WebAuthnAttestationRequest toRequest(WebAuthnAttestationVector vector) {
        return new WebAuthnAttestationRequest(
                vector.format(),
                vector.registration().attestationObject(),
                vector.registration().clientDataJson(),
                vector.registration().challenge(),
                vector.relyingPartyId(),
                vector.origin());
    }

    @Test
    void attestationVerificationWithVerboseCapturesTrace() {
        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId().toUpperCase(Locale.ROOT),
                        vector.origin(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("fido2.attestation.verify", trace.operation());
        assertEquals("FIDO2", trace.metadata().get("protocol"));
        assertEquals(vector.format().label(), trace.metadata().get("format"));
        assertEquals("educational", trace.metadata().get("tier"));

        var parseRequest = findStep(trace, "parse.request");
        var clientDataJson = new String(vector.registration().clientDataJson(), StandardCharsets.UTF_8);
        var parseClientData = findStep(trace, "parse.clientData");
        assertEquals("webauthn§6.5.1", parseClientData.specAnchor());
        assertEquals("webauthn.create", parseClientData.attributes().get("type"));
        assertEquals("webauthn.create", parseClientData.attributes().get("expected.type"));
        assertEquals(Boolean.TRUE, parseClientData.attributes().get("type.match"));
        assertEquals(
                base64Url(vector.registration().challenge()),
                parseClientData.attributes().get("challenge.b64u"));
        assertEquals(
                vector.registration().challenge().length,
                parseClientData.attributes().get("challenge.decoded.len"));
        assertEquals(vector.origin(), parseClientData.attributes().get("origin"));
        assertEquals(vector.origin(), parseClientData.attributes().get("origin.expected"));
        assertEquals(Boolean.TRUE, parseClientData.attributes().get("origin.match"));
        assertEquals(clientDataJson, parseClientData.attributes().get("clientData.json"));
        assertEquals(
                sha256Digest(vector.registration().clientDataJson()),
                parseClientData.attributes().get("clientDataHash.sha256"));
        assertEquals(Boolean.FALSE, parseClientData.attributes().get("tokenBinding.present"));
        assertTrue(parseClientData.typedAttributes().stream()
                .anyMatch(attr ->
                        "clientData.json".equals(attr.name()) && attr.type() == VerboseTrace.AttributeType.JSON));

        var attestation = decodeAttestation(vector.registration().attestationObject());
        var parseAuthenticatorData = findStep(trace, "parse.authenticatorData");
        assertEquals("webauthn§6.5.4", parseAuthenticatorData.specAnchor());
        assertEquals(
                hex(attestation.rpIdHash()), parseAuthenticatorData.attributes().get("rpIdHash.hex"));
        assertEquals(
                vector.relyingPartyId(), parseAuthenticatorData.attributes().get("rpId.canonical"));
        String expectedRpIdDigest = sha256Digest(vector.relyingPartyId().getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedRpIdDigest, parseAuthenticatorData.attributes().get("rpIdHash.expected"));
        assertEquals(Boolean.TRUE, parseAuthenticatorData.attributes().get("rpIdHash.match"));
        assertEquals(expectedRpIdDigest, parseAuthenticatorData.attributes().get("rpId.expected.sha256"));
        assertEquals(
                formatByte(attestation.flags()),
                parseAuthenticatorData.attributes().get("flags.byte"));
        assertEquals(
                attestation.userPresence(), parseAuthenticatorData.attributes().get("flags.bits.UP"));
        assertEquals(
                attestation.reservedBitRfu1(),
                parseAuthenticatorData.attributes().get("flags.bits.RFU1"));
        assertEquals(
                attestation.userVerification(),
                parseAuthenticatorData.attributes().get("flags.bits.UV"));
        assertEquals(
                attestation.backupEligible(),
                parseAuthenticatorData.attributes().get("flags.bits.BE"));
        assertEquals(
                attestation.backupState(), parseAuthenticatorData.attributes().get("flags.bits.BS"));
        assertEquals(
                attestation.reservedBitRfu2(),
                parseAuthenticatorData.attributes().get("flags.bits.RFU2"));
        assertEquals(
                attestation.attestedCredentialData(),
                parseAuthenticatorData.attributes().get("flags.bits.AT"));
        assertEquals(
                attestation.extensionDataIncluded(),
                parseAuthenticatorData.attributes().get("flags.bits.ED"));
        assertEquals(
                attestedCredential.userVerificationRequired(),
                parseAuthenticatorData.attributes().get("userVerificationRequired"));
        assertEquals(
                !attestedCredential.userVerificationRequired() || attestation.userVerification(),
                parseAuthenticatorData.attributes().get("uv.policy.ok"));
        assertEquals(attestation.counter(), parseAuthenticatorData.attributes().get("counter.reported"));

        var parseExtensions = findStep(trace, "parse.extensions");
        assertEquals("webauthn§6.5.5", parseExtensions.specAnchor());
        assertEquals(Boolean.FALSE, parseExtensions.attributes().get("extensions.present"));
        assertEquals("", parseExtensions.attributes().get("extensions.cbor.hex"));

        var extractCredential = findStep(trace, "extract.attestedCredential");
        assertEquals("webauthn§7.1", extractCredential.specAnchor());
        assertEquals(
                vector.relyingPartyId().toUpperCase(Locale.ROOT),
                parseRequest.attributes().get("relyingPartyId"));
        assertEquals(vector.relyingPartyId(), parseRequest.attributes().get("rpId.canonical"));
        assertEquals(vector.relyingPartyId(), extractCredential.attributes().get("relyingPartyId"));
        assertEquals(vector.relyingPartyId(), extractCredential.attributes().get("rpId.canonical"));
        assertEquals(
                base64Url(attestedCredential.credentialId()),
                extractCredential.attributes().get("credentialId.base64url"));
        assertEquals(
                attestedCredential.algorithm().name(),
                extractCredential.attributes().get("alg"));
        assertEquals(
                attestedCredential.algorithm().coseIdentifier(),
                extractCredential.attributes().get("cose.alg"));
        assertEquals(
                attestedCredential.algorithm().name(),
                extractCredential.attributes().get("cose.alg.name"));
        Map<Integer, Object> cose = decodeCoseMap(attestedCredential.publicKeyCose());
        int coseKeyType = requireInt(cose, 1);
        assertEquals(coseKeyType, extractCredential.attributes().get("cose.kty"));
        assertEquals(
                coseKeyTypeName(coseKeyType), extractCredential.attributes().get("cose.kty.name"));
        if (cose.containsKey(-1)) {
            int coseCurve = requireInt(cose, -1);
            assertEquals(coseCurve, extractCredential.attributes().get("cose.crv"));
            assertEquals(
                    coseCurveName(coseCurve), extractCredential.attributes().get("cose.crv.name"));
        }
        if (cose.containsKey(-2)) {
            byte[] x = requireBytes(cose, -2);
            assertEquals(base64Url(x), extractCredential.attributes().get("cose.x.b64u"));
        }
        if (cose.containsKey(-3)) {
            byte[] y = requireBytes(cose, -3);
            assertEquals(base64Url(y), extractCredential.attributes().get("cose.y.b64u"));
        }
        Map<String, String> jwkFields = jwkFieldsForThumbprint(coseKeyType, cose);
        if (!jwkFields.isEmpty()) {
            assertEquals(
                    jwkThumbprint(jwkFields), extractCredential.attributes().get("publicKey.jwk.thumbprint.sha256"));
        }
        assertEquals(
                attestedCredential.signatureCounter(),
                extractCredential.attributes().get("signatureCounter"));
        assertEquals(
                attestedCredential.userVerificationRequired(),
                extractCredential.attributes().get("userVerificationRequired"));
        assertEquals(hex(attestation.aaguid()), extractCredential.attributes().get("aaguid.hex"));

        var signatureBase = findStep(trace, "build.signatureBase");
        assertEquals("webauthn§6.5.5", signatureBase.specAnchor());
        byte[] clientDataHash = sha256(vector.registration().clientDataJson());
        byte[] signaturePayload = concat(attestation.authenticatorData(), clientDataHash);
        assertEquals(
                hex(attestation.authenticatorData()), signatureBase.attributes().get("authenticatorData.hex"));
        assertEquals(
                attestation.authenticatorData().length,
                signatureBase.attributes().get("authenticatorData.len.bytes"));
        assertEquals(sha256Label(clientDataHash), signatureBase.attributes().get("clientDataHash.sha256"));
        assertEquals(clientDataHash.length, signatureBase.attributes().get("clientDataHash.len.bytes"));
        assertEquals(hex(signaturePayload), signatureBase.attributes().get("signedBytes.hex"));
        assertEquals(signaturePayload.length, signatureBase.attributes().get("signedBytes.len.bytes"));
        assertEquals(previewHex(signaturePayload), signatureBase.attributes().get("signedBytes.preview"));
        assertEquals(sha256Digest(signaturePayload), signatureBase.attributes().get("signedBytes.sha256"));

        var verifySignature = findStep(trace, "verify.signature");
        assertEquals("webauthn§6.5.5", verifySignature.specAnchor());
        assertEquals(
                attestedCredential.algorithm().name(),
                verifySignature.attributes().get("alg"));
        assertEquals(
                attestedCredential.algorithm().coseIdentifier(),
                verifySignature.attributes().get("cose.alg"));
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("valid"));
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("verify.ok"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("policy.lowS.enforced"));
        byte[] attestationSignature =
                extractAttestationSignature(vector.registration().attestationObject());
        assertEquals(
                base64Url(attestationSignature), verifySignature.attributes().get("sig.der.b64u"));
        Number signatureLength = (Number) verifySignature.attributes().get("sig.der.len");
        assertEquals(attestationSignature.length, signatureLength.intValue());
        if (attestedCredential.algorithm().name().startsWith("ES")) {
            EcdsaComponents components = parseEcdsaSignature(attestationSignature, attestedCredential.algorithm());
            assertEquals(components.rHex(), verifySignature.attributes().get("ecdsa.r.hex"));
            assertEquals(components.sHex(), verifySignature.attributes().get("ecdsa.s.hex"));
            assertEquals(components.lowS(), verifySignature.attributes().get("ecdsa.lowS"));
        }

        var verifyAttestation = findStep(trace, "verify.attestation");
        assertEquals("webauthn§7.2", verifyAttestation.specAnchor());
        assertEquals(Boolean.TRUE, verifyAttestation.attributes().get("valid"));

        var validateMetadata = findStep(trace, "validate.metadata");
        assertEquals("webauthn§7.2", validateMetadata.specAnchor());
        assertEquals(Boolean.TRUE, validateMetadata.attributes().get("anchorProvided"));
        assertEquals(Boolean.FALSE, validateMetadata.attributes().get("selfAttested"));
        assertEquals(trustAnchors.size(), validateMetadata.attributes().get("trustAnchors.provided"));
        assertEquals(
                verification.certificateChain().size(),
                validateMetadata.attributes().get("certificateChain.length"));
        assertEquals("fresh", validateMetadata.attributes().get("anchorMode"));
    }

    @Test
    void attestationVerificationWithMalformedSignatureRecordsDecodeError() {
        byte[] tamperedObject = vector.registration().attestationObject().clone();
        byte[] signature = extractAttestationSignature(tamperedObject);
        int offset = indexOf(tamperedObject, signature);
        if (offset < 0) {
            throw new AssertionError("Signature bytes not found in attestation object");
        }
        tamperedObject[offset] ^= 0x7F;

        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId().toUpperCase(Locale.ROOT),
                        vector.origin(),
                        tamperedObject,
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command, true);

        assertEquals(Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID), result.error());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        var verifySignature = findStep(trace, "verify.signature");
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("valid"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("verify.ok"));
        assertTrue(verifySignature.notes().containsKey("signature.decode.error"));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command);

        assertTrue(result.verboseTrace().isEmpty());
    }

    @Test
    void invalidAttestationRecordsFailureInTrace() {
        byte[] tamperedChallenge = vector.registration().challenge().clone();
        tamperedChallenge[0] ^= 0x02;

        WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline command =
                new WebAuthnAttestationVerificationApplicationService.VerificationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        tamperedChallenge,
                        trustAnchors,
                        false,
                        WebAuthnTrustAnchorResolver.Source.MANUAL,
                        null,
                        List.of());

        WebAuthnAttestationVerificationApplicationService.VerificationResult result = service.verify(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertTrue(trace.steps().stream()
                .anyMatch(step -> "verify.attestation".equals(step.id())
                        && Boolean.FALSE.equals(step.attributes().get("valid"))));
        assertEquals(Optional.of(WebAuthnVerificationError.CLIENT_DATA_CHALLENGE_MISMATCH), result.error());
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static ParsedAttestationData decodeAttestation(byte[] attestationObject) {
        Object decoded = TestCborDecoder.decode(attestationObject);
        if (!(decoded instanceof Map<?, ?> rawMap)) {
            throw new IllegalStateException("Attestation object did not decode to a CBOR map");
        }
        Map<String, Object> map = new LinkedHashMap<>(rawMap.size());
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        Object authDataNode = map.get("authData");
        if (!(authDataNode instanceof byte[] authData)) {
            throw new IllegalStateException("Attestation object missing authData");
        }
        return parseAuthData(authData);
    }

    private static ParsedAttestationData parseAuthData(byte[] authData) {
        if (authData.length < 37) {
            throw new IllegalArgumentException("Authenticator data must be at least 37 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(authData).order(ByteOrder.BIG_ENDIAN);
        byte[] rpIdHash = new byte[32];
        buffer.get(rpIdHash);
        int flags = buffer.get() & 0xFF;
        long counter = buffer.getInt() & 0xFFFFFFFFL;
        byte[] aaguid = new byte[16];
        buffer.get(aaguid);
        int credentialIdLength = Short.toUnsignedInt(buffer.getShort());
        if (buffer.remaining() < credentialIdLength) {
            throw new IllegalArgumentException("Credential ID truncated in authenticator data");
        }
        byte[] credentialId = new byte[credentialIdLength];
        buffer.get(credentialId);
        byte[] credentialPublicKey = new byte[buffer.remaining()];
        buffer.get(credentialPublicKey);
        return new ParsedAttestationData(authData, rpIdHash, flags, counter, aaguid, credentialId, credentialPublicKey);
    }

    private static String base64Url(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String sha256Digest(byte[] input) {
        return sha256Label(sha256(input));
    }

    private static String sha256Label(byte[] digest) {
        return "sha256:" + hex(digest);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static String previewHex(byte[] bytes) {
        if (bytes.length <= 32) {
            return hex(bytes);
        }
        int previewLength = Math.min(16, bytes.length);
        byte[] head = Arrays.copyOfRange(bytes, 0, previewLength);
        byte[] tail = Arrays.copyOfRange(bytes, bytes.length - previewLength, bytes.length);
        return hex(head) + "…" + hex(tail);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
    }

    private static byte[] extractAttestationSignature(byte[] attestationObject) {
        Object decoded = TestCborDecoder.decode(attestationObject);
        if (!(decoded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Attestation object did not decode to a CBOR map");
        }
        Object attStmtNode = map.get("attStmt");
        if (!(attStmtNode instanceof Map<?, ?> attStmt)) {
            throw new IllegalStateException("Attestation statement missing");
        }
        Object signatureNode = attStmt.get("sig");
        if (signatureNode instanceof byte[] signature) {
            return signature;
        }
        throw new IllegalStateException("Attestation statement missing signature");
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || needle.length > haystack.length) {
            return -1;
        }
        int matchIndex = -1;
        for (int i = 0; i <= haystack.length - needle.length && matchIndex < 0; i++) {
            boolean matches = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                matchIndex = i;
            }
        }
        return matchIndex;
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static EcdsaComponents parseEcdsaSignature(byte[] derSignature, WebAuthnSignatureAlgorithm algorithm) {
        int offset = 0;
        if (derSignature.length < 8 || derSignature[offset++] != 0x30) {
            throw new IllegalArgumentException("ECDSA signature must be a DER sequence");
        }
        LengthResult sequence = readLength(derSignature, offset);
        offset = sequence.nextOffset();
        if (offset + sequence.length() > derSignature.length) {
            throw new IllegalArgumentException("ECDSA signature truncated");
        }
        if (derSignature[offset++] != 0x02) {
            throw new IllegalArgumentException("ECDSA signature missing R integer");
        }
        LengthResult rLength = readLength(derSignature, offset);
        offset = rLength.nextOffset();
        byte[] r = Arrays.copyOfRange(derSignature, offset, offset + rLength.length());
        offset += rLength.length();
        if (derSignature[offset++] != 0x02) {
            throw new IllegalArgumentException("ECDSA signature missing S integer");
        }
        LengthResult sLength = readLength(derSignature, offset);
        offset = sLength.nextOffset();
        byte[] s = Arrays.copyOfRange(derSignature, offset, offset + sLength.length());
        byte[] normalizedR = stripLeadingZeros(r);
        byte[] normalizedS = stripLeadingZeros(s);
        BigInteger sValue = new BigInteger(1, normalizedS);
        boolean lowS = isLowS(algorithm, sValue);
        return new EcdsaComponents(hex(normalizedR), hex(normalizedS), lowS);
    }

    @SuppressWarnings("PMD.AssignmentInOperand")
    private static LengthResult readLength(byte[] der, int offset) {
        int first = der[offset++] & 0xFF;
        if ((first & 0x80) == 0) {
            return new LengthResult(first, offset);
        }
        int byteCount = first & 0x7F;
        if (byteCount == 0 || byteCount > 4) {
            throw new IllegalArgumentException("Unsupported DER length encoding");
        }
        int length = 0;
        for (int i = 0; i < byteCount; i++) {
            length = (length << 8) | (der[offset++] & 0xFF);
        }
        return new LengthResult(length, offset);
    }

    private static byte[] stripLeadingZeros(byte[] value) {
        int index = 0;
        while (index < value.length - 1 && value[index] == 0) {
            index++;
        }
        return Arrays.copyOfRange(value, index, value.length);
    }

    private static boolean isLowS(WebAuthnSignatureAlgorithm algorithm, BigInteger s) {
        BigInteger order =
                switch (algorithm) {
                    case ES256 -> P256_ORDER;
                    case ES384 -> P384_ORDER;
                    case ES512 -> P521_ORDER;
                    default -> null;
                };
        if (order == null) {
            return true;
        }
        return s.compareTo(order.shiftRight(1)) <= 0;
    }

    private static Map<Integer, Object> decodeCoseMap(byte[] coseKey) {
        try {
            Object decoded = io.openauth.sim.core.fido2.CborDecoder.decode(coseKey);
            if (!(decoded instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("COSE key is not a CBOR map");
            }
            Map<Integer, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof Number number)) {
                    throw new IllegalArgumentException("COSE key contains non-integer identifiers");
                }
                result.put(number.intValue(), entry.getValue());
            }
            return result;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decode COSE key", ex);
        }
    }

    private record LengthResult(int length, int nextOffset) {
        // Represents the parsed DER length and cursor location for helper assertions.
    }

    private record EcdsaComponents(String rHex, String sHex, boolean lowS) {
        // Stores expected ECDSA components used when validating low-S handling.
    }

    private static int requireInt(Map<Integer, Object> map, int key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Missing integer field " + key);
    }

    private static byte[] requireBytes(Map<Integer, Object> map, int key) {
        Object value = map.get(key);
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.toByteArray();
        }
        throw new IllegalArgumentException("Missing byte field " + key);
    }

    private static String coseKeyTypeName(int keyType) {
        return switch (keyType) {
            case 1 -> "OKP";
            case 2 -> "EC2";
            case 3 -> "RSA";
            default -> "UNKNOWN";
        };
    }

    private static String coseCurveName(int curve) {
        return switch (curve) {
            case 1 -> "P-256";
            case 2 -> "P-384";
            case 3 -> "P-521";
            case 6 -> "Ed25519";
            default -> "UNKNOWN";
        };
    }

    private static Map<String, String> jwkFieldsForThumbprint(int keyType, Map<Integer, Object> cose) {
        Map<String, String> fields = new LinkedHashMap<>();
        switch (keyType) {
            case 2 -> {
                int curve = requireInt(cose, -1);
                fields.put("crv", coseCurveName(curve));
                fields.put("kty", "EC");
                fields.put("x", base64Url(requireBytes(cose, -2)));
                fields.put("y", base64Url(requireBytes(cose, -3)));
            }
            case 3 -> {
                fields.put("e", base64Url(requireBytes(cose, -2)));
                fields.put("kty", "RSA");
                fields.put("n", base64Url(requireBytes(cose, -1)));
            }
            case 1 -> {
                int curve = requireInt(cose, -1);
                fields.put("crv", coseCurveName(curve));
                fields.put("kty", "OKP");
                fields.put("x", base64Url(requireBytes(cose, -2)));
            }
            default -> {
                // Unsupported key type, return empty map to skip thumbprint assertion.
            }
        }
        return fields;
    }

    private static String jwkThumbprint(Map<String, String> fields) {
        if (fields.isEmpty()) {
            return "";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"')
                    .append(entry.getKey())
                    .append('"')
                    .append(':')
                    .append('"')
                    .append(entry.getValue())
                    .append('"');
        }
        json.append('}');
        byte[] digest = sha256(json.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static final class TestCborDecoder {

        private final byte[] data;
        private int index;

        private TestCborDecoder(byte[] data) {
            this.data = data;
        }

        static Object decode(byte[] data) {
            try {
                TestCborDecoder decoder = new TestCborDecoder(data);
                Object result = decoder.readData();
                decoder.ensureFullyConsumed();
                return result;
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Invalid CBOR payload", ex);
            }
        }

        private void ensureFullyConsumed() throws GeneralSecurityException {
            if (index != data.length) {
                throw new GeneralSecurityException("Unexpected trailing CBOR data");
            }
        }

        private Object readData() throws GeneralSecurityException {
            int initial = readUnsignedByte();
            int majorType = initial >>> 5;
            int additional = initial & 0x1F;

            return switch (majorType) {
                case 0 -> readLength(additional);
                case 1 -> -1 - readLength(additional);
                case 2 -> readByteString(additional);
                case 3 -> readTextString(additional);
                case 4 -> readArray(additional);
                case 5 -> readMap(additional);
                case 6 -> {
                    readLength(additional);
                    yield readData();
                }
                case 7 -> readSimpleValue(additional);
                default -> throw new GeneralSecurityException("Unsupported CBOR major type: " + majorType);
            };
        }

        private byte[] readByteString(int additionalInfo) throws GeneralSecurityException {
            int length = (int) readLength(additionalInfo);
            byte[] value = new byte[length];
            System.arraycopy(data, index, value, 0, length);
            index += length;
            return value;
        }

        private String readTextString(int additionalInfo) throws GeneralSecurityException {
            int length = (int) readLength(additionalInfo);
            String value = new String(data, index, length, StandardCharsets.UTF_8);
            index += length;
            return value;
        }

        private java.util.List<Object> readArray(int additionalInfo) throws GeneralSecurityException {
            int length = (int) readLength(additionalInfo);
            java.util.List<Object> list = new java.util.ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(readData());
            }
            return list;
        }

        private Map<Object, Object> readMap(int additionalInfo) throws GeneralSecurityException {
            int length = (int) readLength(additionalInfo);
            Map<Object, Object> map = new LinkedHashMap<>(length);
            for (int i = 0; i < length; i++) {
                Object key = readData();
                Object value = readData();
                map.put(key, value);
            }
            return map;
        }

        private Object readSimpleValue(int additionalInfo) throws GeneralSecurityException {
            return switch (additionalInfo) {
                case 20 -> Boolean.FALSE;
                case 21 -> Boolean.TRUE;
                case 22 -> null;
                case 23 -> throw new GeneralSecurityException("Unsupported CBOR simple value: undefined");
                case 24 -> {
                    int value = readUnsignedByte();
                    yield (long) value;
                }
                case 25, 26, 27 -> throw new GeneralSecurityException("Floating-point CBOR values unsupported");
                case 31 -> throw new GeneralSecurityException("Indefinite-length items are unsupported");
                default -> throw new GeneralSecurityException("Unsupported CBOR simple value: " + additionalInfo);
            };
        }

        private long readLength(int additionalInfo) throws GeneralSecurityException {
            if (additionalInfo < 24) {
                return additionalInfo;
            }
            int lengthBytes =
                    switch (additionalInfo) {
                        case 24 -> 1;
                        case 25 -> 2;
                        case 26 -> 4;
                        case 27 -> 8;
                        default ->
                            throw new GeneralSecurityException("Unsupported CBOR length specifier: " + additionalInfo);
                    };
            long value = 0;
            for (int i = 0; i < lengthBytes; i++) {
                value = (value << 8) | readUnsignedByte();
            }
            return value;
        }

        private int readUnsignedByte() {
            int result = data[index] & 0xFF;
            index++;
            return result;
        }
    }

    private record ParsedAttestationData(
            byte[] authenticatorData,
            byte[] rpIdHash,
            int flags,
            long counter,
            byte[] aaguid,
            byte[] credentialId,
            byte[] credentialPublicKey) {

        private ParsedAttestationData {
            authenticatorData = authenticatorData == null ? new byte[0] : authenticatorData.clone();
            rpIdHash = rpIdHash == null ? new byte[0] : rpIdHash.clone();
            aaguid = aaguid == null ? new byte[0] : aaguid.clone();
            credentialId = credentialId == null ? new byte[0] : credentialId.clone();
            credentialPublicKey = credentialPublicKey == null ? new byte[0] : credentialPublicKey.clone();
        }

        @Override
        public byte[] authenticatorData() {
            return authenticatorData.clone();
        }

        @Override
        public byte[] rpIdHash() {
            return rpIdHash.clone();
        }

        @Override
        public byte[] aaguid() {
            return aaguid.clone();
        }

        @Override
        public byte[] credentialId() {
            return credentialId.clone();
        }

        @Override
        public byte[] credentialPublicKey() {
            return credentialPublicKey.clone();
        }

        boolean userPresence() {
            return (flags & 0x01) != 0;
        }

        boolean reservedBitRfu1() {
            return (flags & 0x02) != 0;
        }

        boolean userVerification() {
            return (flags & 0x04) != 0;
        }

        boolean backupEligible() {
            return (flags & 0x08) != 0;
        }

        boolean backupState() {
            return (flags & 0x10) != 0;
        }

        boolean reservedBitRfu2() {
            return (flags & 0x20) != 0;
        }

        boolean attestedCredentialData() {
            return (flags & 0x40) != 0;
        }

        boolean extensionDataIncluded() {
            return (flags & 0x80) != 0;
        }
    }
}
