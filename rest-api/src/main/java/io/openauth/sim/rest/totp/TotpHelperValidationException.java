package io.openauth.sim.rest.totp;

import java.util.Map;

final class TotpHelperValidationException extends RuntimeException {

    private final String reasonCode;
    private final Map<String, Object> details;

    TotpHelperValidationException(String reasonCode, String message, Map<String, Object> details) {
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
