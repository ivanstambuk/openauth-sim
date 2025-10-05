package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** Request payload for HOTP replay operations (stored or inline). */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpReplayRequest(
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("identifier") String identifier,
    @JsonProperty("sharedSecretHex") String sharedSecretHex,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("digits") Integer digits,
    @JsonProperty("counter") Long counter,
    @JsonProperty("otp") String otp,
    @JsonProperty("metadata") Map<String, String> metadata) {

  // Canonical record; controller/service validate invariants.
}
