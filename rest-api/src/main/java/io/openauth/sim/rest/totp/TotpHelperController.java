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
@RequestMapping("/api/v1/totp/helper")
class TotpHelperController {

    private final TotpHelperService service;

    TotpHelperController(TotpHelperService service) {
        this.service = service;
    }

    @Operation(
            operationId = "currentTotpHelper",
            summary = "Return the current OTP and metadata for a stored credential",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Helper lookup successful",
                        content = @Content(schema = @Schema(implementation = TotpHelperResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Validation error",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content = @Content(schema = @Schema(implementation = TotpEvaluationErrorResponse.class)))
            })
    @PostMapping("/current")
    ResponseEntity<TotpHelperResponse> current(@RequestBody TotpHelperRequest request) {
        TotpHelperResponse response = service.currentOtp(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(TotpHelperValidationException.class)
    ResponseEntity<TotpEvaluationErrorResponse> handleValidation(TotpHelperValidationException exception) {
        TotpEvaluationErrorResponse body = new TotpEvaluationErrorResponse(
                "invalid_input", exception.reasonCode(), exception.getMessage(), exception.details(), null);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(TotpHelperUnexpectedException.class)
    ResponseEntity<TotpEvaluationErrorResponse> handleUnexpected(TotpHelperUnexpectedException exception) {
        TotpEvaluationErrorResponse body = new TotpEvaluationErrorResponse(
                "internal_error", "totp_helper_failed", exception.getMessage(), Map.of(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
