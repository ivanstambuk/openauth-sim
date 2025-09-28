package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OcraEvaluationEndpointTest {

  @Autowired private MockMvc mockMvc;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SHARED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SESSION_HEX_64 =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
  private static final String SESSION_HEX_128 = SESSION_HEX_64 + SESSION_HEX_64;
  private static final String SESSION_HEX_256 = SESSION_HEX_128 + SESSION_HEX_128;
  private static final String SESSION_HEX_512 = SESSION_HEX_256 + SESSION_HEX_256;
  private static final Logger TELEMETRY_LOGGER =
      Logger.getLogger("io.openauth.sim.rest.ocra.telemetry");

  @ParameterizedTest(name = "{0}")
  @MethodSource("successfulRequests")
  @DisplayName("Valid OCRA requests return expected OTP without leaking secrets")
  void evaluateReturnsOtp(
      String description, String requestJson, String expectedOtp, String secretSnippet)
      throws Exception {
    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/ocra/evaluate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(requestJson))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = MAPPER.readTree(responseBody);
      assertEquals(expectedOtp, response.get("otp").asText());
      assertTrue(response.get("suite").asText().startsWith("OCRA-1:HOTP-SHA256-8"));
      assertNotNull(response.get("telemetryId").asText());
      assertFalse(
          responseBody.toLowerCase(Locale.ROOT).contains(secretSnippet.toLowerCase(Locale.ROOT)),
          () -> "secret material leaked in response: " + responseBody);
      assertTrue(
          handler.records().stream()
              .anyMatch(record -> record.getMessage().contains("rest.ocra.evaluate")),
          "telemetry event missing");
      assertFalse(
          handler.loggedSecrets(secretSnippet),
          () -> "secret material leaked in telemetry: " + handler.records());
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidRequests")
  @DisplayName("Invalid requests receive HTTP 400 without leaking secrets")
  void evaluateReturnsValidationErrors(
      String description, String requestJson, String expectedMessage, String secretSnippet)
      throws Exception {
    TestLogHandler handler = registerTelemetryHandler();
    try {
      String responseBody =
          mockMvc
              .perform(
                  post("/api/v1/ocra/evaluate")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(requestJson))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      JsonNode response = MAPPER.readTree(responseBody);
      assertEquals("invalid_input", response.get("error").asText());
      assertTrue(response.get("message").asText().contains(expectedMessage));
      assertFalse(
          responseBody.toLowerCase(Locale.ROOT).contains(secretSnippet.toLowerCase(Locale.ROOT)),
          () -> "secret material leaked in validation response: " + responseBody);
      assertTrue(handler.records().stream().anyMatch(record -> record.getLevel() == Level.WARNING));
      assertFalse(
          handler.loggedSecrets(secretSnippet),
          () -> "secret material leaked in telemetry: " + handler.records());
    } finally {
      deregisterTelemetryHandler(handler);
    }
  }

  private static Stream<Arguments> successfulRequests() {
    return Stream.of(
        Arguments.of(
            "S064 session request",
            requestJson("OCRA-1:HOTP-SHA256-8:QA08-S064", SESSION_HEX_64),
            "17477202",
            "313233"),
        Arguments.of(
            "S128 session request",
            requestJson("OCRA-1:HOTP-SHA256-8:QA08-S128", SESSION_HEX_128),
            "18468077",
            "313233"),
        Arguments.of(
            "S256 session request",
            requestJson("OCRA-1:HOTP-SHA256-8:QA08-S256", SESSION_HEX_256),
            "77715695",
            "313233"),
        Arguments.of(
            "S512 session request",
            requestJson("OCRA-1:HOTP-SHA256-8:QA08-S512", SESSION_HEX_512),
            "05806151",
            "313233"));
  }

  private static Stream<Arguments> invalidRequests() {
    String missingSession =
        """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"3132333435363738393031323334353637383930313233343536373839303132\",
              \"challenge\": \"SESSION01\"
            }
            """;

    String wrongChallengeLength =
        requestJson("OCRA-1:HOTP-SHA256-8:QA08-S064", SESSION_HEX_64).replace("SESSION01", "SHORT");

    return Stream.of(
        Arguments.of(
            "Missing session returns validation error",
            missingSession,
            "sessionInformation",
            "SESSION01"),
        Arguments.of(
            "Challenge length mismatch returns validation error",
            wrongChallengeLength,
            "challenge",
            "SHORT"));
  }

  private static String requestJson(String suite, String sessionHex) {
    return String.format(
        Locale.ROOT,
        "{"
            + "%n  \"suite\": \"%s\","
            + "%n  \"sharedSecretHex\": \"%s\","
            + "%n  \"challenge\": \"SESSION01\","
            + "%n  \"sessionHex\": \"%s\"%n}",
        suite,
        SHARED_SECRET_HEX,
        sessionHex);
  }

  private static TestLogHandler registerTelemetryHandler() {
    TestLogHandler handler = new TestLogHandler();
    TELEMETRY_LOGGER.addHandler(handler);
    return handler;
  }

  private static void deregisterTelemetryHandler(TestLogHandler handler) {
    TELEMETRY_LOGGER.removeHandler(handler);
  }

  private static final class TestLogHandler extends Handler {
    private final java.util.List<LogRecord> records = new java.util.ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
      // no-op
    }

    @Override
    public void close() {
      // no-op
    }

    private boolean loggedSecrets(String secretSnippet) {
      String needle = secretSnippet.toLowerCase(Locale.ROOT);
      return records.stream()
          .anyMatch(record -> record.getMessage().toLowerCase(Locale.ROOT).contains(needle));
    }

    java.util.List<LogRecord> records() {
      return java.util.List.copyOf(records);
    }
  }
}
