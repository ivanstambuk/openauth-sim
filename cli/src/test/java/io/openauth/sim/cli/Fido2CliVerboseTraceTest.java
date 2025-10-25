package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        long storedCounter = fixture.storedCredential().signatureCounter();
        long reportedCounter = counterFromAuthenticatorData(authenticatorData);
        assertTrue(stdout.contains("  counter.stored = " + storedCounter), stdout);
        assertTrue(stdout.contains("  counter.reported = " + reportedCounter), stdout);

        byte[] clientDataHash = sha256Bytes(fixture.request().clientDataJson());
        String signatureBaseSha256 = sha256Digest(concat(authenticatorData, clientDataHash));
        assertTrue(stdout.contains("  signedBytes.sha256 = " + signatureBaseSha256), stdout);
        assertTrue(stdout.contains("  alg = " + WebAuthnSignatureAlgorithm.ES256.name()), stdout);
        assertTrue(stdout.contains("  cose.alg = " + WebAuthnSignatureAlgorithm.ES256.coseIdentifier()), stdout);
        assertTrue(stdout.contains("  cose.alg.name = " + WebAuthnSignatureAlgorithm.ES256.name()), stdout);
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
        assertTrue(stdout.contains("  valid = true"), stdout);
        assertTrue(stdout.contains("=== End Verbose Trace ==="), stdout);
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
