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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
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
 * Selenium scaffolding for the TOTP operator console coverage. Tests are expected to fail until the
 * TOTP evaluation UI is implemented.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class TotpOperatorUiSeleniumTest {

  private static final String STORED_CREDENTIAL_ID = "ui-totp-sample-sha1-6";
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

  private static final String INLINE_SAMPLE_PRESET_KEY = "inline-rfc6238-sha1";
  private static final SecretMaterial INLINE_SAMPLE_SECRET =
      SecretMaterial.fromStringUtf8("12345678901234567890");
  private static final TotpDescriptor INLINE_SAMPLE_DESCRIPTOR =
      TotpDescriptor.create(
          "inline-rfc6238-sample",
          INLINE_SAMPLE_SECRET,
          TotpHashAlgorithm.SHA1,
          8,
          Duration.ofSeconds(30),
          TotpDriftWindow.of(1, 1));
  private static final Instant INLINE_SAMPLE_TIMESTAMP = Instant.ofEpochSecond(59L);
  private static final String INLINE_SAMPLE_EXPECTED_OTP =
      TotpGenerator.generate(INLINE_SAMPLE_DESCRIPTOR, INLINE_SAMPLE_TIMESTAMP);
  private static final List<String> INLINE_SAMPLE_PRESET_KEYS =
      List.of(
          "inline-rfc6238-sha1",
          "inline-rfc6238-sha256-6",
          "inline-rfc6238-sha256-8",
          "inline-rfc6238-sha512-6",
          "inline-rfc6238-sha512-8",
          "inline-ui-totp-demo");

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("credentials.db");
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
    driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
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
    waitUntilAttribute(modeToggle, "data-mode", "inline");

    WebElement storedToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-stored']"));
    storedToggle.click();
    waitUntilAttribute(modeToggle, "data-mode", "stored");

    WebElement storedLabel =
        driver.findElement(By.cssSelector("label[for='totpStoredCredentialId']"));
    assertEquals("Stored credential", storedLabel.getText().trim());

    selectOption("totpStoredCredentialId", STORED_CREDENTIAL_ID);

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
        panelText.contains("OTP"), "Stored TOTP result should display the evaluated OTP label");
    WebElement otpValue =
        resultPanel.findElement(By.cssSelector("[data-testid='totp-result-otp']"));
    assertEquals(EXPECTED_STORED_OTP, otpValue.getText().trim());

    WebElement statusBadge =
        resultPanel.findElement(By.cssSelector("[data-testid='totp-result-status']"));
    assertEquals("success", statusBadge.getText().trim().toLowerCase());

    WebElement otpInput = driver.findElement(By.id("totpStoredOtp"));
    assertEquals(
        EXPECTED_STORED_OTP,
        otpInput.getAttribute("value"),
        "Stored OTP input should auto-populate with the generated code");
    assertEquals(
        "true",
        otpInput.getAttribute("readonly"),
        "Stored OTP input should be read-only when auto-populated");
  }

  @Test
  @DisplayName("TOTP evaluate tab defaults to inline parameters mode")
  void totpEvaluateDefaultsToInline() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    waitUntilAttribute(modeToggle, "data-mode", "inline");

    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
    assertTrue(inlineToggle.isSelected(), "Inline mode toggle should be selected by default");
  }

  @Test
  @DisplayName("Inline TOTP evaluation generates OTP from supplied parameters")
  void inlineTotpEvaluationGeneratesOtp() {
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
    driftBackward.sendKeys("1");

    WebElement driftForward = driver.findElement(By.id("totpInlineDriftForward"));
    driftForward.clear();
    driftForward.sendKeys("1");

    WebElement timestampInput = driver.findElement(By.id("totpInlineTimestamp"));
    timestampInput.clear();
    timestampInput.sendKeys(Long.toString(INLINE_TIMESTAMP.getEpochSecond()));

    WebElement otpInput = driver.findElement(By.id("totpInlineOtp"));
    assertEquals(
        "",
        otpInput.getAttribute("value"),
        "Inline OTP input should start empty when operator is generating a code");

    driver.findElement(By.cssSelector("[data-testid='totp-inline-evaluate-button']")).click();

    WebElement resultPanel =
        waitForVisible(By.cssSelector("[data-testid='totp-inline-result-panel']"));
    WebElement otpValue =
        resultPanel.findElement(By.cssSelector("[data-testid='totp-result-otp']"));
    assertEquals(INLINE_EXPECTED_OTP, otpValue.getText().trim());
    WebElement statusBadge =
        resultPanel.findElement(By.cssSelector("[data-testid='totp-inline-result-status']"));
    assertEquals("success", statusBadge.getText().trim().toLowerCase());

    assertEquals(
        INLINE_EXPECTED_OTP,
        otpInput.getAttribute("value"),
        "Inline OTP input should be populated with the generated code");
    assertEquals(
        "true",
        otpInput.getAttribute("readonly"),
        "Inline OTP input should be read-only after generation");
  }

  @Test
  @DisplayName("Inline TOTP evaluate preset populates sample vector inputs")
  void totpInlineSamplePresetPopulatesForm() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
    inlineToggle.click();
    waitUntilAttribute(modeToggle, "data-mode", "inline");

    WebElement inlinePanel = waitForVisible(By.cssSelector("[data-testid='totp-inline-panel']"));

    WebElement presetContainer =
        waitForVisible(By.cssSelector("[data-testid='totp-inline-preset']"));
    WebElement presetLabel = presetContainer.findElement(By.tagName("label"));
    assertTrue(
        presetLabel.getText().contains("Load a sample vector"),
        "Inline preset label should mention sample vectors");

    Select presetSelect =
        new Select(
            presetContainer.findElement(
                By.cssSelector("[data-testid='totp-inline-preset-select']")));
    assertTrue(
        presetSelect.getOptions().size() >= INLINE_SAMPLE_PRESET_KEYS.size(),
        "Inline preset dropdown should expose all expected sample options");
    List<String> inlineOptionValues =
        presetSelect.getOptions().stream()
            .map(option -> option.getAttribute("value"))
            .collect(Collectors.toList());
    assertTrue(
        inlineOptionValues.containsAll(INLINE_SAMPLE_PRESET_KEYS),
        "Inline preset dropdown should list all RFC 6238 variants");

    presetSelect.selectByValue(INLINE_SAMPLE_PRESET_KEY);

    WebElement secretField = driver.findElement(By.id("totpInlineSecretHex"));
    assertEquals(INLINE_SAMPLE_SECRET.asHex(), secretField.getAttribute("value"));

    Select algorithmSelect = new Select(driver.findElement(By.id("totpInlineAlgorithm")));
    String algorithmValue =
        algorithmSelect.getFirstSelectedOption() == null
            ? null
            : algorithmSelect.getFirstSelectedOption().getAttribute("value");
    assertEquals("SHA1", algorithmValue);

    WebElement digitsField = driver.findElement(By.id("totpInlineDigits"));
    assertEquals(
        Integer.toString(INLINE_SAMPLE_DESCRIPTOR.digits()), digitsField.getAttribute("value"));

    WebElement stepField = driver.findElement(By.id("totpInlineStepSeconds"));
    assertEquals(
        Long.toString(INLINE_SAMPLE_DESCRIPTOR.stepSeconds()), stepField.getAttribute("value"));

    WebElement timestampField = driver.findElement(By.id("totpInlineTimestamp"));
    assertEquals(
        Long.toString(INLINE_SAMPLE_TIMESTAMP.getEpochSecond()),
        timestampField.getAttribute("value"));

    WebElement otpField = driver.findElement(By.id("totpInlineOtp"));
    assertEquals("", otpField.getAttribute("value"));
    assertEquals(
        "true",
        otpField.getAttribute("readonly"),
        "Inline OTP input should remain read-only when presets are applied");

    WebElement driftBackward = driver.findElement(By.id("totpInlineDriftBackward"));
    assertEquals(
        Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().backwardSteps()),
        driftBackward.getAttribute("value"));

    WebElement driftForward = driver.findElement(By.id("totpInlineDriftForward"));
    assertEquals(
        Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().forwardSteps()),
        driftForward.getAttribute("value"));

    WebElement inlineSection = inlinePanel.findElement(By.tagName("form"));
    assertEquals("totp-inline-form", inlineSection.getAttribute("data-testid"));
  }

  @Test
  @DisplayName("TOTP inline preset spacing matches HOTP baseline")
  void totpInlinePresetSpacingMatchesHotpBaseline() {
    navigateToTotpPanel();

    WebElement evaluateInlineSection = waitFor(By.cssSelector("[data-testid='totp-inline-panel']"));
    assertTrue(
        hasCssClass(evaluateInlineSection, "stack-offset-top-lg"),
        "TOTP evaluate inline section should reuse the shared spacing token");

    switchToReplayTab();

    WebElement replayInlineSection =
        waitFor(By.cssSelector("[data-testid='totp-replay-inline-section']"));
    assertTrue(
        hasCssClass(replayInlineSection, "stack-offset-top-lg"),
        "TOTP replay inline section should reuse the shared spacing token");
  }

  @Test
  @DisplayName("TOTP inline parameter controls align on a single row")
  void totpInlineParameterControlsAlignOnSingleRow() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
    inlineToggle.click();
    waitUntilAttribute(modeToggle, "data-mode", "inline");

    waitForVisible(By.cssSelector("[data-testid='totp-inline-panel']"));

    WebElement inlineParameterGrid =
        waitFor(By.cssSelector("[data-testid='totp-inline-parameters-grid']"));
    WebElement inlineAlgorithm =
        inlineParameterGrid.findElement(By.cssSelector("#totpInlineAlgorithm"));
    WebElement inlineDigits = inlineParameterGrid.findElement(By.cssSelector("#totpInlineDigits"));
    WebElement inlineStep =
        inlineParameterGrid.findElement(By.cssSelector("#totpInlineStepSeconds"));
    assertSameRow("evaluate inline parameters", inlineAlgorithm, inlineDigits, inlineStep);

    switchToReplayTab();

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    WebElement replayInlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
    replayInlineToggle.click();
    waitUntilAttribute(replayToggle, "data-mode", "inline");

    waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-section']"));

    WebElement replayParameterGrid =
        waitFor(By.cssSelector("[data-testid='totp-replay-inline-parameters-grid']"));
    WebElement replayAlgorithm =
        replayParameterGrid.findElement(By.cssSelector("#totpReplayInlineAlgorithm"));
    WebElement replayDigits =
        replayParameterGrid.findElement(By.cssSelector("#totpReplayInlineDigits"));
    WebElement replayStep =
        replayParameterGrid.findElement(By.cssSelector("#totpReplayInlineStepSeconds"));
    assertSameRow("replay inline parameters", replayAlgorithm, replayDigits, replayStep);
  }

  @Test
  @DisplayName("TOTP replay inline preset populates sample vector inputs")
  void totpReplayInlineSamplePresetPopulatesForm() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("tab=replay");

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    WebElement inlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
    inlineToggle.click();
    waitUntilAttribute(replayToggle, "data-mode", "inline");

    waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-section']"));

    WebElement presetContainer =
        waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-preset']"));
    WebElement presetLabel = presetContainer.findElement(By.tagName("label"));
    assertTrue(
        presetLabel.getText().contains("Load a sample vector"),
        "Replay inline preset label should mention sample vectors");

    Select presetSelect =
        new Select(
            presetContainer.findElement(
                By.cssSelector("[data-testid='totp-replay-inline-preset-select']")));
    assertTrue(
        presetSelect.getOptions().size() >= INLINE_SAMPLE_PRESET_KEYS.size(),
        "Replay inline preset dropdown should expose all expected sample options");
    List<String> replayOptionValues =
        presetSelect.getOptions().stream()
            .map(option -> option.getAttribute("value"))
            .collect(Collectors.toList());
    assertTrue(
        replayOptionValues.containsAll(INLINE_SAMPLE_PRESET_KEYS),
        "Replay inline preset dropdown should list all RFC 6238 variants");

    presetSelect.selectByValue(INLINE_SAMPLE_PRESET_KEY);

    WebElement secretField = driver.findElement(By.id("totpReplayInlineSecretHex"));
    assertEquals(INLINE_SAMPLE_SECRET.asHex(), secretField.getAttribute("value"));

    Select algorithmSelect = new Select(driver.findElement(By.id("totpReplayInlineAlgorithm")));
    String algorithmValue =
        algorithmSelect.getFirstSelectedOption() == null
            ? null
            : algorithmSelect.getFirstSelectedOption().getAttribute("value");
    assertEquals("SHA1", algorithmValue);

    WebElement digitsField = driver.findElement(By.id("totpReplayInlineDigits"));
    assertEquals(
        Integer.toString(INLINE_SAMPLE_DESCRIPTOR.digits()), digitsField.getAttribute("value"));

    WebElement stepField = driver.findElement(By.id("totpReplayInlineStepSeconds"));
    assertEquals(
        Long.toString(INLINE_SAMPLE_DESCRIPTOR.stepSeconds()), stepField.getAttribute("value"));

    WebElement timestampField = driver.findElement(By.id("totpReplayInlineTimestamp"));
    assertEquals(
        Long.toString(INLINE_SAMPLE_TIMESTAMP.getEpochSecond()),
        timestampField.getAttribute("value"));

    WebElement otpField = driver.findElement(By.id("totpReplayInlineOtp"));
    assertEquals(INLINE_SAMPLE_EXPECTED_OTP, otpField.getAttribute("value"));

    WebElement driftBackward = driver.findElement(By.id("totpReplayInlineDriftBackward"));
    assertEquals(
        Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().backwardSteps()),
        driftBackward.getAttribute("value"));

    WebElement driftForward = driver.findElement(By.id("totpReplayInlineDriftForward"));
    assertEquals(
        Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().forwardSteps()),
        driftForward.getAttribute("value"));
  }

  @Test
  @DisplayName("TOTP drift controls align on a single row across modes")
  void totpDriftControlsAlignOnSingleRowAcrossModes() {
    navigateToTotpPanel();

    WebElement storedDriftGrid = waitFor(By.cssSelector("[data-testid='totp-stored-drift-grid']"));
    WebElement storedBackward =
        storedDriftGrid.findElement(By.cssSelector("#totpStoredDriftBackward"));
    WebElement storedForward =
        storedDriftGrid.findElement(By.cssSelector("#totpStoredDriftForward"));
    assertSameRow("evaluate stored drift controls", storedBackward, storedForward);

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    WebElement inlineModeToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
    inlineModeToggle.click();
    waitUntilAttribute(modeToggle, "data-mode", "inline");
    waitForVisible(By.cssSelector("[data-testid='totp-inline-panel']"));

    WebElement inlineDriftGrid = waitFor(By.cssSelector("[data-testid='totp-inline-drift-grid']"));
    WebElement inlineBackward =
        inlineDriftGrid.findElement(By.cssSelector("#totpInlineDriftBackward"));
    WebElement inlineForward =
        inlineDriftGrid.findElement(By.cssSelector("#totpInlineDriftForward"));
    assertSameRow("evaluate inline drift controls", inlineBackward, inlineForward);

    switchToReplayTab();

    WebElement replayStoredDriftGrid =
        waitFor(By.cssSelector("[data-testid='totp-replay-stored-drift-grid']"));
    WebElement replayStoredBackward =
        replayStoredDriftGrid.findElement(By.cssSelector("#totpReplayStoredDriftBackward"));
    WebElement replayStoredForward =
        replayStoredDriftGrid.findElement(By.cssSelector("#totpReplayStoredDriftForward"));
    assertSameRow("replay stored drift controls", replayStoredBackward, replayStoredForward);

    WebElement replayModeToggle =
        waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    WebElement replayInlineToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
    replayInlineToggle.click();
    waitUntilAttribute(replayModeToggle, "data-mode", "inline");
    waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-section']"));

    WebElement replayInlineDriftGrid =
        waitFor(By.cssSelector("[data-testid='totp-replay-inline-drift-grid']"));
    WebElement replayInlineBackward =
        replayInlineDriftGrid.findElement(By.cssSelector("#totpReplayInlineDriftBackward"));
    WebElement replayInlineForward =
        replayInlineDriftGrid.findElement(By.cssSelector("#totpReplayInlineDriftForward"));
    assertSameRow("replay inline drift controls", replayInlineBackward, replayInlineForward);
  }

  @Test
  @DisplayName("TOTP mode selectors list inline before stored credentials")
  void totpModeSelectorsListInlineBeforeStored() {
    navigateToTotpPanel();

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
    List<WebElement> evaluateOptions =
        modeToggle.findElements(By.cssSelector(".mode-option label"));
    assertEquals("Inline parameters", evaluateOptions.get(0).getText().trim());
    assertEquals("Stored credential", evaluateOptions.get(1).getText().trim());

    switchToReplayTab();

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    List<WebElement> replayOptions =
        replayToggle.findElements(By.cssSelector(".mode-option label"));
    assertEquals("Inline parameters", replayOptions.get(0).getText().trim());
    assertEquals("Stored credential", replayOptions.get(1).getText().trim());
  }

  @Test
  @DisplayName("Stored TOTP replay returns match without mutating state")
  void storedTotpReplayReturnsMatch() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("tab=replay");

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    waitUntilAttribute(replayToggle, "data-mode", "stored");

    WebElement credentialLabel =
        driver.findElement(By.cssSelector("label[for='totpReplayStoredCredentialId']"));
    assertEquals("Stored credential", credentialLabel.getText().trim());

    By replaySelectLocator = By.id("totpReplayStoredCredentialId");
    waitUntilSelectValue(replaySelectLocator, "");
    assertEquals(
        "",
        new Select(driver.findElement(replaySelectLocator))
            .getFirstSelectedOption()
            .getAttribute("value"),
        "Stored replay credential dropdown should default to placeholder option");
    selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);

    waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);
    waitUntilFieldValue(
        By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));
    waitUntilFieldValue(By.id("totpReplayStoredDriftBackward"), "1");
    waitUntilFieldValue(By.id("totpReplayStoredDriftForward"), "1");

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
    String outcome =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-outcome']"))
            .getText()
            .trim();
    assertEquals("match", outcome.toLowerCase());
  }

  @Test
  @DisplayName("Stored TOTP replay auto-fills OTP and timestamp fields")
  void storedTotpReplaySampleAutoFillsForm() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("tab=replay");

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    waitUntilAttribute(replayToggle, "data-mode", "stored");

    waitUntilOptionsCount(By.id("totpReplayStoredCredentialId"), 2);
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(
            d -> {
              Select select = new Select(d.findElement(By.id("totpReplayStoredCredentialId")));
              try {
                select.selectByValue(STORED_CREDENTIAL_ID);
                return true;
              } catch (NullPointerException ex) {
                return false;
              }
            });
    WebElement sampleStatus = waitFor(By.cssSelector("[data-testid='totp-replay-sample-status']"));
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(d -> !sampleStatus.getText().trim().isEmpty());

    waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);
    waitUntilFieldValue(
        By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));

    String statusText = sampleStatus.getText().trim().toLowerCase();
    assertTrue(
        statusText.contains("sample"),
        () -> "Sample status message should reference applied preset: " + statusText);
  }

  @Test
  @DisplayName("TOTP replay result column preserves status badge without clipping")
  void totpReplayResultColumnPreservesStatusBadge() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("tab=replay");

    WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
    waitUntilAttribute(replayToggle, "data-mode", "stored");

    selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);

    waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);
    waitUntilFieldValue(
        By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));

    WebElement replayButton =
        driver.findElement(By.cssSelector("[data-testid='totp-replay-stored-submit']"));
    replayButton.click();

    WebElement resultPanel =
        waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
    assertTrue(
        resultPanel.isDisplayed(), "Replay result panel should be visible before layout checks");

    WebElement statusColumn =
        driver.findElement(By.cssSelector("#totp-replay-panel .status-column"));
    String overflowX = statusColumn.getCssValue("overflow-x");
    assertEquals(
        "visible",
        overflowX,
        "Replay status column should allow horizontal content without clipping");
  }

  @Test
  @DisplayName("Inline TOTP replay outside drift returns mismatch")
  void inlineTotpReplayReportsMismatch() {
    navigateToTotpPanel();
    switchToReplayTab();
    waitUntilUrlContains("tab=replay");

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

    waitUntilUrlContains("mode=inline");

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
    String outcome =
        resultPanel
            .findElement(By.cssSelector("[data-testid='totp-replay-outcome']"))
            .getText()
            .trim();
    assertEquals("mismatch", outcome.toLowerCase());
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
    waitUntilUrlContains("tab=evaluate");
    waitUntilUrlContains("mode=inline");

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

  private void assertSameRow(String context, WebElement anchor, WebElement... others) {
    int anchorY = anchor.getRect().getY();
    for (WebElement element : others) {
      int difference = Math.abs(element.getRect().getY() - anchorY);
      assertTrue(
          difference <= 2,
          () ->
              String.format(
                  "%s should align on single row: anchorY=%d, elementY=%d (difference=%d)",
                  context, anchorY, element.getRect().getY(), difference));
    }
  }

  private void waitUntilOptionsCount(By locator, int expectedCount) {
    new WebDriverWait(driver, Duration.ofSeconds(10))
        .until(
            driver1 -> {
              try {
                Select select = new Select(driver1.findElement(locator));
                return select.getOptions().size() >= expectedCount;
              } catch (StaleElementReferenceException ex) {
                return false;
              }
            });
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

  private void waitUntilFieldValue(By locator, String expectedValue) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            driver1 -> {
              try {
                WebElement element = driver1.findElement(locator);
                return expectedValue.equals(element.getAttribute("value"));
              } catch (StaleElementReferenceException ex) {
                return false;
              }
            });
  }

  private void waitUntilSelectValue(By locator, String expectedValue) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            driver1 -> {
              try {
                Select select = new Select(driver1.findElement(locator));
                WebElement option = select.getFirstSelectedOption();
                return option != null && expectedValue.equals(option.getAttribute("value"));
              } catch (StaleElementReferenceException ex) {
                return false;
              }
            });
  }

  private boolean hasCssClass(WebElement element, String className) {
    String classAttribute = element.getAttribute("class");
    if (classAttribute == null || classAttribute.isBlank()) {
      return false;
    }
    for (String candidate : classAttribute.split("\\s+")) {
      if (className.equals(candidate)) {
        return true;
      }
    }
    return false;
  }

  private void selectOption(String selectId, String value) {
    By locator = By.id(selectId);
    new WebDriverWait(driver, Duration.ofSeconds(10))
        .until(
            driver1 -> {
              try {
                new Select(driver1.findElement(locator)).selectByValue(value);
                return true;
              } catch (StaleElementReferenceException
                  | NullPointerException
                  | NoSuchElementException
                  | UnsupportedOperationException ex) {
                return false;
              }
            });
  }

  private Credential storedCredential() {
    Credential serialized =
        VersionedCredentialRecordMapper.toCredential(adapter.serialize(STORED_DESCRIPTOR));
    Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
    attributes.put("totp.metadata.presetKey", "inline-ui-totp-demo");
    attributes.put("totp.metadata.presetLabel", "SHA-1, 6 digits, 30s");
    attributes.put("totp.metadata.notes", "Seeded TOTP credential (inline demo preset)");
    attributes.put(
        "totp.metadata.sampleTimestamp", Long.toString(STORED_TIMESTAMP.getEpochSecond()));
    return new Credential(
        serialized.name(),
        serialized.type(),
        serialized.secret(),
        attributes,
        serialized.createdAt(),
        serialized.updatedAt());
  }
}
