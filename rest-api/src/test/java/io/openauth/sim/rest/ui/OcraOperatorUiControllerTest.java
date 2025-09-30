package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ConcurrentModel;

class OcraOperatorUiControllerTest {

  private final OcraOperatorUiController controller =
      new OcraOperatorUiController(new ObjectMapper());

  @Test
  @DisplayName("evaluationForm populates CSRF token, endpoints, and presets")
  void evaluationFormPopulatesModel() {
    OcraEvaluationForm form = controller.formModel();
    MockHttpServletRequest request = new MockHttpServletRequest();
    ConcurrentModel model = new ConcurrentModel();

    String view = controller.evaluationForm(form, request, model);

    assertEquals("ui/ocra/evaluate", view);
    assertEquals("/api/v1/ocra/evaluate", model.getAttribute("evaluationEndpoint"));
    assertEquals("/api/v1/ocra/credentials", model.getAttribute("credentialsEndpoint"));

    String csrf = (String) model.getAttribute("csrfToken");
    assertNotNull(csrf);
    assertEquals(csrf, request.getSession().getAttribute("ocra-ui-csrf-token"));

    assertTrue(model.containsAttribute("policyPresets"));
    assertTrue(model.containsAttribute("policyPresetJson"));
    String json = (String) model.getAttribute("policyPresetJson");
    assertNotNull(json);
    assertTrue(json.contains("qa08"));
  }

  @Test
  @DisplayName("setMode normalises credential inline modes")
  void setModeNormalisesModes() {
    OcraEvaluationForm form = new OcraEvaluationForm();
    form.setMode("credential");
    assertTrue(form.isCredentialMode());
    form.setMode("inline");
    assertTrue(form.isInlineMode());
    form.setMode(null);
    assertTrue(form.isInlineMode());
  }
}
