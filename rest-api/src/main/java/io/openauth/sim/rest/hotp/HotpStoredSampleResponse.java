package io.openauth.sim.rest.hotp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/** Response payload returned when requesting a HOTP stored sample. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record HotpStoredSampleResponse(
    @JsonProperty("credentialId") String credentialId,
    @JsonProperty("otp") String otp,
    @JsonProperty("counter") long counter,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("digits") int digits,
    @JsonProperty("metadata") Map<String, String> metadata) {

  HotpStoredSampleResponse {
    Objects.requireNonNull(credentialId, "credentialId");
    Objects.requireNonNull(otp, "otp");
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
