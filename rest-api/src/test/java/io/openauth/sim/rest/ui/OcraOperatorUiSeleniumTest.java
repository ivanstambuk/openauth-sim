package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import io.openauth.sim.rest.ui.OcraOperatorUiController.InlineSample;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
final class OcraOperatorUiSeleniumTest {

  private static final String DEFAULT_STORED_SECRET =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String QA_PRESET_KEY = "qa08-s064";
  private static final InlineSample QA_SAMPLE = findInlineSample(QA_PRESET_KEY);
  private static final StoredCredentialScenario QA_STORED_SCENARIO =
      new StoredCredentialScenario(
          "QA08 session credential",
          "operator-demo",
          "OCRA-1:HOTP-SHA256-8:QA08-S064",
          DEFAULT_STORED_SECRET,
          new ChallengeExpectation(ChallengeType.ALPHANUMERIC, 8),
          64,
          false,
          false,
          0,
          null);
  private static final StoredCredentialScenario COUNTER_STORED_SCENARIO =
      new StoredCredentialScenario(
          "Counter + hex challenge credential",
          "operator-counter",
          "OCRA-1:HOTP-SHA256-6:C-QH64",
          DEFAULT_STORED_SECRET,
          new ChallengeExpectation(ChallengeType.HEX, 64),
          0,
          true,
          false,
          0,
          0L);
  private static final StoredCredentialScenario TIMESTAMP_STORED_SCENARIO =
      new StoredCredentialScenario(
          "Timestamp + numeric challenge credential",
          "operator-timestamp",
          "OCRA-1:HOTPT30SHA256-7:QN08",
          DEFAULT_STORED_SECRET,
          new ChallengeExpectation(ChallengeType.NUMERIC, 8),
          0,
          false,
          true,
          30,
          null);
  private static final List<StoredCredentialScenario> STORED_CREDENTIAL_SCENARIOS =
      List.of(QA_STORED_SCENARIO, COUNTER_STORED_SCENARIO, TIMESTAMP_STORED_SCENARIO);
  private static final String CREDENTIAL_ID = "operator-demo";

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
    driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    driver.getWebClient().getOptions().setThrowExceptionOnScriptError(true);
    seedCredential();
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inlinePresetScenarios")
  @DisplayName("Inline policy presets populate fields and evaluate successfully")
  void inlinePolicyPresetEvaluatesSuccessfully(InlinePresetScenario scenario) {
    navigateToEvaluationConsole();
    InlineSample sample = scenario.sample();

    WebElement credentialSection = driver.findElement(By.id("credential-parameters"));
    assertThat(credentialSection.getAttribute("hidden")).isNotNull();

    Select presetSelect = new Select(driver.findElement(By.id("policyPreset")));
    presetSelect.selectByValue(scenario.presetKey());
    ((JavascriptExecutor) driver)
        .executeScript("window.__ocraApplyPreset(arguments[0]);", scenario.presetKey());
    waitForBackgroundJavaScript();

    assertValueWithWait(By.id("suite"), sample.getSuite());
    assertValueWithWait(By.id("sharedSecretHex"), sample.getSharedSecretHex());
    assertValueWithWait(By.id("challenge"), sample.getChallenge());
    assertValueWithWait(By.id("clientChallenge"), sample.getClientChallenge());
    assertValueWithWait(By.id("serverChallenge"), sample.getServerChallenge());
    assertValueWithWait(By.id("sessionHex"), sample.getSessionHex());
    waitForBackgroundJavaScript();
    String initialTimestamp = fieldValue("timestampHex");
    if (scenario.requiresDynamicTimestamp()) {
      assertTimestampNearCurrentEpoch(initialTimestamp);
      assertThat(initialTimestamp)
          .as("Dynamic timestamp presets should not reuse static vector value")
          .isNotEqualToIgnoringCase(sample.getTimestampHex());
    } else {
      String expectedTimestamp = sample.getTimestampHex() == null ? "" : sample.getTimestampHex();
      assertThat(initialTimestamp).isEqualTo(expectedTimestamp);
    }
    assertValueWithWait(By.id("pinHashHex"), sample.getPinHashHex());
    assertValueWithWait(By.id("counter"), scenario.expectedCounterAsString());

    driver.findElement(By.cssSelector("button[data-testid='ocra-evaluate-button']")).click();

    WebDriverWait panelWait = new WebDriverWait(driver, Duration.ofSeconds(5));
    panelWait.until(
        webDriver -> {
          WebElement result =
              webDriver.findElement(By.cssSelector("[data-testid='ocra-result-panel']"));
          WebElement error =
              webDriver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
          boolean resultVisible = result.getAttribute("hidden") == null;
          boolean errorVisible = error.getAttribute("hidden") == null;
          return resultVisible || errorVisible;
        });

    WebElement resultPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-result-panel']"));
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));

    String submittedTimestamp = fieldValue("timestampHex");
    if (scenario.requiresDynamicTimestamp()) {
      assertTimestampNearCurrentEpoch(submittedTimestamp);
    }

    if (resultPanel.getAttribute("hidden") != null) {
      String errorText = errorPanel.getText();
      throw new AssertionError(
          "Evaluation failed for preset "
              + scenario.presetKey()
              + " with error panel text: "
              + (errorText == null ? "" : errorText));
    }

    WebElement otpElement = resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp']"));
    String expectedOtp = computeExpectedOtp(sample, submittedTimestamp);
    if (!scenario.requiresDynamicTimestamp() && sample.getExpectedOtp() != null) {
      assertThat(expectedOtp).isEqualTo(sample.getExpectedOtp());
    }
    assertThat(otpElement.getText()).contains(expectedOtp);
    WebElement statusBadge =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-status-value']"));
    assertStatusBadge(statusBadge);
    assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-sanitized-flag']")))
        .as("Sanitized row should be removed from evaluation results")
        .isEmpty();
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
    assertValueWithWait(By.id("sharedSecretHex"), sample.getSharedSecretHex());
    assertThat(credentialSection.getAttribute("hidden")).isNotNull();
  }

  @Test
  @DisplayName("Evaluate button label reflects selected mode")
  void evaluateButtonLabelReflectsSelectedMode() {
    navigateToEvaluationConsole();

    By evaluateButtonLocator = By.cssSelector("button[data-testid='ocra-evaluate-button']");
    assertButtonLabel(evaluateButtonLocator, "Evaluate inline parameters");

    driver.findElement(By.id("mode-credential")).click();
    waitForBackgroundJavaScript();
    waitForCredentialOptions();
    assertButtonLabel(evaluateButtonLocator, "Evaluate stored credential");

    driver.findElement(By.id("mode-inline")).click();
    waitForBackgroundJavaScript();
    assertButtonLabel(evaluateButtonLocator, "Evaluate inline parameters");
  }

  @Test
  @DisplayName(
      "Timestamp auto-fill toggle defaults on, resets to current step, and restores preset value when disabled")
  void timestampAutofillToggleControlsTimestamp() {
    navigateToEvaluationConsole();
    InlineSample t1mSample = findInlineSample("qn08-t1m");

    WebElement advancedToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-advanced-toggle']"));
    advancedToggle.click();
    waitForBackgroundJavaScript();

    WebElement toggle =
        driver.findElement(By.cssSelector("input[data-testid='timestamp-autofill-toggle']"));
    assertThat(toggle.isSelected()).isTrue();

    WebElement resetButton =
        driver.findElement(By.cssSelector("button[data-testid='timestamp-reset-button']"));
    assertThat(resetButton.isEnabled()).isTrue();

    Select presetSelect = new Select(driver.findElement(By.id("policyPreset")));
    presetSelect.selectByValue("qn08-t1m");
    ((JavascriptExecutor) driver)
        .executeScript("window.__ocraApplyPreset(arguments[0]);", "qn08-t1m");
    waitForBackgroundJavaScript();

    String initialTimestamp = fieldValue("timestampHex");
    assertTimestampNearCurrentEpoch(initialTimestamp);

    setFieldValue("timestampHex", "DEADBEEF");
    resetButton.click();
    waitForBackgroundJavaScript();

    String afterReset = fieldValue("timestampHex");
    assertTimestampNearCurrentEpoch(afterReset);
    assertThat(afterReset).isNotEqualToIgnoringCase("DEADBEEF");

    toggle.click();
    waitForBackgroundJavaScript();
    assertThat(toggle.isSelected()).isFalse();
    assertThat(resetButton.isEnabled()).isFalse();

    ((JavascriptExecutor) driver)
        .executeScript("window.__ocraApplyPreset(arguments[0]);", "qn08-t1m");
    waitForBackgroundJavaScript();
    assertThat(fieldValue("timestampHex")).isEqualTo(t1mSample.getTimestampHex());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("storedCredentialAutoPopulateScenarios")
  @DisplayName("Stored credential auto-populate fills required inputs and yields success")
  void storedCredentialAutoPopulateFillsRequiredInputs(
      String description, StoredCredentialScenario scenario) {
    navigateToEvaluationConsole();

    // Seed inline inputs so we can verify they are overridden or cleared.
    driver.findElement(By.id("challenge")).sendKeys("PLACEHOLDER");

    WebElement advancedToggle =
        driver.findElement(By.cssSelector("button[data-testid='ocra-advanced-toggle']"));
    if ("false".equals(advancedToggle.getAttribute("aria-expanded"))) {
      advancedToggle.click();
      waitForBackgroundJavaScript();
    }
    driver.findElement(By.id("sessionHex")).sendKeys("DEADBEEF");
    driver.findElement(By.id("timestampHex")).sendKeys("FEEDFACE");
    driver.findElement(By.id("pinHashHex")).sendKeys("CAFEBABE");
    driver.findElement(By.id("clientChallenge")).sendKeys("CLIENT");
    driver.findElement(By.id("serverChallenge")).sendKeys("SERVER");
    driver.findElement(By.id("counter")).sendKeys("999");

    driver.findElement(By.id("mode-credential")).click();
    waitForBackgroundJavaScript();
    waitForElementEnabled(By.id("credentialId"));
    waitForCredentialOptions();

    Select credentialDropdown = new Select(driver.findElement(By.id("credentialId")));
    credentialDropdown.selectByValue(scenario.credentialId());

    waitForBackgroundJavaScript();
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              WebElement suiteField = d.findElement(By.id("suite"));
              String value = suiteField.getAttribute("value");
              return value != null && value.equals(scenario.suite());
            });

    String challengeValue = fieldValue("challenge");
    scenario.challengeExpectation().assertMatches(challengeValue);

    String sessionValue = fieldValue("sessionHex");
    if (scenario.sessionLengthBytes() > 0) {
      assertThat(sessionValue)
          .hasSize(scenario.sessionHexLength())
          .as("session hex must be uppercase hexadecimal")
          .matches("[0-9A-F]+");
    } else {
      assertThat(sessionValue).isBlank();
    }

    String counterValue = fieldValue("counter");
    if (scenario.counterRequired()) {
      assertThat(counterValue).isNotBlank();
      assertThat(Long.parseLong(counterValue)).isGreaterThanOrEqualTo(0L);
    } else {
      assertThat(counterValue).isBlank();
    }

    String timestampValue = fieldValue("timestampHex");
    if (scenario.timestampRequired()) {
      assertThat(timestampValue)
          .isNotBlank()
          .as("timestamp must be hexadecimal")
          .matches("[0-9A-F]+");
      assertTimestampMatchesStep(timestampValue, scenario.timestampStepSeconds());
    } else {
      assertThat(timestampValue).isBlank();
    }

    assertThat(fieldValue("clientChallenge")).isBlank();
    assertThat(fieldValue("serverChallenge")).isBlank();
    assertThat(fieldValue("pinHashHex")).isBlank();
    assertThat(isDisabled("clientChallenge")).isTrue();
    assertThat(isDisabled("serverChallenge")).isTrue();
    assertDisabledStyling("clientChallenge");
    assertDisabledStyling("serverChallenge");

    WebElement advancedPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-advanced-panel']"));
    if (scenario.requiresAdvanced()) {
      assertThat(advancedPanel.getAttribute("data-open")).isEqualTo("true");
    } else {
      assertThat(advancedPanel.getAttribute("data-open")).isEqualTo("false");
    }

    driver.findElement(By.cssSelector("button[data-testid='ocra-evaluate-button']")).click();
    waitForBackgroundJavaScript();

    WebElement resultPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-result-panel']"));
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
    boolean resultVisible = resultPanel.getAttribute("hidden") == null;
    boolean errorVisible = errorPanel.getAttribute("hidden") == null;

    assertThat(errorVisible)
        .as(
            "expected stored credential evaluation to succeed but received error panel: %s",
            errorPanel.getText())
        .isFalse();
    assertThat(resultVisible).isTrue();

    String otpText =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp-value']")).getText();
    assertThat(otpText).isNotBlank().matches("\\d{4,10}");
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
  }

  @ParameterizedTest(name = "{0} disables unsupported parameters on selection")
  @MethodSource("storedCredentialAutoPopulateScenarios")
  @DisplayName("Stored credential selection disables unsupported request parameters")
  void storedCredentialSelectionDisablesUnsupportedParameters(
      String description, StoredCredentialScenario scenario) {
    navigateToEvaluationConsole();

    driver.findElement(By.id("mode-credential")).click();
    waitForBackgroundJavaScript();
    waitForElementEnabled(By.id("credentialId"));
    waitForCredentialOptions();

    setFieldValue("challenge", "PLACEHOLDER");
    setFieldValue("sessionHex", "DEADBEEF");
    setFieldValue("timestampHex", "FEEDFACE");
    setFieldValue("counter", "123");

    Select credentialDropdown = new Select(driver.findElement(By.id("credentialId")));
    credentialDropdown.selectByValue(scenario.credentialId());
    waitForBackgroundJavaScript();

    boolean challengeDisabledExpected = scenario.challengeExpectation() == null;
    waitForDisabledState("challenge", challengeDisabledExpected);
    assertThat(isDisabled("challenge")).isEqualTo(challengeDisabledExpected);
    if (challengeDisabledExpected) {
      assertThat(fieldValue("challenge")).isBlank();
    }

    boolean counterDisabledExpected = !scenario.counterRequired();
    waitForDisabledState("counter", counterDisabledExpected);
    assertThat(isDisabled("counter")).isEqualTo(counterDisabledExpected);
    if (counterDisabledExpected) {
      assertThat(fieldValue("counter")).isBlank();
    }

    boolean sessionDisabledExpected = scenario.sessionLengthBytes() <= 0;
    waitForDisabledState("sessionHex", sessionDisabledExpected);
    assertThat(isDisabled("sessionHex")).isEqualTo(sessionDisabledExpected);
    if (sessionDisabledExpected) {
      assertThat(fieldValue("sessionHex")).isBlank();
    }

    boolean timestampDisabledExpected = !scenario.timestampRequired();
    waitForDisabledState("timestampHex", timestampDisabledExpected);
    assertThat(isDisabled("timestampHex")).isEqualTo(timestampDisabledExpected);
    if (timestampDisabledExpected) {
      assertThat(fieldValue("timestampHex")).isBlank();
    }

    assertThat(isDisabled("clientChallenge")).isTrue();
    assertThat(isDisabled("serverChallenge")).isTrue();
    assertDisabledStyling("clientChallenge");
    assertDisabledStyling("serverChallenge");
  }

  @Test
  @DisplayName("Stored credential flow succeeds via Selenium driver")
  void storedCredentialEvaluationSucceeds() {
    navigateToEvaluationConsole();

    WebElement credentialSection = driver.findElement(By.id("credential-parameters"));
    assertThat(credentialSection.getAttribute("hidden")).isNotNull();

    driver.findElement(By.id("mode-credential")).click();
    waitForBackgroundJavaScript();
    waitForElementEnabled(By.id("credentialId"));
    assertThat(credentialSection.getAttribute("hidden")).isNull();

    waitForCredentialOptions();
    Select credentialDropdown = new Select(driver.findElement(By.id("credentialId")));
    credentialDropdown.selectByValue(CREDENTIAL_ID);
    waitForBackgroundJavaScript();
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              WebElement suiteField = d.findElement(By.id("suite"));
              return QA_STORED_SCENARIO.suite().equals(suiteField.getAttribute("value"));
            });

    WebElement challengeInput = driver.findElement(By.id("challenge"));
    challengeInput.clear();
    challengeInput.sendKeys(QA_SAMPLE.getChallenge());

    WebElement advancedToggle =
        driver.findElement(By.cssSelector("button[data-testid='ocra-advanced-toggle']"));
    WebElement advancedPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-advanced-panel']"));
    if (!"true".equals(advancedPanel.getAttribute("data-open"))) {
      advancedToggle.click();
      waitForBackgroundJavaScript();
    }

    WebElement sessionInput = driver.findElement(By.id("sessionHex"));
    sessionInput.clear();
    sessionInput.sendKeys(QA_SAMPLE.getSessionHex());

    driver.findElement(By.cssSelector("button[data-testid='ocra-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-result-panel']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement otpElement = resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp']"));
    assertThat(otpElement.getText()).contains(QA_SAMPLE.getExpectedOtp());
    WebElement metadata =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-telemetry-summary']"));
    WebElement statusBadge =
        metadata.findElement(By.cssSelector("[data-testid='ocra-status-value']"));
    assertStatusBadge(statusBadge);
    assertThat(metadata.findElements(By.cssSelector(".result-row")))
        .as("Evaluation metadata should render one row per entry")
        .hasSize(1)
        .allSatisfy(
            row ->
                assertThat(row.findElements(By.cssSelector("dt, dd")))
                    .as("Result row should contain a dt label and a dd value")
                    .hasSize(2));
    assertThat(metadata.getText()).doesNotContain("Sanitized");
    assertThat(metadata.getText()).doesNotContain("Suite");
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
  }

  private void assertStatusBadge(WebElement badge) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(d -> "success".equalsIgnoreCase(badge.getText().trim()));
    assertThat(badge.getText().trim()).isEqualToIgnoringCase("Success");
    assertThat(badge.getAttribute("class")).contains("status-badge--success");
  }

  @Test
  @DisplayName("Advanced parameters disclosure toggles visibility")
  void advancedParametersDisclosureTogglesVisibility() {
    navigateToEvaluationConsole();

    WebElement parametersStack =
        driver.findElement(By.cssSelector(".request-parameters .stack-sm"));
    WebElement advancedPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-advanced-panel']"));
    WebElement advancedToggle =
        driver.findElement(By.cssSelector("button[data-testid='ocra-advanced-toggle']"));

    Object immediateSiblingId =
        ((JavascriptExecutor) driver)
            .executeScript(
                "return arguments[0].nextElementSibling && arguments[0].nextElementSibling.id;",
                parametersStack);
    assertThat(immediateSiblingId).isEqualTo("advanced-parameters");

    assertThat(advancedPanel.isDisplayed()).isFalse();

    advancedToggle.click();
    waitForBackgroundJavaScript();
    assertThat(advancedPanel.isDisplayed()).isTrue();

    advancedToggle.click();
    waitForBackgroundJavaScript();
    assertThat(advancedPanel.isDisplayed()).isFalse();
  }

  @Test
  @DisplayName("Inline policy builder applies suite and generated secret")
  void inlinePolicyBuilderPopulatesSuiteAndSecret() {
    navigateToEvaluationConsole();

    WebElement versionField = driver.findElement(By.id("builderVersion"));
    assertThat(versionField.getAttribute("value")).isEqualTo("OCRA-1");
    assertThat(versionField.getAttribute("readonly")).isNotNull();
    assertThat(versionField.getAttribute("aria-readonly")).isEqualTo("true");

    WebElement builderContainer = driver.findElement(By.cssSelector(".policy-builder"));
    assertThat(builderContainer.getText())
        .doesNotContain("OCRA-1 is the only version defined in the OATH specification.");

    WebElement builderPreview =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-builder-preview']")));
    waitForBackgroundJavaScript();
    String previewText = builderPreview.getText();
    assertThat(previewText).isNotBlank();

    WebElement applyButton =
        driver.findElement(By.cssSelector("button[data-testid='ocra-builder-apply']"));
    applyButton.click();
    waitForBackgroundJavaScript();
    assertValueWithWait(By.id("suite"), previewText);

    WebElement sessionLengthField = driver.findElement(By.id("builderSessionLength"));
    sessionLengthField.clear();
    sessionLengthField.sendKeys("7");
    waitForBackgroundJavaScript();
    assertThat(applyButton.isEnabled()).isFalse();

    sessionLengthField.clear();
    sessionLengthField.sendKeys("64");
    waitForBackgroundJavaScript();
    assertThat(applyButton.isEnabled()).isTrue();

    driver.findElement(By.cssSelector("button[data-testid='ocra-builder-generate']")).click();
    waitForBackgroundJavaScript();
    WebElement secretPreview =
        driver.findElement(By.cssSelector("[data-testid='ocra-builder-secret-preview']"));
    String generatedSecret = secretPreview.getText();
    assertThat(generatedSecret).isNotBlank().isNotEqualTo("—");

    applyButton.click();
    waitForBackgroundJavaScript();
    assertValueWithWait(By.id("sharedSecretHex"), generatedSecret);
  }

  @Test
  @DisplayName("Builder selects use compact styling class and height")
  void builderSelectsUseCompactStyling() {
    navigateToEvaluationConsole();

    List<WebElement> selects =
        List.of(
            driver.findElement(By.id("builderAlgorithm")),
            driver.findElement(By.id("builderDigits")),
            driver.findElement(By.id("builderChallenge")));

    selects.forEach(
        element -> assertThat(element.getAttribute("class")).contains("select-compact"));

    JavascriptExecutor executor = (JavascriptExecutor) driver;
    selects.forEach(
        element -> {
          Number height =
              (Number)
                  executor.executeScript(
                      "return arguments[0].getBoundingClientRect().height;", element);
          assertThat(height.doubleValue()).isLessThanOrEqualTo(48.0d);
          assertThat(height.doubleValue()).isGreaterThanOrEqualTo(32.0d);
        });
  }

  @Test
  @DisplayName("Session checkbox uses compact styling container")
  void sessionCheckboxUsesCompactStyling() {
    navigateToEvaluationConsole();

    WebElement sessionLabel = driver.findElement(By.cssSelector("label[for='builderSession']"));
    assertThat(sessionLabel.getAttribute("class")).contains("choice-control");

    JavascriptExecutor executor = (JavascriptExecutor) driver;
    Number height =
        (Number)
            executor.executeScript(
                "return arguments[0].getBoundingClientRect().height;", sessionLabel);
    assertThat(height.doubleValue()).isLessThanOrEqualTo(48.0d);
    assertThat(height.doubleValue()).isGreaterThanOrEqualTo(32.0d);
  }

  @Test
  @DisplayName("Inline data inputs adopt grid layout and accent styling")
  void inlineCheckboxesAdoptGridLayoutAndAccentStyling() {
    navigateToEvaluationConsole();

    WebElement choiceContainer =
        driver.findElement(By.cssSelector("[data-testid='ocra-builder-data-inputs']"));
    assertThat(choiceContainer.getAttribute("class")).contains("choice-grid");

    JavascriptExecutor executor = (JavascriptExecutor) driver;
    String display =
        (String)
            executor.executeScript(
                "return window.getComputedStyle(arguments[0]).getPropertyValue('display');",
                choiceContainer);
    assertThat(display).isEqualTo("grid");

    String templateColumns =
        (String)
            executor.executeScript(
                "return window.getComputedStyle(arguments[0]).getPropertyValue('grid-template-columns');",
                choiceContainer);
    assertThat(templateColumns).contains("minmax");

    WebElement counterCheckbox = driver.findElement(By.id("builderCounter"));
    String accentColor =
        (String)
            executor.executeScript(
                "return window.getComputedStyle(arguments[0]).getPropertyValue('accent-color');",
                counterCheckbox);
    assertThat(accentColor).isNotBlank();
    assertThat(accentColor).containsAnyOf("22, 211, 243", "var(--ocra-color-primary-accent)");

    Number checkboxWidth =
        (Number)
            executor.executeScript(
                "return arguments[0].getBoundingClientRect().width;", counterCheckbox);
    Number checkboxHeight =
        (Number)
            executor.executeScript(
                "return arguments[0].getBoundingClientRect().height;", counterCheckbox);
    assertThat(checkboxWidth.doubleValue()).isGreaterThanOrEqualTo(18.0d);
    assertThat(checkboxHeight.doubleValue()).isGreaterThanOrEqualTo(18.0d);
  }

  @Test
  @DisplayName("Inline preset labels share typography across OCRA and HOTP tabs")
  void inlinePresetLabelTypographyMatchesAcrossTabs() {
    navigateToEvaluationConsole();

    WebElement evaluateLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(
                        "[data-testid='ocra-evaluate-panel'] label[for='policyPreset']")));
    int evaluateFontWeight = normalizeFontWeight(evaluateLabel.getCssValue("font-weight"));

    WebElement replayToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='ocra-mode-select-replay']")));
    replayToggle.click();
    waitForBackgroundJavaScript();
    waitForPanelVisibility(
        By.cssSelector("[data-testid='ocra-evaluate-panel']"), PanelVisibility.HIDDEN);
    waitForPanelVisibility(
        By.cssSelector("[data-testid='ocra-replay-panel']"), PanelVisibility.VISIBLE);

    WebElement replayLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(
                        "[data-testid='ocra-replay-panel'] label[for='replayPolicyPreset']")));
    int replayFontWeight = normalizeFontWeight(replayLabel.getCssValue("font-weight"));
    assertThat(evaluateFontWeight)
        .as("Expected OCRA evaluate inline preset label weight to match replay tab")
        .isEqualTo(replayFontWeight);

    driver.get(baseUrl("/ui/console?protocol=hotp"));
    WebElement hotpTab =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='protocol-tab-hotp']")));
    hotpTab.click();
    waitForBackgroundJavaScript();

    WebElement hotpInlineLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(
                        "[data-testid='hotp-inline-preset'] label[for='hotpInlinePreset']")));
    int hotpFontWeight = normalizeFontWeight(hotpInlineLabel.getCssValue("font-weight"));
    assertThat(evaluateFontWeight)
        .as("Expected OCRA evaluate inline preset label weight to match HOTP inline preset label")
        .isEqualTo(hotpFontWeight);
  }

  @Test
  @DisplayName("OCRA evaluate sample vector baseline matches replay tab")
  void sampleVectorSpacingMatchesReplay() {
    navigateToEvaluationConsole();

    WebElement evaluateLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(
                        "[data-testid='ocra-evaluate-panel'] label[for='policyPreset']")));
    WebElement evaluateSelect =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-evaluate-panel'] select#policyPreset")));
    assertThat(evaluateSelect.getTagName()).isEqualTo("select");
    Select evaluateSelectControl = new Select(evaluateSelect);
    assertThat(evaluateSelectControl.getOptions())
        .as("Evaluate preset dropdown should expose at least the placeholder and sample vectors")
        .hasSizeGreaterThan(1);
    double evaluateHeadingOffset =
        topOf(evaluateLabel)
            - topOf(
                new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(
                        ExpectedConditions.visibilityOfElementLocated(
                            By.cssSelector("[data-testid='ocra-evaluate-panel'] .section-title"))));
    WebElement replayToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='ocra-mode-select-replay']")));
    replayToggle.click();
    waitForBackgroundJavaScript();
    waitForPanelVisibility(
        By.cssSelector("[data-testid='ocra-evaluate-panel']"), PanelVisibility.HIDDEN);
    waitForPanelVisibility(
        By.cssSelector("[data-testid='ocra-replay-panel']"), PanelVisibility.VISIBLE);

    WebElement replayLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector(
                        "[data-testid='ocra-replay-panel'] label[for='replayPolicyPreset']")));
    WebElement replaySelect =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-replay-panel'] select#replayPolicyPreset")));
    assertThat(replaySelect.isDisplayed()).isTrue();
    Select replaySelectControl = new Select(replaySelect);
    assertThat(replaySelectControl.getOptions())
        .as("Replay preset dropdown should mirror evaluate options")
        .hasSize(evaluateSelectControl.getOptions().size());
    WebElement replayHeading =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-replay-panel'] .section-title")));
    double replayHeadingOffset = topOf(replayLabel) - topOf(replayHeading);

    if (evaluateHeadingOffset <= 0.0d) {
      throw new AssertionError(
          "Expected OCRA evaluate sample preset label to appear below the section heading, but offset was "
              + evaluateHeadingOffset
              + "px");
    }
    if (replayHeadingOffset <= 0.0d) {
      throw new AssertionError(
          "Expected OCRA replay sample preset label to appear below the section heading, but offset was "
              + replayHeadingOffset
              + "px");
    }

    double delta = Math.abs(evaluateHeadingOffset - replayHeadingOffset);
    if (delta > 1.0d) {
      throw new AssertionError(
          "Expected OCRA evaluate sample vector spacing to align with replay baseline within 1px but delta was "
              + delta
              + "px");
    }
  }

  private static InlineSample findInlineSample(String presetKey) {
    return OcraOperatorSampleData.policyPresets().stream()
        .filter(preset -> preset.getKey().equals(presetKey))
        .map(OcraOperatorUiController.PolicyPreset::getSample)
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Missing inline preset for key: " + presetKey));
  }

  private void assertTimestampNearCurrentEpoch(String timestampHex) {
    assertThat(timestampHex).isNotBlank();
    String normalized = timestampHex.trim();
    if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
      normalized = normalized.substring(2);
    }
    long parsed = Long.parseUnsignedLong(normalized, 16);
    long nowSeconds = Instant.now().getEpochSecond();
    long deltaSeconds = Math.abs(parsed - nowSeconds);
    assertThat(deltaSeconds)
        .as("Expected timestamp to match the current Unix second within a 5s window")
        .isLessThanOrEqualTo(5L);
  }

  private void assertTimestampMatchesStep(String timestampHex, long stepSeconds) {
    assertThat(timestampHex).isNotBlank();
    String normalized = timestampHex.trim();
    if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
      normalized = normalized.substring(2);
    }
    long parsed = Long.parseUnsignedLong(normalized, 16);
    long step = Math.max(stepSeconds, 1L);
    long expectedStep = Instant.now().getEpochSecond() / step;
    long deltaSteps = Math.abs(parsed - expectedStep);
    assertThat(deltaSteps)
        .as("Expected timestamp to align with current time-step counter")
        .isLessThanOrEqualTo(1L);
  }

  private String computeExpectedOtp(InlineSample sample, String timestampHexOverride) {
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            "inline-" + sample.getSuite(),
            sample.getSuite(),
            sample.getSharedSecretHex(),
            SecretEncoding.HEX,
            sample.getCounter(),
            sample.getPinHashHex(),
            null,
            Map.of("source", "inline-sample"));
    OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
    String timestamp =
        timestampHexOverride != null ? timestampHexOverride : sample.getTimestampHex();
    OcraResponseCalculator.OcraExecutionContext context =
        new OcraResponseCalculator.OcraExecutionContext(
            sample.getCounter(),
            sample.getChallenge(),
            sample.getSessionHex(),
            sample.getClientChallenge(),
            sample.getServerChallenge(),
            sample.getPinHashHex(),
            timestamp);
    return OcraResponseCalculator.generate(descriptor, context);
  }

  private static Stream<InlinePresetScenario> inlinePresetScenarios() {
    return OcraOperatorSampleData.policyPresets().stream()
        .map(
            preset ->
                new InlinePresetScenario(preset.getLabel(), preset.getKey(), preset.getSample()));
  }

  private static Stream<Arguments> storedCredentialAutoPopulateScenarios() {
    return STORED_CREDENTIAL_SCENARIOS.stream()
        .map(scenario -> Arguments.of(scenario.description(), scenario));
  }

  private record InlinePresetScenario(String description, String presetKey, InlineSample sample) {

    @Override
    public String toString() {
      return description;
    }

    String expectedCounterAsString() {
      Long counter = sample.getCounter();
      return counter == null ? "" : Long.toString(counter);
    }

    boolean requiresDynamicTimestamp() {
      return "qn08-t1m".equals(presetKey) || "qa10-t1m".equals(presetKey);
    }
  }

  private record StoredCredentialScenario(
      String description,
      String credentialId,
      String suite,
      String secretHex,
      ChallengeExpectation challengeExpectation,
      int sessionLengthBytes,
      boolean counterRequired,
      boolean timestampRequired,
      long timestampStepSeconds,
      Long counterValue) {

    @Override
    public String toString() {
      return description;
    }

    int sessionHexLength() {
      return sessionLengthBytes * 2;
    }

    boolean requiresAdvanced() {
      return sessionLengthBytes > 0 || timestampRequired;
    }
  }

  private record ChallengeExpectation(ChallengeType type, int length) {

    void assertMatches(String value) {
      assertThat(value).isNotNull();
      assertThat(value.length()).isEqualTo(length);
      switch (type) {
        case NUMERIC -> assertThat(value).as("challenge must be numeric").matches("\\d+");
        case HEX -> assertThat(value).as("challenge must be hexadecimal").matches("[0-9A-F]+");
        case ALPHANUMERIC ->
            assertThat(value)
                .as("challenge must be uppercase alphanumeric characters")
                .matches("[A-Z0-9]+");
      }
    }
  }

  private enum ChallengeType {
    NUMERIC,
    HEX,
    ALPHANUMERIC
  }

  private void seedCredential() {
    STORED_CREDENTIAL_SCENARIOS.forEach(
        scenario -> {
          credentialStore.delete(scenario.credentialId());
          OcraCredentialFactory factory = new OcraCredentialFactory();
          OcraCredentialRequest request =
              new OcraCredentialRequest(
                  scenario.credentialId(),
                  scenario.suite(),
                  scenario.secretHex(),
                  SecretEncoding.HEX,
                  scenario.counterValue(),
                  null,
                  null,
                  Map.of("source", "selenium-test"));
          OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
          Credential credential =
              VersionedCredentialRecordMapper.toCredential(
                  new OcraCredentialPersistenceAdapter().serialize(descriptor));
          credentialStore.save(credential);
          assertThat(credentialStore.exists(scenario.credentialId())).isTrue();
        });
  }

  private void navigateToEvaluationConsole() {
    driver.get(baseUrl("/ui/console"));
    WebElement modeToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='ocra-mode-toggle']")));
    waitForPresetScripts();

    if (!"evaluate".equals(modeToggle.getAttribute("data-mode"))) {
      driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-evaluate']")).click();
      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(d -> "evaluate".equals(modeToggle.getAttribute("data-mode")));
    }

    waitForPanelVisibility(
        By.cssSelector("[data-testid='ocra-evaluate-panel']"), PanelVisibility.VISIBLE);
    waitForPanelVisibility(
        By.cssSelector("[data-testid='ocra-replay-panel']"), PanelVisibility.HIDDEN);
    waitForPresetScripts();
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  private void waitForElementEnabled(By locator) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.elementToBeClickable(locator));
  }

  private void waitForBackgroundJavaScript() {
    driver.getWebClient().waitForBackgroundJavaScript(Duration.ofSeconds(2).toMillis());
  }

  private void waitForPanelVisibility(By locator, PanelVisibility expectedVisibility) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              WebElement element = d.findElement(locator);
              boolean hidden = element.getAttribute("hidden") != null;
              return expectedVisibility == PanelVisibility.VISIBLE ? !hidden : hidden;
            });
  }

  private void assertButtonLabel(By locator, String expectedText) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              WebElement element = d.findElement(locator);
              return expectedText.equals(element.getText().trim());
            });
    assertThat(driver.findElement(locator).getText().trim()).isEqualTo(expectedText);
  }

  private void assertValueWithWait(By locator, String expected) {
    String expectedValue = expected == null ? "" : expected;
    String elementId = driver.findElement(locator).getAttribute("id");
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              Object value =
                  ((JavascriptExecutor) d)
                      .executeScript(
                          "return document.getElementById(arguments[0]).value;", elementId);
              return expectedValue.equals(value);
            });
    assertThat(driver.findElement(locator).getAttribute("value")).isEqualTo(expectedValue);
  }

  private String fieldValue(String elementId) {
    Object value =
        ((JavascriptExecutor) driver)
            .executeScript(
                "var el = document.getElementById(arguments[0]);"
                    + " return el ? (el.value || '') : '';",
                elementId);
    return value == null ? "" : value.toString();
  }

  private void setFieldValue(String elementId, String value) {
    ((JavascriptExecutor) driver)
        .executeScript(
            "var el = document.getElementById(arguments[0]);"
                + " if (el) { el.value = arguments[1] || ''; }",
            elementId,
            value);
  }

  private boolean isDisabled(String elementId) {
    Object disabled =
        ((JavascriptExecutor) driver)
            .executeScript(
                "var el = document.getElementById(arguments[0]);"
                    + " return el ? !!el.disabled : false;",
                elementId);
    return Boolean.TRUE.equals(disabled);
  }

  private String normalizeColor(String color) {
    if (color == null) {
      return "";
    }
    String trimmed = color.trim().toLowerCase();
    if (trimmed.startsWith("#")) {
      String hex = trimmed.substring(1);
      if (hex.length() == 3) {
        StringBuilder expanded = new StringBuilder();
        for (char c : hex.toCharArray()) {
          expanded.append(c).append(c);
        }
        hex = expanded.toString();
      }
      int value = Integer.parseInt(hex, 16);
      int r = (value >> 16) & 0xFF;
      int g = (value >> 8) & 0xFF;
      int b = value & 0xFF;
      return String.format("rgb(%d,%d,%d)", r, g, b);
    }
    return trimmed.replaceAll("\\s+", "");
  }

  private int normalizeFontWeight(String fontWeight) {
    if (fontWeight == null || fontWeight.isBlank()) {
      return 0;
    }
    String normalized = fontWeight.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "normal" -> 400;
      case "bold" -> 700;
      default -> {
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
          yield 0;
        }
        yield Integer.parseInt(digits);
      }
    };
  }

  private void waitForDisabledState(String elementId, boolean expectedDisabled) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(d -> isDisabled(elementId) == expectedDisabled);
  }

  private double topOf(WebElement element) {
    Object result =
        ((JavascriptExecutor) driver)
            .executeScript("return arguments[0].getBoundingClientRect().top;", element);
    if (result instanceof Number number) {
      return number.doubleValue();
    }
    throw new AssertionError("Expected numeric bounding top but received: " + result);
  }

  private enum PanelVisibility {
    VISIBLE,
    HIDDEN
  }

  private void assertDisabledStyling(String elementId) {
    JavascriptExecutor executor = (JavascriptExecutor) driver;
    String background =
        (String)
            executor.executeScript(
                "var el = document.getElementById(arguments[0]);"
                    + " return el ? window.getComputedStyle(el).getPropertyValue('background-color') : '';",
                elementId);
    String expectedVar =
        (String)
            executor.executeScript(
                "return getComputedStyle(document.documentElement).getPropertyValue('--ocra-color-surface-disabled');");
    assertThat(normalizeColor(background)).isEqualTo(normalizeColor(expectedVar));
    String cursor =
        (String)
            executor.executeScript(
                "var el = document.getElementById(arguments[0]);"
                    + " return el ? window.getComputedStyle(el).getPropertyValue('cursor') : '';",
                elementId);
    assertThat(cursor).isEqualTo("not-allowed");
  }

  private void waitForPresetScripts() {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d ->
                Boolean.TRUE.equals(
                    ((JavascriptExecutor) d)
                        .executeScript(
                            "return typeof window.__ocraApplyPreset === 'function'"
                                + " && typeof window.__ocraUpdateSections === 'function';")));
  }

  private void waitForCredentialOptions() {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              Object count =
                  ((JavascriptExecutor) d)
                      .executeScript(
                          "var select = document.getElementById('credentialId');"
                              + "return select ? select.options.length : 0;");
              return count instanceof Number && ((Number) count).intValue() > 1;
            });
  }
}
