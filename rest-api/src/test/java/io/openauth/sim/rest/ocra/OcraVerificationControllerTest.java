package io.openauth.sim.rest.ocra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcraVerificationController.class)
class OcraVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcraVerificationService service;

    @Test
    @DisplayName("verify returns match payload when service succeeds")
    void verifyReturnsMatch() throws Exception {
        OcraVerificationMetadata metadata = new OcraVerificationMetadata(
                "stored", "stored", "OCRA-1:HOTP-SHA1-6:QN08", 6, 12, "hash-value", "rest-ocra-verify-1", "match");
        when(service.verify(any(OcraVerificationRequest.class), any(OcraVerificationAuditContext.class)))
                .thenReturn(new OcraVerificationResponse("match", "match", metadata, null));

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-ID", "req-1")
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("match"))
                .andExpect(jsonPath("$.reasonCode").value("match"))
                .andExpect(jsonPath("$.metadata.telemetryId").value("rest-ocra-verify-1"))
                .andExpect(jsonPath("$.metadata.credentialSource").value("stored"))
                .andExpect(jsonPath("$.metadata.mode").value("stored"))
                .andExpect(jsonPath("$.metadata.outcome").value("match"));
    }

    @Test
    @DisplayName("verify returns mismatch payload when service reports strict mismatch")
    void verifyReturnsMismatch() throws Exception {
        OcraVerificationMetadata metadata = new OcraVerificationMetadata(
                "stored", "stored", "OCRA-1:HOTP-SHA1-6:QN08", 6, 18, "hash-value", "rest-ocra-verify-2", "mismatch");
        when(service.verify(any(OcraVerificationRequest.class), any(OcraVerificationAuditContext.class)))
                .thenReturn(new OcraVerificationResponse("mismatch", "strict_mismatch", metadata, null));

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Client-ID", "client-7")
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("mismatch"))
                .andExpect(jsonPath("$.reasonCode").value("strict_mismatch"))
                .andExpect(jsonPath("$.metadata.telemetryId").value("rest-ocra-verify-2"))
                .andExpect(jsonPath("$.metadata.mode").value("stored"))
                .andExpect(jsonPath("$.metadata.outcome").value("mismatch"));
    }

    @Test
    @DisplayName("verify surfaces validation errors as 422 with sanitized payload")
    void verifySurfacesValidationErrors() throws Exception {
        OcraVerificationValidationException exception = new OcraVerificationValidationException(
                "rest-ocra-verify-validation",
                "OCRA-1:HOTP-SHA1-6:QN08",
                "sessionHex",
                "session_required",
                "sessionHex is required",
                true,
                new IllegalArgumentException("missing"));
        when(service.verify(any(OcraVerificationRequest.class), any(OcraVerificationAuditContext.class)))
                .thenThrow(exception);

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("invalid_input"))
                .andExpect(jsonPath("$.message").value("sessionHex is required"))
                .andExpect(jsonPath("$.details.telemetryId").value("rest-ocra-verify-validation"))
                .andExpect(jsonPath("$.details.field").value("sessionHex"))
                .andExpect(jsonPath("$.details.reasonCode").value("session_required"))
                .andExpect(jsonPath("$.details.sanitized").value("true"));
    }

    @Test
    @DisplayName("verify surfaces credential_not_found as 404 with sanitized payload")
    void verifySurfacesCredentialNotFound() throws Exception {
        OcraVerificationValidationException exception = new OcraVerificationValidationException(
                "rest-ocra-verify-404",
                null,
                "credentialId",
                "credential_not_found",
                "credentialId demo-token-1 not found",
                true,
                null);
        when(service.verify(any(OcraVerificationRequest.class), any(OcraVerificationAuditContext.class)))
                .thenThrow(exception);

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("invalid_input"))
                .andExpect(jsonPath("$.details.telemetryId").value("rest-ocra-verify-404"))
                .andExpect(jsonPath("$.details.field").value("credentialId"))
                .andExpect(jsonPath("$.details.reasonCode").value("credential_not_found"))
                .andExpect(jsonPath("$.details.sanitized").value("true"));
    }

    private ObjectNode defaultRequest() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("otp", "17477202");
        root.put("credentialId", "demo-token-1");
        ObjectNode context = root.putObject("context");
        context.put("challenge", "BANK-REF-2024");
        context.put("sessionHex", "0011223344556677");
        context.put("counter", 42);
        return root;
    }

    @Test
    @DisplayName("verify returns 500 when service throws unexpected exception")
    void verifyHandlesUnexpected() throws Exception {
        when(service.verify(any(OcraVerificationRequest.class), any(OcraVerificationAuditContext.class)))
                .thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"));
    }

    @Test
    @DisplayName("verify generates audit context defaults when headers absent")
    void verifyGeneratesDefaultAuditContext() throws Exception {
        OcraVerificationMetadata metadata = new OcraVerificationMetadata(
                "stored", "stored", "OCRA-1:HOTP-SHA1-6:QN08", 6, 9, "fingerprint", "telemetry", "match");
        org.mockito.ArgumentCaptor<OcraVerificationAuditContext> captor =
                org.mockito.ArgumentCaptor.forClass(OcraVerificationAuditContext.class);
        when(service.verify(any(OcraVerificationRequest.class), captor.capture()))
                .thenReturn(new OcraVerificationResponse("match", "match", metadata, null));

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isOk());

        OcraVerificationAuditContext auditContext = captor.getValue();
        assertTrue(auditContext.requestId().startsWith("rest-ocra-request-"));
        assertEquals("anonymous", auditContext.resolvedOperatorPrincipal());
        assertEquals(null, auditContext.clientId());
    }

    @Test
    @DisplayName("verify surfaces validation failure without optional metadata")
    void verifyValidationWithoutOptionalMetadata() throws Exception {
        OcraVerificationValidationException exception = new OcraVerificationValidationException(
                "rest-ocra-verify-minimal", null, null, null, "invalid payload", false, null);
        when(service.verify(any(OcraVerificationRequest.class), any(OcraVerificationAuditContext.class)))
                .thenThrow(exception);

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details.telemetryId").value("rest-ocra-verify-minimal"))
                .andExpect(jsonPath("$.details.sanitized").value("false"))
                .andExpect(jsonPath("$.details.field").doesNotExist())
                .andExpect(jsonPath("$.details.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.details.suite").doesNotExist());
    }

    @Test
    @DisplayName("verify captures operator principal when remote user present")
    void verifyCapturesOperatorPrincipal() throws Exception {
        OcraVerificationMetadata metadata = new OcraVerificationMetadata(
                "stored", "stored", "OCRA-1:HOTP-SHA1-6:QN08", 6, 9, "fingerprint", "telemetry", "match");
        org.mockito.ArgumentCaptor<OcraVerificationAuditContext> captor =
                org.mockito.ArgumentCaptor.forClass(OcraVerificationAuditContext.class);
        when(service.verify(any(OcraVerificationRequest.class), captor.capture()))
                .thenReturn(new OcraVerificationResponse("match", "match", metadata, null));

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest()))
                        .with(request -> {
                            request.setRemoteUser("  operator@example.com  ");
                            request.addHeader("X-Client-ID", "client-902");
                            request.addHeader("X-Request-ID", "req-operator");
                            return request;
                        }))
                .andExpect(status().isOk());

        OcraVerificationAuditContext context = captor.getValue();
        assertEquals("operator@example.com", context.resolvedOperatorPrincipal());
        assertEquals("client-902", context.clientId());
        assertEquals("req-operator", context.requestId());
    }

    @Test
    @DisplayName("verify normalizes blank audit headers to defaults")
    void verifyNormalizesBlankHeaders() throws Exception {
        OcraVerificationMetadata metadata = new OcraVerificationMetadata(
                "stored", "stored", "OCRA-1:HOTP-SHA1-6:QN08", 6, 12, "hash", "telemetry-blank", "match");
        org.mockito.ArgumentCaptor<OcraVerificationAuditContext> captor =
                org.mockito.ArgumentCaptor.forClass(OcraVerificationAuditContext.class);
        when(service.verify(any(OcraVerificationRequest.class), captor.capture()))
                .thenReturn(new OcraVerificationResponse("match", "match", metadata, null));

        mockMvc.perform(post("/api/v1/ocra/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultRequest()))
                        .with(request -> {
                            request.addHeader("X-Request-ID", "   ");
                            request.addHeader("X-Client-ID", "   ");
                            return request;
                        }))
                .andExpect(status().isOk());

        OcraVerificationAuditContext context = captor.getValue();
        assertTrue(context.requestId().startsWith("rest-ocra-request-"));
        assertEquals(null, context.clientId());
    }
}
