package io.openauth.sim.rest.webauthn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

final class Fido2AttestationReplayMetadataAnchorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Controller forwards metadata anchor identifiers to the service")
    void controllerPropagatesMetadataAnchors() throws Exception {
        WebAuthnAttestationService service = mock(WebAuthnAttestationService.class);
        WebAuthnAttestationController controller = new WebAuthnAttestationController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        WebAuthnAttestationMetadata metadata = WebAuthnAttestationMetadata.forReplay(
                "telemetry",
                "match",
                "packed",
                java.util.Map.of("anchorSource", "metadata", "metadataAnchorIds", List.of("mds-w3c-packed-es256")),
                List.of());

        when(service.replay(any(WebAuthnAttestationReplayRequest.class)))
                .thenReturn(new WebAuthnAttestationResponse(
                        "success",
                        null,
                        new WebAuthnAttestedCredential("example.org", "id", "es256", true, 0L, null),
                        metadata,
                        null));

        ObjectNode json = objectMapper.createObjectNode();
        json.put("inputSource", "MANUAL");
        json.put("attestationId", "vector");
        json.put("format", "packed");
        json.put("relyingPartyId", "example.org");
        json.put("origin", "https://example.org");
        json.put("attestationObject", "YWJj");
        json.put("clientDataJson", "ZGVm");
        json.put("expectedChallenge", "Z2hp");
        json.putArray("metadataAnchorIds").add("mds-w3c-packed-es256");
        json.putArray("trustAnchors");

        String response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/webauthn/attest/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(json)))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        ArgumentCaptor<WebAuthnAttestationReplayRequest> captor =
                ArgumentCaptor.forClass(WebAuthnAttestationReplayRequest.class);
        verify(service).replay(captor.capture());
        assertThat(captor.getValue().metadataAnchorIds()).containsExactly("mds-w3c-packed-es256");

        assertThat(response).contains("\"metadataAnchorIds\":[\"mds-w3c-packed-es256\"]");
        assertThat(response).contains("\"status\":\"success\"");
    }
}
