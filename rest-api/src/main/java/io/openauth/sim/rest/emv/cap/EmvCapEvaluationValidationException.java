package io.openauth.sim.rest.emv.cap;

import java.util.Map;

final class EmvCapEvaluationValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;
    private final transient Map<String, Object> details;

    EmvCapEvaluationValidationException(String reasonCode, String message, Map<String, Object> details) {
        super(message);
        this.reasonCode = reasonCode;
        this.details = Map.copyOf(details);
    }

    String reasonCode() {
        return reasonCode;
    }

    Map<String, Object> details() {
        return details;
    }
}
