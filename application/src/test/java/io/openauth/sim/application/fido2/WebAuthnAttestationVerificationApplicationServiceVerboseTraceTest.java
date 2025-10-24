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
import io.openauth.sim.core.fido2.WebAuthnStoredCredential;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.trace.VerboseTrace;
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
        assertEquals(sha256Label(clientDataHash), signatureBase.attributes().get("clientDataHash.sha256"));
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

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
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
