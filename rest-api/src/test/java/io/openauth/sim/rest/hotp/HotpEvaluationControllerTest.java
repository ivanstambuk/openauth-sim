package io.openauth.sim.rest.hotp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures;
import io.openauth.sim.core.otp.hotp.HotpJsonVectorFixtures.HotpJsonVector;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HotpEvaluationController.class)
class HotpEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HotpEvaluationService service;

    @Test
    @DisplayName("Stored evaluation returns generated OTP payload with metadata")
    void storedEvaluationReturnsResponse() throws Exception {
        HotpJsonVector vector = vector(6, 0L);
        HotpEvaluationMetadata metadata = new HotpEvaluationMetadata(
                "stored",
                "demo",
                true,
                vector.algorithm().name(),
                vector.digits(),
                vector.counter(),
                vector.counter() + 1,
                null,
                null,
                "rest-hotp-1");
        HotpEvaluationResponse response =
                new HotpEvaluationResponse("generated", "generated", vector.otp(), metadata, null);
        when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenReturn(response);

        HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo", null);

        mockMvc.perform(post("/api/v1/hotp/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.otp").value(vector.otp()))
                .andExpect(jsonPath("$.metadata.credentialSource").value("stored"))
                .andExpect(jsonPath("$.metadata.telemetryId").value("rest-hotp-1"));
    }

    @Test
    @DisplayName("Inline evaluation returns generated OTP payload with metadata")
    void inlineEvaluationReturnsResponse() throws Exception {
        HotpJsonVector vector = vector(6, 5L);
        HotpEvaluationMetadata metadata = new HotpEvaluationMetadata(
                "inline",
                null,
                false,
                vector.algorithm().name(),
                vector.digits(),
                vector.counter(),
                vector.counter() + 1,
                null,
                null,
                "rest-hotp-2");
        HotpEvaluationResponse response =
                new HotpEvaluationResponse("generated", "generated", vector.otp(), metadata, null);
        when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenReturn(response);

        HotpInlineEvaluationRequest request = new HotpInlineEvaluationRequest(
                vector.secret().asHex(),
                vector.algorithm().name(),
                vector.digits(),
                vector.counter(),
                Map.of("source", "test"),
                null);

        mockMvc.perform(post("/api/v1/hotp/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("generated"))
                .andExpect(jsonPath("$.otp").value(vector.otp()))
                .andExpect(jsonPath("$.metadata.credentialSource").value("inline"))
                .andExpect(jsonPath("$.metadata.credentialId").doesNotExist());
    }

    @Test
    @DisplayName("Stored evaluation propagates credential not found as HTTP 422")
    void storedEvaluationHandlesCredentialNotFound() throws Exception {
        HotpEvaluationValidationException exception = new HotpEvaluationValidationException(
                "rest-hotp-3",
                "stored",
                "missing",
                "credential_not_found",
                true,
                Map.<String, Object>of("credentialId", "missing"),
                "credential missing",
                null);
        when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenThrow(exception);

        HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("missing", null);

        mockMvc.perform(post("/api/v1/hotp/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("invalid_input"))
                .andExpect(jsonPath("$.reasonCode").value("credential_not_found"))
                .andExpect(jsonPath("$.message").value("credential missing"))
                .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-3"))
                .andExpect(jsonPath("$.details.credentialId").value("missing"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Stored evaluation propagates counter overflow as HTTP 422")
    void storedEvaluationHandlesCounterOverflow() throws Exception {
        HotpEvaluationValidationException exception = new HotpEvaluationValidationException(
                "rest-hotp-7",
                "stored",
                "demo",
                "counter_overflow",
                true,
                Map.<String, Object>of("credentialId", "demo"),
                "counter overflow",
                null);
        when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenThrow(exception);

        HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo", null);

        mockMvc.perform(post("/api/v1/hotp/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("invalid_input"))
                .andExpect(jsonPath("$.reasonCode").value("counter_overflow"))
                .andExpect(jsonPath("$.message").value("counter overflow"))
                .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-7"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Inline evaluation propagates validation errors as HTTP 422")
    void inlineEvaluationHandlesValidationError() throws Exception {
        HotpEvaluationValidationException exception = new HotpEvaluationValidationException(
                "rest-hotp-4",
                "inline",
                null,
                "counter_required",
                true,
                Map.<String, Object>of("field", "counter"),
                "counter required",
                null);
        when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenThrow(exception);

        HotpJsonVector sample = vector(6, 0L);
        HotpInlineEvaluationRequest request = new HotpInlineEvaluationRequest(
                sample.secret().asHex(), sample.algorithm().name(), sample.digits(), null, Map.of(), null);

        mockMvc.perform(post("/api/v1/hotp/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("invalid_input"))
                .andExpect(jsonPath("$.reasonCode").value("counter_required"))
                .andExpect(jsonPath("$.message").value("counter required"))
                .andExpect(jsonPath("$.details.sanitized").value(true))
                .andExpect(jsonPath("$.details.identifier").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Unexpected errors surface as HTTP 500")
    void inlineEvaluationHandlesUnexpectedError() throws Exception {
        when(service.evaluateInline(any(HotpInlineEvaluationRequest.class)))
                .thenThrow(new IllegalStateException("store offline"));

        HotpJsonVector sample = vector(6, 0L);
        HotpInlineEvaluationRequest request = new HotpInlineEvaluationRequest(
                sample.secret().asHex(), sample.algorithm().name(), sample.digits(), 0L, Map.of(), null);

        mockMvc.perform(post("/api/v1/hotp/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("internal_error"))
                .andExpect(jsonPath("$.reasonCode").value("hotp_evaluation_failed"))
                .andExpect(jsonPath("$.details.status").value("error"));
    }

    @Test
    @DisplayName("Stored evaluation unexpected error surfaces telemetry metadata")
    void storedEvaluationHandlesCustomUnexpectedError() throws Exception {
        HotpEvaluationUnexpectedException exception =
                new HotpEvaluationUnexpectedException("rest-hotp-5", "stored", "boom", Map.of("status", "error"), null);
        when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenThrow(exception);

        HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo", null);

        mockMvc.perform(post("/api/v1/hotp/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("internal_error"))
                .andExpect(jsonPath("$.reasonCode").value("hotp_evaluation_failed"))
                .andExpect(jsonPath("$.details.status").value("error"))
                .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-5"))
                .andExpect(jsonPath("$.details.sanitized").value(false));
    }

    @Test
    @DisplayName("Validation handler preserves sanitized flag when false")
    void inlineEvaluationHandlesUnsanitizedValidation() throws Exception {
        HotpEvaluationValidationException exception = new HotpEvaluationValidationException(
                "rest-hotp-6",
                "inline",
                "  ",
                null,
                false,
                Map.<String, Object>of("field", "counter"),
                "counter required",
                null);
        when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenThrow(exception);

        HotpJsonVector sample = vector(6, 0L);
        HotpInlineEvaluationRequest request = new HotpInlineEvaluationRequest(
                sample.secret().asHex(), sample.algorithm().name(), sample.digits(), null, Map.of(), null);

        mockMvc.perform(post("/api/v1/hotp/evaluate/inline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-6"))
                .andExpect(jsonPath("$.details.sanitized").value(false))
                .andExpect(jsonPath("$.details.field").value("counter"))
                .andExpect(jsonPath("$.details.identifier").doesNotExist());
    }

    private HotpJsonVector vector(int digits, long counter) {
        return HotpJsonVectorFixtures.loadAll()
                .filter(v -> v.digits() == digits && v.counter() == counter)
                .findFirst()
                .orElseThrow();
    }
}
