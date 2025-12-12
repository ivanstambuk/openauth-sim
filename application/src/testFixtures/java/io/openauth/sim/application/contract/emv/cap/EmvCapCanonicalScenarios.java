package io.openauth.sim.application.contract.emv.cap;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.emv.cap.EmvCapEvaluationApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapReplayApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.core.emv.cap.EmvCapInput;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import java.util.List;
import java.util.Optional;

public final class EmvCapCanonicalScenarios {

    private static final String INLINE_VECTOR_ID = "identify-baseline";
    private static final String STORED_CREDENTIAL_ID = "emv-cap-identify-baseline";

    private EmvCapCanonicalScenarios() {}

    public static List<CanonicalScenario> scenarios(ScenarioEnvironment env) {
        seedStored(env);

        EmvCapVectorFixtures.EmvCapVector vector = EmvCapVectorFixtures.load(INLINE_VECTOR_ID);
        EmvCapEvaluationApplicationService.EvaluationRequest inlineRequest = toRequest(vector, 0, 0);

        CanonicalScenario inlineEvaluate = new CanonicalScenario(
                "S-005-CF-01-inline-evaluate",
                CanonicalScenario.Protocol.EMV_CAP,
                CanonicalScenario.Kind.EVALUATE_INLINE,
                inlineRequest,
                new CanonicalFacadeResult(
                        true, "generated", vector.outputs().otpDecimal(), null, null, null, null, false, true));

        EmvCapReplayApplicationService.ReplayCommand.Stored storedReplayMatchCommand =
                new EmvCapReplayApplicationService.ReplayCommand.Stored(
                        STORED_CREDENTIAL_ID,
                        vector.input().mode(),
                        vector.outputs().otpDecimal(),
                        0,
                        0,
                        Optional.empty());

        CanonicalScenario storedReplayMatch = new CanonicalScenario(
                "S-005-CF-02-stored-replay-match",
                CanonicalScenario.Protocol.EMV_CAP,
                CanonicalScenario.Kind.REPLAY_STORED,
                storedReplayMatchCommand,
                new CanonicalFacadeResult(
                        true, "match", vector.outputs().otpDecimal(), null, null, null, null, false, true));

        EmvCapReplayApplicationService.ReplayCommand.Stored storedReplayMismatchCommand =
                new EmvCapReplayApplicationService.ReplayCommand.Stored(
                        STORED_CREDENTIAL_ID, vector.input().mode(), "00000000", 0, 0, Optional.empty());

        CanonicalScenario storedReplayMismatch = new CanonicalScenario(
                "S-005-CF-03-stored-replay-mismatch",
                CanonicalScenario.Protocol.EMV_CAP,
                CanonicalScenario.Kind.FAILURE_STORED,
                storedReplayMismatchCommand,
                new CanonicalFacadeResult(false, "otp_mismatch", null, null, null, null, null, false, true));

        return List.of(inlineEvaluate, storedReplayMatch, storedReplayMismatch);
    }

    private static void seedStored(ScenarioEnvironment env) {
        EmvCapSeedApplicationService seeder = new EmvCapSeedApplicationService();
        List<EmvCapSeedApplicationService.SeedCommand> commands = EmvCapSeedSamples.samples().stream()
                .map(EmvCapSeedSamples.SeedSample::toSeedCommand)
                .toList();
        seeder.seed(commands, env.store());
    }

    private static EmvCapEvaluationApplicationService.EvaluationRequest toRequest(
            EmvCapVectorFixtures.EmvCapVector vector, int previewBackward, int previewForward) {
        EmvCapInput input = vector.input();
        EmvCapEvaluationApplicationService.CustomerInputs customerInputs =
                new EmvCapEvaluationApplicationService.CustomerInputs(
                        input.customerInputs().challenge(),
                        input.customerInputs().reference(),
                        input.customerInputs().amount());
        EmvCapEvaluationApplicationService.TransactionData transactionData =
                new EmvCapEvaluationApplicationService.TransactionData(
                        input.transactionData().terminalHexOverride(),
                        input.transactionData().iccHexOverride());
        return new EmvCapEvaluationApplicationService.EvaluationRequest(
                input.mode(),
                input.masterKeyHex(),
                input.atcHex(),
                input.branchFactor(),
                input.height(),
                previewBackward,
                previewForward,
                input.ivHex(),
                input.cdol1Hex(),
                input.issuerProprietaryBitmapHex(),
                customerInputs,
                transactionData,
                input.iccDataTemplateHex(),
                input.issuerApplicationDataHex());
    }
}
