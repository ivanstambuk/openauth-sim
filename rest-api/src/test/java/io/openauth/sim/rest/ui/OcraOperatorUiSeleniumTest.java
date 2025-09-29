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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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
  }

  @Test
  @DisplayName("Stored credential flow succeeds via Selenium driver")
  void storedCredentialEvaluationSucceeds() {
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

    driver.findElement(By.id("mode-credential")).click();
    waitForBackgroundJavaScript();
    waitForElementEnabled(By.id("credentialId"));

    driver.findElement(By.id("credentialId")).sendKeys(CREDENTIAL_ID);
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

  private void seedCredential() {
    credentialStore.delete(CREDENTIAL_ID);
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            CREDENTIAL_ID,
            QA_EXPECTED_SUITE,
            "3132333435363738393031323334353637383930313233343536373839303132",
            SecretEncoding.HEX,
            null,
            null,
            null,
            Map.of("source", "selenium-test"));
    OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(
            new OcraCredentialPersistenceAdapter().serialize(descriptor));
    credentialStore.save(credential);
    assertThat(credentialStore.exists(CREDENTIAL_ID)).isTrue();
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
}
