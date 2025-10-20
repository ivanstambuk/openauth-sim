package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;

final class OcraOperatorUiControllerAttestationTest {

    @Test
    void unifiedConsoleExposesAttestationVectorsJson() throws Exception {
        OcraOperatorUiController controller =
                new OcraOperatorUiController(new ObjectMapper(), new OcraOperatorUiReplayLogger());
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        controller.unifiedConsole(controller.formModel(), request, model);

        Object attribute = model.getAttribute("fido2AttestationVectorsJson");
        assertNotNull(attribute, "Expected attestation vectors JSON attribute to be present");
        String attributeJson = attribute.toString();
        assertTrue(
                attributeJson.contains("w3c"),
                () -> "Expected attestation vectors JSON to include W3C fixture metadata but was: " + attributeJson);

        JsonNode vectors = new ObjectMapper().readTree(attributeJson);
        JsonNode packedVector = null;
        for (JsonNode vector : vectors) {
            if (vector.hasNonNull("vectorId")
                    && "w3c-packed-es256".equals(vector.get("vectorId").asText())) {
                packedVector = vector;
                break;
            }
        }
        assertNotNull(packedVector, "Expected packed ES256 vector to be present in attestation fixtures");
        assertEquals("ES256 (packed, W3C 16.1.6)", packedVector.get("label").asText());
    }
}
