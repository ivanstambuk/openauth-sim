package io.openauth.sim.rest.webauthn;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;

final class WebAuthnEvaluationUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, Object> details;
    private final transient VerboseTrace trace;

    WebAuthnEvaluationUnexpectedException(
            String message, Throwable cause, Map<String, Object> details, VerboseTrace trace) {
        super(message, cause);
        this.details = details;
        this.trace = trace;
    }

    Map<String, Object> details() {
        return details;
    }

    VerboseTrace trace() {
        return trace;
    }
}
