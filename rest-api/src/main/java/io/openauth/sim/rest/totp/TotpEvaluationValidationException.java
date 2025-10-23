package io.openauth.sim.rest.totp;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;

final class TotpEvaluationValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;
    private final transient Map<String, Object> details;
    private final transient VerboseTrace trace;

    TotpEvaluationValidationException(
            String reasonCode, String message, Map<String, Object> details, VerboseTrace trace) {
        super(message);
        this.reasonCode = reasonCode;
        this.details = Map.copyOf(details);
        this.trace = trace;
    }

    String reasonCode() {
        return reasonCode;
    }

    Map<String, Object> details() {
        return details;
    }

    VerboseTrace trace() {
        return trace;
    }
}
