package io.openauth.sim.rest.totp;

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
@RequestMapping("/api/v1/totp")
class TotpEvaluationController {

    private final TotpEvaluationService service;

    TotpEvaluationController(TotpEvaluationService service) {
        this.service = service;
    }

    @Operation(
            operationId = "evaluateStoredTotp",
            summary = "Validate a stored TOTP credential",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OTP accepted",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "OTP rejected or validation failed",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationErrorResponse.class)))
            })
    @PostMapping("/evaluate")
    ResponseEntity<TotpEvaluationResponse> evaluateStored(@RequestBody TotpStoredEvaluationRequest request) {
        TotpEvaluationResponse response = service.evaluateStored(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            operationId = "evaluateInlineTotp",
            summary = "Validate a TOTP submission without referencing stored credentials",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OTP accepted",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "OTP rejected or validation failed",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationErrorResponse.class)))
            })
    @PostMapping("/evaluate/inline")
    ResponseEntity<TotpEvaluationResponse> evaluateInline(@RequestBody TotpInlineEvaluationRequest request) {
        TotpEvaluationResponse response = service.evaluateInline(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(TotpEvaluationValidationException.class)
    ResponseEntity<TotpEvaluationErrorResponse> handleValidation(TotpEvaluationValidationException exception) {
        TotpEvaluationErrorResponse body = new TotpEvaluationErrorResponse(
                "invalid_input", exception.reasonCode(), exception.getMessage(), exception.details());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(TotpEvaluationUnexpectedException.class)
    ResponseEntity<TotpEvaluationErrorResponse> handleUnexpected(TotpEvaluationUnexpectedException exception) {
        TotpEvaluationErrorResponse body = new TotpEvaluationErrorResponse(
                "internal_error",
                "totp_evaluation_failed",
                exception.getMessage(),
                Map.of(
                        "details",
                        exception.getCause() != null ? exception.getCause().getMessage() : "n/a"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<TotpEvaluationErrorResponse> handleFallback(RuntimeException exception) {
        TotpEvaluationErrorResponse body = new TotpEvaluationErrorResponse(
                "internal_error", "totp_evaluation_failed", exception.getMessage(), Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
