package io.openauth.sim.rest.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui")
final class OcraOperatorUiController {

  private static final String REST_EVALUATION_PATH = "/api/v1/ocra/evaluate";
  private static final String CSRF_ATTRIBUTE = "ocra-ui-csrf-token";
  private static final String REST_VERIFICATION_PATH = "/api/v1/ocra/verify";
  private final ObjectMapper objectMapper;
  private final OcraOperatorUiReplayLogger telemetry;

  OcraOperatorUiController(ObjectMapper objectMapper, OcraOperatorUiReplayLogger telemetry) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
  }

  @ModelAttribute("form")
  OcraEvaluationForm formModel() {
    return new OcraEvaluationForm();
  }

  @GetMapping("/ocra")
  String landingPage() {
    return "redirect:/ui/console";
  }

  @GetMapping("/console")
  String unifiedConsole(
      @ModelAttribute("form") OcraEvaluationForm form, HttpServletRequest request, Model model) {
    HttpSession session = request.getSession(true);
    model.addAttribute("csrfToken", ensureCsrfToken(session));
    model.addAttribute("evaluationEndpoint", REST_EVALUATION_PATH);
    model.addAttribute("verificationEndpoint", REST_VERIFICATION_PATH);
    model.addAttribute("credentialsEndpoint", "/api/v1/ocra/credentials");
    model.addAttribute(
        "credentialSampleEndpoint", "/api/v1/ocra/credentials/{credentialId}/sample");
    model.addAttribute("seedEndpoint", "/api/v1/ocra/credentials/seed");
    model.addAttribute("telemetryEndpoint", "/ui/ocra/replay/telemetry");
    model.addAttribute("activeProtocol", "ocra");
    populatePolicyPresets(model);
    return "ui/console/index";
  }

  @PostMapping(value = "/ocra/replay/telemetry", consumes = "application/json")
  org.springframework.http.ResponseEntity<Void> replayTelemetry(
      @RequestBody OcraReplayUiEventRequest request) {
    telemetry.record(request);
    return org.springframework.http.ResponseEntity.noContent().build();
  }

  private void populatePolicyPresets(Model model) {
    List<PolicyPreset> presets = OcraOperatorSampleData.policyPresets();
    model.addAttribute("policyPresets", presets);
    try {
      List<Map<String, Object>> payload =
          presets.stream()
              .map(
                  preset -> {
                    Map<String, Object> sampleMap = new java.util.LinkedHashMap<>();
                    InlineSample sample = preset.getSample();
                    putIfNotNull(sampleMap, "suite", sample.getSuite());
                    putIfNotNull(sampleMap, "sharedSecretHex", sample.getSharedSecretHex());
                    putIfNotNull(sampleMap, "challenge", sample.getChallenge());
                    putIfNotNull(sampleMap, "sessionHex", sample.getSessionHex());
                    putIfNotNull(sampleMap, "clientChallenge", sample.getClientChallenge());
                    putIfNotNull(sampleMap, "serverChallenge", sample.getServerChallenge());
                    putIfNotNull(sampleMap, "pinHashHex", sample.getPinHashHex());
                    putIfNotNull(sampleMap, "timestampHex", sample.getTimestampHex());
                    putIfNotNull(sampleMap, "counter", sample.getCounter());
                    putIfNotNull(sampleMap, "expectedOtp", sample.getExpectedOtp());
                    return Map.of(
                        "key", preset.getKey(), "label", preset.getLabel(), "sample", sampleMap);
                  })
              .toList();
      model.addAttribute("policyPresetJson", objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to render policy presets", ex);
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

  public static final class PolicyPreset {
    private final String key;
    private final String label;
    private final InlineSample sample;

    PolicyPreset(String key, String label, InlineSample sample) {
      this.key = key;
      this.label = label;
      this.sample = sample;
    }

    public String getKey() {
      return key;
    }

    public String getLabel() {
      return label;
    }

    public InlineSample getSample() {
      return sample;
    }
  }

  public static final class InlineSample {
    private final String suite;
    private final String sharedSecretHex;
    private final String challenge;
    private final String sessionHex;
    private final String clientChallenge;
    private final String serverChallenge;
    private final String pinHashHex;
    private final String timestampHex;
    private final Long counter;
    private final String expectedOtp;

    InlineSample(
        String suite,
        String sharedSecretHex,
        String challenge,
        String sessionHex,
        String clientChallenge,
        String serverChallenge,
        String pinHashHex,
        String timestampHex,
        Long counter,
        String expectedOtp) {
      this.suite = suite;
      this.sharedSecretHex = sharedSecretHex;
      this.challenge = challenge;
      this.sessionHex = sessionHex;
      this.clientChallenge = clientChallenge;
      this.serverChallenge = serverChallenge;
      this.pinHashHex = pinHashHex;
      this.timestampHex = timestampHex;
      this.counter = counter;
      this.expectedOtp = expectedOtp;
    }

    public String getSuite() {
      return suite;
    }

    public String getSharedSecretHex() {
      return sharedSecretHex;
    }

    public String getChallenge() {
      return challenge;
    }

    public String getSessionHex() {
      return sessionHex;
    }

    public String getClientChallenge() {
      return clientChallenge;
    }

    public String getServerChallenge() {
      return serverChallenge;
    }

    public String getPinHashHex() {
      return pinHashHex;
    }

    public String getTimestampHex() {
      return timestampHex;
    }

    public Long getCounter() {
      return counter;
    }

    public String getExpectedOtp() {
      return expectedOtp;
    }
  }

  private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
