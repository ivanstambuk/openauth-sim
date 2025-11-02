package io.openauth.sim.rest.emv.cap;

import java.util.Map;

final class EmvCapEvaluationUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, Object> details;

    EmvCapEvaluationUnexpectedException(String message, Throwable cause, Map<String, Object> details) {
        super(message, cause);
        this.details = Map.copyOf(details);
    }

    Map<String, Object> details() {
        return details;
    }
}
