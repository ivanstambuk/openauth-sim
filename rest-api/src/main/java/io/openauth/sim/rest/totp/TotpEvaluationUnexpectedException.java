package io.openauth.sim.rest.totp;

final class TotpEvaluationUnexpectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  TotpEvaluationUnexpectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
