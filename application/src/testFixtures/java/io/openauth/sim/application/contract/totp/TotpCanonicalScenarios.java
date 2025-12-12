package io.openauth.sim.application.contract.totp;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.totp.TotpEvaluationApplicationService;
import io.openauth.sim.application.totp.TotpReplayApplicationService;
import io.openauth.sim.application.totp.TotpSeedApplicationService;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpJsonVectorFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TotpCanonicalScenarios {

    private static final String VECTOR_ID = "rfc6238_sha1_digits8_t59";
    private static final String STORED_ID = "totp-rfc";

    private TotpCanonicalScenarios() {}

    public static List<CanonicalScenario> scenarios(ScenarioEnvironment env) {
        TotpJsonVectorFixtures.TotpJsonVector vector = TotpJsonVectorFixtures.getById(VECTOR_ID);
        seedStored(vector, env);

        TotpDriftWindow driftWindow = TotpDriftWindow.of(0, 0);

        CanonicalScenario inlineEvaluate = new CanonicalScenario(
                "S-002-CF-01-inline-evaluate",
                CanonicalScenario.Protocol.TOTP,
                CanonicalScenario.Kind.EVALUATE_INLINE,
                new TotpEvaluationApplicationService.EvaluationCommand.Inline(
                        vector.secret().asHex(),
                        vector.algorithm(),
                        vector.digits(),
                        vector.stepDuration(),
                        "",
                        driftWindow,
                        vector.timestamp(),
                        Optional.empty()),
                new CanonicalFacadeResult(
                        true,
                        "generated",
                        vector.otp(),
                        null,
                        null,
                        vector.timestampEpochSeconds(),
                        null,
                        false,
                        true));

        CanonicalScenario storedEvaluate = new CanonicalScenario(
                "S-002-CF-02-stored-evaluate",
                CanonicalScenario.Protocol.TOTP,
                CanonicalScenario.Kind.EVALUATE_STORED,
                new TotpEvaluationApplicationService.EvaluationCommand.Stored(
                        STORED_ID, "", driftWindow, vector.timestamp(), Optional.empty()),
                new CanonicalFacadeResult(
                        true,
                        "generated",
                        vector.otp(),
                        null,
                        null,
                        vector.timestampEpochSeconds(),
                        null,
                        false,
                        true));

        CanonicalScenario storedReplayMatch = new CanonicalScenario(
                "S-002-CF-03-stored-replay-match",
                CanonicalScenario.Protocol.TOTP,
                CanonicalScenario.Kind.REPLAY_STORED,
                new TotpReplayApplicationService.ReplayCommand.Stored(
                        STORED_ID, vector.otp(), driftWindow, vector.timestamp(), Optional.empty()),
                new CanonicalFacadeResult(
                        true, "match", vector.otp(), null, null, vector.timestampEpochSeconds(), null, false, true));

        CanonicalScenario storedReplayMismatch = new CanonicalScenario(
                "S-002-CF-04-stored-replay-mismatch",
                CanonicalScenario.Protocol.TOTP,
                CanonicalScenario.Kind.FAILURE_STORED,
                new TotpReplayApplicationService.ReplayCommand.Stored(
                        STORED_ID, "00000000", driftWindow, vector.timestamp(), Optional.empty()),
                new CanonicalFacadeResult(
                        false,
                        "otp_out_of_window",
                        null,
                        null,
                        null,
                        vector.timestampEpochSeconds(),
                        null,
                        false,
                        true));

        return List.of(inlineEvaluate, storedEvaluate, storedReplayMatch, storedReplayMismatch);
    }

    private static void seedStored(TotpJsonVectorFixtures.TotpJsonVector vector, ScenarioEnvironment env) {
        TotpSeedApplicationService seeder = new TotpSeedApplicationService();
        seeder.seed(
                List.of(new TotpSeedApplicationService.SeedCommand(
                        STORED_ID,
                        vector.secret().asHex(),
                        vector.algorithm(),
                        vector.digits(),
                        vector.stepDuration(),
                        TotpDriftWindow.of(vector.driftBackwardSteps(), vector.driftForwardSteps()),
                        Map.of("preset", VECTOR_ID))),
                env.store());
    }
}
