package io.openauth.sim.rest.hotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HotpReplayControllerTest {

    private static final String INLINE_REPLAY_ID = "hotp-inline-replay";

    private HotpReplayService service;
    private HotpReplayController controller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(HotpReplayService.class);
        controller = new HotpReplayController(service);
    }

    @Test
    @DisplayName("Replay delegates to service and returns the response body")
    void replayDelegatesToService() {
        HotpReplayRequest request = new HotpReplayRequest("cred-1", null, null, null, null, null, "123456", null, null);
        HotpReplayResponse expected = new HotpReplayResponse(
                "match",
                "match",
                new HotpReplayMetadata("stored", "cred-1", true, "SHA1", 6, 10L, 10L, "rest-hotp-1"),
                null);

        when(service.replay(request)).thenReturn(expected);

        ResponseEntity<HotpReplayResponse> response = controller.replay(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).replay(request);
    }

    @Test
    @DisplayName("Stored credential not found surfaces 422 with enriched details")
    void validationNotFoundReturns422() {
        HotpReplayValidationException exception = new HotpReplayValidationException(
                "rest-hotp-404",
                "stored",
                "cred-404",
                "credential_not_found",
                true,
                Map.<String, Object>of("field", "credentialId"),
                "Credential not found",
                null);

        ResponseEntity<HotpReplayErrorResponse> response = controller.handleValidation(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        HotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("invalid_input", body.status());
        assertEquals("credential_not_found", body.reasonCode());
        assertEquals("Credential not found", body.message());
        assertThat(body.details())
                .containsEntry("telemetryId", "rest-hotp-404")
                .containsEntry("credentialSource", "stored")
                .containsEntry("credentialId", "cred-404")
                .containsEntry("reasonCode", "credential_not_found")
                .containsEntry("sanitized", true);
        assertThat(body.trace()).isNull();
    }

    @Test
    @DisplayName("Inline validation failures return 422 and surface credential metadata")
    void validationInlineReturns422() {
        HotpReplayValidationException exception = new HotpReplayValidationException(
                "rest-hotp-inline",
                "inline",
                INLINE_REPLAY_ID,
                "otp_required",
                false,
                Map.<String, Object>of("field", "otp"),
                "OTP is required",
                null);

        ResponseEntity<HotpReplayErrorResponse> response = controller.handleValidation(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        HotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("invalid_input", body.status());
        assertEquals("otp_required", body.reasonCode());
        assertEquals("OTP is required", body.message());
        assertThat(body.details())
                .containsEntry("telemetryId", "rest-hotp-inline")
                .containsEntry("credentialSource", "inline")
                .containsEntry("credentialId", INLINE_REPLAY_ID)
                .containsEntry("reasonCode", "otp_required")
                .containsEntry("sanitized", false);
        assertThat(body.trace()).isNull();
    }

    @Test
    @DisplayName("Unexpected exceptions return 500 with sanitized=false")
    void unexpectedExceptionsReturn500() {
        HotpReplayUnexpectedException exception = new HotpReplayUnexpectedException(
                "rest-hotp-error", "inline", "Failure", Map.of("hint", "retry"), null);

        ResponseEntity<HotpReplayErrorResponse> response = controller.handleUnexpected(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        HotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("internal_error", body.status());
        assertEquals("hotp_replay_failed", body.reasonCode());
        assertEquals("Failure", body.message());
        assertThat(body.details())
                .containsEntry("telemetryId", "rest-hotp-error")
                .containsEntry("credentialSource", "inline")
                .containsEntry("status", "error")
                .containsEntry("sanitized", false)
                .containsEntry("hint", "retry");
        assertThat(body.trace()).isNull();
    }

    @Test
    @DisplayName("Fallback handler returns 500 with static error payload")
    void fallbackReturns500() {
        ResponseEntity<HotpReplayErrorResponse> response = controller.handleFallback(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        HotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("internal_error", body.status());
        assertEquals("hotp_replay_failed", body.reasonCode());
        assertEquals("boom", body.message());
        assertThat(body.details()).containsEntry("status", "error").containsEntry("sanitized", false);
    }

    @Test
    @DisplayName("Validation handler omits credential reference and reason when payload is blank")
    void validationHandlesBlankCredentialId() {
        Map<String, Object> baseDetails = new LinkedHashMap<>();
        HotpReplayValidationException exception = new HotpReplayValidationException(
                "rest-hotp-blank", "inline", "  ", null, true, baseDetails, "Input invalid", null);

        ResponseEntity<HotpReplayErrorResponse> response = controller.handleValidation(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        HotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("invalid_input", body.status());
        assertThat(body.details())
                .containsEntry("telemetryId", "rest-hotp-blank")
                .containsEntry("credentialSource", "inline")
                .containsEntry("sanitized", true)
                .doesNotContainKey("credentialId")
                .doesNotContainKey("reasonCode");
        assertThat(body.trace()).isNull();
    }
}
