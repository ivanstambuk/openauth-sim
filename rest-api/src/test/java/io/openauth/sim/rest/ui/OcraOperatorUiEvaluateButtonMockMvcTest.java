package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
final class OcraOperatorUiEvaluateButtonMockMvcTest {

  @TempDir static Path tempDir;

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add(
        "openauth.sim.persistence.database-path",
        () -> tempDir.resolve("credentials.db").toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Test
  @DisplayName("Evaluate button exposes mode-specific labels")
  void evaluateButtonExposesModeSpecificLabels() throws Exception {
    String html =
        mockMvc
            .perform(get("/ui/console"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("data-inline-label=\"Evaluate inline parameters\"");
    assertThat(html).contains("data-credential-label=\"Evaluate stored credential\"");
    assertThat(html).contains("Evaluate inline parameters");
  }
}
