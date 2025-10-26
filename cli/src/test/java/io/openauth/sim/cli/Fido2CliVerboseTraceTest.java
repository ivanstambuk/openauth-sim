package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.SignatureInspector;
import io.openauth.sim.core.fido2.SignatureInspector.EcdsaSignatureDetails;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class Fido2CliVerboseTraceTest {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String EXTENSIONS_CBOR_HEX =
            "a4696372656450726f7073a162726bf56a6372656450726f74656374a166706f6c696379026c6c61726765426c6f624b657958200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f206b686d61632d736563726574f5";
    private static final byte[] EXTENSIONS_CBOR = hexToBytes(EXTENSIONS_CBOR_HEX);
    private static final String LARGE_BLOB_KEY_B64U = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    @TempDir
    Path tempDir;

    @Test
    void replayStoredEmitsVerboseTrace() throws Exception {
        Path database = tempDir.resolve("fido2-trace.db");
        CommandHarness harness = CommandHarness.create(database);

        WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
        harness.save("fido2-packed-es256", fixture, WebAuthnSignatureAlgorithm.ES256);

        int exitCode = harness.execute(
                "replay",
                "--credential-id",
                "fido2-packed-es256",
                "--relying-party-id",
                "EXAMPLE.ORG",
                "--origin",
                "https://example.org",
                "--type",
                fixture.request().expectedType(),
                "--expected-challenge",
                encode(fixture.request().expectedChallenge()),
                "--client-data",
                encode(fixture.request().clientDataJson()),
                "--authenticator-data",
                encode(fixture.request().authenticatorData()),
                "--signature",
                encode(fixture.request().signature()),
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("=== Verbose Trace ==="), stdout);
        assertTrue(stdout.contains("operation=fido2.assertion.evaluate.stored"), stdout);
        assertTrue(stdout.contains("metadata.mode=stored"), stdout);
        assertTrue(stdout.contains("resolve.credential"), stdout);
        assertTrue(stdout.contains("verify.assertion"), stdout);
        assertTrue(stdout.contains("metadata.tier=educational"), stdout);

        String clientDataJson = new String(fixture.request().clientDataJson(), StandardCharsets.UTF_8);
        String expectedClientDataSha256 = sha256Digest(fixture.request().clientDataJson());
        assertTrue(stdout.contains("  clientData.json = " + clientDataJson), stdout);
        assertTrue(stdout.contains("  clientDataHash.sha256 = " + expectedClientDataSha256), stdout);
        assertTrue(
                stdout.contains(
                        "  challenge.b64u = " + base64Url(fixture.request().expectedChallenge())),
                stdout);
        assertTrue(
                stdout.contains("  challenge.decoded.len = " + fixture.request().expectedChallenge().length), stdout);
        assertTrue(stdout.contains("  expected.type = " + fixture.request().expectedType()), stdout);
        assertTrue(stdout.contains("  type.match = true"), stdout);

        byte[] authenticatorData = fixture.request().authenticatorData();
        String rpIdHashHex = hex(Arrays.copyOf(authenticatorData, 32));
        assertTrue(stdout.contains("  rpIdHash.hex = " + rpIdHashHex), stdout);

        String expectedRpDigest =
                sha256Digest(fixture.request().relyingPartyId().getBytes(StandardCharsets.UTF_8));
        assertTrue(stdout.contains("  rpId.canonical = " + fixture.request().relyingPartyId()), stdout);
        assertTrue(stdout.contains("  rpIdHash.expected = " + expectedRpDigest), stdout);
        assertTrue(stdout.contains("  rpIdHash.match = true"), stdout);
        assertTrue(stdout.contains("  rpId.expected.sha256 = " + expectedRpDigest), stdout);

        int flagsByte = authenticatorData[32] & 0xFF;
        assertTrue(stdout.contains("  flags.byte = " + formatByte(flagsByte)), stdout);
        assertTrue(stdout.contains("  flags.bits.UP = " + String.valueOf((flagsByte & 0x01) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.RFU1 = " + String.valueOf((flagsByte & 0x02) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.UV = " + String.valueOf((flagsByte & 0x04) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.BE = " + String.valueOf((flagsByte & 0x08) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.BS = " + String.valueOf((flagsByte & 0x10) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.RFU2 = " + String.valueOf((flagsByte & 0x20) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.AT = " + String.valueOf((flagsByte & 0x40) != 0)), stdout);
        assertTrue(stdout.contains("  flags.bits.ED = " + String.valueOf((flagsByte & 0x80) != 0)), stdout);
        boolean userVerificationRequired = fixture.storedCredential().userVerificationRequired();
        assertTrue(stdout.contains("  userVerificationRequired = " + String.valueOf(userVerificationRequired)), stdout);
        boolean uvPolicyOk = !userVerificationRequired || (flagsByte & 0x04) != 0;
        assertTrue(stdout.contains("  uv.policy.ok = " + String.valueOf(uvPolicyOk)), stdout);
        assertTrue(stdout.contains("  origin.expected = " + fixture.request().origin()), stdout);
        assertTrue(stdout.contains("  origin.match = true"), stdout);
        assertTrue(stdout.contains("  tokenBinding.present = false"), stdout);
        assertTrue(stdout.contains("  tokenBinding.status = "), stdout);
        assertTrue(stdout.contains("  tokenBinding.id = "), stdout);

        long storedCounter = fixture.storedCredential().signatureCounter();
        long reportedCounter = counterFromAuthenticatorData(authenticatorData);
        assertTrue(stdout.contains("  counter.stored = " + storedCounter), stdout);
        assertTrue(stdout.contains("  counter.reported = " + reportedCounter), stdout);
        assertTrue(stdout.contains("  extensions.present = false"), stdout);
        assertTrue(stdout.contains("  extensions.cbor.hex = "), stdout);

        byte[] clientDataHash = sha256Bytes(fixture.request().clientDataJson());
        byte[] signaturePayload = concat(authenticatorData, clientDataHash);
        String signatureBaseSha256 = sha256Digest(signaturePayload);
        assertTrue(stdout.contains("  authenticatorData.len.bytes = " + authenticatorData.length), stdout);
        assertTrue(stdout.contains("  clientDataHash.len.bytes = " + clientDataHash.length), stdout);
        assertTrue(stdout.contains("  signedBytes.hex = " + hex(signaturePayload)), stdout);
        assertTrue(stdout.contains("  signedBytes.len.bytes = " + signaturePayload.length), stdout);
        assertTrue(stdout.contains("  signedBytes.preview = " + previewHex(signaturePayload)), stdout);
        assertTrue(stdout.contains("  signedBytes.sha256 = " + signatureBaseSha256), stdout);
        assertTrue(stdout.contains("  alg = " + WebAuthnSignatureAlgorithm.ES256.name()), stdout);
        assertTrue(stdout.contains("  cose.alg = " + WebAuthnSignatureAlgorithm.ES256.coseIdentifier()), stdout);
        assertTrue(stdout.contains("  cose.alg.name = " + WebAuthnSignatureAlgorithm.ES256.name()), stdout);
        assertTrue(
                stdout.contains("  sig.der.b64u = " + encode(fixture.request().signature())), stdout);
        assertTrue(stdout.contains("  sig.der.len = " + fixture.request().signature().length), stdout);
        Map<Integer, Object> cose = decodeCoseMap(fixture.storedCredential().publicKeyCose());
        int coseKeyType = requireInt(cose, 1);
        assertTrue(stdout.contains("  cose.kty = " + coseKeyType), stdout);
        assertTrue(stdout.contains("  cose.kty.name = " + coseKeyTypeName(coseKeyType)), stdout);
        if (cose.containsKey(-1)) {
            int curve = requireInt(cose, -1);
            assertTrue(stdout.contains("  cose.crv = " + curve), stdout);
            assertTrue(stdout.contains("  cose.crv.name = " + coseCurveName(curve)), stdout);
        }
        if (cose.containsKey(-2)) {
            String xB64 = base64Url(requireBytes(cose, -2));
            assertTrue(stdout.contains("  cose.x.b64u = " + xB64), stdout);
        }
        if (cose.containsKey(-3)) {
            String yB64 = base64Url(requireBytes(cose, -3));
            assertTrue(stdout.contains("  cose.y.b64u = " + yB64), stdout);
        }
        Map<String, String> jwkFields = jwkFieldsForThumbprint(coseKeyType, cose);
        if (!jwkFields.isEmpty()) {
            String thumbprint = jwkThumbprint(jwkFields);
            assertTrue(stdout.contains("  publicKey.jwk.thumbprint.sha256 = " + thumbprint), stdout);
        }
        EcdsaSignatureDetails ecdsaDetails = SignatureInspector.inspect(
                        WebAuthnSignatureAlgorithm.ES256, fixture.request().signature())
                .ecdsa()
                .orElseThrow();
        assertTrue(stdout.contains("  ecdsa.lowS = " + ecdsaDetails.lowS()), stdout);
        assertTrue(stdout.contains("  policy.lowS.enforced = false"), stdout);
        assertTrue(stdout.contains("  verify.ok = true"), stdout);
        assertTrue(stdout.contains("  valid = true"), stdout);
        assertTrue(stdout.contains("=== End Verbose Trace ==="), stdout);
    }

    @Test
    void replayStoredEmitsExtensionDetailsWhenPresent() throws Exception {
        Path database = tempDir.resolve("fido2-extensions.db");
        CommandHarness harness = CommandHarness.create(database);

        WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
        harness.save("fido2-packed-es256", fixture, WebAuthnSignatureAlgorithm.ES256);

        byte[] extendedAuthenticatorData =
                extendAuthenticatorData(fixture.request().authenticatorData(), EXTENSIONS_CBOR);
        byte[] signature = signAssertion(
                fixture.credentialPrivateKeyJwk(),
                fixture.algorithm(),
                extendedAuthenticatorData,
                fixture.request().clientDataJson());

        int exitCode = harness.execute(
                "replay",
                "--credential-id",
                "fido2-packed-es256",
                "--relying-party-id",
                "example.org",
                "--origin",
                "https://example.org",
                "--type",
                fixture.request().expectedType(),
                "--expected-challenge",
                encode(fixture.request().expectedChallenge()),
                "--client-data",
                encode(fixture.request().clientDataJson()),
                "--authenticator-data",
                encode(extendedAuthenticatorData),
                "--signature",
                encode(signature),
                "--verbose");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("parse.extensions"), stdout);
        assertTrue(stdout.contains("  extensions.present = true"), stdout);
        assertTrue(stdout.contains("  extensions.cbor.hex = " + EXTENSIONS_CBOR_HEX), stdout);
        assertTrue(stdout.contains("  ext.credProps.rk = true"), stdout);
        assertTrue(stdout.contains("  ext.credProtect.policy = required"), stdout);
        assertTrue(stdout.contains("  ext.largeBlobKey.b64u = " + LARGE_BLOB_KEY_B64U), stdout);
        assertTrue(stdout.contains("  ext.hmac-secret = requested"), stdout);
    }

    @Test
    void replayStoredOmitsVerboseTraceByDefault() throws Exception {
        Path database = tempDir.resolve("fido2-default.db");
        CommandHarness harness = CommandHarness.create(database);

        WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
        harness.save("fido2-default", fixture, WebAuthnSignatureAlgorithm.ES256);

        int exitCode = harness.execute(
                "replay",
                "--credential-id",
                "fido2-default",
                "--relying-party-id",
                "example.org",
                "--origin",
                "https://example.org",
                "--type",
                fixture.request().expectedType(),
                "--expected-challenge",
                encode(fixture.request().expectedChallenge()),
                "--client-data",
                encode(fixture.request().clientDataJson()),
                "--authenticator-data",
                encode(fixture.request().authenticatorData()),
                "--signature",
                encode(fixture.request().signature()));

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertFalse(stdout.contains("=== Verbose Trace ==="), stdout);
        assertFalse(stdout.contains("operation=fido2.assertion.evaluate.stored"), stdout);
    }

    private static String encode(byte[] data) {
        return URL_ENCODER.encodeToString(data);
    }

    private static final class CommandHarness {

        private final Fido2Cli cli;
        private final CommandLine commandLine;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private final Path database;

        private CommandHarness(Path database) {
            this.database = database.toAbsolutePath();
            this.cli = new Fido2Cli();
            cli.overrideDatabase(this.database);
            this.commandLine = new CommandLine(cli);
            commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        }

        static CommandHarness create(Path database) {
            return new CommandHarness(database);
        }

        int execute(String... args) {
            stdout.reset();
            stderr.reset();
            return commandLine.execute(args);
        }

        String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }

        void save(String credentialName, WebAuthnFixture fixture, WebAuthnSignatureAlgorithm algorithm)
                throws Exception {
            WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                    .name(credentialName)
                    .relyingPartyId(fixture.storedCredential().relyingPartyId())
                    .credentialId(fixture.storedCredential().credentialId())
                    .publicKeyCose(fixture.storedCredential().publicKeyCose())
                    .signatureCounter(fixture.storedCredential().signatureCounter())
                    .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                    .algorithm(algorithm)
                    .build();

            try (CredentialStore store = CredentialStoreFactory.openFileStore(database)) {
                Credential credential = VersionedCredentialRecordMapper.toCredential(
                        new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter().serialize(descriptor));
                store.save(credential);
            }
        }
    }

    private static String sha256Digest(byte[] input) {
        return "sha256:" + hex(sha256Bytes(input));
    }

    private static byte[] sha256Bytes(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
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

    private static String formatByte(int value) {
        return String.format("%02x", value & 0xFF);
    }

    private static long counterFromAuthenticatorData(byte[] authenticatorData) {
        return ByteBuffer.wrap(authenticatorData, 33, 4).getInt() & 0xFFFFFFFFL;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] combined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
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
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decode COSE key", ex);
        }
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

    private static byte[] extendAuthenticatorData(byte[] authenticatorData, byte[] extensions) {
        byte[] original = authenticatorData == null ? new byte[0] : authenticatorData;
        if (original.length < 33) {
            throw new IllegalArgumentException("Authenticator data must contain RP ID hash and flags");
        }
        byte[] extended = Arrays.copyOf(original, original.length + extensions.length);
        extended[32] = (byte) (extended[32] | 0x80);
        System.arraycopy(extensions, 0, extended, original.length, extensions.length);
        return extended;
    }

    private static byte[] signAssertion(
            String privateKeyJwk,
            WebAuthnSignatureAlgorithm algorithm,
            byte[] authenticatorData,
            byte[] clientDataJson) {
        try {
            PrivateKey privateKey = privateKeyFromJwk(privateKeyJwk, algorithm);
            Signature signature = signatureFor(algorithm);
            signature.initSign(privateKey);
            byte[] clientDataHash = sha256Bytes(clientDataJson);
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
        if (!curve.equalsIgnoreCase(namedCurveLabel(algorithm))) {
            throw new IllegalArgumentException(
                    "JWK curve " + curve + " does not match expected " + namedCurveLabel(algorithm));
        }
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName(algorithm)));
        ECParameterSpec parameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
        byte[] scalar = Base64.getUrlDecoder().decode(d);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(new BigInteger(1, scalar), parameterSpec);
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

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String string && !string.isBlank()) {
            return string;
        }
        throw new IllegalArgumentException("Missing JWK field: " + key);
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
                // Unsupported type
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
        byte[] digest = sha256Bytes(json.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
