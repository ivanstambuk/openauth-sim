package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OpenApiDocumentationTest {

  @Autowired private MockMvc mockMvc;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("OpenAPI document includes the OCRA evaluation endpoint")
  void ocraEvaluationEndpointDocumented() throws Exception {
    String responseBody =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode paths = MAPPER.readTree(responseBody).get("paths");
    assertNotNull(paths, "OpenAPI document missing paths node");
    assertTrue(paths.has("/api/v1/ocra/evaluate"), "OCRA evaluation endpoint missing from OpenAPI");
    JsonNode postNode = paths.get("/api/v1/ocra/evaluate").get("post");
    assertNotNull(postNode, "OpenAPI document missing POST operation for evaluation endpoint");
    assertTrue(
        postNode.get("responses").has("200"),
        "OpenAPI document missing HTTP 200 response for evaluation endpoint");
  }
}
