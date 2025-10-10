package io.openauth.sim.rest.webauthn;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webauthn")
class WebAuthnEvaluationController {

  private final WebAuthnEvaluationService service;

  WebAuthnEvaluationController(WebAuthnEvaluationService service) {
    this.service = service;
  }

  @Operation(
      operationId = "evaluateStoredWebAuthn",
      summary = "Validate a stored WebAuthn credential",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Assertion accepted",
            content =
                @Content(schema = @Schema(implementation = WebAuthnEvaluationResponse.class))),
        @ApiResponse(
            responseCode = "422",
            description = "Assertion rejected or request invalid",
            content =
                @Content(schema = @Schema(implementation = WebAuthnEvaluationErrorResponse.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error",
            content =
                @Content(schema = @Schema(implementation = WebAuthnEvaluationErrorResponse.class)))
      })
  @PostMapping("/evaluate")
  ResponseEntity<WebAuthnEvaluationResponse> evaluateStored(
      @RequestBody WebAuthnStoredEvaluationRequest request) {
    WebAuthnEvaluationResponse response = service.evaluateStored(request);
    return ResponseEntity.ok(response);
  }

  @Operation(
      operationId = "evaluateInlineWebAuthn",
      summary = "Validate an inline WebAuthn assertion without loading a stored credential",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Assertion accepted",
            content =
                @Content(schema = @Schema(implementation = WebAuthnEvaluationResponse.class))),
        @ApiResponse(
            responseCode = "422",
            description = "Assertion rejected or request invalid",
            content =
                @Content(schema = @Schema(implementation = WebAuthnEvaluationErrorResponse.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error",
            content =
                @Content(schema = @Schema(implementation = WebAuthnEvaluationErrorResponse.class)))
      })
  @PostMapping("/evaluate/inline")
  ResponseEntity<WebAuthnEvaluationResponse> evaluateInline(
      @RequestBody WebAuthnInlineEvaluationRequest request) {
    WebAuthnEvaluationResponse response = service.evaluateInline(request);
    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(WebAuthnEvaluationValidationException.class)
  ResponseEntity<WebAuthnEvaluationErrorResponse> handleValidation(
      WebAuthnEvaluationValidationException exception) {
    WebAuthnEvaluationErrorResponse body =
        new WebAuthnEvaluationErrorResponse(
            exception.reasonCode(),
            exception.reasonCode(),
            exception.getMessage(),
            exception.details(),
            exception.details());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(WebAuthnEvaluationUnexpectedException.class)
  ResponseEntity<WebAuthnEvaluationErrorResponse> handleUnexpected(
      WebAuthnEvaluationUnexpectedException exception) {
    WebAuthnEvaluationErrorResponse body =
        new WebAuthnEvaluationErrorResponse(
            "internal_error",
            "webauthn_evaluation_failed",
            exception.getMessage(),
            exception.details(),
            exception.details());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<WebAuthnEvaluationErrorResponse> handleFallback(RuntimeException exception) {
    WebAuthnEvaluationErrorResponse body =
        new WebAuthnEvaluationErrorResponse(
            "internal_error",
            "webauthn_evaluation_failed",
            exception.getMessage(),
            Map.of(),
            Map.of());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
