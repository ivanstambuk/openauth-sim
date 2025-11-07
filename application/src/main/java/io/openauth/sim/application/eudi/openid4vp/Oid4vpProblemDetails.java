package io.openauth.sim.application.eudi.openid4vp;

import java.util.List;
import java.util.Objects;

/** RFC 7807 problem-details payload aligned with OpenID4VP error codes. */
public record Oid4vpProblemDetails(String type, String title, int status, String detail, List<Violation> violations) {
    public Oid4vpProblemDetails {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(title, "title");
        if (status <= 0) {
            throw new IllegalArgumentException("status must be positive");
        }
        detail = detail == null ? "" : detail;
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public record Violation(String field, String message) {
        public Violation {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(message, "message");
        }
    }
}
