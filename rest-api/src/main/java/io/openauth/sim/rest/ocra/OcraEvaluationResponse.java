package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OcraEvaluationResponse(String suite, String otp, String telemetryId) {

  public OcraEvaluationResponse {
    suite = suite == null ? null : suite.trim();
    otp = otp == null ? null : otp.trim();
    telemetryId = telemetryId == null ? null : telemetryId.trim();
  }
}
