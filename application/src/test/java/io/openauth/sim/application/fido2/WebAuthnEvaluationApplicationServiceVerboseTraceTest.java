package io.openauth.sim.application.fido2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAssertionVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.fido2.WebAuthnVerificationError;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.core.trace.VerboseTrace;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class WebAuthnEvaluationApplicationServiceVerboseTraceTest {

    private static final String CREDENTIAL_ID = "fido2-trace-credential";
    private static final BigInteger P256_ORDER =
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);
    private static final BigInteger P384_ORDER = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973", 16);
    private static final BigInteger P521_ORDER = new BigInteger(
            "01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
            16);
    private static final String EXTENSIONS_CBOR_HEX =
            "a4696372656450726f7073a162726bf56a6372656450726f74656374a166706f6c696379026c6c61726765426c6f624b657958200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f206b686d61632d736563726574f5";
    private static final byte[] EXTENSIONS_CBOR = hexToBytes(EXTENSIONS_CBOR_HEX);
    private static final String LARGE_BLOB_KEY_B64U = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

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
        assertEquals("not_present", parseClientData.attributes().get("tokenBinding.status"));
        assertEquals("", parseClientData.attributes().get("tokenBinding.id"));
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

        var parseExtensions = findStep(trace, "parse.extensions");
        assertEquals("webauthn§6.5.5", parseExtensions.specAnchor());
        assertEquals(Boolean.FALSE, parseExtensions.attributes().get("extensions.present"));
        assertEquals("", parseExtensions.attributes().get("extensions.cbor.hex"));

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
        assertEquals(
                fixture.request().authenticatorData().length,
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
                WebAuthnSignatureAlgorithm.ES256.name(),
                verifySignature.attributes().get("alg"));
        assertEquals(
                WebAuthnSignatureAlgorithm.ES256.coseIdentifier(),
                verifySignature.attributes().get("cose.alg"));
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("valid"));
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("verify.ok"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("policy.lowS.enforced"));
        assertEquals(
                base64Url(fixture.request().signature()),
                verifySignature.attributes().get("sig.der.b64u"));
        Number signatureLength = (Number) verifySignature.attributes().get("sig.der.len");
        assertEquals(fixture.request().signature().length, signatureLength.intValue());
        EcdsaComponents inlineComponents =
                parseEcdsaSignature(fixture.request().signature(), WebAuthnSignatureAlgorithm.ES256);
        assertEquals(inlineComponents.rHex(), verifySignature.attributes().get("ecdsa.r.hex"));
        assertEquals(inlineComponents.sHex(), verifySignature.attributes().get("ecdsa.s.hex"));
        assertEquals(inlineComponents.lowS(), verifySignature.attributes().get("ecdsa.lowS"));

        var verifyAssertion = findStep(trace, "verify.assertion");
        assertEquals("webauthn§7.2", verifyAssertion.specAnchor());
        assertEquals("stored", verifyAssertion.attributes().get("credentialSource"));
        assertEquals(Boolean.TRUE, verifyAssertion.attributes().get("valid"));
    }

    @Test
    void storedEvaluationWithExtensionsDecodesTrace() {
        saveFixtureCredential();
        byte[] extendedAuthenticatorData =
                extendAuthenticatorData(fixture.request().authenticatorData(), EXTENSIONS_CBOR);
        byte[] extendedSignature = signAssertion(
                fixture.algorithm(),
                fixture.credentialPrivateKeyJwk(),
                extendedAuthenticatorData,
                fixture.request().clientDataJson());

        WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID,
                        fixture.request().relyingPartyId().toUpperCase(Locale.ROOT),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        extendedAuthenticatorData,
                        extendedSignature);

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertTrue(result.verboseTrace().isPresent());
        var trace = result.verboseTrace().orElseThrow();
        var parseExtensions = findStep(trace, "parse.extensions");
        assertEquals("webauthn§6.5.5", parseExtensions.specAnchor());
        assertEquals(Boolean.TRUE, parseExtensions.attributes().get("extensions.present"));
        assertEquals(EXTENSIONS_CBOR_HEX, parseExtensions.attributes().get("extensions.cbor.hex"));
        assertEquals(Boolean.TRUE, parseExtensions.attributes().get("ext.credProps.rk"));
        assertEquals("required", parseExtensions.attributes().get("ext.credProtect.policy"));
        assertEquals(LARGE_BLOB_KEY_B64U, parseExtensions.attributes().get("ext.largeBlobKey.b64u"));
        assertEquals("requested", parseExtensions.attributes().get("ext.hmac-secret"));
    }

    @Test
    void storedEvaluationRejectsHighSSignatureWhenPolicyEnforced() {
        saveFixtureCredential();
        WebAuthnEvaluationApplicationService policyService = new WebAuthnEvaluationApplicationService(
                credentialStore,
                new WebAuthnAssertionVerifier(),
                persistenceAdapter,
                WebAuthnSignaturePolicy.enforceLowSPolicy());

        byte[] highSignature = toHighSSignature(fixture.request().signature(), WebAuthnSignatureAlgorithm.ES256);

        WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID,
                        fixture.request().relyingPartyId().toUpperCase(Locale.ROOT),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        highSignature);

        WebAuthnEvaluationApplicationService.EvaluationResult result = policyService.evaluate(command, true);

        assertFalse(result.valid());
        assertEquals(Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID), result.error());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        var verifySignature = findStep(trace, "verify.signature");
        assertEquals(Boolean.TRUE, verifySignature.attributes().get("policy.lowS.enforced"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("ecdsa.lowS"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("valid"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("verify.ok"));
        assertTrue(verifySignature.notes().containsKey("error.lowS"));
    }

    @Test
    void storedEvaluationWithMalformedSignatureRecordsDecodeError() {
        saveFixtureCredential();
        byte[] malformedSignature = new byte[] {0x01, 0x02, 0x03};

        WebAuthnEvaluationApplicationService.EvaluationCommand.Stored command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Stored(
                        CREDENTIAL_ID,
                        fixture.request().relyingPartyId().toUpperCase(Locale.ROOT),
                        fixture.request().origin(),
                        fixture.request().expectedType(),
                        fixture.request().expectedChallenge(),
                        fixture.request().clientDataJson(),
                        fixture.request().authenticatorData(),
                        malformedSignature);

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertFalse(result.valid());
        assertEquals(Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID), result.error());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        var verifySignature = findStep(trace, "verify.signature");
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("valid"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("verify.ok"));
        assertEquals(base64Url(malformedSignature), verifySignature.attributes().get("sig.raw.b64u"));
        Number rawLength = (Number) verifySignature.attributes().get("sig.raw.len");
        assertEquals(malformedSignature.length, rawLength.intValue());
        assertTrue(verifySignature.notes().containsKey("signature.decode.error"));
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
        assertEquals("not_present", parseClientData.attributes().get("tokenBinding.status"));
        assertEquals("", parseClientData.attributes().get("tokenBinding.id"));

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

        var parseExtensions = findStep(trace, "parse.extensions");
        assertEquals("webauthn§6.5.5", parseExtensions.specAnchor());
        assertEquals(Boolean.FALSE, parseExtensions.attributes().get("extensions.present"));
        assertEquals("", parseExtensions.attributes().get("extensions.cbor.hex"));

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
        assertEquals(
                fixture.request().authenticatorData().length,
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
    void inlineEvaluationWithMalformedSignatureRecordsDecodeError() {
        byte[] malformedSignature = new byte[] {0x10};

        WebAuthnEvaluationApplicationService.EvaluationCommand.Inline command =
                new WebAuthnEvaluationApplicationService.EvaluationCommand.Inline(
                        "inline-malformed",
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
                        malformedSignature);

        WebAuthnEvaluationApplicationService.EvaluationResult result = service.evaluate(command, true);

        assertFalse(result.valid());
        assertEquals(Optional.of(WebAuthnVerificationError.SIGNATURE_INVALID), result.error());
        VerboseTrace trace = result.verboseTrace().orElseThrow();
        var verifySignature = findStep(trace, "verify.signature");
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("valid"));
        assertEquals(Boolean.FALSE, verifySignature.attributes().get("verify.ok"));
        assertEquals(base64Url(malformedSignature), verifySignature.attributes().get("sig.raw.b64u"));
        Number rawLength = (Number) verifySignature.attributes().get("sig.raw.len");
        assertEquals(malformedSignature.length, rawLength.intValue());
        assertTrue(verifySignature.notes().containsKey("signature.decode.error"));
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
        return new EcdsaComponents(normalizedR, normalizedS, hex(normalizedR), hex(normalizedS), lowS, sValue);
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

    private static byte[] toHighSSignature(byte[] signature, WebAuthnSignatureAlgorithm algorithm) {
        EcdsaComponents components = parseEcdsaSignature(signature, algorithm);
        BigInteger order = orderFor(algorithm);
        if (order == null) {
            return signature.clone();
        }
        BigInteger s = components.sValue();
        BigInteger highS = order.subtract(s).add(BigInteger.ONE);
        if (highS.compareTo(order) >= 0) {
            highS = order.subtract(BigInteger.ONE);
        }
        if (highS.compareTo(order.shiftRight(1)) <= 0) {
            highS = order.subtract(BigInteger.ONE);
        }
        byte[] rBytes = components.rBytes();
        byte[] sBytes = toUnsigned(highS);
        return encodeDerSignature(rBytes, sBytes);
    }

    private static BigInteger orderFor(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> P256_ORDER;
            case ES384 -> P384_ORDER;
            case ES512 -> P521_ORDER;
            default -> null;
        };
    }

    private static byte[] toUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] normalized = stripLeadingZeros(bytes);
        return normalized.length == 0 ? new byte[] {0} : normalized;
    }

    private static byte[] encodeDerSignature(byte[] rBytes, byte[] sBytes) {
        byte[] encodedR = encodeDerInteger(rBytes);
        byte[] encodedS = encodeDerInteger(sBytes);
        int payloadLength = encodedR.length + encodedS.length;
        byte[] lengthBytes = encodeDerLength(payloadLength);
        byte[] sequence = new byte[1 + lengthBytes.length + payloadLength];
        sequence[0] = 0x30;
        System.arraycopy(lengthBytes, 0, sequence, 1, lengthBytes.length);
        System.arraycopy(encodedR, 0, sequence, 1 + lengthBytes.length, encodedR.length);
        System.arraycopy(encodedS, 0, sequence, 1 + lengthBytes.length + encodedR.length, encodedS.length);
        return sequence;
    }

    private static byte[] encodeDerInteger(byte[] value) {
        byte[] normalized = stripLeadingZeros(value);
        boolean needsPadding = normalized.length == 0 || (normalized[0] & 0x80) != 0;
        int valueLength = normalized.length + (needsPadding ? 1 : 0);
        byte[] lengthBytes = encodeDerLength(valueLength);
        byte[] result = new byte[1 + lengthBytes.length + valueLength];
        result[0] = 0x02;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        int offset = 1 + lengthBytes.length;
        if (needsPadding) {
            result[offset] = 0x00;
            offset++;
        }
        System.arraycopy(normalized, 0, result, offset, normalized.length);
        return result;
    }

    private static byte[] encodeDerLength(int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }
        byte[] bytes = BigInteger.valueOf(length).toByteArray();
        byte[] normalized = stripLeadingZeros(bytes);
        byte prefix = (byte) (0x80 | normalized.length);
        byte[] result = new byte[1 + normalized.length];
        result[0] = prefix;
        System.arraycopy(normalized, 0, result, 1, normalized.length);
        return result;
    }

    private record LengthResult(int length, int nextOffset) {
        // Represents the parsed DER length and cursor location for helper assertions.
    }

    private record EcdsaComponents(
            byte[] rBytes, byte[] sBytes, String rHex, String sHex, boolean lowS, BigInteger sValue) {
        // Captures derived ECDSA values used to craft high-S signatures for test coverage.
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

    private static byte[] extendAuthenticatorData(byte[] authenticatorData, byte[] extensions) {
        byte[] original = authenticatorData == null ? new byte[0] : authenticatorData;
        if (original.length < 33) {
            throw new IllegalArgumentException("Authenticator data must contain RP hash and flags");
        }
        byte[] extended = Arrays.copyOf(original, original.length + extensions.length);
        extended[32] = (byte) (extended[32] | 0x80);
        System.arraycopy(extensions, 0, extended, original.length, extensions.length);
        return extended;
    }

    private static byte[] signAssertion(
            WebAuthnSignatureAlgorithm algorithm,
            String privateKeyJwk,
            byte[] authenticatorData,
            byte[] clientDataJson) {
        try {
            PrivateKey privateKey = privateKeyFromJwk(privateKeyJwk, algorithm);
            Signature signature = signatureFor(algorithm);
            signature.initSign(privateKey);
            byte[] clientDataHash = sha256(clientDataJson);
            byte[] payload = concat(authenticatorData, clientDataHash);
            signature.update(payload);
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign assertion with extensions", ex);
        }
    }

    private static PrivateKey privateKeyFromJwk(String jwk, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        Object parsed = SimpleJson.parse(jwk);
        if (!(parsed instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("JWK must be a JSON object");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));
        String d = requireString(map, "d");
        String curve = requireString(map, "crv");
        byte[] privateScalar = base64UrlDecode(d);

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName(algorithm)));
        ECParameterSpec parameterSpec = parameters.getParameterSpec(ECParameterSpec.class);

        if (!curve.equalsIgnoreCase(namedCurveLabel(algorithm))) {
            throw new IllegalArgumentException(
                    "JWK curve " + curve + " does not match expected " + namedCurveLabel(algorithm));
        }

        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(new BigInteger(1, privateScalar), parameterSpec);
        try {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        } catch (InvalidKeySpecException ex) {
            throw new GeneralSecurityException("Unable to materialise EC private key from JWK", ex);
        }
    }

    private static Signature signatureFor(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256 -> Signature.getInstance("SHA256withECDSA");
            case ES384 -> Signature.getInstance("SHA384withECDSA");
            case ES512 -> Signature.getInstance("SHA512withECDSA");
            case RS256 -> Signature.getInstance("SHA256withRSA");
            case PS256 -> Signature.getInstance("RSASSA-PSS");
            case EDDSA -> Signature.getInstance("Ed25519");
        };
    }

    private static String curveName(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "secp256r1";
            case ES384 -> "secp384r1";
            case ES512 -> "secp521r1";
            case RS256, PS256, EDDSA ->
                throw new IllegalArgumentException("Unsupported algorithm for EC curve: " + algorithm);
        };
    }

    private static String namedCurveLabel(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "P-256";
            case ES384 -> "P-384";
            case ES512 -> "P-521";
            case RS256, PS256, EDDSA ->
                throw new IllegalArgumentException("Unsupported algorithm for EC curve: " + algorithm);
        };
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException("Missing or blank JWK field: " + key);
        }
        return string;
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return bytes;
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
