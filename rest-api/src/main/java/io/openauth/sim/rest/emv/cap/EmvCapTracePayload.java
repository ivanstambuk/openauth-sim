package io.openauth.sim.rest.emv.cap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
record EmvCapTracePayload(
        @JsonProperty("masterKeySha256") String masterKeySha256,
        @JsonProperty("sessionKey") String sessionKey,
        @JsonProperty("generateAcInput") GenerateAcInput generateAcInput,
        @JsonProperty("generateAcResult") String generateAcResult,
        @JsonProperty("bitmask") String bitmask,
        @JsonProperty("maskedDigitsOverlay") String maskedDigitsOverlay,
        @JsonProperty("issuerApplicationData") String issuerApplicationData,
        @JsonProperty("iccPayloadTemplate") String iccPayloadTemplate,
        @JsonProperty("iccPayloadResolved") String iccPayloadResolved) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GenerateAcInput(
            @JsonProperty("terminal") String terminal,
            @JsonProperty("icc") String icc) {
        // no members
    }
}
