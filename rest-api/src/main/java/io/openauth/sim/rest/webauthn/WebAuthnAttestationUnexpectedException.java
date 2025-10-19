package io.openauth.sim.rest.webauthn;

import java.util.Map;

final class WebAuthnAttestationUnexpectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String reasonCode;
  private final transient Map<String, Object> metadata;

  WebAuthnAttestationUnexpectedException(
      String reasonCode, String message, Map<String, Object> metadata) {
    super(message);
    this.reasonCode = reasonCode;
    this.metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
  }

  String reasonCode() {
    return reasonCode;
  }

  Map<String, Object> metadata() {
    return metadata;
  }
}
