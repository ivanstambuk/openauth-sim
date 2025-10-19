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
    @DisplayName("landingPage redirects to unified console")
    void landingPageRedirectsToConsole() {
        assertEquals("redirect:/ui/console", controller.landingPage());
    }

    @Test
    @DisplayName("unified console populates CSRF token, endpoints, and presets")
    void unifiedConsolePopulatesModel() {
        OcraEvaluationForm form = controller.formModel();
        MockHttpServletRequest request = new MockHttpServletRequest();
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.unifiedConsole(form, request, model);

        assertEquals("ui/console/index", view);
        assertEquals("/api/v1/ocra/evaluate", model.getAttribute("evaluationEndpoint"));
        assertEquals("/api/v1/ocra/verify", model.getAttribute("verificationEndpoint"));
        assertEquals("/api/v1/ocra/credentials", model.getAttribute("credentialsEndpoint"));
        assertEquals("/api/v1/hotp/credentials/seed", model.getAttribute("hotpSeedEndpoint"));
        assertEquals("/ui/ocra/replay/telemetry", model.getAttribute("telemetryEndpoint"));
        assertEquals("ocra", model.getAttribute("activeProtocol"));
        assertTrue(model.containsAttribute("hotpSeedDefinitionsJson"));
        String hotpSeedJson = (String) model.getAttribute("hotpSeedDefinitionsJson");
        assertNotNull(hotpSeedJson);
        assertTrue(hotpSeedJson.contains("ui-hotp-demo"));

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
    @DisplayName("unified console reuses existing CSRF token")
    void unifiedConsoleReusesExistingToken() {
        OcraEvaluationForm form = controller.formModel();
        MockHttpServletRequest request = new MockHttpServletRequest();
        jakarta.servlet.http.HttpSession originalSession = request.getSession(true);
        assertNotNull(originalSession);
        originalSession.setAttribute("ocra-ui-csrf-token", "existing-token");
        ConcurrentModel model = new ConcurrentModel();

        controller.unifiedConsole(form, request, model);

        assertEquals("existing-token", model.getAttribute("csrfToken"));
        assertEquals("existing-token", originalSession.getAttribute("ocra-ui-csrf-token"));
    }

    @Test
    @DisplayName("unified console regenerates CSRF token when stored value blank")
    void unifiedConsoleRegeneratesBlankToken() {
        OcraEvaluationForm form = controller.formModel();
        MockHttpServletRequest request = new MockHttpServletRequest();
        jakarta.servlet.http.HttpSession session = request.getSession(true);
        assertNotNull(session);
        session.setAttribute("ocra-ui-csrf-token", "   ");
        ConcurrentModel model = new ConcurrentModel();

        controller.unifiedConsole(form, request, model);

        String token = (String) model.getAttribute("csrfToken");
        assertNotNull(token);
        assertEquals(token, session.getAttribute("ocra-ui-csrf-token"));
        assertTrue(token.trim().length() > 0);
    }

    @Test
    @DisplayName("unified console wraps JSON errors in IllegalStateException")
    void unifiedConsoleWrapsJsonErrors() {
        OcraOperatorUiController failingController =
                new OcraOperatorUiController(new FailingObjectMapper(), new OcraOperatorUiReplayLogger());

        OcraEvaluationForm form = failingController.formModel();
        MockHttpServletRequest request = new MockHttpServletRequest();

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> failingController.unifiedConsole(form, request, new ConcurrentModel()));

        String message = exception.getMessage();
        assertTrue(message.contains("Unable to render HOTP seed definitions")
                || message.contains("Unable to render policy presets"));
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
        public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
            throw new com.fasterxml.jackson.core.JsonProcessingException("boom") {
                private static final long serialVersionUID = 1L;
            };
        }
    }
}
