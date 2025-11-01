package io.openauth.sim.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpPreviewResponse(
        @JsonProperty("counter") String counter,
        @JsonProperty("delta") int delta,
        @JsonProperty("otp") String otp) {
    public static OtpPreviewResponse evaluatedOnly(String otp) {
        return new OtpPreviewResponse(null, 0, otp);
    }
}
