package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class TotpOperatorUiSeedingSeleniumTest {

  private static final List<SeedExpectation> CANONICAL_CREDENTIALS =
      List.of(
          new SeedExpectation("ui-totp-sample-sha1-6", "SHA-1, 6 digits, 30s", "SHA1", 6, 30, 1, 1),
          new SeedExpectation(
              "ui-totp-sample-sha1-8", "SHA-1, 8 digits, 30s (RFC 6238)", "SHA1", 8, 30, 1, 1),
          new SeedExpectation(
              "ui-totp-sample-sha256-6", "SHA-256, 6 digits, 30s", "SHA256", 6, 30, 1, 1),
          new SeedExpectation(
              "ui-totp-sample-sha256-8",
              "SHA-256, 8 digits, 30s (RFC 6238)",
              "SHA256",
              8,
              30,
              1,
              1),
          new SeedExpectation(
              "ui-totp-sample-sha512-6", "SHA-512, 6 digits, 30s", "SHA512", 6, 30, 1, 1),
          new SeedExpectation(
              "ui-totp-sample-sha512-8",
              "SHA-512, 8 digits, 30s (RFC 6238)",
              "SHA512",
              8,
              30,
              1,
              1));

  @TempDir static Path tempDir;
  private static Path databasePath;
  private static final double MIN_SEED_MARGIN_PX = 12.0;

  @DynamicPropertySource
  static void configurePersistence(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("totp-seed-ui.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private CredentialStore credentialStore;

  @LocalServerPort private int port;

  private HtmlUnitDriver driver;

  @BeforeEach
  void setUp() {
    credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @DisplayName("Operator seeds canonical TOTP credentials from stored mode control")
  void operatorSeedsCanonicalTotpCredentials() {
    driver.get(baseUrl("/ui/console?protocol=totp"));

    WebElement storedToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.elementToBeClickable(By.id("totpModeStored")));
    storedToggle.click();

    WebElement storedLabel =
        driver.findElement(By.cssSelector("label[for='totpStoredCredentialId']"));
    assertThat(normalizeText(storedLabel.getText())).isEqualTo("Stored credential");

    WebElement seedActions =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='totp-seed-actions']")));

    WebElement seedButton =
        seedActions.findElement(By.cssSelector("button[data-testid='totp-seed-credentials']"));
    WebElement seedHint = seedActions.findElement(By.cssSelector("[data-testid='totp-seed-hint']"));
    WebElement seedStatus =
        seedActions.findElement(By.cssSelector("[data-testid='totp-seed-status']"));

    assertHidden(seedActions, false);
    assertHidden(seedStatus, true);
    assertThat(normalizeText(seedHint.getText())).contains("Seed sample credentials");

    driver.findElement(By.id("totpModeInline")).click();
    waitUntil(driver1 -> isHidden(seedActions));

    storedToggle.click();
    waitUntil(driver1 -> !isHidden(seedActions));

    double marginTop =
        Double.parseDouble(
            driver
                .executeScript(
                    "return window.getComputedStyle(arguments[0]).marginTop;", seedActions)
                .toString()
                .replace("px", ""));
    assertThat(marginTop)
        .as("Seed actions should include visual separation above the controls")
        .isGreaterThanOrEqualTo(MIN_SEED_MARGIN_PX);

    waitUntil(driver1 -> storedSelectHasOptionCount(1));
    WebElement storedSelectElement = driver.findElement(By.id("totpStoredCredentialId"));
    assertThat(storedSelectElement.getAttribute("disabled"))
        .as("Stored credential dropdown should disable when empty")
        .isNotNull();
    Select storedSelect = new Select(storedSelectElement);
    assertThat(normalizeText(storedSelect.getFirstSelectedOption().getText()))
        .isEqualTo("No stored credentials available");

    seedButton.click();
    waitForBackgroundJavaScript();

    waitUntil(driver1 -> storedSelectHasOptionCount(CANONICAL_CREDENTIALS.size() + 1));
    storedSelectElement = driver.findElement(By.id("totpStoredCredentialId"));
    assertThat(storedSelectElement.getAttribute("disabled"))
        .as("Stored credential dropdown should enable after seeding")
        .isNull();
    storedSelect = new Select(storedSelectElement);
    assertThat(normalizeText(storedSelect.getFirstSelectedOption().getText()))
        .isEqualTo("Select a credential");

    Set<String> optionLabels =
        storedSelect.getOptions().stream()
            .skip(1)
            .map(WebElement::getText)
            .map(this::normalizeText)
            .collect(Collectors.toSet());
    assertThat(optionLabels)
        .containsExactlyInAnyOrderElementsOf(
            CANONICAL_CREDENTIALS.stream().map(SeedExpectation::optionLabel).toList());

    waitUntil(driver1 -> credentialStore.findAll().size() == CANONICAL_CREDENTIALS.size());
    Map<String, Credential> persisted =
        credentialStore.findAll().stream()
            .collect(Collectors.toMap(Credential::name, credential -> credential));
    assertThat(persisted.keySet())
        .containsExactlyInAnyOrderElementsOf(
            CANONICAL_CREDENTIALS.stream().map(SeedExpectation::credentialId).toList());
    for (SeedExpectation expectation : CANONICAL_CREDENTIALS) {
      Credential credential = persisted.get(expectation.credentialId());
      assertThat(credential.type()).isEqualTo(CredentialType.OATH_TOTP);
      assertThat(credential.attributes())
          .containsEntry("totp.algorithm", expectation.algorithm())
          .containsEntry("totp.digits", Integer.toString(expectation.digits()))
          .containsEntry("totp.stepSeconds", Integer.toString(expectation.stepSeconds()))
          .containsEntry("totp.drift.backward", Integer.toString(expectation.driftBackward()))
          .containsEntry("totp.drift.forward", Integer.toString(expectation.driftForward()));
    }

    seedButton.click();
    waitForBackgroundJavaScript();

    waitUntil(driver1 -> normalizeText(seedStatus.getText()).contains("Seeded"));
    assertHidden(seedStatus, false);
    assertThat(seedStatus.getAttribute("class"))
        .contains("credential-status--warning")
        .doesNotContain("credential-status--error");
  }

  private boolean isHidden(WebElement element) {
    return element.getAttribute("hidden") != null;
  }

  private void assertHidden(WebElement element, boolean expectedHidden) {
    if (expectedHidden) {
      assertThat(element.getAttribute("hidden")).isNotNull();
    } else {
      assertThat(element.getAttribute("hidden")).isNull();
    }
  }

  private Select locateStoredSelect() {
    return new Select(driver.findElement(By.id("totpStoredCredentialId")));
  }

  private void waitForBackgroundJavaScript() {
    driver.getWebClient().waitForBackgroundJavaScript(Duration.ofSeconds(2).toMillis());
  }

  private void waitUntil(ExpectedCondition<?> condition) {
    new WebDriverWait(driver, Duration.ofSeconds(5)).until(condition);
  }

  private String normalizeText(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  private boolean storedSelectHasOptionCount(int expected) {
    try {
      return locateStoredSelect().getOptions().size() == expected;
    } catch (StaleElementReferenceException exception) {
      return false;
    }
  }

  private record SeedExpectation(
      String credentialId,
      String optionLabel,
      String algorithm,
      int digits,
      int stepSeconds,
      int driftBackward,
      int driftForward) {
    // Marker record for canonical seeding expectations.
  }
}
