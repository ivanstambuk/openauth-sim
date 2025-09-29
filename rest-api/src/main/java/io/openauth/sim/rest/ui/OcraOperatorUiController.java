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
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ui/ocra")
final class OcraOperatorUiController {

  private static final String REST_EVALUATION_PATH = "/api/v1/ocra/evaluate";
  private static final String CSRF_ATTRIBUTE = "ocra-ui-csrf-token";
  private static final List<PolicyPreset> INLINE_POLICY_PRESETS =
      List.of(
          new PolicyPreset(
              "qa08-s064",
              "QA08 S064 (session 64)",
              new InlineSample(
                  "OCRA-1:HOTP-SHA256-8:QA08-S064",
                  "3132333435363738393031323334353637383930313233343536373839303132",
                  "SESSION01",
                  "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
                  null,
                  null,
                  null,
                  null,
                  null)),
          new PolicyPreset(
              "qa08-s128",
              "QA08 S128 (session 128)",
              new InlineSample(
                  "OCRA-1:HOTP-SHA256-8:QA08-S128",
                  "3132333435363738393031323334353637383930313233343536373839303132",
                  "SESSION01",
                  "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF0123456700112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567",
                  null,
                  null,
                  null,
                  null,
                  null)),
          new PolicyPreset(
              "qa08-s256",
              "QA08 S256 (session 256)",
              new InlineSample(
                  "OCRA-1:HOTP-SHA256-8:QA08-S256",
                  "3132333435363738393031323334353637383930313233343536373839303132",
                  "SESSION01",
                  ("00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"),
                  null,
                  null,
                  null,
                  null,
                  null)),
          new PolicyPreset(
              "qa08-s512",
              "QA08 S512 (session 512)",
              new InlineSample(
                  "OCRA-1:HOTP-SHA256-8:QA08-S512",
                  "3132333435363738393031323334353637383930313233343536373839303132",
                  "SESSION01",
                  ("00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"
                      + "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567"),
                  null,
                  null,
                  null,
                  null,
                  null)),
          new PolicyPreset(
              "c-qh64",
              "C-QH64 (HOTP-SHA256-6)",
              new InlineSample(
                  "OCRA-1:HOTP-SHA256-6:C-QH64",
                  "3132333435363738393031323334353637383930313233343536373839303132",
                  "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF",
                  null,
                  null,
                  null,
                  null,
                  null,
                  1L)));

  private final ObjectMapper objectMapper;

  OcraOperatorUiController(ObjectMapper objectMapper) {
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
    model.addAttribute("evaluationEndpoint", REST_EVALUATION_PATH);
    populatePolicyPresets(model);
    return "ui/ocra/evaluate";
  }

  private void populatePolicyPresets(Model model) {
    model.addAttribute("policyPresets", INLINE_POLICY_PRESETS);
    try {
      List<Map<String, Object>> payload =
          INLINE_POLICY_PRESETS.stream()
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

    InlineSample(
        String suite,
        String sharedSecretHex,
        String challenge,
        String sessionHex,
        String clientChallenge,
        String serverChallenge,
        String pinHashHex,
        String timestampHex,
        Long counter) {
      this.suite = suite;
      this.sharedSecretHex = sharedSecretHex;
      this.challenge = challenge;
      this.sessionHex = sessionHex;
      this.clientChallenge = clientChallenge;
      this.serverChallenge = serverChallenge;
      this.pinHashHex = pinHashHex;
      this.timestampHex = timestampHex;
      this.counter = counter;
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
  }

  private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
