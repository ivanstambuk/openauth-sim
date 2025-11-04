package io.openauth.sim.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.Outputs;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.Resolved;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@Tag("cli")
final class EmvCliTest {

    @TempDir
    Path tempDir;

    @Test
    void evaluateIdentifyEmitsTextOutputWithTraceByDefault() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex());

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        String masterKeyDigest = expectedMasterKeyDigest(vector);

        assertTrue(stdout.contains("event=cli.emv.cap.identify"), stdout);
        assertTrue(stdout.contains("status=success"), stdout);
        assertTrue(stdout.contains("reasonCode=generated"), stdout);
        assertTrue(stdout.contains("mode=IDENTIFY"), stdout);
        assertTrue(stdout.contains("atc=" + vector.input().atcHex()), stdout);
        assertTrue(stdout.contains("otp=" + vector.outputs().otpDecimal()), stdout);

        int expectedMaskLength = countMaskedDigits(vector.outputs());
        assertTrue(stdout.contains("maskLength=" + expectedMaskLength), stdout);

        assertTrue(stdout.contains("Preview window:"), stdout);
        assertTrue(stdout.contains("[0]"), stdout);
        assertTrue(stdout.contains("> " + vector.input().atcHex()), stdout);

        assertTrue(stdout.contains("trace.masterKeySha256=" + masterKeyDigest), stdout);
        assertTrue(stdout.contains("trace.sessionKey=" + vector.outputs().sessionKeyHex()), stdout);
        assertTrue(
                stdout.contains(
                        "trace.generateAcInput.terminal=" + vector.outputs().generateAcInputTerminalHex()),
                stdout);
        assertTrue(
                stdout.contains("trace.generateAcInput.icc=" + vector.outputs().generateAcInputIccHex()), stdout);
        assertTrue(stdout.contains("trace.generateAcResult=" + vector.outputs().generateAcResultHex()), stdout);
        assertTrue(stdout.contains("trace.bitmask=" + vector.outputs().bitmaskOverlay()), stdout);
        assertTrue(
                stdout.contains("trace.maskedDigitsOverlay=" + vector.outputs().maskedDigitsOverlay()), stdout);
        assertTrue(
                stdout.contains("trace.issuerApplicationData=" + vector.input().issuerApplicationDataHex()), stdout);
        assertTrue(stdout.contains("trace.iccPayloadTemplate=" + vector.input().iccDataTemplateHex()), stdout);
        Resolved resolved = vector.resolved();
        if (resolved != null && resolved.iccDataHex() != null) {
            assertTrue(stdout.contains("trace.iccPayloadResolved=" + resolved.iccDataHex()), stdout);
        }
    }

    @Test
    void evaluateRespondPrintsRestEquivalentJsonWhenRequested() {
        EmvCapVector vector = EmvCapVectorFixtures.load("respond-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--challenge",
                vector.input().customerInputs().challenge(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout().trim();
        assertFalse(stdout.isEmpty(), "JSON output must not be empty");

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;

        assertEquals(vector.outputs().otpDecimal(), root.get("otp"));
        assertEquals(countMaskedDigits(vector.outputs()), ((Number) root.get("maskLength")).intValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previews = (List<Map<String, Object>>) root.get("previews");
        assertNotNull(previews, "Preview array should be present");
        assertFalse(previews.isEmpty(), "Preview array must include the evaluated entry");
        Map<String, Object> primaryPreview = previews.get(0);
        assertEquals(vector.input().atcHex(), primaryPreview.get("counter"));
        assertEquals(0, ((Number) primaryPreview.get("delta")).intValue());
        assertEquals(vector.outputs().otpDecimal(), primaryPreview.get("otp"));

        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) root.get("trace");
        assertNotNull(trace, "Trace payload should be present by default");
        assertEquals(expectedMasterKeyDigest(vector), trace.get("masterKeySha256"));
        assertEquals(vector.outputs().sessionKeyHex(), trace.get("sessionKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> generateAcInput = (Map<String, Object>) trace.get("generateAcInput");
        assertEquals(vector.outputs().generateAcInputTerminalHex(), generateAcInput.get("terminal"));
        assertEquals(vector.outputs().generateAcInputIccHex(), generateAcInput.get("icc"));
        assertEquals(vector.outputs().generateAcResultHex(), trace.get("generateAcResult"));
        assertEquals(vector.outputs().bitmaskOverlay(), trace.get("bitmask"));
        assertEquals(vector.outputs().maskedDigitsOverlay(), trace.get("maskedDigitsOverlay"));
        assertEquals(vector.input().issuerApplicationDataHex(), trace.get("issuerApplicationData"));
        assertEquals(vector.input().iccDataTemplateHex(), trace.get("iccPayloadTemplate"));
        Resolved resolved = vector.resolved();
        if (resolved != null && resolved.iccDataHex() != null) {
            assertEquals(resolved.iccDataHex(), trace.get("iccPayloadResolved"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> telemetry = (Map<String, Object>) root.get("telemetry");
        assertEquals("emv.cap.respond", telemetry.get("event"));
        assertEquals("success", telemetry.get("status"));
        assertEquals("generated", telemetry.get("reasonCode"));
        assertEquals(Boolean.TRUE, telemetry.get("sanitized"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) telemetry.get("fields");
        String telemetryId = (String) fields.get("telemetryId");
        assertNotNull(telemetryId, "telemetryId should be present");
        assertTrue(telemetryId.startsWith("cli-emv-cap-"), telemetryId);
        assertEquals(vector.input().mode().name(), fields.get("mode"));
        assertEquals(vector.input().atcHex(), fields.get("atc"));
        assertEquals(vector.input().branchFactor(), ((Number) fields.get("branchFactor")).intValue());
        assertEquals(vector.input().height(), ((Number) fields.get("height")).intValue());
        assertEquals(
                vector.input().issuerProprietaryBitmapHex().length() / 2,
                ((Number) fields.get("ipbMaskLength")).intValue());
        assertEquals(countMaskedDigits(vector.outputs()), ((Number) fields.get("maskedDigitsCount")).intValue());
        assertEquals(0, ((Number) fields.get("previewWindowBackward")).intValue());
        assertEquals(0, ((Number) fields.get("previewWindowForward")).intValue());
    }

    @Test
    void evaluateIdentifyDisablesTraceWhenRequested() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--terminal-data",
                vector.outputs().generateAcInputTerminalHex(),
                "--icc-data",
                vector.outputs().generateAcInputIccHex(),
                "--include-trace=false");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.emv.cap.identify"), stdout);
        assertTrue(stdout.contains("otp=" + vector.outputs().otpDecimal()), stdout);
        assertFalse(stdout.contains("trace."), stdout);
    }

    @Test
    void evaluateSignMissingAmountFailsValidation() {
        EmvCapVector vector = EmvCapVectorFixtures.load("sign-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--challenge",
                vector.input().customerInputs().challenge(),
                "--reference",
                vector.input().customerInputs().reference(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex());

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.emv.cap.sign"), stderr);
        assertTrue(stderr.contains("status=invalid"), stderr);
        assertTrue(stderr.contains("reasonCode=invalid_input"), stderr);
        assertTrue(stderr.contains("Sign mode requires an amount value"), stderr);
    }

    @Test
    void evaluateIdentifyJsonOmitsTraceWhenDisabled() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex(),
                "--include-trace=false",
                "--output-json");

        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());
        String stdout = harness.stdout().trim();
        assertFalse(stdout.isEmpty(), "JSON output must not be empty");

        Object parsed = SimpleJson.parse(stdout);
        assertTrue(parsed instanceof Map, () -> "Unexpected JSON payload: " + stdout);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertEquals(vector.outputs().otpDecimal(), root.get("otp"));
        assertEquals(countMaskedDigits(vector.outputs()), ((Number) root.get("maskLength")).intValue());
        assertFalse(root.containsKey("trace"), "Trace payload must be omitted when includeTrace=false");
    }

    @Test
    void evaluateRejectsUnknownMode() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                "UNKNOWN",
                "--master-key",
                vector.input().masterKeyHex(),
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex());

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("error=invalid_mode"), stderr);
    }

    @Test
    void evaluateRejectsEmptyMasterKey() {
        EmvCapVector vector = EmvCapVectorFixtures.load("identify-baseline");
        CommandHarness harness = CommandHarness.create();

        int exitCode = harness.execute(
                "cap",
                "evaluate",
                "--mode",
                vector.input().mode().name(),
                "--master-key",
                "   ",
                "--atc",
                vector.input().atcHex(),
                "--branch-factor",
                String.valueOf(vector.input().branchFactor()),
                "--height",
                String.valueOf(vector.input().height()),
                "--iv",
                vector.input().ivHex(),
                "--cdol1",
                vector.input().cdol1Hex(),
                "--issuer-proprietary-bitmap",
                vector.input().issuerProprietaryBitmapHex(),
                "--icc-template",
                vector.input().iccDataTemplateHex(),
                "--issuer-application-data",
                vector.input().issuerApplicationDataHex());

        assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        String stderr = harness.stderr();
        assertTrue(stderr.contains("event=cli.emv.cap.identify"), stderr);
        assertTrue(stderr.contains("status=invalid"), stderr);
        assertTrue(stderr.toLowerCase(Locale.ROOT).contains("masterkey must not be empty"), stderr);
    }

    @Test
    void seedCommandPopulatesCanonicalCredentials() throws Exception {
        Path database = tempDir.resolve("emv-cap-seed.db");
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(database);

        int exitCode = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, exitCode, harness.stderr());

        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.emv.cap.seed"), stdout);
        assertTrue(stdout.contains("status=seeded"), stdout);
        assertTrue(stdout.contains("addedCount=" + EmvCapSeedSamples.samples().size()), stdout);

        try (var store = CredentialStoreFactory.openFileStore(database)) {
            List<Credential> credentials = store.findAll();
            assertEquals(EmvCapSeedSamples.samples().size(), credentials.size());

            Map<String, Credential> byId = credentials.stream()
                    .collect(Collectors.toMap(
                            Credential::name, credential -> credential, (first, second) -> first, LinkedHashMap::new));

            for (SeedSample sample : EmvCapSeedSamples.samples()) {
                Credential credential = byId.get(sample.credentialId());
                assertNotNull(credential, sample.credentialId());
                assertEquals(CredentialType.EMV_CA, credential.type());
                assertEquals(
                        sample.vector().input().masterKeyHex(),
                        credential.secret().asHex().toUpperCase());
            }
        }
    }

    @Test
    void seedCommandIsIdempotent() throws Exception {
        Path database = tempDir.resolve("emv-cap-seed-idempotent.db");
        CommandHarness harness = CommandHarness.create();
        harness.cli().overrideDatabase(database);

        int firstExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, firstExit, harness.stderr());

        try (var store = CredentialStoreFactory.openFileStore(database)) {
            assertEquals(EmvCapSeedSamples.samples().size(), store.findAll().size());
            store.delete("emv-cap-identify-baseline");
        }

        int secondExit = harness.execute("cap", "seed");
        assertEquals(CommandLine.ExitCode.OK, secondExit, harness.stderr());

        String stdout = harness.stdout();
        assertTrue(stdout.contains("event=cli.emv.cap.seed"), stdout);
        assertTrue(stdout.contains("addedCount=1"), stdout);
        assertTrue(stdout.contains("emv-cap-identify-baseline"), stdout);
    }

    private static int countMaskedDigits(Outputs outputs) {
        return (int)
                outputs.maskedDigitsOverlay().chars().filter(ch -> ch != '.').count();
    }

    private static String expectedMasterKeyDigest(EmvCapVector vector) {
        return sha256Digest(vector.input().masterKeyHex());
    }

    private static String sha256Digest(String hex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = hexToBytes(hex);
            byte[] hashed = digest.digest(bytes);
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.trim().toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex input must contain an even number of characters");
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
        }
        return builder.toString();
    }

    private static final class CommandHarness {

        private final EmvCli cli = new EmvCli();
        private final CommandLine commandLine = new CommandLine(cli);
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CommandHarness() {
            commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
            commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        }

        static CommandHarness create() {
            return new CommandHarness();
        }

        EmvCli cli() {
            return cli;
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
