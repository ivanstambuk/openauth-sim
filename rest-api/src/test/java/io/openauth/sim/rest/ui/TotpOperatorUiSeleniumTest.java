package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Selenium scaffolding for the TOTP operator console coverage. Tests are expected to fail until the
 * TOTP evaluation UI is implemented.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class TotpOperatorUiSeleniumTest {

  private static final String STORED_CREDENTIAL_ID = "ui-totp-demo";
  private static final SecretMaterial STORED_SECRET =
      SecretMaterial.fromStringUtf8("1234567890123456789012");
  private static final TotpDescriptor STORED_DESCRIPTOR =
      TotpDescriptor.create(
          STORED_CREDENTIAL_ID,
          STORED_SECRET,
          TotpHashAlgorithm.SHA1,
          6,
          Duration.ofSeconds(30),
          TotpDriftWindow.of(1, 1));
  private static final Instant STORED_TIMESTAMP = Instant.ofEpochSecond(1_111_111_111L);
  private static final String EXPECTED_STORED_OTP =
      TotpGenerator.generate(STORED_DESCRIPTOR, STORED_TIMESTAMP);

  private static final SecretMaterial INLINE_SECRET =
      SecretMaterial.fromStringUtf8(
          "1234567890123456789012345678901234567890123456789012345678901234");
  private static final TotpDescriptor INLINE_DESCRIPTOR =
      TotpDescriptor.create(
          "inline-demo",
          INLINE_SECRET,
          TotpHashAlgorithm.SHA512,
          8,
          Duration.ofSeconds(60),
          TotpDriftWindow.of(0, 0));
  private static final Instant INLINE_TIMESTAMP = Instant.ofEpochSecond(1_234_567_890L);
  private static final String INLINE_EXPECTED_OTP =
      TotpGenerator.generate(INLINE_DESCRIPTOR, INLINE_TIMESTAMP);

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("totp-credentials.db");
    registry.add("openauth.sim.persistence.database-path", () -> databasePath.toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private CredentialStore credentialStore;

  @LocalServerPort private int port;

  private HtmlUnitDriver driver;

  private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();

  @BeforeEach
  void setUp() {
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    credentialStore.delete(STORED_CREDENTIAL_ID);
    credentialStore.save(storedCredential());
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
  @DisplayName("Stored TOTP credential evaluation succeeds via operator console")
  void storedTotpCredentialEvaluationSucceeds() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    waitUntilAttribute(modeToggle, "data-mode", "stored");

    WebElement credentialInput = driver.findElement(By.id("totpStoredCredentialId"));
    credentialInput.clear();
    credentialInput.sendKeys(STORED_CREDENTIAL_ID);

    WebElement otpInput = driver.findElement(By.id("totpStoredOtp"));
    otpInput.clear();
    otpInput.sendKeys(EXPECTED_STORED_OTP);

    WebElement timestampInput = driver.findElement(By.id("totpStoredTimestamp"));
    timestampInput.clear();
    timestampInput.sendKeys(Long.toString(STORED_TIMESTAMP.getEpochSecond()));

    WebElement driftBackward = driver.findElement(By.id("totpStoredDriftBackward"));
    driftBackward.clear();
    driftBackward.sendKeys("1");

    WebElement driftForward = driver.findElement(By.id("totpStoredDriftForward"));
    driftForward.clear();
    driftForward.sendKeys("1");

    driver.findElement(By.cssSelector("[data-testid='totp-stored-evaluate-button']")).click();

    WebElement resultPanel =
        waitForVisible(By.cssSelector("[data-testid='totp-stored-result-panel']"));
    String panelText = resultPanel.getText();
    assertTrue(
        panelText.contains("validated"), "Stored TOTP result should surface validated status");
    assertTrue(
        panelText.contains("Matched skew steps"),
        "Stored TOTP result should list matched skew steps metadata");

    WebElement reasonCode =
        resultPanel.findElement(By.cssSelector("[data-testid='totp-result-reason-code']"));
    assertEquals("validated", reasonCode.getText().trim());
  }

  @Test
  @DisplayName("Inline TOTP evaluation outside configured window reports validation error")
  void inlineTotpEvaluationReportsValidationError() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
    inlineToggle.click();
    waitUntilAttribute(modeToggle, "data-mode", "inline");

    WebElement secretInput = driver.findElement(By.id("totpInlineSecretHex"));
    secretInput.clear();
    secretInput.sendKeys(INLINE_SECRET.asHex());

    selectOption("totpInlineAlgorithm", "SHA512");

    WebElement digitsInput = driver.findElement(By.id("totpInlineDigits"));
    digitsInput.clear();
    digitsInput.sendKeys("8");

    WebElement stepInput = driver.findElement(By.id("totpInlineStepSeconds"));
    stepInput.clear();
    stepInput.sendKeys("60");

    WebElement driftBackward = driver.findElement(By.id("totpInlineDriftBackward"));
    driftBackward.clear();
    driftBackward.sendKeys("0");

    WebElement driftForward = driver.findElement(By.id("totpInlineDriftForward"));
    driftForward.clear();
    driftForward.sendKeys("0");

    WebElement timestampInput = driver.findElement(By.id("totpInlineTimestamp"));
    timestampInput.clear();
    timestampInput.sendKeys(Long.toString(INLINE_TIMESTAMP.plusSeconds(180).getEpochSecond()));

    WebElement otpInput = driver.findElement(By.id("totpInlineOtp"));
    otpInput.clear();
    otpInput.sendKeys(INLINE_EXPECTED_OTP);

    driver.findElement(By.cssSelector("[data-testid='totp-inline-evaluate-button']")).click();

    WebElement errorPanel =
        waitForVisible(By.cssSelector("[data-testid='totp-inline-error-panel']"));
    String errorText = errorPanel.getText();
    assertTrue(
        errorText.contains("otp_out_of_window"),
        "Inline TOTP validation error should expose otp_out_of_window reason code");
  }

  @Test
  @DisplayName("Stored TOTP replay returns match without mutating state")
  void storedTotpReplayReturnsMatch() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("totpTab=replay");

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    waitUntilAttribute(replayToggle, "data-mode", "stored");

    WebElement credentialInput = driver.findElement(By.id("totpReplayStoredCredentialId"));
    credentialInput.clear();
    credentialInput.sendKeys(STORED_CREDENTIAL_ID);

    WebElement otpInput = driver.findElement(By.id("totpReplayStoredOtp"));
    otpInput.clear();
    otpInput.sendKeys(EXPECTED_STORED_OTP);

    WebElement timestampInput = driver.findElement(By.id("totpReplayStoredTimestamp"));
    timestampInput.clear();
    timestampInput.sendKeys(Long.toString(STORED_TIMESTAMP.getEpochSecond()));

    WebElement driftBackward = driver.findElement(By.id("totpReplayStoredDriftBackward"));
    driftBackward.clear();
    driftBackward.sendKeys("1");

    WebElement driftForward = driver.findElement(By.id("totpReplayStoredDriftForward"));
    driftForward.clear();
    driftForward.sendKeys("1");

    WebElement replayButton =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-stored-submit']"));
    replayButton.click();

    WebElement resultPanel =
        waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
    String statusText =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-status']"))
            .getText()
            .trim();
    assertEquals("match", statusText.toLowerCase());
    String reasonCode =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-reason-code']"))
            .getText()
            .trim();
    assertEquals("match", reasonCode.toLowerCase());
    assertEquals(
        "0",
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-matched-skew']"))
            .getText()
            .trim());
    assertEquals(
        "stored",
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-credential-source']"))
            .getText()
            .trim());
    String telemetryId =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-telemetry']"))
            .getText()
            .trim();
    assertTrue(telemetryId.startsWith("rest-totp-"));
  }

  @Test
  @DisplayName("Inline TOTP replay outside drift returns mismatch")
  void inlineTotpReplayReportsMismatch() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("totpTab=replay");

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
    inlineToggle.click();
    waitUntilAttribute(replayToggle, "data-mode", "inline");

    WebElement secretInput = driver.findElement(By.id("totpReplayInlineSecretHex"));
    secretInput.clear();
    secretInput.sendKeys(INLINE_SECRET.asHex());

    selectOption("totpReplayInlineAlgorithm", "SHA512");

    WebElement digitsInput = driver.findElement(By.id("totpReplayInlineDigits"));
    digitsInput.clear();
    digitsInput.sendKeys("8");

    WebElement stepSecondsInput = driver.findElement(By.id("totpReplayInlineStepSeconds"));
    stepSecondsInput.clear();
    stepSecondsInput.sendKeys("60");

    WebElement driftBackward = driver.findElement(By.id("totpReplayInlineDriftBackward"));
    driftBackward.clear();
    driftBackward.sendKeys("0");

    WebElement driftForward = driver.findElement(By.id("totpReplayInlineDriftForward"));
    driftForward.clear();
    driftForward.sendKeys("0");

    WebElement timestampInputReplay = driver.findElement(By.id("totpReplayInlineTimestamp"));
    timestampInputReplay.clear();
    timestampInputReplay.sendKeys(
        Long.toString(INLINE_TIMESTAMP.plusSeconds(180).getEpochSecond()));

    WebElement timestampOverrideInput =
        driver.findElement(By.id("totpReplayInlineTimestampOverride"));
    timestampOverrideInput.clear();
    timestampOverrideInput.sendKeys(
        Long.toString(INLINE_TIMESTAMP.minusSeconds(120).getEpochSecond()));

    WebElement otpInput = driver.findElement(By.id("totpReplayInlineOtp"));
    otpInput.clear();
    otpInput.sendKeys(INLINE_EXPECTED_OTP);

    WebElement replayButton =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-inline-submit']"));
    replayButton.click();

    waitUntilUrlContains("totpReplayMode=inline");

    WebElement resultPanel =
        waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
    String statusText =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-status']"))
            .getText()
            .trim();
    assertEquals("mismatch", statusText.toLowerCase());
    String reasonCode =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-reason-code']"))
            .getText()
            .trim();
    assertEquals("otp_out_of_window", reasonCode.toLowerCase());
    assertEquals(
        "inline",
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-credential-source']"))
            .getText()
            .trim());
    String telemetryId =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-telemetry']"))
            .getText()
            .trim();
    assertTrue(telemetryId.startsWith("rest-totp-"));
  }

  @Test
  @DisplayName("TOTP inline mode persists across refresh via query parameters")
  void totpInlineModePersistsAcrossRefresh() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
    inlineToggle.click();
    waitUntilAttribute(modeToggle, "data-mode", "inline");

    waitUntilUrlContains("protocol=totp");
    waitUntilUrlContains("totpTab=evaluate");
    waitUntilUrlContains("totpMode=inline");

    driver.navigate().refresh();

    WebElement refreshedToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    waitUntilAttribute(refreshedToggle, "data-mode", "inline");
    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='totp-panel-tab-evaluate']"));
    waitUntilAttribute(evaluateTab, "aria-selected", "true");
  }

  private void navigateToTotpPanel() {
    driver.get("http://localhost:" + port + "/ui/console?protocol=totp");
    waitFor(By.cssSelector("[data-testid='protocol-tab-totp']"));
    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='totp-panel-tab-evaluate']"));
    if (!"true".equals(evaluateTab.getAttribute("aria-selected"))) {
      evaluateTab.click();
      waitUntilAttribute(evaluateTab, "aria-selected", "true");
    }
  }

  private void switchToReplayTab() {
    WebElement replayTab = waitFor(By.cssSelector("[data-testid='totp-panel-tab-replay']"));
    if (!"true".equals(replayTab.getAttribute("aria-selected"))) {
      replayTab.click();
      waitUntilAttribute(replayTab, "aria-selected", "true");
    }
  }

  private WebElement waitFor(By locator) {
    return new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.presenceOfElementLocated(locator));
  }

  private WebElement waitForVisible(By locator) {
    return new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.visibilityOfElementLocated(locator));
  }

  private void waitUntilAttribute(WebElement element, String attribute, String expectedValue) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.attributeToBe(element, attribute, expectedValue));
  }

  private void waitUntilUrlContains(String fragment) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.urlContains(fragment));
  }

  private void selectOption(String selectId, String value) {
    WebElement select = driver.findElement(By.id(selectId));
    new org.openqa.selenium.support.ui.Select(select).selectByValue(value);
  }

  private Credential storedCredential() {
    return VersionedCredentialRecordMapper.toCredential(adapter.serialize(STORED_DESCRIPTOR));
  }
}
