package io.openauth.sim.rest.ocra;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
@RequestMapping("/api/v1/ocra")
class OcraEvaluationController {

    private final OcraEvaluationService service;

    OcraEvaluationController(OcraEvaluationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Operation(
            summary = "Evaluate an OCRA request",
            description = "Computes an OCRA OTP for the supplied suite and runtime parameters.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Evaluation successful",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraEvaluationResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request payload",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraEvaluationErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "Missing session payload",
                                                        summary = "Validation failure with reason code",
                                                        value = """
                                {
                                  "error": "invalid_input",
                                  "message": "sessionHex is required for the requested suite",
                                  "details": {
                                    "telemetryId": "rest-ocra-<uuid>",
                                    "status": "invalid",
                                    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
                                    "field": "sessionHex",
                                    "reasonCode": "session_required",
                                    "sanitized": "true"
                                  }
                                }
                                """))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected server error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraEvaluationErrorResponse.class)))
            })
    @PostMapping(path = "/evaluate")
    ResponseEntity<OcraEvaluationResponse> evaluate(@RequestBody OcraEvaluationRequest request) {
        OcraEvaluationResponse response = service.evaluate(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(OcraEvaluationValidationException.class)
    ResponseEntity<OcraEvaluationErrorResponse> handleValidationError(OcraEvaluationValidationException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("telemetryId", exception.telemetryId());
        details.put("status", "invalid");
        details.put("suite", exception.suite());
        if (exception.field() != null) {
            details.put("field", exception.field());
        }
        if (exception.reasonCode() != null) {
            details.put("reasonCode", exception.reasonCode());
        }
        details.put("sanitized", Boolean.toString(exception.sanitized()));

        OcraEvaluationErrorResponse body =
                new OcraEvaluationErrorResponse("invalid_input", exception.getMessage(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<OcraEvaluationErrorResponse> handleUnexpected(RuntimeException exception) {
        OcraEvaluationErrorResponse body =
                new OcraEvaluationErrorResponse("internal_error", "OCRA evaluation failed", Map.of("status", "error"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
