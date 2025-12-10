package io.openauth.sim.cli.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.cli.support.CliJsonSchemas;
import io.openauth.sim.cli.support.JsonShapeAsserter;
import io.openauth.sim.core.json.SimpleJson;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
        assertTrue(stdout.contains("\"operation\":\"eudiw.request.create\""), stdout);
        assertTrue(stdout.contains("\"steps\""), stdout);

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertEquals("cli.eudiw.request.create", root.get("event"));
        assertEquals("success", root.get("status"));
        assertEquals("success", root.get("reasonCode"));
        assertNotNull(root.get("telemetryId"), "telemetryId should be present on the envelope");
        assertEquals(Boolean.TRUE, root.get("sanitized"));
        assertTrue(root.get("data") instanceof Map, "data object should be present on the envelope");

        JsonShapeAsserter.assertMatchesShape(CliJsonSchemas.schemaForEvent("cli.eudiw.request.create"), stdout);
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
        assertTrue(stdout.contains("\"operation\":\"eudiw.wallet.simulate\""), stdout);
        assertTrue(stdout.contains("\"steps\""), stdout);
        assertTrue(stdout.contains("\"vp_token_hash\""), stdout);

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertEquals("cli.eudiw.wallet.simulate", root.get("event"));
        assertEquals("success", root.get("status"));
        assertEquals("success", root.get("reasonCode"));
        assertNotNull(root.get("telemetryId"), "telemetryId should be present on the envelope");
        assertEquals(Boolean.TRUE, root.get("sanitized"));
        assertTrue(root.get("data") instanceof Map, "data object should be present on the envelope");

        JsonShapeAsserter.assertMatchesShape(CliJsonSchemas.schemaForEvent("cli.eudiw.wallet.simulate"), stdout);
    }

    @Test
    void validatePrintsProblemDetailsOnTrustedAuthorityFailure() throws Exception {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "validate", "--preset", "pid-haip-baseline", "--trusted-authority", "aki:unknown", "--output-json");
        assertEquals(2, exitCode, harness.stderr());
        String stdout = harness.stdout();
        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertEquals("cli.eudiw.validate", root.get("event"));
        assertEquals("invalid", root.get("status"));
        assertTrue(root.get("reasonCode").toString().contains("invalid_scope"), stdout);
        assertEquals(Boolean.TRUE, root.get("sanitized"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertNotNull(data, "data object should be present on the envelope");
        assertTrue(data.get("type").toString().contains("invalid_scope"), stdout);
        assertEquals(400, data.get("status"));
        assertTrue(data.get("detail").toString().contains("Trusted Authority"), stdout);
        JsonShapeAsserter.assertMatchesShape(CliJsonSchemas.schemaForEvent("cli.eudiw.validate"), stdout);
    }

    @Test
    void validateEmitsSuccessEnvelopeWhenTrustedAuthorityMatches() throws Exception {
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "validate",
                "--request-id",
                "HAIP-0001",
                "--preset",
                "pid-haip-baseline",
                "--trusted-authority",
                "aki:s9tIpP7qrS9=",
                "--verbose",
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("\"status\":\"SUCCESS\""), stdout);
        assertTrue(stdout.contains("\"trustedAuthorityMatch\":\"aki:s9tIpP7qrS9=\""), stdout);
        assertTrue(stdout.contains("\"trace\""), stdout);
        assertTrue(stdout.contains("\"steps\""), stdout);

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertEquals("cli.eudiw.validate", root.get("event"));
        assertEquals("success", root.get("status"));
        assertEquals("success", root.get("reasonCode"));
        assertNotNull(root.get("telemetryId"), "telemetryId should be present on the envelope");
        assertEquals(Boolean.TRUE, root.get("sanitized"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertNotNull(data, "data object should be present on the envelope");
        assertEquals("HAIP-0001", data.get("requestId"));
        assertEquals("SUCCESS", data.get("status"));
        assertEquals("HAIP", data.get("profile"));
        assertEquals("DIRECT_POST_JWT", data.get("responseMode"));
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> presentations =
                (java.util.List<Map<String, Object>>) data.get("presentations");
        assertNotNull(presentations, "presentations list should be present");
        assertTrue(!presentations.isEmpty(), "presentations list should not be empty");
        Map<String, Object> first = presentations.get(0);
        assertEquals("pid-haip-baseline", first.get("credentialId"));
        assertEquals("dc+sd-jwt", first.get("format"));
        assertEquals(Boolean.TRUE, first.get("holderBinding"));
        assertEquals("aki:s9tIpP7qrS9=", first.get("trustedAuthorityMatch"));

        JsonShapeAsserter.assertMatchesShape(CliJsonSchemas.schemaForEvent("cli.eudiw.validate"), stdout);
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
