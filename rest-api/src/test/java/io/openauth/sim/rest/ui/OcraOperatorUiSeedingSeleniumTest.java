package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OcraOperatorUiSeedingSeleniumTest {

  private static final List<String> CANONICAL_SUITES =
      List.of(
          "OCRA-1:HOTP-SHA256-8:QA08-S064",
          "OCRA-1:HOTP-SHA256-8:QA08-S128",
          "OCRA-1:HOTP-SHA256-8:QA08-S256",
          "OCRA-1:HOTP-SHA256-8:QA08-S512",
          "OCRA-1:HOTP-SHA256-6:C-QH64");

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configurePersistence(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("operator-ui-seed.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @LocalServerPort private int port;

  @Autowired private CredentialStore credentialStore;

  private HtmlUnitDriver driver;

  @BeforeEach
  void setUp() {
    credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
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
  @DisplayName("Operator can seed canonical stored credentials when registry empty")
  void operatorSeedsCanonicalStoredCredentials() {
    driver.get(baseUrl("/ui/console"));

    WebElement storedModeRadio =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.elementToBeClickable(By.id("mode-credential")));
    storedModeRadio.click();

    WebElement seedButton =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[data-testid='ocra-seed-credentials']")));

    WebElement seedHint = driver.findElement(By.cssSelector("[data-testid='ocra-seed-hint']"));
    String seedHintText = seedHint.getDomProperty("textContent");
    if (seedHintText == null) {
      seedHintText = seedHint.getAttribute("textContent");
    }
    assertThat(seedHintText).contains("Adds canonical demo credentials");

    WebElement seedStatus = driver.findElement(By.cssSelector("[data-testid='ocra-seed-status']"));
    assertThat(seedStatus.getAttribute("hidden")).isNotNull();

    Select storedSelect =
        new Select(
            driver.findElement(By.cssSelector("select[data-testid='stored-credential-select']")));
    assertThat(storedSelect.getOptions()).hasSize(1);

    seedButton.click();
    waitForBackgroundJavaScript();

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(webDriver -> storedSelect.getOptions().size() == CANONICAL_SUITES.size() + 1);

    Set<String> storedSuites =
        credentialStore.findAll().stream()
            .map(
                credential ->
                    credential.attributes().get(OcraCredentialPersistenceAdapter.ATTR_SUITE))
            .collect(Collectors.toSet());
    assertThat(storedSuites).containsExactlyInAnyOrderElementsOf(CANONICAL_SUITES);

    seedButton.click();
    waitForBackgroundJavaScript();

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(webDriver -> storedSelect.getOptions().size() == CANONICAL_SUITES.size() + 1);

    String statusMessage = seedStatus.getDomProperty("textContent");
    if (statusMessage == null) {
      statusMessage = seedStatus.getAttribute("textContent");
    }
    assertThat(statusMessage).contains("Seeded");
    assertThat(seedStatus.getAttribute("hidden")).isNull();

    String finalHintText = seedHint.getDomProperty("textContent");
    if (finalHintText == null) {
      finalHintText = seedHint.getAttribute("textContent");
    }
    assertThat(finalHintText).isEqualTo(seedHintText);
  }

  private void waitForBackgroundJavaScript() {
    driver.getWebClient().waitForBackgroundJavaScript(Duration.ofSeconds(2).toMillis());
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
