package io.openauth.sim.rest.hotp;

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

/** HOTP evaluation endpoints (stored credential + inline secret modes). */
@RestController
@RequestMapping("/api/v1/hotp")
class HotpEvaluationController {

    private final HotpEvaluationService service;

    HotpEvaluationController(HotpEvaluationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Operation(
            summary = "Evaluate a stored HOTP credential",
            description = "Validates a submitted OTP against a persisted HOTP credential and advances"
                    + " the counter on success.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Evaluation completed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Credential not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationErrorResponse.class)))
            })
    @PostMapping(path = "/evaluate")
    ResponseEntity<HotpEvaluationResponse> evaluateStored(@RequestBody HotpStoredEvaluationRequest request) {
        HotpEvaluationResponse response = service.evaluateStored(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Evaluate HOTP parameters inline",
            description = "Validates an OTP using inline HOTP parameters without mutating stored credentials.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Evaluation completed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpEvaluationErrorResponse.class)))
            })
    @PostMapping(path = "/evaluate/inline")
    ResponseEntity<HotpEvaluationResponse> evaluateInline(@RequestBody HotpInlineEvaluationRequest request) {
        HotpEvaluationResponse response = service.evaluateInline(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HotpEvaluationValidationException.class)
    ResponseEntity<HotpEvaluationErrorResponse> handleValidation(HotpEvaluationValidationException exception) {
        Map<String, String> details = new LinkedHashMap<>(exception.details());
        details.putIfAbsent("telemetryId", exception.telemetryId());
        details.putIfAbsent("credentialSource", exception.credentialSource());
        if (exception.credentialId() != null && !exception.credentialId().isBlank()) {
            String key = "stored".equals(exception.credentialSource()) ? "credentialId" : "identifier";
            details.putIfAbsent(key, exception.credentialId());
        }
        if (exception.reasonCode() != null) {
            details.putIfAbsent("reasonCode", exception.reasonCode());
        }
        details.put("sanitized", Boolean.toString(exception.sanitized()));

        HotpEvaluationErrorResponse body =
                new HotpEvaluationErrorResponse("invalid_input", exception.getMessage(), details);

        HttpStatus status =
                "credential_not_found".equals(exception.reasonCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HotpEvaluationUnexpectedException.class)
    ResponseEntity<HotpEvaluationErrorResponse> handleUnexpected(HotpEvaluationUnexpectedException exception) {
        Map<String, String> details = new LinkedHashMap<>(exception.details());
        details.putIfAbsent("telemetryId", exception.telemetryId());
        details.putIfAbsent("credentialSource", exception.credentialSource());
        details.put("status", "error");
        details.putIfAbsent("sanitized", "false");

        HotpEvaluationErrorResponse body =
                new HotpEvaluationErrorResponse("internal_error", "HOTP evaluation failed", details);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<HotpEvaluationErrorResponse> handleFallback(RuntimeException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("status", "error");
        details.put("sanitized", "false");

        HotpEvaluationErrorResponse body =
                new HotpEvaluationErrorResponse("internal_error", "HOTP evaluation failed", details);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
