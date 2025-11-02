package io.openauth.sim.rest.emv.cap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.LinkedHashMap;
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
@RequestMapping("/api/v1/emv/cap")
final class EmvCapEvaluationController {

    private final EmvCapEvaluationService service;

    EmvCapEvaluationController(EmvCapEvaluationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Operation(
            operationId = "evaluateEmvCap",
            summary = "Evaluate EMV/CAP parameters and derive an OTP",
            description =
                    "Executes the EMV/CAP derivation flow (Identify, Respond, or Sign) and returns the resulting OTP along with sanitized telemetry metadata.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Evaluation completed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmvCapEvaluationResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmvCapEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmvCapEvaluationErrorResponse.class)))
            })
    @PostMapping("/evaluate")
    ResponseEntity<EmvCapEvaluationResponse> evaluate(@RequestBody EmvCapEvaluationRequest request) {
        EmvCapEvaluationResponse response = service.evaluate(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(EmvCapEvaluationValidationException.class)
    ResponseEntity<EmvCapEvaluationErrorResponse> handleValidation(EmvCapEvaluationValidationException exception) {
        Map<String, Object> details = new LinkedHashMap<>(exception.details());
        details.putIfAbsent("status", "invalid");
        EmvCapEvaluationErrorResponse body = new EmvCapEvaluationErrorResponse(
                "invalid_input", exception.reasonCode(), exception.getMessage(), details, null);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(EmvCapEvaluationUnexpectedException.class)
    ResponseEntity<EmvCapEvaluationErrorResponse> handleUnexpected(EmvCapEvaluationUnexpectedException exception) {
        Map<String, Object> details = new LinkedHashMap<>(exception.details());
        details.putIfAbsent("status", "error");
        EmvCapEvaluationErrorResponse body = new EmvCapEvaluationErrorResponse(
                "internal_error", "emv_cap_evaluation_failed", exception.getMessage(), details, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<EmvCapEvaluationErrorResponse> handleFallback(RuntimeException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "error");
        EmvCapEvaluationErrorResponse body = new EmvCapEvaluationErrorResponse(
                "internal_error", "emv_cap_evaluation_failed", exception.getMessage(), details, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
