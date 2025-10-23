package io.openauth.sim.rest.webauthn;

import io.openauth.sim.core.trace.VerboseTrace;
import java.util.Map;

final class WebAuthnAttestationValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;
    private final transient Map<String, Object> details;
    private final transient Map<String, Object> metadata;
    private final transient VerboseTrace trace;

    WebAuthnAttestationValidationException(
            String reasonCode,
            String message,
            Map<String, Object> details,
            Map<String, Object> metadata,
            VerboseTrace trace) {
        super(message);
        this.reasonCode = reasonCode;
        this.details = Map.copyOf(details == null ? Map.of() : details);
        this.metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        this.trace = trace;
    }

    String reasonCode() {
        return reasonCode;
    }

    Map<String, Object> details() {
        return details;
    }

    Map<String, Object> metadata() {
        return metadata;
    }

    VerboseTrace trace() {
        return trace;
    }
}
