package io.openauth.sim.rest.webauthn;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webauthn")
class WebAuthnAttestationController {

    private final WebAuthnAttestationService service;

    WebAuthnAttestationController(WebAuthnAttestationService service) {
        this.service = service;
    }

    @Operation(
            operationId = "generateWebAuthnAttestation",
            summary = "Generate a deterministic WebAuthn attestation object",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Attestation generated",
                        content = @Content(schema = @Schema(implementation = WebAuthnAttestationResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Request invalid",
                        content = @Content(schema = @Schema(implementation = WebAuthnAttestationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content = @Content(schema = @Schema(implementation = WebAuthnAttestationErrorResponse.class)))
            })
    @PostMapping("/attest")
    ResponseEntity<WebAuthnAttestationResponse> attest(@RequestBody WebAuthnAttestationGenerationRequest request) {
        WebAuthnAttestationResponse response = service.generate(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            operationId = "replayWebAuthnAttestation",
            summary = "Replay a WebAuthn attestation verification",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Attestation replay succeeded",
                        content = @Content(schema = @Schema(implementation = WebAuthnAttestationResponse.class))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Request invalid",
                        content = @Content(schema = @Schema(implementation = WebAuthnAttestationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content = @Content(schema = @Schema(implementation = WebAuthnAttestationErrorResponse.class)))
            })
    @PostMapping("/attest/replay")
    ResponseEntity<WebAuthnAttestationResponse> replay(@RequestBody WebAuthnAttestationReplayRequest request) {
        WebAuthnAttestationResponse response = service.replay(request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(WebAuthnAttestationValidationException.class)
    ResponseEntity<WebAuthnAttestationErrorResponse> handleValidation(
            WebAuthnAttestationValidationException exception) {
        WebAuthnAttestationErrorResponse body = new WebAuthnAttestationErrorResponse(
                "invalid", exception.reasonCode(), exception.getMessage(), exception.details(), exception.metadata());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(WebAuthnAttestationUnexpectedException.class)
    ResponseEntity<WebAuthnAttestationErrorResponse> handleUnexpected(
            WebAuthnAttestationUnexpectedException exception) {
        WebAuthnAttestationErrorResponse body = new WebAuthnAttestationErrorResponse(
                "error", exception.reasonCode(), exception.getMessage(), java.util.Map.of(), exception.metadata());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<WebAuthnAttestationErrorResponse> handleFallback(RuntimeException exception) {
        WebAuthnAttestationErrorResponse body = new WebAuthnAttestationErrorResponse(
                "error", "attestation_failed", exception.getMessage(), java.util.Map.of(), java.util.Map.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
