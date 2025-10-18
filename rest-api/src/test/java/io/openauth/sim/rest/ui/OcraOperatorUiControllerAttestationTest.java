package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;

final class OcraOperatorUiControllerAttestationTest {

  @Test
  void unifiedConsoleExposesAttestationVectorsJson() {
    OcraOperatorUiController controller =
        new OcraOperatorUiController(new ObjectMapper(), new OcraOperatorUiReplayLogger());
    ConcurrentModel model = new ConcurrentModel();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSession(new MockHttpSession());

    controller.unifiedConsole(controller.formModel(), request, model);

    Object attribute = model.getAttribute("fido2AttestationVectorsJson");
    assertNotNull(attribute, "Expected attestation vectors JSON attribute to be present");
    assertTrue(
        attribute.toString().contains("w3c"),
        () ->
            "Expected attestation vectors JSON to include W3C fixture metadata but was: "
                + attribute);
  }
}
