package io.openauth.sim.application.contract.ocra;

import io.openauth.sim.application.contract.CanonicalFacadeResult;
import io.openauth.sim.application.contract.CanonicalScenario;
import io.openauth.sim.application.contract.ScenarioEnvironment;
import io.openauth.sim.application.ocra.OcraEvaluationApplicationService;
import io.openauth.sim.application.ocra.OcraSeedApplicationService;
import io.openauth.sim.application.ocra.OcraVerificationApplicationService;
import java.util.List;
import java.util.Map;

public final class OcraCanonicalScenarios {

    private static final String VECTOR_ID = "rfc6287_standard-challenge-question-numeric-q";
    private static final String STORED_ID = "ocra-rfc";
    private static final String SUITE = "OCRA-1:HOTP-SHA1-6:QN08";
    private static final String SECRET_RFC_HEX = "3132333435363738393031323334353637383930";
    private static final String CHALLENGE = "00000000";
    private static final String EXPECTED_OTP = "237653";

    private OcraCanonicalScenarios() {}

    public static List<CanonicalScenario> scenarios(ScenarioEnvironment env) {
        seedStored(env);

        CanonicalScenario inlineEvaluate = new CanonicalScenario(
                "S-003-CF-01-inline-evaluate",
                CanonicalScenario.Protocol.OCRA,
                CanonicalScenario.Kind.EVALUATE_INLINE,
                new OcraEvaluationApplicationService.EvaluationCommand.Inline(
                        "inline", SUITE, SECRET_RFC_HEX, CHALLENGE, null, null, null, null, null, null, null, 0, 0),
                new CanonicalFacadeResult(true, "success", EXPECTED_OTP, null, null, null, SUITE, false, true));

        CanonicalScenario storedEvaluate = new CanonicalScenario(
                "S-003-CF-02-stored-evaluate",
                CanonicalScenario.Protocol.OCRA,
                CanonicalScenario.Kind.EVALUATE_STORED,
                new OcraEvaluationApplicationService.EvaluationCommand.Stored(
                        STORED_ID, CHALLENGE, null, null, null, null, null, null, 0, 0),
                new CanonicalFacadeResult(true, "success", EXPECTED_OTP, null, null, null, SUITE, false, true));

        CanonicalScenario storedVerifyMatch = new CanonicalScenario(
                "S-003-CF-03-stored-verify-match",
                CanonicalScenario.Protocol.OCRA,
                CanonicalScenario.Kind.REPLAY_STORED,
                new OcraVerificationApplicationService.VerificationCommand.Stored(
                        STORED_ID, EXPECTED_OTP, CHALLENGE, null, null, null, null, null, null),
                new CanonicalFacadeResult(true, "match", EXPECTED_OTP, null, null, null, SUITE, false, true));

        CanonicalScenario storedVerifyMismatch = new CanonicalScenario(
                "S-003-CF-04-stored-verify-mismatch",
                CanonicalScenario.Protocol.OCRA,
                CanonicalScenario.Kind.FAILURE_STORED,
                new OcraVerificationApplicationService.VerificationCommand.Stored(
                        STORED_ID, "000000", CHALLENGE, null, null, null, null, null, null),
                new CanonicalFacadeResult(false, "strict_mismatch", null, null, null, null, SUITE, false, true));

        return List.of(inlineEvaluate, storedEvaluate, storedVerifyMatch, storedVerifyMismatch);
    }

    private static void seedStored(ScenarioEnvironment env) {
        OcraSeedApplicationService seeder = new OcraSeedApplicationService();
        seeder.seed(
                List.of(new OcraSeedApplicationService.SeedCommand(
                        STORED_ID, SUITE, SECRET_RFC_HEX, null, null, null, Map.of("preset", VECTOR_ID))),
                env.store());
    }
}
