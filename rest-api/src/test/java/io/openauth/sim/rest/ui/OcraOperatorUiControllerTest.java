package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openauth.sim.rest.ocra.OcraEvaluationRequest;
import io.openauth.sim.rest.ocra.OcraEvaluationResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OcraOperatorUiControllerTest {

  private static final String UI_LANDING_PATH = "/ui/ocra";
  private static final String UI_EVALUATION_PATH = "/ui/ocra/evaluate";
  private static final String REST_EVALUATION_PATH = "/api/v1/ocra/evaluate";
  private static final Pattern CSRF_PATTERN =
      Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SESSION_HEX_64 =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";

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
  void evaluationFormRendersCsrfToken() throws Exception {
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
  }

  @Test
  @DisplayName("Successful evaluation renders OTP and sanitized telemetry details")
  void evaluationSubmissionDisplaysOtp() throws Exception {
    OcraEvaluationResponse apiResponse =
        new OcraEvaluationResponse("OCRA-1:HOTP-SHA256-8:QA08-S064", "17477202", "telemetry-123");
    when(restTemplate.postForEntity(
            contains(REST_EVALUATION_PATH), any(), eq(OcraEvaluationResponse.class)))
        .thenReturn(ResponseEntity.ok(apiResponse));

    MvcResult formResult = renderEvaluationForm();
    MockHttpSession session = (MockHttpSession) formResult.getRequest().getSession(false);
    assertThat(session).isNotNull();
    String csrfToken = extractCsrfToken(formResult);

    String html =
        mockMvc
            .perform(
                post(UI_EVALUATION_PATH)
                    .session(session)
                    .param("_csrf", csrfToken)
                    .param("mode", "inline")
                    .param("suite", "OCRA-1:HOTP-SHA256-8:QA08-S064")
                    .param("sharedSecretHex", SHARED_SECRET_HEX)
                    .param("challenge", "SESSION01")
                    .param("sessionHex", SESSION_HEX_64))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ArgumentCaptor<OcraEvaluationRequest> requestCaptor =
        ArgumentCaptor.forClass(OcraEvaluationRequest.class);
    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate)
        .postForEntity(
            urlCaptor.capture(), requestCaptor.capture(), eq(OcraEvaluationResponse.class));

    assertThat(urlCaptor.getValue()).endsWith(REST_EVALUATION_PATH);

    OcraEvaluationRequest forwardedRequest = requestCaptor.getValue();
    assertThat(forwardedRequest.credentialId()).isNull();
    assertThat(forwardedRequest.sharedSecretHex()).isEqualTo(SHARED_SECRET_HEX);
    assertThat(forwardedRequest.sessionHex()).isEqualTo(SESSION_HEX_64);

    assertThat(html).contains("data-testid=\"ocra-otp\">17477202");
    assertThat(html).contains("data-testid=\"ocra-telemetry-id\">telemetry-123");
    assertThat(html).contains("data-testid=\"ocra-reason-code\">success");
    assertThat(html).contains("data-testid=\"ocra-sanitized-flag\">true");
    assertThat(html).doesNotContain(SHARED_SECRET_HEX);
  }

  @Test
  @DisplayName("REST validation errors render sanitized banner without leaking secrets")
  void evaluationSubmissionShowsSanitizedError() throws Exception {
    String errorJson =
        """
            {"error":"invalid_input","message":"suite is missing","details":{"reasonCode":"invalid_suite","sanitized":"true","field":"suite"}}
            """;
    when(restTemplate.postForEntity(
            contains(REST_EVALUATION_PATH), any(), eq(OcraEvaluationResponse.class)))
        .thenThrow(
            HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                errorJson.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

    MvcResult formResult = renderEvaluationForm();
    MockHttpSession session = (MockHttpSession) formResult.getRequest().getSession(false);
    assertThat(session).isNotNull();
    String csrfToken = extractCsrfToken(formResult);

    String html =
        mockMvc
            .perform(
                post(UI_EVALUATION_PATH)
                    .session(session)
                    .param("_csrf", csrfToken)
                    .param("mode", "inline")
                    .param("suite", "OCRA-1:HOTP-SHA256-8:QA08-S064")
                    .param("sharedSecretHex", SHARED_SECRET_HEX)
                    .param("challenge", "SESSION01")
                    .param("sessionHex", SESSION_HEX_64))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ArgumentCaptor<String> errorUrlCaptor = ArgumentCaptor.forClass(String.class);
    verify(restTemplate)
        .postForEntity(errorUrlCaptor.capture(), any(), eq(OcraEvaluationResponse.class));

    assertThat(errorUrlCaptor.getValue()).endsWith(REST_EVALUATION_PATH);
    assertThat(html).contains("data-testid=\"ocra-error-banner\"");
    assertThat(html).contains("data-testid=\"ocra-error-reason\">invalid_suite");
    assertThat(html).contains("data-testid=\"ocra-error-sanitized\">true");
    assertThat(html).doesNotContain(SHARED_SECRET_HEX);
  }

  @Test
  @DisplayName("Missing CSRF token rejects evaluation submissions")
  void evaluationSubmissionWithoutCsrfIsForbidden() throws Exception {
    mockMvc
        .perform(
            post(UI_EVALUATION_PATH)
                .param("mode", "inline")
                .param("suite", "OCRA-1:HOTP-SHA256-8:QA08-S064")
                .param("sharedSecretHex", SHARED_SECRET_HEX))
        .andExpect(status().isForbidden());
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
