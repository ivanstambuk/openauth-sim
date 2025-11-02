package io.openauth.sim.rest.emv.cap;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emv/cap")
final class EmvCapReplayController {

    private final EmvCapReplayService service;

    EmvCapReplayController(EmvCapReplayService service) {
        this.service = service;
    }

    @Operation(
            operationId = "replayEmvCap",
            summary = "Validate an EMV/CAP OTP against stored or inline parameters",
            description =
                    "Compares an operator-supplied OTP to recomputed EMV/CAP values across the configured preview window.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Replay completed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmvCapReplayResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmvCapReplayErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = EmvCapReplayErrorResponse.class)))
            })
    @PostMapping("/replay")
    ResponseEntity<EmvCapReplayResponse> replay(@RequestBody EmvCapReplayRequest request) {
        EmvCapReplayResponse response = service.replay(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(EmvCapReplayValidationException.class)
    ResponseEntity<EmvCapReplayErrorResponse> handleValidation(EmvCapReplayValidationException exception) {
        EmvCapReplayErrorResponse body = new EmvCapReplayErrorResponse(
                "invalid_input", exception.reasonCode(), exception.getMessage(), exception.details(), null);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(EmvCapReplayUnexpectedException.class)
    ResponseEntity<EmvCapReplayErrorResponse> handleUnexpected(EmvCapReplayUnexpectedException exception) {
        EmvCapReplayErrorResponse body = new EmvCapReplayErrorResponse(
                "internal_error", "emv_cap_replay_failed", exception.getMessage(), exception.details(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<EmvCapReplayErrorResponse> handleFallback(RuntimeException exception) {
        EmvCapReplayErrorResponse body = new EmvCapReplayErrorResponse(
                "internal_error", "emv_cap_replay_failed", exception.getMessage(), Map.of(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
