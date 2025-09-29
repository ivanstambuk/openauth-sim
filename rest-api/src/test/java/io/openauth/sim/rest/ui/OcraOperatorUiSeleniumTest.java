package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

  private static final String QA_PRESET_KEY = "qa08-s064";
  private static final String QA_EXPECTED_SUITE = "OCRA-1:HOTP-SHA256-8:QA08-S064";
  private static final String QA_EXPECTED_CHALLENGE = "SESSION01";
  private static final String QA_EXPECTED_SESSION =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
  private static final String QA_EXPECTED_OTP = "17477202";
  private static final String QH64_PRESET_KEY = "c-qh64";
  private static final String QH64_EXPECTED_SUITE = "OCRA-1:HOTP-SHA256-6:C-QH64";
  private static final String QH64_EXPECTED_CHALLENGE =
      "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF";
  private static final long QH64_EXPECTED_COUNTER = 1L;
  private static final String QH64_EXPECTED_OTP = "429968";
  private static final String INLINE_SHARED_SECRET =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String DEFAULT_STORED_SECRET =
      "3132333435363738393031323334353637383930313233343536373839303132";
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
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

    WebElement credentialSection = driver.findElement(By.id("credential-parameters"));
    assertThat(credentialSection.getAttribute("hidden")).isNotNull();

    Select presetSelect = new Select(driver.findElement(By.id("policyPreset")));
    presetSelect.selectByValue(scenario.presetKey());
    ((JavascriptExecutor) driver)
        .executeScript("window.__ocraApplyPreset(arguments[0]);", scenario.presetKey());
    waitForBackgroundJavaScript();

    assertValueWithWait(By.id("suite"), scenario.expectedSuite());
    assertValueWithWait(By.id("challenge"), scenario.expectedChallenge());
    assertValueWithWait(By.id("sessionHex"), scenario.expectedSessionHex());
    assertValueWithWait(By.id("counter"), scenario.expectedCounterAsString());
    assertValueWithWait(By.id("sharedSecretHex"), scenario.expectedSharedSecretHex());

    driver.findElement(By.cssSelector("button[data-testid='ocra-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-result-panel']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement otpElement = resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp']"));
    assertThat(otpElement.getText()).contains(scenario.expectedOtp());
    WebElement statusValue =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-status-value']"));
    assertThat(statusValue.getText()).isEqualTo("Success");
    WebElement sanitizedValue =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-sanitized-flag']"));
    assertThat(sanitizedValue.getText()).isEqualTo("true");
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
    assertValueWithWait(By.id("sharedSecretHex"), scenario.expectedSharedSecretHex());
    assertThat(credentialSection.getAttribute("hidden")).isNotNull();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("storedCredentialAutoPopulateScenarios")
  @DisplayName("Stored credential auto-populate fills required inputs and yields success")
  void storedCredentialAutoPopulateFillsRequiredInputs(
      String description, StoredCredentialScenario scenario) {
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

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

    WebElement autoFillButton =
        driver.findElement(By.cssSelector("button[data-testid='stored-credential-autofill']"));
    autoFillButton.click();
    waitForBackgroundJavaScript();

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
      long parsedStep = Long.parseUnsignedLong(timestampValue, 16);
      long expectedStep = currentTimeStep(scenario.timestampStepSeconds());
      assertThat(Math.abs(parsedStep - expectedStep)).isLessThanOrEqualTo(1L);
    } else {
      assertThat(timestampValue).isBlank();
    }

    assertThat(fieldValue("clientChallenge")).isBlank();
    assertThat(fieldValue("serverChallenge")).isBlank();
    assertThat(fieldValue("pinHashHex")).isBlank();

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

  @Test
  @DisplayName("Stored credential flow succeeds via Selenium driver")
  void storedCredentialEvaluationSucceeds() {
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

    WebElement credentialSection = driver.findElement(By.id("credential-parameters"));
    assertThat(credentialSection.getAttribute("hidden")).isNotNull();

    driver.findElement(By.id("mode-credential")).click();
    waitForBackgroundJavaScript();
    waitForElementEnabled(By.id("credentialId"));
    assertThat(credentialSection.getAttribute("hidden")).isNull();

    waitForCredentialOptions();
    Select credentialDropdown = new Select(driver.findElement(By.id("credentialId")));
    credentialDropdown.selectByValue(CREDENTIAL_ID);
    driver.findElement(By.id("challenge")).sendKeys(QA_EXPECTED_CHALLENGE);
    driver.findElement(By.cssSelector("button[data-testid='ocra-advanced-toggle']")).click();
    waitForBackgroundJavaScript();
    driver.findElement(By.id("sessionHex")).sendKeys(QA_EXPECTED_SESSION);

    driver.findElement(By.cssSelector("button[data-testid='ocra-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-result-panel']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement otpElement = resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp']"));
    assertThat(otpElement.getText()).contains(QA_EXPECTED_OTP);
    WebElement metadata =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-telemetry-summary']"));
    assertThat(metadata.getText()).contains("Success").contains("true");
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
  }

  @Test
  @DisplayName("Advanced parameters disclosure toggles visibility")
  void advancedParametersDisclosureTogglesVisibility() {
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

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
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

    WebElement versionField = driver.findElement(By.id("builderVersion"));
    assertThat(versionField.getAttribute("value")).isEqualTo("OCRA-1");
    assertThat(versionField.getAttribute("readonly")).isNotNull();
    assertThat(versionField.getAttribute("aria-readonly")).isEqualTo("true");

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
  @DisplayName("Inline data inputs adopt grid layout and accent styling")
  void inlineCheckboxesAdoptGridLayoutAndAccentStyling() {
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

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
    assertThat(accentColor).containsAnyOf("26, 166, 160", "var(--ocra-color-primary-accent)");

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

  private static Stream<InlinePresetScenario> inlinePresetScenarios() {
    return Stream.of(
        new InlinePresetScenario(
            "QA08 S064 preset",
            QA_PRESET_KEY,
            QA_EXPECTED_SUITE,
            QA_EXPECTED_CHALLENGE,
            QA_EXPECTED_SESSION,
            null,
            QA_EXPECTED_OTP,
            INLINE_SHARED_SECRET),
        new InlinePresetScenario(
            "C-QH64 preset",
            QH64_PRESET_KEY,
            QH64_EXPECTED_SUITE,
            QH64_EXPECTED_CHALLENGE,
            null,
            QH64_EXPECTED_COUNTER,
            QH64_EXPECTED_OTP,
            INLINE_SHARED_SECRET));
  }

  private static Stream<Arguments> storedCredentialAutoPopulateScenarios() {
    return STORED_CREDENTIAL_SCENARIOS.stream()
        .map(scenario -> Arguments.of(scenario.description(), scenario));
  }

  private record InlinePresetScenario(
      String description,
      String presetKey,
      String expectedSuite,
      String expectedChallenge,
      String expectedSessionHex,
      Long expectedCounter,
      String expectedOtp,
      String expectedSharedSecretHex) {

    @Override
    public String toString() {
      return description;
    }

    String expectedCounterAsString() {
      return expectedCounter == null ? "" : Long.toString(expectedCounter);
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

  private long currentTimeStep(long stepSeconds) {
    if (stepSeconds <= 0) {
      return 0L;
    }
    long epochSeconds = Instant.now().getEpochSecond();
    return epochSeconds / stepSeconds;
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
