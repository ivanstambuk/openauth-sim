package io.openauth.sim.rest.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.rest.ocra.OcraEvaluationRequest;
import io.openauth.sim.rest.ocra.OcraEvaluationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@RequestMapping("/ui/ocra")
final class OcraOperatorUiController {

  private static final String REST_EVALUATION_PATH = "/api/v1/ocra/evaluate";
  private static final String CSRF_ATTRIBUTE = "ocra-ui-csrf-token";

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  OcraOperatorUiController(RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @ModelAttribute("form")
  OcraEvaluationForm formModel() {
    return new OcraEvaluationForm();
  }

  @GetMapping
  String landingPage() {
    return "ui/ocra/index";
  }

  @GetMapping("/evaluate")
  String evaluationForm(
      @ModelAttribute("form") OcraEvaluationForm form, HttpServletRequest request, Model model) {
    HttpSession session = request.getSession(true);
    model.addAttribute("csrfToken", ensureCsrfToken(session));
    model.addAttribute("viewState", ViewState.empty());
    return "ui/ocra/evaluate";
  }

  @PostMapping("/evaluate")
  String submitEvaluation(
      @ModelAttribute("form") OcraEvaluationForm form, HttpServletRequest request, Model model) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "missing session");
    }
    validateCsrfToken(request, session);

    OcraEvaluationRequest ocraRequest = form.toOcraRequest();
    ViewState state;
    try {
      String targetUrl =
          ServletUriComponentsBuilder.fromCurrentContextPath()
              .path(REST_EVALUATION_PATH)
              .toUriString();
      ResponseEntity<OcraEvaluationResponse> response =
          restTemplate.postForEntity(targetUrl, ocraRequest, OcraEvaluationResponse.class);
      OcraEvaluationResponse body = response.getBody();
      state =
          ViewState.success(
              body != null ? body.otp() : null,
              body != null ? body.telemetryId() : null,
              body != null ? body.suite() : form.getSuite());
    } catch (HttpClientErrorException ex) {
      state = ViewState.validationFailure(parseError(ex));
    } catch (RestClientException ex) {
      state =
          ViewState.validationFailure(
              new SanitizedError("unexpected_error", "false", "Unexpected error"));
    }

    form.scrubSecrets();
    model.addAttribute("csrfToken", ensureCsrfToken(session));
    model.addAttribute("viewState", state);
    return "ui/ocra/evaluate";
  }

  private static void validateCsrfToken(HttpServletRequest request, HttpSession session) {
    String expectedToken = (String) session.getAttribute(CSRF_ATTRIBUTE);
    String providedToken = request.getParameter("_csrf");
    if (!StringUtils.hasText(expectedToken)
        || !StringUtils.hasText(providedToken)
        || !Objects.equals(expectedToken, providedToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid CSRF token");
    }
  }

  private static String ensureCsrfToken(HttpSession session) {
    Object existing = session.getAttribute(CSRF_ATTRIBUTE);
    if (existing instanceof String token && StringUtils.hasText(token)) {
      return token;
    }
    String generated = UUID.randomUUID().toString();
    session.setAttribute(CSRF_ATTRIBUTE, generated);
    return generated;
  }

  private SanitizedError parseError(HttpClientErrorException exception) {
    String body = exception.getResponseBodyAsString(StandardCharsets.UTF_8);
    if (!StringUtils.hasText(body)) {
      return new SanitizedError("invalid_input", "true", exception.getStatusText());
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode details = root.path("details");
      String reasonCode = details.path("reasonCode").asText("invalid_input");
      String sanitized = details.path("sanitized").asText("true");
      String message = root.path("message").asText(exception.getStatusText());
      return new SanitizedError(reasonCode, sanitized, message);
    } catch (IOException parsingFailure) {
      return new SanitizedError("invalid_input", "true", "Request rejected");
    }
  }

  record SanitizedError(String reasonCode, String sanitized, String message) {
    SanitizedError {
      String normalizedReason = StringUtils.hasText(reasonCode) ? reasonCode : "invalid_input";
      String normalizedSanitized = StringUtils.hasText(sanitized) ? sanitized : "true";
      String normalizedMessage =
          StringUtils.hasText(message) ? message : "Your request could not be processed";
      reasonCode = normalizedReason;
      sanitized = normalizedSanitized;
      message = normalizedMessage;
    }
  }

  record ViewState(Map<String, Object> payload) {

    static ViewState empty() {
      return new ViewState(Map.of());
    }

    static ViewState success(String otp, String telemetryId, String suite) {
      String safeOtp = StringUtils.hasText(otp) ? otp : "";
      String safeTelemetry = StringUtils.hasText(telemetryId) ? telemetryId : "";
      String safeSuite = StringUtils.hasText(suite) ? suite : "";
      return new ViewState(
          Map.of(
              "status",
              "success",
              "message",
              "Evaluation succeeded",
              "otp",
              safeOtp,
              "telemetryId",
              safeTelemetry,
              "suite",
              safeSuite,
              "reasonCode",
              "success",
              "sanitized",
              "true"));
    }

    static ViewState validationFailure(SanitizedError error) {
      return new ViewState(
          Map.of(
              "status", "error",
              "reasonCode", error.reasonCode(),
              "sanitized", error.sanitized(),
              "message", error.message()));
    }

    public boolean hasResult() {
      return "success".equals(payload.get("status"));
    }

    public boolean hasError() {
      return "error".equals(payload.get("status"));
    }
  }
}
