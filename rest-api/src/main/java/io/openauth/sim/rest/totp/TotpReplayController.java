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
class TotpReplayController {

  private final TotpReplayService service;

  TotpReplayController(TotpReplayService service) {
    this.service = service;
  }

  @Operation(
      operationId = "replayTotp",
      summary = "Replay a TOTP credential without mutating simulator state",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Replay completed",
            content = @Content(schema = @Schema(implementation = TotpReplayResponse.class))),
        @ApiResponse(
            responseCode = "422",
            description = "Replay validation failed",
            content = @Content(schema = @Schema(implementation = TotpReplayErrorResponse.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = TotpReplayErrorResponse.class)))
      })
  @PostMapping("/replay")
  ResponseEntity<TotpReplayResponse> replay(@RequestBody TotpReplayRequest request) {
    TotpReplayResponse response = service.replay(request);
    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(TotpReplayValidationException.class)
  ResponseEntity<TotpReplayErrorResponse> handleValidation(
      TotpReplayValidationException exception) {
    TotpReplayErrorResponse body =
        new TotpReplayErrorResponse(
            "invalid_input", exception.reasonCode(), exception.getMessage(), exception.details());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(TotpReplayUnexpectedException.class)
  ResponseEntity<TotpReplayErrorResponse> handleUnexpected(
      TotpReplayUnexpectedException exception) {
    TotpReplayErrorResponse body =
        new TotpReplayErrorResponse(
            "internal_error", "totp_replay_failed", exception.getMessage(), exception.details());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<TotpReplayErrorResponse> handleFallback(RuntimeException exception) {
    TotpReplayErrorResponse body =
        new TotpReplayErrorResponse(
            "internal_error", "totp_replay_failed", exception.getMessage(), Map.of());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
