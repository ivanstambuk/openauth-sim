package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures;
import io.openauth.sim.core.eudi.openid4vp.TrustedAuthorityFixtures.TrustedAuthoritySnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TrustedAuthorityEvaluatorTest {

    private static final TrustedAuthoritySnapshot SNAPSHOT = TrustedAuthorityFixtures.loadSnapshot("haip-baseline");

    @Test
    void authorityKeyIdentifierMatchReturnsFriendlyLabel() {
        TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(SNAPSHOT);

        TrustedAuthorityEvaluator.Decision decision =
                evaluator.evaluate(Optional.of("aki:s9tIpP7qrS9="), List.of("aki:s9tIpP7qrS9="));

        assertTrue(decision.trustedAuthorityMatch().isPresent());
        TrustedAuthorityEvaluator.TrustedAuthorityVerdict match =
                decision.trustedAuthorityMatch().orElseThrow();
        assertEquals("aki", match.type());
        assertEquals("s9tIpP7qrS9=", match.value());
        assertEquals("EU PID Issuer", match.label());
        assertEquals("aki:s9tIpP7qrS9=", match.policy());
        assertTrue(decision.problemDetails().isEmpty());
    }

    @Test
    void missingAuthorityKeyIdentifierReturnsInvalidScopeProblem() {
        TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(SNAPSHOT);

        TrustedAuthorityEvaluator.Decision decision =
                evaluator.evaluate(Optional.of("aki:unknown"), List.of("aki:s9tIpP7qrS9="));

        assertTrue(decision.trustedAuthorityMatch().isEmpty());
        Oid4vpProblemDetails problem = decision.problemDetails().orElseThrow();
        assertEquals(
                "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#error/invalid_scope",
                problem.type());
        assertEquals("invalid_scope", problem.title());
        assertEquals(400, problem.status());
        assertTrue(problem.detail().contains("aki:unknown"));
        assertTrue(problem.violations().isEmpty());
    }

    @Test
    void absentRequestedPolicySkipsEvaluation() {
        TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(SNAPSHOT);

        TrustedAuthorityEvaluator.Decision decision = evaluator.evaluate(Optional.empty(), List.of());

        assertTrue(decision.trustedAuthorityMatch().isEmpty());
        assertTrue(decision.problemDetails().isEmpty());
    }

    @Test
    void describePoliciesReturnsFriendlyLabels() {
        TrustedAuthorityEvaluator evaluator = TrustedAuthorityEvaluator.fromSnapshot(SNAPSHOT);

        List<TrustedAuthorityEvaluator.TrustedAuthorityVerdict> verdicts =
                evaluator.describePolicies(List.of("aki:s9tIpP7qrS9="));

        assertEquals(1, verdicts.size());
        assertEquals("EU PID Issuer", verdicts.get(0).label());
    }
}
