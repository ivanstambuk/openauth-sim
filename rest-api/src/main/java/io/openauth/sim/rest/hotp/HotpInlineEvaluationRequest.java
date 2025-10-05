package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Request payload for inline HOTP evaluation (placeholder). */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpInlineEvaluationRequest(
    @JsonProperty("sharedSecretHex") String sharedSecretHex,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("digits") Integer digits,
    @JsonProperty("counter") Long counter,
    @JsonProperty("otp") String otp,
    @JsonProperty("metadata") Map<String, String> metadata) {

  // Canonical record; no additional behaviour.
}
