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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
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
        assertTrue(stdout.contains("  clientData.sha256 = " + expectedClientDataSha256), stdout);

        byte[] authenticatorData = fixture.request().authenticatorData();
        String rpIdHashHex = hex(Arrays.copyOf(authenticatorData, 32));
        assertTrue(stdout.contains("  rpId.hash.hex = " + rpIdHashHex), stdout);

        String expectedRpDigest =
                sha256Digest(fixture.request().relyingPartyId().getBytes(StandardCharsets.UTF_8));
        assertTrue(stdout.contains("  rpId.expected.sha256 = " + expectedRpDigest), stdout);

        int flagsByte = authenticatorData[32] & 0xFF;
        assertTrue(stdout.contains("  flags.byte = " + formatByte(flagsByte)), stdout);
        assertTrue(stdout.contains("  flags.userPresence = true"), stdout);
        assertTrue(stdout.contains("  flags.userVerification = true"), stdout);

        long storedCounter = fixture.storedCredential().signatureCounter();
        long reportedCounter = counterFromAuthenticatorData(authenticatorData);
        assertTrue(stdout.contains("  counter.stored = " + storedCounter), stdout);
        assertTrue(stdout.contains("  counter.reported = " + reportedCounter), stdout);

        byte[] clientDataHash = sha256Bytes(fixture.request().clientDataJson());
        String signatureBaseSha256 = sha256Digest(concat(authenticatorData, clientDataHash));
        assertTrue(stdout.contains("  signature.base.sha256 = " + signatureBaseSha256), stdout);
        assertTrue(stdout.contains("  algorithm = " + WebAuthnSignatureAlgorithm.ES256.name()), stdout);
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
        return String.format("0x%02x", value & 0xFF);
    }

    private static long counterFromAuthenticatorData(byte[] authenticatorData) {
        return ByteBuffer.wrap(authenticatorData, 33, 4).getInt() & 0xFFFFFFFFL;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] combined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }
}
