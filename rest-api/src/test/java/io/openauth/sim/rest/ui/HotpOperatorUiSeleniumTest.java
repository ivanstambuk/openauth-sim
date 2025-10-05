package io.openauth.sim.rest.ui;

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
 * Selenium scaffolding for HOTP operator console coverage. Tests are expected to fail until the
 * HOTP evaluation UI is implemented.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class HotpOperatorUiSeleniumTest {

  private static final String STORED_CREDENTIAL_ID = "ui-hotp-demo";
  private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final int DIGITS = 6;
  private static final long INITIAL_COUNTER = 0L;
  private static final HotpDescriptor DESCRIPTOR =
      HotpDescriptor.create(
          STORED_CREDENTIAL_ID, SecretMaterial.fromHex(SECRET_HEX), HotpHashAlgorithm.SHA1, DIGITS);
  private static final String EXPECTED_STORED_OTP =
      HotpGenerator.generate(DESCRIPTOR, INITIAL_COUNTER);

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
  @DisplayName("Stored HOTP credential evaluation succeeds via operator console")
  void storedCredentialEvaluationSucceeds() {
    navigateToHotpPanel();

    WebElement storedPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-stored-evaluation-panel']")));

    String storedAriaLabelledBy = storedPanel.getAttribute("aria-labelledby");
    if (storedAriaLabelledBy == null || storedAriaLabelledBy.isBlank()) {
      throw new AssertionError("Expected HOTP stored panel to expose aria-labelledby metadata");
    }

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.elementToBeClickable(By.id("hotpStoredCredentialId")));

    Select credentialSelect = new Select(driver.findElement(By.id("hotpStoredCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement otpInput = driver.findElement(By.id("hotpStoredOtp"));
    otpInput.clear();
    otpInput.sendKeys(EXPECTED_STORED_OTP);

    driver.findElement(By.cssSelector("button[data-testid='hotp-stored-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-stored-result-panel']")));
    // Expect result panel to surface the OTP match outcome.
    WebElement statusValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-result-status']"));
    WebElement metadataValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-result-metadata']"));

    // When implemented, the UI should display match status and next counter metadata.
    if (!"match".equalsIgnoreCase(statusValue.getText())) {
      throw new AssertionError("Expected HOTP match status in result panel");
    }
    if (!metadataValue.getText().contains("nextCounter=1")) {
      throw new AssertionError("Expected HOTP metadata to include nextCounter=1");
    }
  }

  @Test
  @DisplayName("Inline HOTP evaluation succeeds via operator console")
  void inlineHotpEvaluationSucceeds() {
    navigateToHotpPanel();

    driver.findElement(By.cssSelector("[data-testid='hotp-mode-select-inline']")).click();

    WebElement inlinePanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-evaluation-panel']")));

    String inlineAriaLabelledBy = inlinePanel.getAttribute("aria-labelledby");
    if (inlineAriaLabelledBy == null || inlineAriaLabelledBy.isBlank()) {
      throw new AssertionError("Expected HOTP inline panel to expose aria-labelledby metadata");
    }

    driver.findElement(By.id("hotpInlineIdentifier")).sendKeys("device-456");
    driver.findElement(By.id("hotpInlineSecretHex")).sendKeys(SECRET_HEX);
    driver.findElement(By.id("hotpInlineDigits")).clear();
    driver.findElement(By.id("hotpInlineDigits")).sendKeys(Integer.toString(DIGITS));
    driver.findElement(By.id("hotpInlineCounter")).clear();
    driver.findElement(By.id("hotpInlineCounter")).sendKeys(Long.toString(INITIAL_COUNTER));
    driver.findElement(By.id("hotpInlineOtp")).sendKeys(EXPECTED_STORED_OTP);

    driver.findElement(By.cssSelector("button[data-testid='hotp-inline-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-result-panel']")));

    WebElement statusValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-result-status']"));
    if (!"match".equalsIgnoreCase(statusValue.getText())) {
      throw new AssertionError("Expected HOTP match status for inline evaluation");
    }
  }

  private void navigateToHotpPanel() {
    driver.get("http://localhost:" + port + "/ui/console?protocol=hotp");
    WebElement tab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-hotp']"));
    tab.click();
  }

  private Credential storedCredential() {
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
            Long.toString(INITIAL_COUNTER)));
  }
}
