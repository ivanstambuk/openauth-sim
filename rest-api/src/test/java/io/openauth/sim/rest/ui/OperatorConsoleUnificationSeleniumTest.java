package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OperatorConsoleUnificationSeleniumTest {

  @LocalServerPort private int port;

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("console-unification.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  private HtmlUnitDriver driver;

  @BeforeEach
  void setUp() {
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    driver.getWebClient().getOptions().setThrowExceptionOnScriptError(true);
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @DisplayName("Unified console exposes protocol tabs with OCRA active and others disabled")
  void unifiedConsoleRendersProtocolTabs() {
    driver.get(baseUrl("/ui/console"));

    WebElement tabList =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='operator-protocol-tabs']")));
    assertThat(tabList.getAttribute("role")).isEqualTo("tablist");

    WebElement ocraTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-ocra']"));
    assertThat(ocraTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement fidoTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']"));
    assertThat(fidoTab.getAttribute("aria-disabled")).isEqualTo("true");

    WebElement emvTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-emv']"));
    assertThat(emvTab.getAttribute("aria-disabled")).isEqualTo("true");

    WebElement modeToggle = driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));
    assertThat(modeToggle.getAttribute("data-mode"))
        .as("OCRA mode toggle should default to evaluation")
        .isEqualTo("evaluate");

    WebElement evaluateLink =
        driver.findElement(By.cssSelector("[data-testid='ocra-open-evaluate']"));
    assertThat(evaluateLink.getAttribute("href")).contains("/ui/ocra/evaluate");
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
