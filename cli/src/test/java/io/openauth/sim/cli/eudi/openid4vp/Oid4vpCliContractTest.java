package io.openauth.sim.cli.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@Tag("cli")
final class Oid4vpCliContractTest {

    @Test
    void requestCreateProducesVerboseTraceJson() throws Exception {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "request",
                "create",
                "--profile",
                "HAIP",
                "--response-mode",
                "DIRECT_POST_JWT",
                "--dcql-preset",
                "pid-haip-baseline",
                "--signed-request",
                "--include-qr",
                "--verbose",
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("\"profile\":\"HAIP\""), stdout);
        assertTrue(stdout.contains("authorizationRequest"), stdout);
        assertTrue(stdout.contains("qr"), stdout);
        assertTrue(stdout.contains("\"trace\""), stdout);
        assertTrue(stdout.contains("dcqlHash"), stdout);
        assertTrue(stdout.contains("nonceFull"), stdout);
    }

    @Test
    void walletSimulateSurfacesTrustedAuthorityMetadata() throws Exception {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "wallet",
                "simulate",
                "--request-id",
                "REQ-7K3D",
                "--wallet-preset",
                "pid-haip-baseline",
                "--profile",
                "HAIP",
                "--trusted-authority",
                "aki:s9tIpP7qrS9=",
                "--verbose",
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("\"status\":\"SUCCESS\""), stdout);
        assertTrue(stdout.contains("\"format\":\"dc+sd-jwt\""), stdout);
        assertTrue(stdout.contains("\"trustedAuthorityMatch\":\"aki:s9tIpP7qrS9=\""), stdout);
        assertTrue(stdout.contains("vpTokenHash"), stdout);
        assertTrue(stdout.contains("trustedAuthority"), stdout);
    }

    @Test
    void validatePrintsProblemDetailsOnTrustedAuthorityFailure() throws Exception {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "validate", "--preset", "pid-haip-baseline", "--trusted-authority", "aki:unknown", "--output-json");

        assertEquals(2, exitCode, harness.stderr());
        String stderr = harness.stderr();
        assertTrue(stderr.contains("invalid_scope"), stderr);
        assertTrue(stderr.contains("\"status\":400"), stderr);
        assertTrue(stderr.contains("Trusted Authority"), stderr);
        assertTrue(stderr.contains("violations"), stderr);
    }

    private static final class CommandHarness {
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        static CommandHarness create() {
            return new CommandHarness();
        }

        int execute(String... args) {
            stdout.reset();
            stderr.reset();
            CommandLine cli = new CommandLine(new EudiwCli());
            cli.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            cli.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
            return cli.execute(args);
        }

        String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }
    }
}
