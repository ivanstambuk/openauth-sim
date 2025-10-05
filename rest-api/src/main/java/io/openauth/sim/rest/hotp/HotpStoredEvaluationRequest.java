package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request payload for evaluating a stored HOTP credential (placeholder). */
record HotpStoredEvaluationRequest(
    @JsonProperty("credentialId") String credentialId, @JsonProperty("otp") String otp) {

  // Canonical record; no additional behaviour.
}
