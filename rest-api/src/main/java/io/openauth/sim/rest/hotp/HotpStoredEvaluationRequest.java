package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Request payload for evaluating a stored HOTP credential. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpStoredEvaluationRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("verbose") Boolean verbose) {

    // Canonical record; no additional behaviour.
}
