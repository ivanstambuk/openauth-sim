package io.openauth.sim.rest.hotp;

import io.openauth.sim.rest.VerboseTracePayload;
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

/** HOTP replay endpoint (stored + inline). */
@RestController
@RequestMapping("/api/v1/hotp")
class HotpReplayController {

    private final HotpReplayService service;

    HotpReplayController(HotpReplayService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Operation(
            summary = "Replay a HOTP submission",
            description = "Validates an OTP against a stored HOTP credential without advancing counters or"
                    + " replays inline HOTP parameters for diagnostic purposes.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Replay completed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpReplayResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpReplayErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = HotpReplayErrorResponse.class)))
            })
    @PostMapping(path = "/replay")
    ResponseEntity<HotpReplayResponse> replay(@RequestBody HotpReplayRequest request) {
        HotpReplayResponse response = service.replay(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(HotpReplayValidationException.class)
    ResponseEntity<HotpReplayErrorResponse> handleValidation(HotpReplayValidationException exception) {
        Map<String, Object> details = new LinkedHashMap<>(exception.details());
        details.putIfAbsent("telemetryId", exception.telemetryId());
        details.putIfAbsent("credentialSource", exception.credentialSource());
        if (exception.credentialId() != null && !exception.credentialId().isBlank()) {
            details.putIfAbsent("credentialId", exception.credentialId());
        }
        if (exception.reasonCode() != null) {
            details.putIfAbsent("reasonCode", exception.reasonCode());
        }
        details.putIfAbsent("sanitized", exception.sanitized());

        HotpReplayErrorResponse body = new HotpReplayErrorResponse(
                "invalid_input",
                exception.reasonCode(),
                exception.getMessage(),
                details,
                exception.trace() != null ? VerboseTracePayload.from(exception.trace()) : null);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(HotpReplayUnexpectedException.class)
    ResponseEntity<HotpReplayErrorResponse> handleUnexpected(HotpReplayUnexpectedException exception) {
        Map<String, Object> details = new LinkedHashMap<>(exception.details());
        details.putIfAbsent("telemetryId", exception.telemetryId());
        details.putIfAbsent("credentialSource", exception.credentialSource());
        details.put("status", "error");
        details.putIfAbsent("sanitized", false);

        HotpReplayErrorResponse body = new HotpReplayErrorResponse(
                "internal_error",
                "hotp_replay_failed",
                exception.getMessage(),
                details,
                exception.trace() != null ? VerboseTracePayload.from(exception.trace()) : null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<HotpReplayErrorResponse> handleFallback(RuntimeException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "error");
        details.put("sanitized", false);

        HotpReplayErrorResponse body = new HotpReplayErrorResponse(
                "internal_error", "hotp_replay_failed", exception.getMessage(), details, null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
