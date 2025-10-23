package io.openauth.sim.rest.hotp;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;
import java.util.Objects;

/** Placeholder validation exception surfaced by HOTP REST evaluation. */
final class HotpEvaluationValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String telemetryId;
    private final String credentialSource;
    private final String credentialId;
    private final String reasonCode;
    private final boolean sanitized;
    private final transient Map<String, Object> details;
    private final transient VerboseTrace trace;

    HotpEvaluationValidationException(
            String telemetryId,
            String credentialSource,
            String credentialId,
            String reasonCode,
            boolean sanitized,
            Map<String, Object> details,
            String message,
            VerboseTrace trace) {
        super(message);
        this.telemetryId = Objects.requireNonNull(telemetryId, "telemetryId");
        this.credentialSource = credentialSource;
        this.credentialId = credentialId;
        this.reasonCode = reasonCode;
        this.sanitized = sanitized;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.trace = trace;
    }

    String telemetryId() {
        return telemetryId;
    }

    String credentialSource() {
        return credentialSource;
    }

    String credentialId() {
        return credentialId;
    }

    String reasonCode() {
        return reasonCode;
    }

    boolean sanitized() {
        return sanitized;
    }

    Map<String, Object> details() {
        return details;
    }

    VerboseTrace trace() {
        return trace;
    }
}
