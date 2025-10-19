package io.openauth.sim.rest.ocra;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ocra")
class OcraVerificationController {

    private final OcraVerificationService service;

    OcraVerificationController(OcraVerificationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Operation(
            summary = "Verify an OCRA OTP submission",
            description = "Replays an OCRA execution with the supplied OTP and context to determine whether the"
                    + " OTP was valid for the referenced credential configuration.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Verification completed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraVerificationResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "Match",
                                                        summary = "Successful verification",
                                                        value = """
                                {
                                  "status": "match",
                                  "reasonCode": "match",
                                  "metadata": {
                                    "credentialSource": "stored",
                                    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
                                    "otpLength": 8,
                                    "durationMillis": 11,
                                    "contextFingerprint": "Base64URL-Hash",
                                    "telemetryId": "rest-ocra-verify-<uuid>",
                                    "outcome": "match"
                                  }
                                }
                                """))),
                @ApiResponse(
                        responseCode = "422",
                        description = "Verification payload failed validation",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraVerificationErrorResponse.class),
                                        examples =
                                                @ExampleObject(
                                                        name = "Missing context",
                                                        summary = "Validation failure",
                                                        value = """
                                {
                                  "error": "invalid_input",
                                  "message": "sessionHex is required",
                                  "details": {
                                    "telemetryId": "rest-ocra-verify-<uuid>",
                                    "status": "invalid",
                                    "suite": "OCRA-1:HOTP-SHA256-8:QA08-S064",
                                    "field": "sessionHex",
                                    "reasonCode": "session_required",
                                    "sanitized": "true"
                                  }
                                }
                                """))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Credential not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraVerificationErrorResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Unexpected error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = OcraVerificationErrorResponse.class)))
            })
    @PostMapping(path = "/verify")
    ResponseEntity<OcraVerificationResponse> verify(
            @RequestBody OcraVerificationRequest request, HttpServletRequest httpRequest) {
        OcraVerificationAuditContext auditContext = buildAuditContext(httpRequest);
        OcraVerificationResponse response = service.verify(request, auditContext);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(OcraVerificationValidationException.class)
    ResponseEntity<OcraVerificationErrorResponse> handleValidation(OcraVerificationValidationException exception) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("telemetryId", exception.telemetryId());
        details.put("status", "invalid");
        if (exception.suite() != null) {
            details.put("suite", exception.suite());
        }
        if (exception.field() != null) {
            details.put("field", exception.field());
        }
        if (exception.reasonCode() != null) {
            details.put("reasonCode", exception.reasonCode());
        }
        details.put("sanitized", Boolean.toString(exception.sanitized()));

        HttpStatus status = "credential_not_found".equals(exception.reasonCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNPROCESSABLE_ENTITY;

        OcraVerificationErrorResponse body =
                new OcraVerificationErrorResponse("invalid_input", exception.getMessage(), details);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<OcraVerificationErrorResponse> handleUnexpected(RuntimeException exception) {
        OcraVerificationErrorResponse body = new OcraVerificationErrorResponse(
                "internal_error", "OCRA verification failed", Map.of("status", "error"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private OcraVerificationAuditContext buildAuditContext(HttpServletRequest request) {
        String requestId = Optional.ofNullable(request.getHeader("X-Request-ID"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse("rest-ocra-request-" + UUID.randomUUID());

        String clientId = Optional.ofNullable(request.getHeader("X-Client-ID"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);

        String operatorPrincipal = Optional.ofNullable(request.getRemoteUser())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);

        return new OcraVerificationAuditContext(requestId, clientId, operatorPrincipal);
    }
}
