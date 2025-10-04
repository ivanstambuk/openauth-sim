package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraCredentialSampleResponse(
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("presetKey") String presetKey,
    @JsonProperty("suite") String suite,
    @JsonProperty("otp") String otp,
    @JsonProperty("context") Context context) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record Context(
      @JsonProperty("challenge") String challenge,
      @JsonProperty("sessionHex") String sessionHex,
      @JsonProperty("clientChallenge") String clientChallenge,
      @JsonProperty("serverChallenge") String serverChallenge,
      @JsonProperty("pinHashHex") String pinHashHex,
      @JsonProperty("timestampHex") String timestampHex,
      @JsonProperty("counter") Long counter) {
    // marker record
  }
}
