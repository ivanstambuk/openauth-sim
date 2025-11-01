package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.EvaluationWindowRequest;
import java.util.Map;

/** Request payload for inline HOTP evaluation (placeholder). */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpInlineEvaluationRequest(
        @JsonProperty("sharedSecretHex") String sharedSecretHex,
        @JsonProperty("sharedSecretBase32") String sharedSecretBase32,
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("digits") Integer digits,
        @JsonProperty("window") EvaluationWindowRequest window,
        @JsonProperty("counter") Long counter,
        @JsonProperty("metadata") Map<String, String> metadata,
        @JsonProperty("verbose") Boolean verbose) {

    // Canonical record; no additional behaviour.
}
