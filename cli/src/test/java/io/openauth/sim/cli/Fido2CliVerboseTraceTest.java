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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
}
