package io.openauth.sim.rest.eudi.openid4vp;

import io.openauth.sim.application.eudi.openid4vp.Oid4vpProblemDetails;
import java.util.List;
import java.util.Objects;

record Oid4vpProblemDetailsResponse(String type, String title, int status, String detail, List<Violation> violations) {

    Oid4vpProblemDetailsResponse {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(detail, "detail");
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    static Oid4vpProblemDetailsResponse from(Oid4vpProblemDetails problem) {
        Objects.requireNonNull(problem, "problem");
        List<Violation> mapped = problem.violations().stream()
                .map(v -> new Violation(v.field(), v.message()))
                .toList();
        return new Oid4vpProblemDetailsResponse(
                problem.type(), problem.title(), problem.status(), problem.detail(), mapped);
    }

    record Violation(String field, String message) {
        Violation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
        }
    }
}
