package io.openauth.sim.application.eudi.openid4vp;

import java.util.List;
import java.util.Objects;

public final class Oid4vpProblemDetailsMapper {

    private static final String SPEC_BASE =
            "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#error/";

    private Oid4vpProblemDetailsMapper() {
        throw new AssertionError("Utility class");
    }

    public static Oid4vpProblemDetails invalidRequest(String detail, List<Oid4vpProblemDetails.Violation> violations) {
        return problem("invalid_request", 400, detail, violations);
    }

    public static Oid4vpProblemDetails invalidScope(String detail) {
        return problem("invalid_scope", 400, detail, List.of());
    }

    public static Oid4vpProblemDetails walletUnavailable(String detail) {
        return problem("wallet_unavailable", 503, detail, List.of());
    }

    public static Oid4vpProblemDetails invalidPresentation(String detail) {
        return problem("invalid_presentation", 422, detail, List.of());
    }

    private static Oid4vpProblemDetails problem(
            String code, int status, String detail, List<Oid4vpProblemDetails.Violation> violations) {
        Objects.requireNonNull(code, "code");
        return new Oid4vpProblemDetails(specType(code), code, status, detail, copyViolations(violations));
    }

    private static String specType(String code) {
        return SPEC_BASE + code;
    }

    private static List<Oid4vpProblemDetails.Violation> copyViolations(
            List<Oid4vpProblemDetails.Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            return List.of();
        }
        return List.copyOf(violations);
    }
}
