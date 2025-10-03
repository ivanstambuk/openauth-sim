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
    assertThat(fidoTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement emvTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-emv']"));
    assertThat(emvTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement modeToggle = driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));
    assertThat(modeToggle.getAttribute("data-mode"))
        .as("OCRA mode toggle should default to evaluation")
        .isEqualTo("evaluate");

    WebElement ocraPanel = driver.findElement(By.cssSelector("[data-protocol-panel='ocra']"));
    WebElement evaluateSection =
        ocraPanel.findElement(By.cssSelector("[data-testid='ocra-evaluate-panel']"));
    assertThat(evaluateSection.getAttribute("hidden"))
        .as("Evaluation panel should be visible by default")
        .isNull();
    WebElement evaluateForm =
        evaluateSection.findElement(By.cssSelector("[data-testid='ocra-evaluate-form']"));
    assertThat(evaluateForm).isNotNull();

    WebElement replaySection =
        driver.findElement(By.cssSelector("[data-testid='ocra-replay-panel']"));
    assertThat(replaySection.getAttribute("hidden"))
        .as("Replay panel should start hidden")
        .isNotNull();
    WebElement replayForm =
        replaySection.findElement(By.cssSelector("[data-testid='ocra-replay-form']"));
    assertThat(replayForm).isNotNull();

    WebElement replayToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-replay']"));
    replayToggle.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "replay".equals(modeToggle.getAttribute("data-mode")));

    assertThat(evaluateSection.getAttribute("hidden"))
        .as("Evaluation panel should be hidden after switching to replay")
        .isNotNull();
    assertThat(replaySection.getAttribute("hidden"))
        .as("Replay panel should be visible after switch")
        .isNull();

    WebElement evaluateToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-evaluate']"));
    evaluateToggle.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "evaluate".equals(modeToggle.getAttribute("data-mode")));

    assertThat(evaluateSection.getAttribute("hidden")).isNull();
    assertThat(replaySection.getAttribute("hidden")).isNotNull();

    fidoTab.click();
    WebElement fidoPanel = driver.findElement(By.cssSelector("[data-protocol-panel='fido2']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> fidoPanel.getAttribute("hidden") == null);
    assertThat(modeToggle.getAttribute("hidden"))
        .as("Mode toggle should hide for non-OCRA protocols")
        .isNotNull();
    assertThat(ocraPanel.getAttribute("hidden")).isNotNull();
    assertThat(fidoPanel.getText()).contains("FIDO2");

    emvTab.click();
    WebElement emvPanel = driver.findElement(By.cssSelector("[data-protocol-panel='emv']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> emvPanel.getAttribute("hidden") == null);
    assertThat(emvPanel.getText()).contains("EMV");

    ocraTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> modeToggle.getAttribute("hidden") == null);
    assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("evaluate");
    assertThat(ocraPanel.getAttribute("hidden")).isNull();
    assertThat(fidoPanel.getAttribute("hidden")).isNotNull();
    assertThat(emvPanel.getAttribute("hidden")).isNotNull();
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
