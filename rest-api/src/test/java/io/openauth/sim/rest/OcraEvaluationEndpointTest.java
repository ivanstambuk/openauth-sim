package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("requestPayloads")
  @DisplayName("Endpoint placeholder still returns 404 and never echoes secrets")
  void evaluateEndpointStillUnavailable(
      String description, String requestJson, String secretSnippet) throws Exception {
    String responseBody =
        mockMvc
            .perform(
                post("/api/v1/ocra/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
            .andExpect(status().isNotFound()) // TODO R004: flip to isOk()/isBadRequest()
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertFalse(
        responseBody.toLowerCase(Locale.ROOT).contains(secretSnippet.toLowerCase(Locale.ROOT)),
        () -> "secret material leaked in 404 response: " + responseBody);
  }

  private static Stream<Arguments> requestPayloads() {
    String validPayload =
        """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"3132333435363738393031323334353637383930313233343536373839303132\",
              \"challenge\": \"SESSION01\",
              \"sessionHex\": \"00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567\"
            }
            """;

    String missingSession =
        """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"3132333435363738393031323334353637383930313233343536373839303132\",
              \"challenge\": \"SESSION01\"
            }
            """;

    return Stream.of(
        Arguments.of("valid payload still unhandled", validPayload, "313233"),
        Arguments.of("missing session payload still unhandled", missingSession, "SESSION01"));
  }
}
