package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
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
final class Fido2CliAttestationReplayMetadataAnchorsTest {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    @TempDir
    Path tempDir;

    @Test
    void replayWithMetadataAnchorUsesCuratedRoots() throws Exception {
        WebAuthnAttestationVector vector = attestationVector();
        WebAuthnAttestationVerification verification = new WebAuthnAttestationVerifier()
                .verify(new WebAuthnAttestationRequest(
                        vector.format(),
                        vector.registration().attestationObject(),
                        vector.registration().clientDataJson(),
                        vector.registration().challenge(),
                        vector.relyingPartyId(),
                        vector.origin()));
        if (verification.certificateChain().isEmpty()) {
            throw new IllegalStateException("Attestation fixture missing certificate chain");
        }

        CommandHarness harness = CommandHarness.create(tempDir.resolve("fido2-attest-replay-metadata.db"));

        int exitCode = harness.execute(
                "attest-replay",
                "--format",
                vector.format().label(),
                "--relying-party-id",
                vector.relyingPartyId(),
                "--origin",
                vector.origin(),
                "--attestation-object",
                encode(vector.registration().attestationObject()),
                "--client-data-json",
                encode(vector.registration().clientDataJson()),
                "--expected-challenge",
                encode(vector.registration().challenge()),
                "--metadata-anchor",
                "mds-w3c-packed-es256");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.fido2.attestReplay status=success"), stdout);
        assertTrue(stdout.contains("anchorSource=metadata"), stdout);
        assertTrue(stdout.contains("anchorProvided=true"), stdout);
        assertTrue(stdout.contains("metadataAnchorIds=[mds-w3c-packed-es256]"), stdout);
    }

    private static WebAuthnAttestationVector attestationVector() {
        return WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing packed attestation fixture"));
    }

    private static String encode(byte[] value) {
        return URL_ENCODER.encodeToString(value);
    }

    private static final class CommandHarness {

        private final Fido2Cli cli;
        private final CommandLine commandLine;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness(Path database) {
            this.cli = new Fido2Cli();
            this.cli.overrideDatabase(database);
            this.commandLine = new CommandLine(cli);
            this.commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            this.commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
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
    }
}
