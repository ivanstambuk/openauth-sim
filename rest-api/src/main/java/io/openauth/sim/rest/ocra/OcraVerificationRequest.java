package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationRequest(
        @JsonProperty("otp") String otp,
        @JsonProperty("credentialId") String credentialId,
        @JsonProperty("inlineCredential") OcraVerificationInlineCredential inlineCredential,
        @JsonProperty("context") OcraVerificationContext context,
        @JsonProperty("verbose") Boolean verbose) {
    // Payload contract only; behaviour defined in OcraVerificationService.
}
