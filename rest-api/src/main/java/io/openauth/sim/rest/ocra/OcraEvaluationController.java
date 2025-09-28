package io.openauth.sim.rest.ocra;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ocra")
class OcraEvaluationController {

  private final OcraEvaluationService service;

  OcraEvaluationController(OcraEvaluationService service) {
    this.service = Objects.requireNonNull(service, "service");
  }

  @PostMapping(path = "/evaluate")
  ResponseEntity<OcraEvaluationResponse> evaluate(@RequestBody OcraEvaluationRequest request) {
    OcraEvaluationResponse response = service.evaluate(request);
    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(OcraEvaluationValidationException.class)
  ResponseEntity<OcraEvaluationErrorResponse> handleValidationError(
      OcraEvaluationValidationException exception) {
    OcraEvaluationErrorResponse body =
        new OcraEvaluationErrorResponse(
            "invalid_input",
            exception.getMessage(),
            Map.of(
                "telemetryId",
                exception.telemetryId(),
                "status",
                "invalid",
                "suite",
                exception.suite()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<OcraEvaluationErrorResponse> handleUnexpected(RuntimeException exception) {
    OcraEvaluationErrorResponse body =
        new OcraEvaluationErrorResponse(
            "internal_error", "OCRA evaluation failed", Map.of("status", "error"));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
