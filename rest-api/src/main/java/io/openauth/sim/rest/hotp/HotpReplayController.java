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
      description =
          "Validates an OTP against a stored HOTP credential without advancing counters or"
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
            responseCode = "400",
            description = "Validation error",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = HotpReplayErrorResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Credential not found",
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
  ResponseEntity<HotpReplayErrorResponse> handleValidation(
      HotpReplayValidationException exception) {
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
    details.putIfAbsent("sanitized", Boolean.toString(exception.sanitized()));

    HotpReplayErrorResponse body =
        new HotpReplayErrorResponse("invalid_input", exception.getMessage(), details);

    HttpStatus status =
        "credential_not_found".equals(exception.reasonCode())
            ? HttpStatus.NOT_FOUND
            : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(HotpReplayUnexpectedException.class)
  ResponseEntity<HotpReplayErrorResponse> handleUnexpected(
      HotpReplayUnexpectedException exception) {
    Map<String, String> details = new LinkedHashMap<>(exception.details());
    details.putIfAbsent("telemetryId", exception.telemetryId());
    details.putIfAbsent("credentialSource", exception.credentialSource());
    details.put("status", "error");
    details.putIfAbsent("sanitized", "false");

    HotpReplayErrorResponse body =
        new HotpReplayErrorResponse("internal_error", "HOTP replay failed", details);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<HotpReplayErrorResponse> handleFallback(RuntimeException exception) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("status", "error");
    details.put("sanitized", "false");

    HotpReplayErrorResponse body =
        new HotpReplayErrorResponse("internal_error", "HOTP replay failed", details);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
