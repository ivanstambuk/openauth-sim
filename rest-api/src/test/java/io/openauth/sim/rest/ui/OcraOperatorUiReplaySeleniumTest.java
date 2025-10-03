package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
final class OcraOperatorUiReplaySeleniumTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
  private static final String STORED_CREDENTIAL_ID = "operator-demo";
  private static final String STORED_SUITE = "OCRA-1:HOTP-SHA256-8:QA08-S064";
  private static final String STORED_SECRET_HEX =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String STORED_CHALLENGE = "SESSION01";
  private static final String STORED_SESSION_HEX =
      "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
          + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
  private static final String STORED_EXPECTED_OTP = "17477202";

  @TempDir static Path tempDir;
  private static Path databasePath;
  private static boolean sampleDatabaseCopied;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("credentials.db");
    Path samplePath = Path.of("data/ocra-credentials.db");
    if (!Files.exists(samplePath)) {
      samplePath = Path.of("../data/ocra-credentials.db");
    }
    try {
      Files.copy(samplePath, databasePath, StandardCopyOption.REPLACE_EXISTING);
      sampleDatabaseCopied = true;
    } catch (IOException ignored) {
      sampleDatabaseCopied = false;
    }
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
    seedStoredCredential();
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @DisplayName("Stored credential replay renders match outcome with telemetry metadata")
  void storedCredentialReplayRendersMatchOutcome() {
    assertThat(sampleDatabaseCopied)
        .as("Sample credential database should be available for replay tests")
        .isTrue();

    driver.get(baseUrl("/ui/ocra/replay"));
    waitForReplayBootstrap();
    WebElement storedMode = driver.findElement(By.id("replayModeStored"));
    if (!storedMode.isSelected()) {
      storedMode.click();
      waitForBackgroundJavaScript();
    }

    waitForElementEnabled(By.id("replayCredentialId"));
    waitForStoredCredentialOptions();
    waitForElementEnabled(By.id("replayOtp"));
    waitForElementEnabled(By.id("replayChallenge"));

    Select credentialSelect = new Select(driver.findElement(By.id("replayCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    driver.findElement(By.id("replayOtp")).sendKeys(STORED_EXPECTED_OTP);
    driver.findElement(By.id("replayChallenge")).sendKeys(STORED_CHALLENGE);

    WebElement advancedToggle =
        driver.findElement(By.cssSelector("button[data-testid='replay-advanced-toggle']"));
    if ("false".equals(advancedToggle.getAttribute("aria-expanded"))) {
      advancedToggle.click();
      waitForBackgroundJavaScript();
    }

    waitForElementEnabled(By.id("replaySessionHex"));
    driver.findElement(By.id("replaySessionHex")).sendKeys(STORED_SESSION_HEX);

    driver.findElement(By.cssSelector("button[data-testid='ocra-replay-submit']")).click();

    JsonNode response = awaitReplayResponse();
    String payload = currentPayload();
    assertThat(response.path("ok").asBoolean())
        .as("replay response should succeed: %s payload=%s", response, payload)
        .isTrue();

    WebElement resultPanel =
        new WebDriverWait(driver, WAIT_TIMEOUT)
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-replay-result']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement status =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-status']"));
    assertThat(status.getText()).isEqualTo("Match");

    WebElement telemetry =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-telemetry']"));
    assertThat(telemetry.getText())
        .contains("telemetryId=")
        .contains("mode=stored")
        .contains("credentialSource=stored")
        .contains("reasonCode=match")
        .contains("outcome=match")
        .contains("sanitized=true")
        .contains("contextFingerprint=");

    WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-replay-error']"));
    assertThat(errorPanel.getAttribute("hidden")).isNotNull();
  }

  @Test
  @DisplayName("Inline replay renders match outcome and sanitized telemetry")
  void inlineReplayRendersMatchOutcome() {
    driver.get(baseUrl("/ui/ocra/replay"));
    waitForReplayBootstrap();

    driver.findElement(By.id("replayModeInline")).click();
    waitForBackgroundJavaScript();

    waitForElementEnabled(By.id("replaySuite"));
    waitForElementEnabled(By.id("replaySharedSecretHex"));
    waitForElementEnabled(By.id("replayOtp"));
    waitForElementEnabled(By.id("replayChallenge"));
    driver.findElement(By.id("replaySuite")).sendKeys(STORED_SUITE);
    driver.findElement(By.id("replaySharedSecretHex")).sendKeys(STORED_SECRET_HEX);
    driver.findElement(By.id("replayOtp")).sendKeys(STORED_EXPECTED_OTP);
    driver.findElement(By.id("replayChallenge")).sendKeys(STORED_CHALLENGE);

    WebElement advancedToggle =
        driver.findElement(By.cssSelector("button[data-testid='replay-advanced-toggle']"));
    if ("false".equals(advancedToggle.getAttribute("aria-expanded"))) {
      advancedToggle.click();
      waitForBackgroundJavaScript();
    }

    waitForElementEnabled(By.id("replaySessionHex"));
    driver.findElement(By.id("replaySessionHex")).sendKeys(STORED_SESSION_HEX);

    driver.findElement(By.cssSelector("button[data-testid='ocra-replay-submit']")).click();

    JsonNode response = awaitReplayResponse();
    String payload = currentPayload();
    assertThat(response.path("ok").asBoolean())
        .as("inline replay should succeed: %s payload=%s", response, payload)
        .isTrue();

    WebElement resultPanel =
        new WebDriverWait(driver, WAIT_TIMEOUT)
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-replay-result']")));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement telemetry =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-telemetry']"));
    assertThat(telemetry.getText())
        .contains("telemetryId=")
        .contains("mode=inline")
        .contains("credentialSource=inline")
        .contains("reasonCode=match")
        .contains("outcome=match")
        .contains("sanitized=true")
        .contains("contextFingerprint=");

    WebElement status =
        resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-status']"));
    assertThat(status.getText()).isEqualTo("Match");
  }

  @Test
  @DisplayName("Inline replay surfaces validation error when OTP is missing")
  void inlineReplaySurfacesValidationError() {
    driver.get(baseUrl("/ui/ocra/replay"));
    waitForReplayBootstrap();

    driver.findElement(By.id("replayModeInline")).click();
    waitForBackgroundJavaScript();

    waitForElementEnabled(By.id("replaySuite"));
    waitForElementEnabled(By.id("replaySharedSecretHex"));
    waitForElementEnabled(By.id("replayChallenge"));
    driver.findElement(By.id("replaySuite")).sendKeys(STORED_SUITE);
    driver.findElement(By.id("replaySharedSecretHex")).sendKeys(STORED_SECRET_HEX);
    driver.findElement(By.id("replayChallenge")).sendKeys(STORED_CHALLENGE);

    driver.findElement(By.cssSelector("button[data-testid='ocra-replay-submit']")).click();

    JsonNode response = awaitReplayResponse();
    assertThat(response.path("ok").asBoolean())
        .as("inline replay missing OTP should fail: %s", response)
        .isFalse();

    WebElement errorPanel =
        new WebDriverWait(driver, WAIT_TIMEOUT)
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='ocra-replay-error']")));
    assertThat(errorPanel.getAttribute("hidden")).isNull();
    assertThat(errorPanel.getText()).contains("validation_failure").contains("otp");

    WebElement resultPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-replay-result']"));
    assertThat(resultPanel.getAttribute("hidden")).isNotNull();
  }

  private void waitForReplayBootstrap() {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(
            d ->
                Boolean.TRUE.equals(
                    ((JavascriptExecutor) d)
                        .executeScript(
                            "return typeof window.__ocraReplayReady !== 'undefined'"
                                + " && window.__ocraReplayReady === true;")));
  }

  private void waitForStoredCredentialOptions() {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(
            d -> {
              Object count =
                  ((JavascriptExecutor) d)
                      .executeScript(
                          "var select = document.getElementById('replayCredentialId');"
                              + "return select ? select.options.length : 0;");
              return count instanceof Number && ((Number) count).intValue() > 1;
            });
  }

  private void waitForElementEnabled(By locator) {
    new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
  }

  private void waitForBackgroundJavaScript() {
    driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());
  }

  private JsonNode awaitReplayResponse() {
    String payload =
        new WebDriverWait(driver, WAIT_TIMEOUT)
            .until(
                d -> {
                  Object value =
                      ((JavascriptExecutor) d)
                          .executeScript("return window.__ocraReplayLastResponse;");
                  if (value instanceof String json && !json.isBlank()) {
                    return json;
                  }
                  return null;
                });
    try {
      return JSON.readTree(payload);
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to parse replay response: " + payload, ex);
    }
  }

  private String currentPayload() {
    Object value =
        ((JavascriptExecutor) driver).executeScript("return window.__ocraReplayLastPayload;");
    return value instanceof String ? (String) value : null;
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  private void seedStoredCredential() {
    if (sampleDatabaseCopied && credentialStore.exists(STORED_CREDENTIAL_ID)) {
      return;
    }
    credentialStore.delete(STORED_CREDENTIAL_ID);
    OcraCredentialFactory factory = new OcraCredentialFactory();
    OcraCredentialRequest request =
        new OcraCredentialRequest(
            STORED_CREDENTIAL_ID,
            STORED_SUITE,
            STORED_SECRET_HEX,
            SecretEncoding.HEX,
            null,
            null,
            null,
            Map.of("source", "selenium-replay-test"));
    OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(
            new OcraCredentialPersistenceAdapter().serialize(descriptor));
    credentialStore.save(credential);
    assertThat(credentialStore.exists(STORED_CREDENTIAL_ID)).isTrue();
  }
}
