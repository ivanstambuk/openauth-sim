package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
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

/**
 * Failing Selenium coverage for the upcoming HOTP replay operator UI. These scenarios exercise
 * stored and inline replay flows, sample data affordances, advanced toggles, and telemetry
 * surfacing. They are expected to fail until R2216 wires the templates and JavaScript.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class HotpOperatorUiReplaySeleniumTest {

  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(6);

  private static final String STORED_CREDENTIAL_ID = "ui-hotp-demo";
  private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final int DIGITS = 6;
  private static final long STORED_COUNTER = 0L;
  private static final long INLINE_COUNTER = 5L;
  private static final String INLINE_SAMPLE_KEY = "demo-inline";
  private static final HotpDescriptor DESCRIPTOR =
      HotpDescriptor.create(
          STORED_CREDENTIAL_ID, SecretMaterial.fromHex(SECRET_HEX), HotpHashAlgorithm.SHA1, DIGITS);
  private static final String EXPECTED_STORED_OTP =
      HotpGenerator.generate(DESCRIPTOR, STORED_COUNTER);
  private static final String EXPECTED_INLINE_OTP =
      HotpGenerator.generate(DESCRIPTOR, INLINE_COUNTER);

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("credentials.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private CredentialStore credentialStore;

  @LocalServerPort private int port;

  private HtmlUnitDriver driver;

  @BeforeEach
  void setUp() {
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    credentialStore.delete(STORED_CREDENTIAL_ID);
    credentialStore.save(storedCredential(STORED_COUNTER));
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      try {
        driver.quit();
      } catch (WebDriverException ignored) {
        // driver already closed
      }
    }
  }

  @Test
  @DisplayName("Stored HOTP replay surfaces telemetry and counter metadata")
  void storedHotpReplaySurfacesTelemetryAndMetadata() {
    navigateToReplayPanel();

    waitForVisible(By.cssSelector("[data-testid='hotp-replay-panel']"));

    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));
    assertThat(storedMode.isSelected()).as("Stored mode should be selected by default").isTrue();

    Select credentialSelect = new Select(waitForVisible(By.id("hotpReplayStoredCredentialId")));
    waitForCredentialOption(credentialSelect, STORED_CREDENTIAL_ID);
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    waitForTextContains(
        By.cssSelector("[data-testid='hotp-replay-sample-status']"), "Sample data ready");
    WebElement sampleButton =
        waitForClickable(By.cssSelector("button[data-testid='hotp-replay-sample-load']"));
    sampleButton.click();

    waitForAttribute(
        By.id("hotpReplayStoredOtp"),
        "value",
        EXPECTED_STORED_OTP,
        "Stored replay OTP should auto-fill from curated sample data");

    WebElement advancedToggle =
        waitForVisible(By.cssSelector("button[data-testid='hotp-replay-advanced-toggle']"));
    if ("false".equals(advancedToggle.getAttribute("aria-expanded"))) {
      advancedToggle.click();
    }

    WebElement advancedPanel =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-advanced-panel']"));
    assertThat(advancedPanel.getAttribute("hidden")).isNull();
    assertThat(advancedPanel.getAttribute("data-open")).isEqualTo("true");

    waitForClickable(By.cssSelector("button[data-testid='hotp-replay-submit']")).click();

    WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='hotp-replay-result']"));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    assertThat(
            resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-status']")).getText())
        .isEqualToIgnoringCase("match");
    assertThat(
            resultPanel
                .findElement(By.cssSelector("[data-testid='hotp-replay-metadata']"))
                .getText())
        .contains("previousCounter=0")
        .contains("nextCounter=0")
        .contains("credentialSource=stored");

    String telemetryId =
        resultPanel
            .findElement(By.cssSelector("[data-testid='hotp-replay-telemetry-id']"))
            .getText();
    assertThat(telemetryId)
        .as("Telemetry identifier should surface in the replay result")
        .isNotBlank()
        .startsWith("ui-hotp-");
  }

  @Test
  @DisplayName("Inline HOTP replay auto-fills sample vector and emits telemetry")
  void inlineHotpReplayAutoFillsSampleAndEmitsTelemetry() {
    navigateToReplayPanel();

    waitForClickable(By.id("hotpReplayModeInline")).click();

    WebElement inlinePanel =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-inline-panel']"));
    assertThat(inlinePanel.getAttribute("hidden")).isNull();

    Select presetSelect =
        new Select(
            waitForVisible(
                By.cssSelector("select[data-testid='hotp-replay-inline-sample-select']")));
    waitForOption(presetSelect, INLINE_SAMPLE_KEY);
    presetSelect.selectByValue(INLINE_SAMPLE_KEY);

    waitForAttribute(
        By.id("hotpReplayInlineIdentifier"),
        "value",
        "inline-sample",
        "Inline identifier should auto-fill from the selected preset");
    waitForAttribute(
        By.id("hotpReplayInlineSecretHex"),
        "value",
        SECRET_HEX,
        "Inline secret should auto-fill from preset data");
    waitForAttribute(
        By.id("hotpReplayInlineCounter"),
        "value",
        Long.toString(INLINE_COUNTER),
        "Inline counter should auto-fill from preset data");
    waitForAttribute(
        By.id("hotpReplayInlineDigits"),
        "value",
        Integer.toString(DIGITS),
        "Digits field should align with preset metadata");
    waitForAttribute(
        By.id("hotpReplayInlineOtp"),
        "value",
        EXPECTED_INLINE_OTP,
        "Inline OTP should auto-fill from preset data");

    WebElement advancedToggle =
        waitForVisible(By.cssSelector("button[data-testid='hotp-replay-advanced-toggle']"));
    if ("false".equals(advancedToggle.getAttribute("aria-expanded"))) {
      advancedToggle.click();
    }
    WebElement advancedPanel =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-advanced-panel']"));
    assertThat(advancedPanel.getAttribute("hidden")).isNull();

    waitForClickable(By.cssSelector("button[data-testid='hotp-replay-submit']")).click();

    WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='hotp-replay-result']"));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    assertThat(
            resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-status']")).getText())
        .isEqualToIgnoringCase("match");
    assertThat(
            resultPanel
                .findElement(By.cssSelector("[data-testid='hotp-replay-metadata']"))
                .getText())
        .contains("previousCounter=5")
        .contains("nextCounter=5")
        .contains("credentialSource=inline");
    String telemetryId =
        resultPanel
            .findElement(By.cssSelector("[data-testid='hotp-replay-telemetry-id']"))
            .getText();
    assertThat(telemetryId)
        .as("Inline replay should expose telemetry identifier for audit trail")
        .isNotBlank()
        .startsWith("ui-hotp-");
  }

  private void navigateToReplayPanel() {
    driver.get("http://localhost:" + port + "/ui/console?protocol=hotp");
    WebElement tab = driver.findElement(By.cssSelector("[data-testid='hotp-panel-tab-replay']"));
    tab.click();
  }

  private Credential storedCredential(long counter) {
    return Credential.create(
        STORED_CREDENTIAL_ID,
        CredentialType.OATH_HOTP,
        SecretMaterial.fromHex(SECRET_HEX),
        java.util.Map.of(
            "hotp.algorithm",
            HotpHashAlgorithm.SHA1.name(),
            "hotp.digits",
            Integer.toString(DIGITS),
            "hotp.counter",
            Long.toString(counter)));
  }

  private WebElement waitForVisible(By locator) {
    return new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(ExpectedConditions.visibilityOfElementLocated(locator));
  }

  private WebElement waitForClickable(By locator) {
    return new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(ExpectedConditions.elementToBeClickable(locator));
  }

  private void waitForCredentialOption(Select select, String value) {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(
            d ->
                select.getWrappedElement().isEnabled()
                    && select.getOptions().stream()
                        .anyMatch(option -> value.equals(option.getAttribute("value"))));
    select.selectByValue(value);
  }

  private void waitForOption(Select select, String value) {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(
            d ->
                select.getOptions().stream()
                    .anyMatch(option -> value.equals(option.getAttribute("value"))));
  }

  private void waitForTextContains(By locator, String substring) {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(ExpectedConditions.textToBePresentInElementLocated(locator, substring));
  }

  private void waitForAttribute(By locator, String attribute, String expected, String message) {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(ExpectedConditions.attributeToBe(locator, attribute, expected));
    String actual = driver.findElement(locator).getAttribute(attribute);
    assertThat(actual).as(message).isEqualTo(expected);
  }
}
