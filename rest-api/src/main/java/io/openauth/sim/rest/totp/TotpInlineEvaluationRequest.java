package io.openauth.sim.rest.totp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openauth.sim.rest.EvaluationWindowRequest;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record TotpInlineEvaluationRequest(
        @JsonProperty("sharedSecretHex") String sharedSecretHex,
        @JsonProperty("sharedSecretBase32") String sharedSecretBase32,
        @JsonProperty("algorithm") String algorithm,
        @JsonProperty("digits") Integer digits,
        @JsonProperty("stepSeconds") Long stepSeconds,
        @JsonProperty("window") EvaluationWindowRequest window,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("timestampOverride") Long timestampOverride,
        @JsonProperty("otp") String otp,
        @JsonProperty("metadata") Map<String, String> metadata,
        @JsonProperty("verbose") Boolean verbose) {
    // no members
}
