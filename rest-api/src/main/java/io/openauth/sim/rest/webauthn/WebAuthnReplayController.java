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
class WebAuthnReplayController {

  private final WebAuthnReplayService service;

  WebAuthnReplayController(WebAuthnReplayService service) {
    this.service = service;
  }

  @Operation(
      operationId = "replayStoredWebAuthn",
      summary = "Replay a stored WebAuthn assertion without mutating credential state",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Replay matched stored credential",
            content = @Content(schema = @Schema(implementation = WebAuthnReplayResponse.class))),
        @ApiResponse(
            responseCode = "422",
            description = "Replay rejected or request invalid",
            content =
                @Content(schema = @Schema(implementation = WebAuthnReplayErrorResponse.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Unexpected error",
            content =
                @Content(schema = @Schema(implementation = WebAuthnReplayErrorResponse.class)))
      })
  @PostMapping("/replay")
  ResponseEntity<WebAuthnReplayResponse> replay(@RequestBody WebAuthnReplayRequest request) {
    WebAuthnReplayResponse response = service.replay(request);
    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(WebAuthnReplayValidationException.class)
  ResponseEntity<WebAuthnReplayErrorResponse> handleValidation(
      WebAuthnReplayValidationException exception) {
    WebAuthnReplayErrorResponse body =
        new WebAuthnReplayErrorResponse(
            exception.reasonCode(),
            exception.reasonCode(),
            exception.getMessage(),
            exception.details(),
            exception.details());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
  }

  @ExceptionHandler(WebAuthnReplayUnexpectedException.class)
  ResponseEntity<WebAuthnReplayErrorResponse> handleUnexpected(
      WebAuthnReplayUnexpectedException exception) {
    WebAuthnReplayErrorResponse body =
        new WebAuthnReplayErrorResponse(
            "internal_error",
            "webauthn_replay_failed",
            exception.getMessage(),
            exception.details(),
            exception.details());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }

  @ExceptionHandler(RuntimeException.class)
  ResponseEntity<WebAuthnReplayErrorResponse> handleFallback(RuntimeException exception) {
    WebAuthnReplayErrorResponse body =
        new WebAuthnReplayErrorResponse(
            "internal_error", "webauthn_replay_failed", exception.getMessage(), Map.of(), Map.of());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
  }
}
