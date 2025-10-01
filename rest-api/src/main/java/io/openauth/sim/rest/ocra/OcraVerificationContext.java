package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationContext(
    @JsonProperty("challenge") String challenge,
    @JsonProperty("clientChallenge") String clientChallenge,
    @JsonProperty("serverChallenge") String serverChallenge,
    @JsonProperty("sessionHex") String sessionHex,
    @JsonProperty("timestampHex") String timestampHex,
    @JsonProperty("counter") Long counter,
    @JsonProperty("pinHashHex") String pinHashHex) {
  // Context payload mirrors RFC 6287 inputs.
}
