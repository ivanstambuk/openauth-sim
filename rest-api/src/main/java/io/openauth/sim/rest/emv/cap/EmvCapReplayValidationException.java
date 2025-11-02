package io.openauth.sim.rest.emv.cap;

import java.util.Collections;
import java.util.Map;

final class EmvCapReplayValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;
    private final Map<String, Object> details;

    EmvCapReplayValidationException(String reasonCode, String message, Map<String, Object> details) {
        super(message);
        this.reasonCode = reasonCode;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
    }

    String reasonCode() {
        return reasonCode;
    }

    Map<String, Object> details() {
        return details;
    }
}
