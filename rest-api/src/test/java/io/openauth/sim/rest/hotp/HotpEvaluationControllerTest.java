package io.openauth.sim.rest.hotp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private HotpEvaluationService service;

  @Test
  @DisplayName("Stored evaluation returns generated OTP payload with metadata")
  void storedEvaluationReturnsResponse() throws Exception {
    HotpEvaluationMetadata metadata =
        new HotpEvaluationMetadata(
            "stored", "demo", true, "SHA1", 6, 0L, 1L, null, null, "rest-hotp-1");
    HotpEvaluationResponse response =
        new HotpEvaluationResponse("generated", "generated", "755224", metadata);
    when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenReturn(response);

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo");

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("generated"))
        .andExpect(jsonPath("$.otp").value("755224"))
        .andExpect(jsonPath("$.metadata.credentialSource").value("stored"))
        .andExpect(jsonPath("$.metadata.telemetryId").value("rest-hotp-1"));
  }

  @Test
  @DisplayName("Inline evaluation returns generated OTP payload with metadata")
  void inlineEvaluationReturnsResponse() throws Exception {
    HotpEvaluationMetadata metadata =
        new HotpEvaluationMetadata(
            "inline", null, false, "SHA1", 6, 7L, 8L, null, null, "rest-hotp-2");
    HotpEvaluationResponse response =
        new HotpEvaluationResponse("generated", "generated", "254676", metadata);
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenReturn(response);

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "3132333435363738393031323334353637383930", "SHA1", 6, 7L, Map.of("source", "test"));

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate/inline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("generated"))
        .andExpect(jsonPath("$.otp").value("254676"))
        .andExpect(jsonPath("$.metadata.credentialSource").value("inline"))
        .andExpect(jsonPath("$.metadata.credentialId").doesNotExist());
  }

  @Test
  @DisplayName("Stored evaluation propagates credential not found as 404")
  void storedEvaluationHandlesCredentialNotFound() throws Exception {
    HotpEvaluationValidationException exception =
        new HotpEvaluationValidationException(
            "rest-hotp-3",
            "stored",
            "missing",
            "credential_not_found",
            true,
            Map.of("credentialId", "missing"),
            "credential missing");
    when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenThrow(exception);

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("missing");

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("invalid_input"))
        .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-3"))
        .andExpect(jsonPath("$.details.reasonCode").value("credential_not_found"))
        .andExpect(jsonPath("$.details.credentialId").value("missing"));
  }

  @Test
  @DisplayName("Stored evaluation propagates counter overflow as HTTP 400")
  void storedEvaluationHandlesCounterOverflow() throws Exception {
    HotpEvaluationValidationException exception =
        new HotpEvaluationValidationException(
            "rest-hotp-7",
            "stored",
            "demo",
            "counter_overflow",
            true,
            Map.of("credentialId", "demo"),
            "counter overflow");
    when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenThrow(exception);

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo");

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_input"))
        .andExpect(jsonPath("$.details.reasonCode").value("counter_overflow"))
        .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-7"));
  }

  @Test
  @DisplayName("Inline evaluation propagates validation errors as HTTP 400")
  void inlineEvaluationHandlesValidationError() throws Exception {
    HotpEvaluationValidationException exception =
        new HotpEvaluationValidationException(
            "rest-hotp-4",
            "inline",
            null,
            "counter_required",
            true,
            Map.of("field", "counter"),
            "counter required");
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenThrow(exception);

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "3132333435363738393031323334353637383930", "SHA1", 6, null, Map.of());

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate/inline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_input"))
        .andExpect(jsonPath("$.details.reasonCode").value("counter_required"))
        .andExpect(jsonPath("$.details.sanitized").value("true"))
        .andExpect(jsonPath("$.details.identifier").doesNotExist());
  }

  @Test
  @DisplayName("Unexpected errors surface as HTTP 500")
  void inlineEvaluationHandlesUnexpectedError() throws Exception {
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class)))
        .thenThrow(new IllegalStateException("store offline"));

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "3132333435363738393031323334353637383930", "SHA1", 6, 0L, Map.of());

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate/inline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("internal_error"))
        .andExpect(jsonPath("$.details.status").value("error"));
  }

  @Test
  @DisplayName("Stored evaluation unexpected error surfaces telemetry metadata")
  void storedEvaluationHandlesCustomUnexpectedError() throws Exception {
    HotpEvaluationUnexpectedException exception =
        new HotpEvaluationUnexpectedException(
            "rest-hotp-5", "stored", "boom", Map.of("status", "error"));
    when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenThrow(exception);

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo");

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("internal_error"))
        .andExpect(jsonPath("$.details.status").value("error"))
        .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-5"))
        .andExpect(jsonPath("$.details.sanitized").value("false"));
  }

  @Test
  @DisplayName("Validation handler preserves sanitized flag when false")
  void inlineEvaluationHandlesUnsanitizedValidation() throws Exception {
    HotpEvaluationValidationException exception =
        new HotpEvaluationValidationException(
            "rest-hotp-6",
            "inline",
            "  ",
            null,
            false,
            Map.of("field", "counter"),
            "counter required");
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenThrow(exception);

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "3132333435363738393031323334353637383930", "SHA1", 6, null, Map.of());

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate/inline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.telemetryId").value("rest-hotp-6"))
        .andExpect(jsonPath("$.details.sanitized").value("false"))
        .andExpect(jsonPath("$.details.field").value("counter"))
        .andExpect(jsonPath("$.details.identifier").doesNotExist());
  }
}
