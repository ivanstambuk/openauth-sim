package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnEvaluationApplicationServiceVerboseTraceTest {

    private static final String CREDENTIAL_ID = "fido2-trace-credential";

    private WebAuthnFixture fixture;
    private InMemoryCredentialStore credentialStore;
    private WebAuthnCredentialPersistenceAdapter persistenceAdapter;
    private WebAuthnEvaluationApplicationService service;

    @BeforeEach
    void setUp() {
        fixture = WebAuthnFixtures.loadPackedEs256();
        credentialStore = new InMemoryCredentialStore();
        persistenceAdapter = new WebAuthnCredentialPersistenceAdapter();
        service = new WebAuthnEvaluationApplicationService(
                credentialStore, new WebAuthnAssertionVerifier(), persistenceAdapter);
    }

    @Test
    void storedEvaluationWithVerboseCapturesTrace() {
        saveFixtureCredential();
        WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID,
                        fixture.request().relyingPartyId().toUpperCase(Locale.ROOT),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        fixture.request().signature());

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("fido2.assertion.evaluate.stored", trace.operation());
        assertEquals("FIDO2", trace.metadata().get("protocol"));
        assertEquals("stored", trace.metadata().get("mode"));
        assertEquals(CREDENTIAL_ID, trace.metadata().get("credentialName"));
        assertEquals("educational", trace.metadata().get("tier"));

        var parseClientData = findStep(trace, "parse.clientData");
        var clientDataJson = new String(fixture.request().clientDataJson(), StandardCharsets.UTF_8);
        assertEquals("webauthn§6.5.1", parseClientData.specAnchor());
        assertEquals(
                fixture.request().expectedType(), parseClientData.attributes().get("type"));
        assertEquals(
                fixture.request().expectedType(), parseClientData.attributes().get("expected.type"));
        assertEquals(Boolean.TRUE, parseClientData.attributes().get("type.match"));
        assertEquals(
                base64Url(fixture.request().expectedChallenge()),
                parseClientData.attributes().get("challenge.b64u"));
        assertEquals(
                fixture.request().expectedChallenge().length,
                parseClientData.attributes().get("challenge.decoded.len"));
        assertEquals(fixture.request().origin(), parseClientData.attributes().get("origin"));
        assertEquals(fixture.request().origin(), parseClientData.attributes().get("origin.expected"));
        assertEquals(Boolean.TRUE, parseClientData.attributes().get("origin.match"));
        assertEquals(clientDataJson, parseClientData.attributes().get("clientData.json"));
        assertEquals(
                sha256Digest(fixture.request().clientDataJson()),
                parseClientData.attributes().get("clientDataHash.sha256"));
        assertEquals(Boolean.FALSE, parseClientData.attributes().get("tokenBinding.present"));
        assertTrue(parseClientData.typedAttributes().stream()
                .anyMatch(attr ->
                        "clientData.json".equals(attr.name()) && attr.type() == VerboseTrace.AttributeType.JSON));

        var authenticator = parseAuthenticatorData(fixture.request().authenticatorData());
        var parseAuthenticatorData = findStep(trace, "parse.authenticatorData");
        assertEquals("webauthn§6.5.4", parseAuthenticatorData.specAnchor());
        assertEquals(
                hex(authenticator.rpIdHash()),
                parseAuthenticatorData.attributes().get("rpIdHash.hex"));
        assertEquals(
                fixture.request().relyingPartyId(),
                parseAuthenticatorData.attributes().get("rpId.canonical"));
        String expectedRpIdDigest =
                sha256Digest(fixture.request().relyingPartyId().getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedRpIdDigest, parseAuthenticatorData.attributes().get("rpIdHash.expected"));
        assertEquals(Boolean.TRUE, parseAuthenticatorData.attributes().get("rpIdHash.match"));
        assertEquals(expectedRpIdDigest, parseAuthenticatorData.attributes().get("rpId.expected.sha256"));
        assertEquals(
                formatByte(authenticator.flags()),
                parseAuthenticatorData.attributes().get("flags.byte"));
        assertEquals(
                authenticator.userPresence(),
                parseAuthenticatorData.attributes().get("flags.bits.UP"));
        assertEquals(
                authenticator.reservedBitRfu1(),
                parseAuthenticatorData.attributes().get("flags.bits.RFU1"));
        assertEquals(
                authenticator.userVerification(),
                parseAuthenticatorData.attributes().get("flags.bits.UV"));
        assertEquals(
                authenticator.backupEligible(),
                parseAuthenticatorData.attributes().get("flags.bits.BE"));
        assertEquals(
                authenticator.backupState(), parseAuthenticatorData.attributes().get("flags.bits.BS"));
        assertEquals(
                authenticator.reservedBitRfu2(),
                parseAuthenticatorData.attributes().get("flags.bits.RFU2"));
        assertEquals(
                authenticator.attestedCredentialData(),
                parseAuthenticatorData.attributes().get("flags.bits.AT"));
        assertEquals(
                authenticator.extensionDataIncluded(),
                parseAuthenticatorData.attributes().get("flags.bits.ED"));
        assertEquals(
                fixture.storedCredential().userVerificationRequired(),
                parseAuthenticatorData.attributes().get("userVerificationRequired"));
        assertEquals(
                !fixture.storedCredential().userVerificationRequired() || authenticator.userVerification(),
                parseAuthenticatorData.attributes().get("uv.policy.ok"));
        assertEquals(
                fixture.storedCredential().signatureCounter(),
                parseAuthenticatorData.attributes().get("counter.stored"));
        assertEquals(
                authenticator.counter(), parseAuthenticatorData.attributes().get("counter.reported"));

        var counterStep = findStep(trace, "evaluate.counter");
        assertEquals("webauthn§6.5.4", counterStep.specAnchor());
        assertEquals(
                fixture.storedCredential().signatureCounter(),
                counterStep.attributes().get("counter.previous"));
        assertEquals(authenticator.counter(), counterStep.attributes().get("counter.reported"));
        assertEquals(
                authenticator.counter() > fixture.storedCredential().signatureCounter(),
                counterStep.attributes().get("counter.incremented"));

        var signatureBase = findStep(trace, "build.signatureBase");
        assertEquals("webauthn§6.5.5", signatureBase.specAnchor());
        byte[] clientDataHash = sha256(fixture.request().clientDataJson());
        byte[] signaturePayload = concat(fixture.request().authenticatorData(), clientDataHash);
        assertEquals(
                hex(fixture.request().authenticatorData()),
                signatureBase.attributes().get("authenticatorData.hex"));
        assertEquals(sha256Label(clientDataHash), signatureBase.attributes().get("clientDataHash.sha256"));
        assertEquals(sha256Digest(signaturePayload), signatureBase.attributes().get("signedBytes.sha256"));

        var verifySignature = findStep(trace, "verify.signature");
        assertEquals("webauthn§6.5.5", verifySignature.specAnchor());
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.name(),
                verifySignature.attributes().get("alg"));
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.coseIdentifier(),
                verifySignature.attributes().get("cose.alg"));
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("valid"));

        var verifyAssertion = findStep(trace, "verify.assertion");
        assertEquals("webauthn§7.2", verifyAssertion.specAnchor());
        assertEquals("stored", verifyAssertion.attributes().get("credentialSource"));
        assertEquals(Boolean.TRUE, verifyAssertion.attributes().get("valid"));
    }

    @Test
    void inlineEvaluationWithVerboseCapturesTrace() {
        WebAuthnEvaluationApplicationService.EvaluationCommand.Inline command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Inline(
                        "inline-fixture",
                        fixture.request().relyingPartyId().toUpperCase(Locale.ROOT),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.storedCredential().credentialId(),
                        fixture.storedCredential().publicKeyCose(),
                        fixture.storedCredential().signatureCounter(),
                        fixture.storedCredential().userVerificationRequired(),
                        WebAuthnSignatureAlgorithm.ES256,
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        fixture.request().signature());

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        assertEquals("fido2.assertion.evaluate.inline", trace.operation());
        assertEquals("inline", trace.metadata().get("mode"));
        assertEquals("inline-fixture", trace.metadata().get("credentialName"));
        assertEquals("educational", trace.metadata().get("tier"));

        var parseClientData = findStep(trace, "parse.clientData");
        var clientDataJson = new String(fixture.request().clientDataJson(), StandardCharsets.UTF_8);
        assertEquals("webauthn§6.5.1", parseClientData.specAnchor());
        assertEquals(
                fixture.request().expectedType(), parseClientData.attributes().get("type"));
        assertEquals(
                fixture.request().expectedType(), parseClientData.attributes().get("expected.type"));
        assertEquals(Boolean.TRUE, parseClientData.attributes().get("type.match"));
        assertEquals(
                base64Url(fixture.request().expectedChallenge()),
                parseClientData.attributes().get("challenge.b64u"));
        assertEquals(
                fixture.request().expectedChallenge().length,
                parseClientData.attributes().get("challenge.decoded.len"));
        assertEquals(fixture.request().origin(), parseClientData.attributes().get("origin"));
        assertEquals(fixture.request().origin(), parseClientData.attributes().get("origin.expected"));
        assertEquals(Boolean.TRUE, parseClientData.attributes().get("origin.match"));
        assertEquals(clientDataJson, parseClientData.attributes().get("clientData.json"));
        assertEquals(
                sha256Digest(fixture.request().clientDataJson()),
                parseClientData.attributes().get("clientDataHash.sha256"));
        assertEquals(Boolean.FALSE, parseClientData.attributes().get("tokenBinding.present"));

        var authenticator = parseAuthenticatorData(fixture.request().authenticatorData());
        var parseAuthenticatorData = findStep(trace, "parse.authenticatorData");
        assertEquals("webauthn§6.5.4", parseAuthenticatorData.specAnchor());
        assertEquals(
                hex(authenticator.rpIdHash()),
                parseAuthenticatorData.attributes().get("rpIdHash.hex"));
        assertEquals(
                fixture.request().relyingPartyId(),
                parseAuthenticatorData.attributes().get("rpId.canonical"));
        String inlineExpectedDigest =
                sha256Digest(fixture.request().relyingPartyId().getBytes(StandardCharsets.UTF_8));
        assertEquals(inlineExpectedDigest, parseAuthenticatorData.attributes().get("rpIdHash.expected"));
        assertEquals(Boolean.TRUE, parseAuthenticatorData.attributes().get("rpIdHash.match"));
        assertEquals(inlineExpectedDigest, parseAuthenticatorData.attributes().get("rpId.expected.sha256"));
        assertEquals(
                formatByte(authenticator.flags()),
                parseAuthenticatorData.attributes().get("flags.byte"));
        assertEquals(
                authenticator.userPresence(),
                parseAuthenticatorData.attributes().get("flags.bits.UP"));
        assertEquals(
                authenticator.reservedBitRfu1(),
                parseAuthenticatorData.attributes().get("flags.bits.RFU1"));
        assertEquals(
                authenticator.userVerification(),
                parseAuthenticatorData.attributes().get("flags.bits.UV"));
        assertEquals(
                authenticator.backupEligible(),
                parseAuthenticatorData.attributes().get("flags.bits.BE"));
        assertEquals(
                authenticator.backupState(), parseAuthenticatorData.attributes().get("flags.bits.BS"));
        assertEquals(
                authenticator.reservedBitRfu2(),
                parseAuthenticatorData.attributes().get("flags.bits.RFU2"));
        assertEquals(
                authenticator.attestedCredentialData(),
                parseAuthenticatorData.attributes().get("flags.bits.AT"));
        assertEquals(
                authenticator.extensionDataIncluded(),
                parseAuthenticatorData.attributes().get("flags.bits.ED"));
        assertEquals(
                command.userVerificationRequired(),
                parseAuthenticatorData.attributes().get("userVerificationRequired"));
        assertEquals(
                !command.userVerificationRequired() || authenticator.userVerification(),
                parseAuthenticatorData.attributes().get("uv.policy.ok"));
        assertEquals(
                command.signatureCounter(), parseAuthenticatorData.attributes().get("counter.stored"));
        assertEquals(
                authenticator.counter(), parseAuthenticatorData.attributes().get("counter.reported"));

        var constructStep = findStep(trace, "construct.credential");
        assertEquals("webauthn§6.1", constructStep.specAnchor());
        assertEquals(
                fixture.request().relyingPartyId(), constructStep.attributes().get("rpId.canonical"));
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.name(),
                constructStep.attributes().get("alg"));
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.coseIdentifier(),
                constructStep.attributes().get("cose.alg"));
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.name(),
                constructStep.attributes().get("cose.alg.name"));
        assertEquals(hex(command.publicKeyCose()), constructStep.attributes().get("publicKey.cose.hex"));
        Map<Integer, Object> cose = decodeCoseMap(command.publicKeyCose());
        int coseKeyType = requireInt(cose, 1);
        assertEquals(coseKeyType, constructStep.attributes().get("cose.kty"));
        assertEquals(coseKeyTypeName(coseKeyType), constructStep.attributes().get("cose.kty.name"));
        int coseCurve = requireInt(cose, -1);
        assertEquals(coseCurve, constructStep.attributes().get("cose.crv"));
        assertEquals(coseCurveName(coseCurve), constructStep.attributes().get("cose.crv.name"));
        byte[] coseX = requireBytes(cose, -2);
        byte[] coseY = requireBytes(cose, -3);
        assertEquals(base64Url(coseX), constructStep.attributes().get("cose.x.b64u"));
        assertEquals(base64Url(coseY), constructStep.attributes().get("cose.y.b64u"));
        assertEquals(
                jwkThumbprint(ecJwkFields(coseCurveName(coseCurve), coseX, coseY)),
                constructStep.attributes().get("publicKey.jwk.thumbprint.sha256"));

        var counterStep = findStep(trace, "evaluate.counter");
        assertEquals("webauthn§6.5.4", counterStep.specAnchor());
        assertEquals(command.signatureCounter(), counterStep.attributes().get("counter.previous"));
        assertEquals(authenticator.counter(), counterStep.attributes().get("counter.reported"));
        assertEquals(
                authenticator.counter() > command.signatureCounter(),
                counterStep.attributes().get("counter.incremented"));

        var signatureBase = findStep(trace, "build.signatureBase");
        assertEquals("webauthn§6.5.5", signatureBase.specAnchor());
        byte[] clientDataHash = sha256(fixture.request().clientDataJson());
        byte[] signaturePayload = concat(fixture.request().authenticatorData(), clientDataHash);
        assertEquals(
                hex(fixture.request().authenticatorData()),
                signatureBase.attributes().get("authenticatorData.hex"));
        assertEquals(sha256Label(clientDataHash), signatureBase.attributes().get("clientDataHash.sha256"));
        assertEquals(sha256Digest(signaturePayload), signatureBase.attributes().get("signedBytes.sha256"));

        var verifySignature = findStep(trace, "verify.signature");
        assertEquals("webauthn§6.5.5", verifySignature.specAnchor());
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.name(),
                verifySignature.attributes().get("alg"));
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.coseIdentifier(),
                verifySignature.attributes().get("cose.alg"));
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("valid"));

        var verifyAssertion = findStep(trace, "verify.assertion");
        assertEquals("webauthn§7.2", verifyAssertion.specAnchor());
        assertEquals("inline", verifyAssertion.attributes().get("credentialSource"));
        assertEquals(Boolean.TRUE, verifyAssertion.attributes().get("valid"));
    }

    @Test
    void verboseDisabledLeavesTraceEmpty() {
        saveFixtureCredential();
        WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID,
                        fixture.request().relyingPartyId(),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        fixture.request().signature());

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command);

        assertTrue(result.verboseTrace().isEmpty());
    }

    private void saveFixtureCredential() {
        WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                .name(CREDENTIAL_ID)
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(WebAuthnSignatureAlgorithm.ES256)
                .build();

        Credential credential = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
        credentialStore.save(credential);
    }

    private static final class InMemoryCredentialStore implements CredentialStore {
        private final Map<String, Credential> data = new ConcurrentHashMap<>();

        @Override
        public void save(Credential credential) {
            data.put(credential.name(), credential);
        }

        @Override
        public Optional<Credential> findByName(String name) {
            return Optional.ofNullable(data.get(name));
        }

        @Override
        public java.util.List<Credential> findAll() {
            return java.util.List.copyOf(data.values());
        }

        @Override
        public boolean delete(String name) {
            return data.remove(name) != null;
        }

        @Override
        public void close() {
            data.clear();
        }
    }

    private static VerboseTrace.TraceStep findStep(VerboseTrace trace, String id) {
        return trace.steps().stream()
                .filter(step -> id.equals(step.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing trace step: " + id));
    }

    private static ParsedAuthenticatorData parseAuthenticatorData(byte[] authenticatorData) {
        if (authenticatorData.length < 37) {
            throw new IllegalArgumentException("Authenticator data must be at least 37 bytes");
        }
        byte[] rpIdHash = Arrays.copyOfRange(authenticatorData, 0, 32);
        int flags = authenticatorData[32] & 0xFF;
        ByteBuffer counterBuffer = ByteBuffer.wrap(authenticatorData, 33, 4);
        long counter = counterBuffer.getInt() & 0xFFFFFFFFL;
        return new ParsedAuthenticatorData(rpIdHash, flags, counter);
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

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] combined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
    }

    private record ParsedAuthenticatorData(byte[] rpIdHash, int flags, long counter) {

        private ParsedAuthenticatorData {
            rpIdHash = rpIdHash == null ? new byte[0] : rpIdHash.clone();
        }

        @Override
        public byte[] rpIdHash() {
            return rpIdHash.clone();
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

    private static Map<Integer, Object> decodeCoseMap(byte[] coseKey) {
        try {
            Object decoded = io.openauth.sim.core.fido2.CborDecoder.decode(coseKey);
            if (!(decoded instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("COSE key is not a CBOR map");
            }
            return raw.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> ((Number) entry.getKey()).intValue(),
                            Map.Entry::getValue,
                            (a, b) -> b,
                            java.util.LinkedHashMap::new));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decode COSE key", ex);
        }
    }

    private static int requireInt(Map<Integer, Object> cose, int key) {
        Object value = cose.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Missing integer field " + key);
    }

    private static byte[] requireBytes(Map<Integer, Object> cose, int key) {
        Object value = cose.get(key);
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

    private static Map<String, String> ecJwkFields(String curve, byte[] x, byte[] y) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("crv", curve);
        fields.put("kty", "EC");
        fields.put("x", base64Url(x));
        fields.put("y", base64Url(y));
        return fields;
    }

    private static String jwkThumbprint(Map<String, String> fields) {
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
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
