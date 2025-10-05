package io.openauth.sim.rest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OpenApiDocumentationTest {

  @Autowired private MockMvc mockMvc;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("openapi-docs.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Test
  @DisplayName("OpenAPI document includes the HOTP and OCRA evaluation endpoints")
  void evaluationEndpointsDocumented() throws Exception {
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
    assertTrue(paths.has("/api/v1/hotp/evaluate"), "HOTP evaluation endpoint missing from OpenAPI");
    assertTrue(
        paths.has("/api/v1/hotp/evaluate/inline"),
        "HOTP inline evaluation endpoint missing from OpenAPI");

    JsonNode ocraPost = paths.get("/api/v1/ocra/evaluate").get("post");
    assertNotNull(ocraPost, "OpenAPI document missing POST operation for OCRA evaluation endpoint");
    JsonNode hotpStoredPost = paths.get("/api/v1/hotp/evaluate").get("post");
    assertNotNull(
        hotpStoredPost, "OpenAPI document missing POST operation for HOTP stored evaluation");
    JsonNode hotpInlinePost = paths.get("/api/v1/hotp/evaluate/inline").get("post");
    assertNotNull(
        hotpInlinePost, "OpenAPI document missing POST operation for HOTP inline evaluation");
    assertTrue(
        ocraPost.get("responses").has("200"),
        "OpenAPI document missing HTTP 200 response for OCRA evaluation endpoint");
    assertTrue(
        hotpStoredPost.get("responses").has("200"),
        "OpenAPI document missing HTTP 200 response for HOTP stored evaluation endpoint");
    assertTrue(
        hotpInlinePost.get("responses").has("200"),
        "OpenAPI document missing HTTP 200 response for HOTP inline evaluation endpoint");
  }

  @Test
  @DisplayName("OpenAPI YAML document includes HOTP and OCRA evaluation endpoints")
  void evaluationEndpointsDocumentedYaml() throws Exception {
    String responseBody =
        mockMvc
            .perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andExpect(
                content()
                    .contentTypeCompatibleWith(MediaType.valueOf("application/vnd.oai.openapi")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(
        responseBody.contains("/api/v1/ocra/evaluate"),
        "OCRA evaluation endpoint missing from OpenAPI YAML");
    assertTrue(
        responseBody.contains("/api/v1/hotp/evaluate"),
        "HOTP evaluation endpoint missing from OpenAPI YAML");
    assertTrue(
        responseBody.contains("/api/v1/hotp/evaluate/inline"),
        "HOTP inline evaluation endpoint missing from OpenAPI YAML");
    assertTrue(
        responseBody.contains("post:"),
        "OpenAPI YAML missing POST operations for evaluation endpoints");
  }
}
