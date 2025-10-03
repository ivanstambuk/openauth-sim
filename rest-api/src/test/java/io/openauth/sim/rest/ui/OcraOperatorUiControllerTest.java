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
      new OcraOperatorUiController(new ObjectMapper(), new OcraOperatorUiReplayLogger());

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
  @DisplayName("evaluationForm reuses existing CSRF token")
  void evaluationFormReusesExistingToken() {
    OcraEvaluationForm form = controller.formModel();
    MockHttpServletRequest request = new MockHttpServletRequest();
    jakarta.servlet.http.HttpSession originalSession = request.getSession(true);
    assertNotNull(originalSession);
    originalSession.setAttribute("ocra-ui-csrf-token", "existing-token");
    ConcurrentModel model = new ConcurrentModel();

    controller.evaluationForm(form, request, model);

    assertEquals("existing-token", model.getAttribute("csrfToken"));
    assertEquals("existing-token", originalSession.getAttribute("ocra-ui-csrf-token"));
  }

  @Test
  @DisplayName("evaluationForm regenerates CSRF token when stored value blank")
  void evaluationFormRegeneratesBlankToken() {
    OcraEvaluationForm form = controller.formModel();
    MockHttpServletRequest request = new MockHttpServletRequest();
    jakarta.servlet.http.HttpSession session = request.getSession(true);
    assertNotNull(session);
    session.setAttribute("ocra-ui-csrf-token", "   ");
    ConcurrentModel model = new ConcurrentModel();

    controller.evaluationForm(form, request, model);

    String token = (String) model.getAttribute("csrfToken");
    assertNotNull(token);
    assertEquals(token, session.getAttribute("ocra-ui-csrf-token"));
    assertTrue(token.trim().length() > 0);
  }

  @Test
  @DisplayName("evaluationForm wraps JSON errors in IllegalStateException")
  void evaluationFormWrapsJsonErrors() {
    OcraOperatorUiController failingController =
        new OcraOperatorUiController(new FailingObjectMapper(), new OcraOperatorUiReplayLogger());

    OcraEvaluationForm form = failingController.formModel();
    MockHttpServletRequest request = new MockHttpServletRequest();

    IllegalStateException exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class,
            () -> failingController.evaluationForm(form, request, new ConcurrentModel()));

    assertTrue(exception.getMessage().contains("Unable to render policy presets"));
  }

  @Test
  @DisplayName("replayView exposes verification, credentials, and telemetry endpoints")
  void replayViewExposesEndpoints() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    ConcurrentModel model = new ConcurrentModel();

    String view = controller.replayView(request, model);

    assertEquals("ui/ocra/replay", view);
    assertEquals("/api/v1/ocra/verify", model.getAttribute("verificationEndpoint"));
    assertEquals("/api/v1/ocra/credentials", model.getAttribute("credentialsEndpoint"));
    assertEquals("/ui/ocra/replay/telemetry", model.getAttribute("telemetryEndpoint"));
    assertNotNull(model.getAttribute("csrfToken"));
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

  private static final class FailingObjectMapper extends ObjectMapper {
    private static final long serialVersionUID = 1L;

    @Override
    public String writeValueAsString(Object value)
        throws com.fasterxml.jackson.core.JsonProcessingException {
      throw new com.fasterxml.jackson.core.JsonProcessingException("boom") {
        private static final long serialVersionUID = 1L;
      };
    }
  }
}
