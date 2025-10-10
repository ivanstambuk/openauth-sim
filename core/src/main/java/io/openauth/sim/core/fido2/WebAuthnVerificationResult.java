package io.openauth.sim.core.fido2;

import java.util.Optional;

/** Result container for WebAuthn assertion verification. */
public record WebAuthnVerificationResult(
    boolean success, Optional<WebAuthnVerificationError> error, String message) {

  public static WebAuthnVerificationResult successResult() {
    return new WebAuthnVerificationResult(true, Optional.empty(), "OK");
  }

  public static WebAuthnVerificationResult failure(
      WebAuthnVerificationError error, String message) {
    return new WebAuthnVerificationResult(false, Optional.of(error), message);
  }
}
