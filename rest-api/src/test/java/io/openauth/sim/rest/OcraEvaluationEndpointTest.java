package io.openauth.sim.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OcraEvaluationEndpointTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("POST /api/v1/ocra/evaluate currently returns 404")
  void evaluateEndpointNotYetImplemented() throws Exception {
    String requestJson =
        """
            {
              \"suite\": \"OCRA-1:HOTP-SHA256-8:QA08-S064\",
              \"sharedSecretHex\": \"3132333435363738393031323334353637383930313233343536373839303132\",
              \"challenge\": \"SESSION01\",
              \"sessionHex\": \"00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567\"
            }
            """;

    mockMvc
        .perform(
            post("/api/v1/ocra/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isNotFound()); // TODO R004: flip to isOk() once endpoint exists.
  }
}
