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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  private static final String INLINE_PRESET_KEY = "qa08-s064";
  private static final String EXPECTED_SUITE = "OCRA-1:HOTP-SHA256-8:QA08-S064";
  private static final String EXPECTED_INLINE_CHALLENGE = "SESSION01";
  private static final String EXPECTED_SESSION =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
  private static final String EXPECTED_OTP = "17477202";
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

  @Test
  @DisplayName("Inline policy preset populates fields and evaluates successfully")
  void inlinePolicyPresetEvaluatesSuccessfully() {
    driver.get(baseUrl("/ui/ocra/evaluate"));
    waitForPresetScripts();

    Select presetSelect = new Select(driver.findElement(By.id("policyPreset")));
    presetSelect.selectByValue(INLINE_PRESET_KEY);
    ((JavascriptExecutor) driver)
        .executeScript("window.__ocraApplyPreset(arguments[0]);", INLINE_PRESET_KEY);
    waitForBackgroundJavaScript();

    assertValueWithWait(By.id("suite"), EXPECTED_SUITE);
    assertValueWithWait(By.id("challenge"), EXPECTED_INLINE_CHALLENGE);
    assertValueWithWait(By.id("sessionHex"), EXPECTED_SESSION);

    driver.findElement(By.cssSelector("form[data-testid='ocra-evaluate-form']")).submit();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-result-panel']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement otpElement = resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp']"));
    assertThat(otpElement.getText()).contains(EXPECTED_OTP);
    WebElement reasonCode =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-reason-code']"));
    assertThat(reasonCode.getText()).isEqualTo("success");
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
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
    driver.findElement(By.id("challenge")).sendKeys(EXPECTED_INLINE_CHALLENGE);
    driver.findElement(By.id("sessionHex")).sendKeys(EXPECTED_SESSION);

    driver.findElement(By.cssSelector("form[data-testid='ocra-evaluate-form']")).submit();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-result-panel']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement otpElement = resultPanel.findElement(By.cssSelector("[data-testid='ocra-otp']"));
    assertThat(otpElement.getText()).contains(EXPECTED_OTP);
    WebElement telemetryBlock =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-telemetry-summary']"));
    assertThat(telemetryBlock.getText()).contains("success").contains("true");
    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-error-panel']"));
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
  }

  private void seedCredential() {
    credentialStore.delete(CREDENTIAL_ID);
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            CREDENTIAL_ID,
            EXPECTED_SUITE,
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
    String elementId = driver.findElement(locator).getAttribute("id");
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              Object value =
                  ((JavascriptExecutor) d)
                      .executeScript(
                          "return document.getElementById(arguments[0]).value;", elementId);
              return expected.equals(value);
            });
    assertThat(driver.findElement(locator).getAttribute("value")).isEqualTo(expected);
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
