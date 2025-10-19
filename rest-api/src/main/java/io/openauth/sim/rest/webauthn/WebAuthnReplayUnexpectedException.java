package io.openauth.sim.rest.webauthn;

import java.util.Map;

final class WebAuthnReplayUnexpectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Map<String, Object> details;

  WebAuthnReplayUnexpectedException(String message, Throwable cause, Map<String, Object> details) {
    super(message, cause);
    this.details = details;
  }

  Map<String, Object> details() {
    return details;
  }
}
