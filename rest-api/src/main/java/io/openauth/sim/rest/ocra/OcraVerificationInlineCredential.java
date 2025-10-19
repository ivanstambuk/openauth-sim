package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OcraVerificationInlineCredential(
        @JsonProperty("suite") String suite,
        @JsonProperty("sharedSecretHex") String sharedSecretHex) {
    // Marker record for inline credential payloads.
}
