package io.openauth.sim.rest.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openauth.sim.core.trace.VerboseTrace;
import io.openauth.sim.rest.VerboseTracePayload;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class TotpReplayControllerTest {

    private TotpReplayService service;
    private TotpReplayController controller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(TotpReplayService.class);
        controller = new TotpReplayController(service);
    }

    @Test
    @DisplayName("Replay delegates to service and surfaces the payload")
    void replayDelegatesToService() {
        TotpReplayRequest request =
                new TotpReplayRequest("cred-1", "654321", 1L, null, 1, 1, null, null, null, null, true);
        TotpReplayMetadata metadata =
                new TotpReplayMetadata("stored", "cred-1", true, "SHA1", 6, 30L, 1, 1, 0, false, "rest-totp-1");
        TotpReplayResponse expected =
                new TotpReplayResponse("match", "match", metadata, VerboseTracePayloadFixtures.verboseTracePayload());

        when(service.replay(request)).thenReturn(expected);

        ResponseEntity<TotpReplayResponse> response = controller.replay(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).replay(request);
    }

    @Test
    @DisplayName("Validation handler returns 422 and includes verbose trace when provided")
    void validationHandlerReturnsTrace() {
        VerboseTrace trace = VerboseTraceFixtures.sampleTrace();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("field", "otp");
        details.put("telemetryId", "rest-totp-422");

        TotpReplayValidationException exception = new TotpReplayValidationException(
                "rest-totp-422", "inline", "otp_required", "OTP is required", true, details, trace);

        ResponseEntity<TotpReplayErrorResponse> response = controller.handleValidation(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        TotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("invalid_input", body.status());
        assertEquals("otp_required", body.reasonCode());
        assertEquals("OTP is required", body.message());
        assertThat(body.details()).containsEntry("field", "otp").containsEntry("telemetryId", "rest-totp-422");
        assertThat(body.trace()).isNotNull();
        assertThat(body.trace().operation()).isEqualTo(trace.operation());
    }

    @Test
    @DisplayName("Unexpected handler returns 500 and inflates trace payload")
    void unexpectedHandlerReturnsTrace() {
        VerboseTrace trace = VerboseTraceFixtures.sampleTrace();
        Map<String, Object> details = Map.of("status", "error", "hint", "retry");
        TotpReplayUnexpectedException exception =
                new TotpReplayUnexpectedException("rest-totp-500", "stored", "Failure", details, null, trace);

        ResponseEntity<TotpReplayErrorResponse> response = controller.handleUnexpected(exception);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        TotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("internal_error", body.status());
        assertEquals("totp_replay_failed", body.reasonCode());
        assertEquals("Failure", body.message());
        assertThat(body.details()).containsEntry("status", "error").containsEntry("hint", "retry");
        assertThat(body.trace()).isNotNull();
    }

    @Test
    @DisplayName("Fallback handler returns fixed payload without trace")
    void fallbackHandlerReturnsFixedPayload() {
        ResponseEntity<TotpReplayErrorResponse> response = controller.handleFallback(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        TotpReplayErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertEquals("internal_error", body.status());
        assertEquals("totp_replay_failed", body.reasonCode());
        assertEquals("boom", body.message());
        assertThat(body.details()).isEmpty();
        assertThat(body.trace()).isNull();
    }

    @Test
    @DisplayName("Validation details map is defensive and unmodifiable")
    void validationDetailsUnmodifiable() {
        TotpReplayValidationException exception = new TotpReplayValidationException(
                "rest-totp-immutable",
                "inline",
                "otp_required",
                "OTP is required",
                false,
                Map.of("field", "otp"),
                null);

        Map<String, Object> details = exception.details();
        assertThat(details).containsEntry("field", "otp");
        assertThatThrownBy(() -> details.put("extra", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class VerboseTraceFixtures {
        private static VerboseTrace sampleTrace() {
            return VerboseTrace.builder("totp.replay.inline")
                    .withMetadata("credentialSource", "inline")
                    .addStep(step -> step.id("decode.secret")
                            .summary("Decode shared secret")
                            .detail("TotpReplayService.buildInlineCommand")
                            .attribute("length", 32)
                            .note("hint", "hex"))
                    .build();
        }
    }

    private static final class VerboseTracePayloadFixtures {
        private static VerboseTracePayload verboseTracePayload() {
            VerboseTrace trace = VerboseTrace.builder("totp.replay.stored")
                    .withMetadata("credentialSource", "stored")
                    .addStep(step -> step.id("resolve")
                            .summary("Resolve credential")
                            .detail("MapDbCredentialStore.findByName")
                            .attribute("telemetryId", "rest-totp-1"))
                    .build();
            return VerboseTracePayload.from(trace);
        }
    }
}
