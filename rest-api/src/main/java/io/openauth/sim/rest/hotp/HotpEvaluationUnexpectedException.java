package io.openauth.sim.rest.hotp;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Wraps unexpected evaluation errors so the controller can emit a 500 response with metadata. */
final class HotpEvaluationUnexpectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String telemetryId;
    private final String credentialSource;
    private final transient Map<String, Object> details;
    private final transient VerboseTrace trace;

    HotpEvaluationUnexpectedException(
            String telemetryId,
            String credentialSource,
            String message,
            Map<String, Object> details,
            VerboseTrace trace) {
        super(message);
        this.telemetryId = Objects.requireNonNull(telemetryId, "telemetryId");
        this.credentialSource = credentialSource;
        this.details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
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
