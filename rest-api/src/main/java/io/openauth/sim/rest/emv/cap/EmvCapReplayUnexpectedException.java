package io.openauth.sim.rest.emv.cap;

import java.util.Collections;
import java.util.Map;

final class EmvCapReplayUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Map<String, Object> details;

    EmvCapReplayUnexpectedException(String message, Map<String, Object> details) {
        super(message);
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(details);
    }

    Map<String, Object> details() {
        return details;
    }
}
