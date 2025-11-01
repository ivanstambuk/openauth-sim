package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.EvaluationWindowRequest;

/** Request payload for evaluating a stored HOTP credential. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpStoredEvaluationRequest(
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("window") EvaluationWindowRequest window,
        @JsonProperty("verbose") Boolean verbose) {

    // Canonical record; no additional behaviour.
}
