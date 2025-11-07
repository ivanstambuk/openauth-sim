package io.openauth.sim.application.eudi.openid4vp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class Oid4vpProblemDetailsMapperTest {

    @Test
    void invalidRequestIncludesViolationsAnd400Status() {
        Oid4vpProblemDetails problem = Oid4vpProblemDetailsMapper.invalidRequest(
                "Missing dcql query",
                List.of(new Oid4vpProblemDetails.Violation("dcqlPreset", "dcqlPreset is required")));

        assertEquals(
                "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#error/invalid_request",
                problem.type());
        assertEquals("invalid_request", problem.title());
        assertEquals(400, problem.status());
        assertEquals("Missing dcql query", problem.detail());
        assertEquals(1, problem.violations().size());
        assertEquals("dcqlPreset", problem.violations().get(0).field());
        assertEquals("dcqlPreset is required", problem.violations().get(0).message());
    }

    @Test
    void invalidScopeUses400StatusWithoutViolations() {
        Oid4vpProblemDetails problem = Oid4vpProblemDetailsMapper.invalidScope("Trusted Authority policy rejected");

        assertEquals(
                "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#error/invalid_scope",
                problem.type());
        assertEquals("invalid_scope", problem.title());
        assertEquals(400, problem.status());
        assertEquals("Trusted Authority policy rejected", problem.detail());
        assertTrue(problem.violations().isEmpty());
    }

    @Test
    void walletUnavailableUses503Status() {
        Oid4vpProblemDetails problem = Oid4vpProblemDetailsMapper.walletUnavailable("Wallet simulator disabled");

        assertEquals(
                "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#error/wallet_unavailable",
                problem.type());
        assertEquals("wallet_unavailable", problem.title());
        assertEquals(503, problem.status());
        assertEquals("Wallet simulator disabled", problem.detail());
    }
}
