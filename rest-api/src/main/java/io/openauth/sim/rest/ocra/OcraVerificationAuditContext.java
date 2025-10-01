package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Immutable request-level metadata captured for verification telemetry. */
record OcraVerificationAuditContext(String requestId, String clientId, String operatorPrincipal) {

  OcraVerificationAuditContext {
    requestId = normalise(requestId);
    clientId = normalise(clientId);
    operatorPrincipal = normalise(operatorPrincipal);
  }

  @JsonIgnore
  String resolvedOperatorPrincipal() {
    return operatorPrincipal != null ? operatorPrincipal : "anonymous";
  }

  private static String normalise(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
