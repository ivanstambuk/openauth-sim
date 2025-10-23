package io.openauth.sim.rest.hotp;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;

/** Signals HOTP replay unexpected failures. */
class HotpReplayUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String telemetryId;
    private final String credentialSource;
    private final transient Map<String, Object> details;
    private final transient VerboseTrace trace;

    HotpReplayUnexpectedException(
            String telemetryId,
            String credentialSource,
            String message,
            Map<String, Object> details,
            VerboseTrace trace) {
        super(message);
        this.telemetryId = telemetryId;
        this.credentialSource = credentialSource;
        this.details = details;
        this.trace = trace;
    }

    String telemetryId() {
        return telemetryId;
    }

    String credentialSource() {
        return credentialSource;
    }

    Map<String, Object> details() {
        return details;
    }

    VerboseTrace trace() {
        return trace;
    }
}
