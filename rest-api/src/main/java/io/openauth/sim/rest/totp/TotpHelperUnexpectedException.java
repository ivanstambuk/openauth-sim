package io.openauth.sim.rest.totp;

final class TotpHelperUnexpectedException extends RuntimeException {

    TotpHelperUnexpectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
