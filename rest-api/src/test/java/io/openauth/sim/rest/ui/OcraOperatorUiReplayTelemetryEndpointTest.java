package io.openauth.sim.rest.ui;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OcraOperatorUiController.class)
class OcraOperatorUiReplayTelemetryEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OcraOperatorUiReplayLogger telemetry;

    @Test
    @DisplayName("replay telemetry endpoint forwards payload to telemetry component")
    void replayTelemetryEndpoint() throws Exception {
        OcraReplayUiEventRequest request = new OcraReplayUiEventRequest(
                "ui-telemetry-3", "match", "match", null, "stored", "stored", "match", "hash", Boolean.TRUE);

        mockMvc.perform(post("/ui/ocra/replay/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<OcraReplayUiEventRequest> captor = ArgumentCaptor.forClass(OcraReplayUiEventRequest.class);
        verify(telemetry).record(captor.capture());
    }
}
