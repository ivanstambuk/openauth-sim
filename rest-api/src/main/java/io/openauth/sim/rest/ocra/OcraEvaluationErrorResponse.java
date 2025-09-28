package io.openauth.sim.rest.ocra;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OcraEvaluationErrorResponse(
    String error, String message, Map<String, String> details) {

  public OcraEvaluationErrorResponse {
    details = details == null ? Map.of() : Map.copyOf(details);
  }
}
