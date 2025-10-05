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
  @DisplayName("Stored evaluation returns response payload with metadata")
  void storedEvaluationReturnsResponse() throws Exception {
    HotpEvaluationMetadata metadata =
        new HotpEvaluationMetadata("stored", "demo", true, "SHA1", 6, 0L, 1L, "rest-hotp-1");
    HotpEvaluationResponse response = new HotpEvaluationResponse("match", "match", metadata);
    when(service.evaluateStored(any(HotpStoredEvaluationRequest.class))).thenReturn(response);

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo", "755224");

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("match"))
        .andExpect(jsonPath("$.reasonCode").value("match"))
        .andExpect(jsonPath("$.metadata.credentialSource").value("stored"))
        .andExpect(jsonPath("$.metadata.telemetryId").value("rest-hotp-1"));
  }

  @Test
  @DisplayName("Inline evaluation returns response payload with metadata")
  void inlineEvaluationReturnsResponse() throws Exception {
    HotpEvaluationMetadata metadata =
        new HotpEvaluationMetadata("inline", "device-123", false, "SHA1", 6, 7L, 7L, "rest-hotp-2");
    HotpEvaluationResponse response = new HotpEvaluationResponse("match", "match", metadata);
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenReturn(response);

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "device-123",
            "3132333435363738393031323334353637383930",
            "SHA1",
            6,
            7L,
            "123456",
            Map.of("source", "test"));

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate/inline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("match"))
        .andExpect(jsonPath("$.metadata.credentialSource").value("inline"))
        .andExpect(jsonPath("$.metadata.telemetryId").value("rest-hotp-2"));
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

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("missing", "000000");

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
  @DisplayName("Inline evaluation propagates validation errors as HTTP 400")
  void inlineEvaluationHandlesValidationError() throws Exception {
    HotpEvaluationValidationException exception =
        new HotpEvaluationValidationException(
            "rest-hotp-4",
            "inline",
            "device-1",
            "counter_required",
            true,
            Map.of("field", "counter", "identifier", "device-1"),
            "counter required");
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class))).thenThrow(exception);

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "device-1",
            "3132333435363738393031323334353637383930",
            "SHA1",
            6,
            0L,
            "000000",
            Map.of());

    mockMvc
        .perform(
            post("/api/v1/hotp/evaluate/inline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_input"))
        .andExpect(jsonPath("$.details.reasonCode").value("counter_required"))
        .andExpect(jsonPath("$.details.identifier").value("device-1"))
        .andExpect(jsonPath("$.details.sanitized").value("true"));
  }

  @Test
  @DisplayName("Unexpected errors surface as HTTP 500")
  void inlineEvaluationHandlesUnexpectedError() throws Exception {
    when(service.evaluateInline(any(HotpInlineEvaluationRequest.class)))
        .thenThrow(new IllegalStateException("store offline"));

    HotpInlineEvaluationRequest request =
        new HotpInlineEvaluationRequest(
            "device-123",
            "3132333435363738393031323334353637383930",
            "SHA1",
            6,
            0L,
            "123456",
            Map.of());

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

    HotpStoredEvaluationRequest request = new HotpStoredEvaluationRequest("demo", "123456");

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
}
