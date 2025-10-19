package io.openauth.sim.rest.webauthn;

import java.util.Map;

final class WebAuthnReplayValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String reasonCode;
    private final transient Map<String, Object> details;

    WebAuthnReplayValidationException(String reasonCode, String message, Map<String, Object> details) {
        super(message);
        this.reasonCode = reasonCode;
        this.details = details;
    }

    String reasonCode() {
        return reasonCode;
    }

    Map<String, Object> details() {
        return details;
    }
}
