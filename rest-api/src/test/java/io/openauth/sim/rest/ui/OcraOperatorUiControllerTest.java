package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "openauth.sim.persistence.enable-store=false")
@AutoConfigureMockMvc
final class OcraOperatorUiControllerTest {

  private static final String UI_LANDING_PATH = "/ui/ocra";
  private static final String UI_EVALUATION_PATH = "/ui/ocra/evaluate";
  private static final String REST_EVALUATION_PATH = "/api/v1/ocra/evaluate";
  private static final Pattern CSRF_PATTERN =
      Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  @Autowired private MockMvc mockMvc;

  @MockBean private RestTemplate restTemplate;

  @BeforeEach
  void resetMocks() {
    reset(restTemplate);
  }

  @Test
  @DisplayName("Landing page advertises OCRA evaluation console")
  void landingPageRendersOverview() throws Exception {
    mockMvc
        .perform(get(UI_LANDING_PATH))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("OCRA Operator Console")))
        .andExpect(content().string(containsString("Evaluate OCRA responses")));
  }

  @Test
  @DisplayName("Evaluation form renders with CSRF token and landmarks")
  void evaluationFormRendersCsrfTokenAndFetchHook() throws Exception {
    String html =
        mockMvc
            .perform(get(UI_EVALUATION_PATH))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Matcher matcher = CSRF_PATTERN.matcher(html);
    assertThat(matcher.find()).isTrue();
    assertThat(html).contains("<form");
    assertThat(html).contains("data-testid=\"ocra-evaluate-form\"");
    assertThat(html).contains("data-testid=\"mode-toggle\"");
    assertThat(html).contains("value=\"inline\"");
    assertThat(html).contains("value=\"credential\"");
    assertThat(html).contains("data-testid=\"inline-policy-select\"");
    assertThat(html).contains("QA08 S064");
    assertThat(html).contains("C-QH64 (HOTP-SHA256-6)");
    assertThat(html).contains("action=\"#\"");
    assertThat(html).doesNotContain("method=\"post\"");
    assertThat(html).contains("data-evaluate-endpoint=\"/api/v1/ocra/evaluate\"");
    assertThat(html).contains("data-testid=\"ocra-evaluate-button\"");
    assertThat(html).contains("type=\"button\"");
    assertThat(html).contains("data-testid=\"ocra-fetch-script\"");
    assertThat(html).contains("data-testid=\"ocra-fetch-script\"");
    assertThat(html).contains("typeof window.fetch === 'function'");
    assertThat(html).contains("new XMLHttpRequest()");
    assertThat(html).contains("form.addEventListener('submit'");
    assertThat(html).contains("JSON.stringify(payload)");
    assertThat(html).contains("Secrets remain visible after");
    assertThat(html).contains("clear the field manually");
    assertThat(html).doesNotContain("type=\"password\"");
  }

  @Test
  @DisplayName("Evaluation markup exposes result and error containers for client rendering")
  void evaluationMarkupProvidesClientContainers() throws Exception {
    String html =
        mockMvc
            .perform(get(UI_EVALUATION_PATH))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("data-testid=\"ocra-result-panel\"");
    assertThat(html).contains("data-testid=\"ocra-otp-value\"");
    assertThat(html).contains("data-testid=\"ocra-telemetry-id\"");
    assertThat(html).contains("data-testid=\"ocra-reason-code\"");
    assertThat(html).contains("data-testid=\"ocra-sanitized-flag\"");
    assertThat(html).contains("data-testid=\"ocra-error-panel\"");
    assertThat(html).contains("data-testid=\"ocra-error-reason\"");
    assertThat(html).contains("data-testid=\"ocra-error-sanitized\"");
  }

  @Test
  @DisplayName("Server rejects legacy POST submissions in favour of fetch workflow")
  void evaluationSubmissionViaPostIsRejected() throws Exception {
    MvcResult formResult = renderEvaluationForm();
    MockHttpSession session = (MockHttpSession) formResult.getRequest().getSession(false);
    assertThat(session).isNotNull();
    String csrfToken = extractCsrfToken(formResult);

    mockMvc
        .perform(
            post(UI_EVALUATION_PATH)
                .session(session)
                .param("_csrf", csrfToken)
                .param("mode", "inline")
                .param("suite", "OCRA-1:HOTP-SHA256-8:QA08-S064")
                .param("sharedSecretHex", SHARED_SECRET_HEX))
        .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(restTemplate);
  }

  @Test
  @DisplayName("Legacy submission without session is rejected")
  void evaluationSubmissionWithoutSessionIsRejected() throws Exception {
    mockMvc
        .perform(
            post(UI_EVALUATION_PATH)
                .param("mode", "inline")
                .param("suite", "OCRA-1:HOTP-SHA256-8:QA08-S064")
                .param("sharedSecretHex", SHARED_SECRET_HEX))
        .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(restTemplate);
  }

  private MvcResult renderEvaluationForm() throws Exception {
    return mockMvc.perform(get(UI_EVALUATION_PATH)).andExpect(status().isOk()).andReturn();
  }

  private static String extractCsrfToken(MvcResult result) throws Exception {
    String html = result.getResponse().getContentAsString();
    Matcher matcher = CSRF_PATTERN.matcher(html);
    if (!matcher.find()) {
      throw new AssertionError("CSRF token not found in rendered form");
    }
    return matcher.group(1);
  }
}
