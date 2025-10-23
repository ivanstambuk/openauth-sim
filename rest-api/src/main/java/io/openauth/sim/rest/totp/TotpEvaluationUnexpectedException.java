package io.openauth.sim.rest.totp;

import io.openauth.sim.core.trace.VerboseTrace;

final class TotpEvaluationUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient VerboseTrace trace;

    TotpEvaluationUnexpectedException(String message, Throwable cause, VerboseTrace trace) {
        super(message, cause);
        this.trace = trace;
    }

    VerboseTrace trace() {
        return trace;
    }
}
