package io.openauth.sim.rest.ocra;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcraEvaluationController.class)
class OcraEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcraEvaluationService service;

    @Test
    @DisplayName("evaluate returns 200 with response payload on success")
    void evaluateReturnsResponse() throws Exception {
        OcraEvaluationResponse response = new OcraEvaluationResponse("OCRA-1", "867530", "rest-ocra-1", null);
        when(service.evaluate(any(OcraEvaluationRequest.class))).thenReturn(response);

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1", "31323334", "12345678", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/ocra/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suite").value("OCRA-1"))
                .andExpect(jsonPath("$.otp").value("867530"))
                .andExpect(jsonPath("$.telemetryId").value("rest-ocra-1"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("evaluate surfaces validation errors as 400 with sanitized details")
    void evaluateHandlesValidationError() throws Exception {
        OcraEvaluationValidationException exception = new OcraEvaluationValidationException(
                "rest-ocra-telemetry",
                "OCRA-1",
                "sessionHex",
                "session_required",
                "sessionHex is required",
                true,
                new IllegalArgumentException("session missing"),
                null);
        when(service.evaluate(any(OcraEvaluationRequest.class))).thenThrow(exception);

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1", "31323334", "12345678", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/ocra/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_input"))
                .andExpect(jsonPath("$.message").value("sessionHex is required"))
                .andExpect(jsonPath("$.details.telemetryId").value("rest-ocra-telemetry"))
                .andExpect(jsonPath("$.details.reasonCode").value("session_required"))
                .andExpect(jsonPath("$.details.field").value("sessionHex"))
                .andExpect(jsonPath("$.details.sanitized").value("true"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("evaluate omits optional detail fields when validation metadata absent")
    void evaluateHandlesValidationErrorWithoutOptionalDetails() throws Exception {
        OcraEvaluationValidationException exception = new OcraEvaluationValidationException(
                "rest-ocra-telemetry",
                "OCRA-1",
                null,
                null,
                "general failure",
                false,
                new IllegalArgumentException("boom"),
                null);
        when(service.evaluate(any(OcraEvaluationRequest.class))).thenThrow(exception);

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1", "31323334", "12345678", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/ocra/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.telemetryId").value("rest-ocra-telemetry"))
                .andExpect(jsonPath("$.details.sanitized").value("false"))
                .andExpect(jsonPath("$.details.field").doesNotExist())
                .andExpect(jsonPath("$.details.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("evaluate returns 500 with error payload on unexpected exception")
    void evaluateHandlesUnexpectedError() throws Exception {
        when(service.evaluate(any(OcraEvaluationRequest.class))).thenThrow(new IllegalStateException("store offline"));

        OcraEvaluationRequest request = new OcraEvaluationRequest(
                null, "OCRA-1", "31323334", "12345678", null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/ocra/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.message").value("OCRA evaluation failed"))
                .andExpect(jsonPath("$.details.status").value("error"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
