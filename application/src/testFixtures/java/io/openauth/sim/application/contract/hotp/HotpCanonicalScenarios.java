package io.openauth.sim.application.contract.hotp;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.hotp.HotpEvaluationApplicationService;
import io.openauth.sim.application.hotp.HotpIssuanceApplicationService;
import io.openauth.sim.application.hotp.HotpReplayApplicationService;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import java.util.List;
import java.util.Map;

public final class HotpCanonicalScenarios {

    private static final String SECRET_RFC_HEX = "3132333435363738393031323334353637383930";
    private static final String STORED_ID = "hotp-rfc";

    private HotpCanonicalScenarios() {}

    public static List<CanonicalScenario> scenarios(ScenarioEnvironment env) {
        seedStored(env);

        CanonicalScenario inlineEvaluate = new CanonicalScenario(
                "S-001-CF-01-inline-evaluate",
                CanonicalScenario.Protocol.HOTP,
                CanonicalScenario.Kind.EVALUATE_INLINE,
                new HotpEvaluationApplicationService.EvaluationCommand.Inline(
                        SECRET_RFC_HEX, HotpHashAlgorithm.SHA1, 6, 0L, Map.of(), 0, 0),
                new CanonicalFacadeResult(true, "generated", "755224", 0L, 1L, null, null, false, true));

        CanonicalScenario storedEvaluate = new CanonicalScenario(
                "S-001-CF-02-stored-evaluate",
                CanonicalScenario.Protocol.HOTP,
                CanonicalScenario.Kind.EVALUATE_STORED,
                new HotpEvaluationApplicationService.EvaluationCommand.Stored(STORED_ID, 0, 0),
                new CanonicalFacadeResult(true, "generated", "755224", 0L, 1L, null, null, false, true));

        CanonicalScenario storedReplayMatch = new CanonicalScenario(
                "S-001-CF-03-stored-replay-match",
                CanonicalScenario.Protocol.HOTP,
                CanonicalScenario.Kind.REPLAY_STORED,
                new HotpReplayApplicationService.ReplayCommand.Stored(STORED_ID, "755224"),
                new CanonicalFacadeResult(true, "match", "755224", 0L, 1L, null, null, false, true));

        CanonicalScenario storedReplayMismatch = new CanonicalScenario(
                "S-001-CF-04-stored-replay-mismatch",
                CanonicalScenario.Protocol.HOTP,
                CanonicalScenario.Kind.FAILURE_STORED,
                new HotpReplayApplicationService.ReplayCommand.Stored(STORED_ID, "000000"),
                new CanonicalFacadeResult(false, "otp_mismatch", null, 0L, 1L, null, null, false, true));

        return List.of(inlineEvaluate, storedEvaluate, storedReplayMatch, storedReplayMismatch);
    }

    private static void seedStored(ScenarioEnvironment env) {
        HotpIssuanceApplicationService issuance = new HotpIssuanceApplicationService(env.store());
        issuance.issue(new HotpIssuanceApplicationService.IssuanceCommand(
                STORED_ID, SECRET_RFC_HEX, HotpHashAlgorithm.SHA1, 6, 0L, Map.of("preset", "rfc4226")));
    }
}
