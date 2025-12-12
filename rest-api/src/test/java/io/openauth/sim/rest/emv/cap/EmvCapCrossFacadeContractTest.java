package io.openauth.sim.rest.emv.cap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.contract.emv.cap.EmvCapCanonicalScenarios;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.cli.EmvCli;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.infra.persistence.CredentialStoreFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import picocli.CommandLine;

@Tag("crossFacadeContract")
final class EmvCapCrossFacadeContractTest {

    @TempDir
    Path tempDir;

    @Test
    void emvCapCanonicalScenariosStayInParityAcrossFacades() throws Exception {
        List<CanonicalScenario> descriptors =
                EmvCapCanonicalScenarios.scenarios(ScenarioEnvironment.fixedAt(Instant.EPOCH));

        for (CanonicalScenario descriptor : descriptors) {
            CanonicalFacadeResult expected = descriptor.expected();

            ScenarioEnvironment nativeEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario nativeScenario = scenario(nativeEnv, descriptor.scenarioId());
            EmvCapEvaluationApplicationService nativeEval = new EmvCapEvaluationApplicationService();
            EmvCapReplayApplicationService nativeReplay =
                    new EmvCapReplayApplicationService(nativeEnv.store(), nativeEval);

            CanonicalFacadeResult nativeResult =
                    switch (nativeScenario.kind()) {
                        case EVALUATE_INLINE ->
                            toCanonical(nativeEval.evaluate(
                                    (EmvCapEvaluationApplicationService.EvaluationRequest) nativeScenario.command()));
                        case REPLAY_STORED, FAILURE_STORED -> {
                            EmvCapReplayApplicationService.ReplayCommand.Stored stored =
                                    (EmvCapReplayApplicationService.ReplayCommand.Stored) nativeScenario.command();
                            yield toCanonical(nativeReplay.replay(stored), stored.otp());
                        }
                        default ->
                            throw new IllegalStateException(
                                    "Unsupported EMV/CAP scenario kind " + nativeScenario.kind());
                    };
            assertEquals(expected, nativeResult, descriptor.scenarioId() + " native");

            ScenarioEnvironment restEnv = ScenarioEnvironment.fixedAt(Instant.EPOCH);
            CanonicalScenario restScenario = scenario(restEnv, descriptor.scenarioId());
            EmvCapEvaluationService restEval =
                    new EmvCapEvaluationService(new EmvCapEvaluationApplicationService(), provider(restEnv.store()));
            EmvCapReplayService restReplay = new EmvCapReplayService(
                    new EmvCapReplayApplicationService(restEnv.store(), new EmvCapEvaluationApplicationService()));

            CanonicalFacadeResult restResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE -> {
                            EmvCapEvaluationApplicationService.EvaluationRequest inline =
                                    (EmvCapEvaluationApplicationService.EvaluationRequest) restScenario.command();
                            EmvCapEvaluationRequest request = new EmvCapEvaluationRequest(
                                    null,
                                    inline.mode().name(),
                                    inline.masterKeyHex(),
                                    inline.atcHex(),
                                    inline.branchFactor(),
                                    inline.height(),
                                    inline.ivHex(),
                                    inline.cdol1Hex(),
                                    inline.issuerProprietaryBitmapHex(),
                                    null,
                                    new EmvCapEvaluationRequest.CustomerInputs(
                                            inline.customerInputs().challenge(),
                                            inline.customerInputs().reference(),
                                            inline.customerInputs().amount()),
                                    new EmvCapEvaluationRequest.TransactionData(
                                            inline.transactionData()
                                                    .terminalHexOverride()
                                                    .orElse(null),
                                            inline.transactionData()
                                                    .iccHexOverride()
                                                    .orElse(null)),
                                    inline.iccDataTemplateHex(),
                                    inline.issuerApplicationDataHex(),
                                    false);
                            yield toCanonical(restEval.evaluate(request));
                        }
                        case REPLAY_STORED, FAILURE_STORED -> {
                            EmvCapReplayApplicationService.ReplayCommand.Stored stored =
                                    (EmvCapReplayApplicationService.ReplayCommand.Stored) restScenario.command();
                            EmvCapReplayRequest request = new EmvCapReplayRequest(
                                    stored.credentialId(),
                                    stored.mode().name(),
                                    stored.otp(),
                                    stored.driftBackward(),
                                    stored.driftForward(),
                                    false,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null);
                            yield toCanonical(restReplay.replay(request), stored.otp());
                        }
                        default ->
                            throw new IllegalStateException("Unsupported EMV/CAP scenario kind " + restScenario.kind());
                    };
            assertEquals(expected, restResult, descriptor.scenarioId() + " rest");

            Path dbPath = tempDir.resolve(descriptor.scenarioId() + ".db");
            seedCliStore(dbPath);
            CanonicalFacadeResult cliResult =
                    switch (restScenario.kind()) {
                        case EVALUATE_INLINE -> executeCliEvaluate(dbPath, restScenario);
                        case REPLAY_STORED, FAILURE_STORED -> executeCliReplay(dbPath, restScenario);
                        default ->
                            throw new IllegalStateException("Unsupported EMV/CAP scenario kind " + restScenario.kind());
                    };
            assertEquals(expected, cliResult, descriptor.scenarioId() + " cli");
        }
    }

    private static CanonicalScenario scenario(ScenarioEnvironment env, String id) {
        return EmvCapCanonicalScenarios.scenarios(env).stream()
                .filter(scenario -> scenario.scenarioId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static CanonicalFacadeResult toCanonical(EmvCapEvaluationApplicationService.EvaluationResult result) {
        boolean success = result.telemetry().status() == EmvCapEvaluationApplicationService.TelemetryStatus.SUCCESS;
        boolean includeTrace = result.traceOptional().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                success ? result.otp() : null,
                null,
                null,
                null,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(EmvCapEvaluationResponse response) {
        boolean success = response.telemetry() != null
                && "success".equals(response.telemetry().status());
        String reasonCode = response.telemetry() != null ? response.telemetry().reasonCode() : "unexpected_error";
        return new CanonicalFacadeResult(
                success,
                reasonCode,
                response.otp(),
                null,
                null,
                null,
                null,
                response.trace() != null,
                response.telemetry() != null
                        && response.telemetry().fields() != null
                        && response.telemetry().fields().get("telemetryId") != null);
    }

    private static CanonicalFacadeResult toCanonical(
            EmvCapReplayApplicationService.ReplayResult result, String submittedOtp) {
        boolean success = result.match();
        boolean includeTrace = result.traceOptional().isPresent();
        return new CanonicalFacadeResult(
                success,
                result.telemetry().reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                null,
                null,
                includeTrace,
                true);
    }

    private static CanonicalFacadeResult toCanonical(EmvCapReplayResponse response, String submittedOtp) {
        boolean success = "match".equals(response.reasonCode());
        EmvCapReplayMetadata meta = response.metadata();
        return new CanonicalFacadeResult(
                success,
                response.reasonCode(),
                success ? submittedOtp : null,
                null,
                null,
                null,
                null,
                response.trace() != null,
                meta != null && meta.telemetryId() != null);
    }

    private CanonicalFacadeResult executeCliEvaluate(Path dbPath, CanonicalScenario scenario) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(stderr, true, StandardCharsets.UTF_8);

        EmvCli cli = new EmvCli();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(out);
        cmd.setErr(err);

        EmvCapEvaluationApplicationService.EvaluationRequest inline =
                (EmvCapEvaluationApplicationService.EvaluationRequest) scenario.command();
        java.util.List<String> args = new java.util.ArrayList<>(List.of(
                "--database",
                dbPath.toString(),
                "cap",
                "evaluate",
                "--mode",
                inline.mode().name(),
                "--master-key",
                inline.masterKeyHex(),
                "--atc",
                inline.atcHex(),
                "--branch-factor",
                Integer.toString(inline.branchFactor()),
                "--height",
                Integer.toString(inline.height()),
                "--iv",
                inline.ivHex(),
                "--cdol1",
                inline.cdol1Hex(),
                "--issuer-proprietary-bitmap",
                inline.issuerProprietaryBitmapHex(),
                "--icc-template",
                inline.iccDataTemplateHex(),
                "--issuer-application-data",
                inline.issuerApplicationDataHex(),
                "--include-trace",
                "false",
                "--output-json"));
        if (!inline.customerInputs().challenge().isBlank()) {
            args.addAll(List.of("--challenge", inline.customerInputs().challenge()));
        }
        if (!inline.customerInputs().reference().isBlank()) {
            args.addAll(List.of("--reference", inline.customerInputs().reference()));
        }
        if (!inline.customerInputs().amount().isBlank()) {
            args.addAll(List.of("--amount", inline.customerInputs().amount()));
        }

        cmd.execute(args.toArray(String[]::new));
        Map<String, Object> envelope = castMap(SimpleJson.parse(stdout.toString(StandardCharsets.UTF_8)));
        return toCanonicalCli(envelope);
    }

    private CanonicalFacadeResult executeCliReplay(Path dbPath, CanonicalScenario scenario) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintWriter out = new PrintWriter(stdout, true, StandardCharsets.UTF_8);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintWriter err = new PrintWriter(stderr, true, StandardCharsets.UTF_8);

        EmvCli cli = new EmvCli();
        CommandLine cmd = new CommandLine(cli);
        cmd.setOut(out);
        cmd.setErr(err);

        EmvCapReplayApplicationService.ReplayCommand.Stored stored =
                (EmvCapReplayApplicationService.ReplayCommand.Stored) scenario.command();
        String[] args = new String[] {
            "--database",
            dbPath.toString(),
            "cap",
            "replay",
            "--credential-id",
            stored.credentialId(),
            "--mode",
            stored.mode().name(),
            "--otp",
            stored.otp(),
            "--include-trace",
            "false",
            "--output-json"
        };

        cmd.execute(args);
        Map<String, Object> envelope = castMap(SimpleJson.parse(stdout.toString(StandardCharsets.UTF_8)));
        CanonicalFacadeResult base = toCanonicalCli(envelope);
        if (base.success() && base.otp() == null) {
            return new CanonicalFacadeResult(
                    true,
                    base.reasonCode(),
                    stored.otp(),
                    null,
                    null,
                    null,
                    null,
                    base.includeTrace(),
                    base.telemetryIdPresent());
        }
        return base;
    }

    private static CanonicalFacadeResult toCanonicalCli(Map<String, Object> envelope) {
        String reasonCode = String.valueOf(envelope.get("reasonCode"));
        boolean success = "match".equals(reasonCode) || "generated".equals(reasonCode);
        boolean telemetryPresent = envelope.get("telemetryId") != null;
        Map<String, Object> data = castMap(envelope.get("data"));
        String otp = data != null ? (String) data.get("otp") : null;
        boolean includeTrace = data != null && data.get("trace") != null;
        return new CanonicalFacadeResult(
                success, reasonCode, success ? otp : null, null, null, null, null, includeTrace, telemetryPresent);
    }

    private static void seedCliStore(Path dbPath) throws Exception {
        try (CredentialStore store = CredentialStoreFactory.openFileStore(dbPath)) {
            ScenarioEnvironment env = new ScenarioEnvironment(store, Clock.systemUTC());
            EmvCapCanonicalScenarios.scenarios(env);
        }
    }

    private static ObjectProvider<CredentialStore> provider(CredentialStore store) {
        return new ObjectProvider<>() {
            @Override
            public CredentialStore getObject(Object... args) {
                return store;
            }

            @Override
            public CredentialStore getObject() {
                return store;
            }

            @Override
            public CredentialStore getIfAvailable() {
                return store;
            }

            @Override
            public CredentialStore getIfUnique() {
                return store;
            }

            @Override
            public java.util.stream.Stream<CredentialStore> stream() {
                return store == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(store);
            }

            @Override
            public java.util.stream.Stream<CredentialStore> orderedStream() {
                return stream();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return (Map<String, Object>) value;
    }
}
