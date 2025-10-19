package io.openauth.sim.rest.webauthn;

import java.util.Map;

final class WebAuthnAttestationValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String reasonCode;
  private final transient Map<String, Object> details;
  private final transient Map<String, Object> metadata;

  WebAuthnAttestationValidationException(
      String reasonCode,
      String message,
      Map<String, Object> details,
      Map<String, Object> metadata) {
    super(message);
    this.reasonCode = reasonCode;
    this.details = Map.copyOf(details == null ? Map.of() : details);
    this.metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
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
}
