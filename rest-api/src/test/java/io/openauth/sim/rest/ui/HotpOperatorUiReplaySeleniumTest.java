package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.hotp.HotpDescriptor;
import io.openauth.sim.core.otp.hotp.HotpGenerator;
import io.openauth.sim.core.otp.hotp.HotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
 * Selenium coverage for the HOTP replay operator UI. These scenarios exercise stored and inline
 * replay flows, sample data affordances, and telemetry surfacing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class HotpOperatorUiReplaySeleniumTest {

  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(6);

  private static final String STORED_CREDENTIAL_ID = "ui-hotp-demo";
  private static final String SECRET_HEX = "3132333435363738393031323334353637383930";
  private static final int DIGITS = 6;
  private static final long STORED_COUNTER = 0L;
  private static final long INLINE_COUNTER = 5L;
  private static final String INLINE_SHA1_PRESET_KEY = "seeded-demo-sha1";
  private static final String INLINE_SHA1_PRESET_LABEL = "SHA-1, 6 digits";
  private static final String INLINE_SHA1_8_PRESET_KEY = "seeded-demo-sha1-8";
  private static final String INLINE_SHA1_8_PRESET_LABEL = "SHA-1, 8 digits";
  private static final String INLINE_SHA256_PRESET_KEY = "seeded-demo-sha256";
  private static final String INLINE_SHA256_PRESET_LABEL = "SHA-256, 8 digits";
  private static final String INLINE_SHA256_6_PRESET_KEY = "seeded-demo-sha256-6";
  private static final String INLINE_SHA256_6_PRESET_LABEL = "SHA-256, 6 digits";
  private static final String INLINE_SHA512_PRESET_KEY = "seeded-demo-sha512";
  private static final String INLINE_SHA512_PRESET_LABEL = "SHA-512, 8 digits";
  private static final String INLINE_SHA512_6_PRESET_KEY = "seeded-demo-sha512-6";
  private static final String INLINE_SHA512_6_PRESET_LABEL = "SHA-512, 6 digits";
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
  @DisplayName("Stored HOTP replay selector label matches OCRA copy")
  void storedHotpReplayLabelMatchesOcraCopy() {
    navigateToReplayPanel();

    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));
    if (!storedMode.isSelected()) {
      storedMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> storedMode.isSelected());
    }

    WebElement label = waitForVisible(By.cssSelector("label[for='hotpReplayStoredCredentialId']"));
    assertThat(label.getText().trim()).isEqualTo("Stored credential");
  }

  @Test
  @DisplayName("Stored HOTP replay sample pre-fills counter and OTP for a successful match")
  void storedHotpReplaySamplePrefillsCounterAndOtp() {
    navigateToReplayPanel();

    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));
    if (!storedMode.isSelected()) {
      storedMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> storedMode.isSelected());
    }

    Select storedSelect = new Select(waitForVisible(By.id("hotpReplayStoredCredentialId")));
    waitForCredentialOption(storedSelect, STORED_CREDENTIAL_ID);

    WebElement counterInput = waitForVisible(By.id("hotpReplayStoredCounter"));
    assertThat(counterInput.isDisplayed()).isTrue();

    waitForAttribute(
        By.id("hotpReplayStoredOtp"),
        "value",
        EXPECTED_STORED_OTP,
        "Stored OTP should pre-fill from sample data");
    waitForAttribute(
        By.id("hotpReplayStoredCounter"),
        "value",
        Long.toString(STORED_COUNTER),
        "Stored counter input should reflect the credential's current counter");

    waitForClickable(By.cssSelector("button[data-testid='hotp-replay-submit']")).click();

    WebElement statusBadge =
        waitForVisible(
            By.cssSelector(
                "[data-testid='hotp-replay-result'] [data-testid='hotp-replay-status']"));
    assertThat(statusBadge.getText()).isEqualTo("Match");

    WebElement reasonValue =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-reason-code']"));
    assertThat(reasonValue.getText()).isEqualTo("match");
  }

  @Test
  @DisplayName("HOTP replay submit button copy matches verification directive")
  void hotpReplaySubmitButtonCopy() {
    navigateToReplayPanel();

    WebElement submit = waitForVisible(By.cssSelector("button[data-testid='hotp-replay-submit']"));
    assertThat(submit.getText().trim()).isEqualTo("Verify OTP");
  }

  @Test
  @DisplayName("HOTP replay panel aligns with evaluate panel baseline")
  void hotpReplayPanelAlignsWithEvaluate() {
    driver.get("http://localhost:" + port + "/ui/console?protocol=hotp");

    String consoleStylesheet = fetchConsoleStylesheet();
    assertThat(consoleStylesheet)
        .as("Operator console CSS should guard card-shell spacing behind :not([hidden])")
        .contains(".card-shell:not([hidden]) + .card-shell");
  }

  @Test
  @DisplayName("HOTP replay observed OTP labels read One-time password")
  void hotpReplayObservedOtpLabel() {
    navigateToReplayPanel();

    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));
    if (!storedMode.isSelected()) {
      storedMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> storedMode.isSelected());
    }

    WebElement storedLabel = waitForVisible(By.cssSelector("label[for='hotpReplayStoredOtp']"));
    assertThat(storedLabel.getText().trim()).isEqualTo("One-time password");

    waitForClickable(By.id("hotpReplayModeInline")).click();
    WebElement inlineLabel = waitForVisible(By.cssSelector("label[for='hotpReplayInlineOtp']"));
    assertThat(inlineLabel.getText().trim()).isEqualTo("One-time password");
  }

  @Test
  @DisplayName("HOTP replay defaults to inline parameters listed before stored credential")
  void hotpReplayDefaultsToInlineMode() {
    navigateToReplayPanel();

    WebElement inlineMode = waitForVisible(By.id("hotpReplayModeInline"));
    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));

    assertThat(inlineMode.isSelected()).as("Inline mode should be selected by default").isTrue();
    assertThat(storedMode.isSelected()).isFalse();

    java.util.List<String> labels =
        driver
            .findElements(
                By.cssSelector("[data-testid='hotp-replay-mode-toggle'] .mode-option label"))
            .stream()
            .map(WebElement::getText)
            .map(String::trim)
            .toList();

    assertThat(labels).containsExactly("Inline parameters", "Stored credential");
  }

  @Test
  @DisplayName("Inline HOTP replay aligns parameters on a single row")
  void inlineHotpReplayParametersRenderOnSingleRow() {
    navigateToReplayPanel();

    WebElement inlineMode = waitForVisible(By.id("hotpReplayModeInline"));
    if (!inlineMode.isSelected()) {
      inlineMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> inlineMode.isSelected());
    }

    WebElement inlinePanel =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-inline-panel']"));
    assertThat(inlinePanel.getAttribute("hidden")).isNull();

    WebElement parameterGrid =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-inline-parameter-grid']"));
    assertThat(parameterGrid.getAttribute("class"))
        .as("Parameter grid should reuse hotp-inline-parameter-grid styling")
        .contains("hotp-inline-parameter-grid");

    java.util.List<String> parameterLabels =
        parameterGrid.findElements(By.cssSelector("label")).stream()
            .map(WebElement::getText)
            .map(String::trim)
            .toList();
    assertThat(parameterLabels).containsExactly("Hash algorithm", "Digits", "Counter");

    assertThat(parameterGrid.findElements(By.id("hotpReplayInlineOtp")))
        .as("OTP input should live outside the single-row parameter grid")
        .isEmpty();
  }

  @Test
  @DisplayName("HOTP replay inline sample options mirror evaluate presets")
  void inlineReplaySampleOptionsMirrorEvaluate() {
    navigateToReplayPanel();

    WebElement inlineMode = waitForVisible(By.id("hotpReplayModeInline"));
    if (!inlineMode.isSelected()) {
      inlineMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> inlineMode.isSelected());
    }

    Select presetSelect =
        new Select(
            waitForVisible(
                By.cssSelector("select[data-testid='hotp-replay-inline-sample-select']")));

    java.util.List<WebElement> options = presetSelect.getOptions();
    assertThat(options)
        .as(
            "Replay inline preset dropdown should expose placeholder plus SHA-1/SHA-256/SHA-512 seeded samples across 6- and 8-digit variants")
        .hasSize(7);

    WebElement placeholder = options.get(0);
    assertThat(placeholder.getAttribute("value")).isEqualTo("");
    assertThat(placeholder.getText().trim()).isEqualTo("Select a sample");

    WebElement sha1Option = options.get(1);
    assertThat(sha1Option.getAttribute("value")).isEqualTo(INLINE_SHA1_PRESET_KEY);
    assertThat(sha1Option.getText().trim()).isEqualTo("SHA-1, 6 digits (RFC 4226)");

    WebElement sha1EightOption = options.get(2);
    assertThat(sha1EightOption.getAttribute("value")).isEqualTo(INLINE_SHA1_8_PRESET_KEY);
    assertThat(sha1EightOption.getText().trim()).isEqualTo(INLINE_SHA1_8_PRESET_LABEL);

    WebElement sha256EightOption = options.get(3);
    assertThat(sha256EightOption.getAttribute("value")).isEqualTo(INLINE_SHA256_PRESET_KEY);
    assertThat(sha256EightOption.getText().trim()).isEqualTo(INLINE_SHA256_PRESET_LABEL);

    WebElement sha256SixOption = options.get(4);
    assertThat(sha256SixOption.getAttribute("value")).isEqualTo(INLINE_SHA256_6_PRESET_KEY);
    assertThat(sha256SixOption.getText().trim()).isEqualTo(INLINE_SHA256_6_PRESET_LABEL);

    WebElement sha512EightOption = options.get(5);
    assertThat(sha512EightOption.getAttribute("value")).isEqualTo(INLINE_SHA512_PRESET_KEY);
    assertThat(sha512EightOption.getText().trim()).isEqualTo(INLINE_SHA512_PRESET_LABEL);

    WebElement sha512SixOption = options.get(6);
    assertThat(sha512SixOption.getAttribute("value")).isEqualTo(INLINE_SHA512_6_PRESET_KEY);
    assertThat(sha512SixOption.getText().trim()).isEqualTo(INLINE_SHA512_6_PRESET_LABEL);
  }

  @Test
  @DisplayName("Stored HOTP replay heading removed so label leads section")
  void storedHotpReplayHeadingRemoved() {
    navigateToReplayPanel();

    List<WebElement> heading = driver.findElements(By.id("hotpReplayStoredHeading"));
    assertThat(heading)
        .as("Stored replay panel should omit the heading before the selector label")
        .isEmpty();
  }

  @Test
  @DisplayName("Stored HOTP replay auto-fills and surfaces sample status")
  void storedHotpReplaySampleStatusDisplayed() {
    navigateToReplayPanel();

    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));
    if (!storedMode.isSelected()) {
      storedMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> storedMode.isSelected());
    }

    Select storedSelect = new Select(waitForVisible(By.id("hotpReplayStoredCredentialId")));
    waitForCredentialOption(storedSelect, STORED_CREDENTIAL_ID);
    storedSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement statusNode =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-sample-status']"));
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(d -> statusNode.getText().trim().toLowerCase().contains("applied"));
    assertThat(statusNode.getText().toLowerCase()).contains("applied");
  }

  @Test
  @DisplayName("HOTP replay result column mirrors OCRA layout")
  void hotpReplayResultLayoutMatchesOcra() {
    navigateToReplayPanel();

    waitForVisible(By.cssSelector("[data-testid='hotp-replay-panel']"));

    WebElement inlineMode = waitForVisible(By.id("hotpReplayModeInline"));
    WebElement storedMode = waitForVisible(By.id("hotpReplayModeStored"));

    assertThat(inlineMode.isSelected()).as("Inline mode should be selected by default").isTrue();
    assertThat(storedMode.isSelected()).isFalse();

    if (!storedMode.isSelected()) {
      storedMode.click();
      new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> storedMode.isSelected());
    }

    Select credentialSelect = new Select(waitForVisible(By.id("hotpReplayStoredCredentialId")));
    waitForCredentialOption(credentialSelect, STORED_CREDENTIAL_ID);
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    waitForAttribute(
        By.id("hotpReplayStoredOtp"),
        "value",
        EXPECTED_STORED_OTP,
        "Stored replay OTP should auto-fill from curated sample data");

    waitForClickable(By.cssSelector("button[data-testid='hotp-replay-submit']")).click();

    WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='hotp-replay-result']"));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement statusColumn =
        resultPanel.findElement(By.xpath("ancestor::div[contains(@class,'status-column')]"));
    assertThat(statusColumn).as("Replay result should render inside the status column").isNotNull();

    WebElement statusBadge =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-status']"));
    assertThat(statusBadge.getText()).isEqualTo("Match");

    WebElement reasonLabel =
        resultPanel.findElement(By.xpath(".//dt[normalize-space() = 'Reason Code']"));
    assertThat(reasonLabel).withFailMessage("Reason Code label should be present").isNotNull();
    WebElement reasonValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-reason-code']"));
    assertThat(reasonValue.getText()).isEqualTo("match");

    WebElement outcomeLabel =
        resultPanel.findElement(By.xpath(".//dt[normalize-space() = 'Outcome']"));
    assertThat(outcomeLabel).withFailMessage("Outcome label should be present").isNotNull();
    WebElement outcomeValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-outcome']"));
    assertThat(outcomeValue.getText()).isEqualTo("match");

    assertThat(resultPanel.findElements(By.cssSelector("[data-testid='hotp-replay-metadata']")))
        .as("Replay metadata list should be hidden from operators")
        .isEmpty();
    assertThat(resultPanel.findElements(By.cssSelector("[data-testid='hotp-replay-telemetry-id']")))
        .as("Telemetry identifier should not render in the replay result")
        .isEmpty();
  }

  @Test
  @DisplayName("Inline HOTP replay reports reason code and outcome")
  void inlineHotpReplayDisplaysReasonAndOutcome() {
    navigateToReplayPanel();

    waitForClickable(By.id("hotpReplayModeInline")).click();

    WebElement inlinePanel =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-inline-panel']"));
    assertThat(inlinePanel.getAttribute("hidden")).isNull();

    Select presetSelect =
        new Select(
            waitForVisible(
                By.cssSelector("select[data-testid='hotp-replay-inline-sample-select']")));
    waitForOption(presetSelect, INLINE_SHA1_PRESET_KEY);
    presetSelect.selectByValue(INLINE_SHA1_PRESET_KEY);

    assertThat(driver.findElements(By.id("hotpReplayInlineIdentifier")))
        .as("Inline replay form should not render an identifier input")
        .isEmpty();
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

    waitForClickable(By.cssSelector("button[data-testid='hotp-replay-submit']")).click();

    WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='hotp-replay-result']"));
    assertThat(resultPanel.getAttribute("hidden")).isNull();

    WebElement statusBadge =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-status']"));
    assertThat(statusBadge.getText()).isEqualTo("Match");

    WebElement reasonValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-reason-code']"));
    assertThat(reasonValue.getText()).isEqualTo("match");

    WebElement outcomeValue =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-replay-outcome']"));
    assertThat(outcomeValue.getText()).isEqualTo("match");

    assertThat(resultPanel.findElements(By.cssSelector("[data-testid='hotp-replay-metadata']")))
        .isEmpty();
    assertThat(resultPanel.findElements(By.cssSelector("[data-testid='hotp-replay-telemetry-id']")))
        .isEmpty();
  }

  @Test
  @DisplayName("HOTP replay success badge uses the success styling")
  void hotpReplayMatchUsesSuccessBadge() {
    navigateToReplayPanel();

    waitForClickable(By.id("hotpReplayModeInline")).click();

    Select presetSelect =
        new Select(
            waitForVisible(
                By.cssSelector("select[data-testid='hotp-replay-inline-sample-select']")));
    waitForOption(presetSelect, INLINE_SHA1_PRESET_KEY);
    presetSelect.selectByValue(INLINE_SHA1_PRESET_KEY);

    waitForClickable(By.cssSelector("button[data-testid='hotp-replay-submit']")).click();

    WebElement statusBadge =
        waitForVisible(
            By.cssSelector(
                "[data-testid='hotp-replay-result'] [data-testid='hotp-replay-status']"));
    String classes = statusBadge.getAttribute("class");
    assertThat(classes)
        .as("Replay success badges should apply the shared success styling")
        .contains("status-badge--success");
    assertThat(classes)
        .as("Replay success badges should not fall back to the info styling")
        .doesNotContain("status-badge--info");
  }

  @Test
  @DisplayName("HOTP replay panel omits advanced context toggle and metadata inputs")
  void hotpReplayOmitsAdvancedContextControls() {
    navigateToReplayPanel();

    assertThat(
            driver.findElements(
                By.cssSelector("button[data-testid='hotp-replay-advanced-toggle']")))
        .as("Advanced context toggle should be removed from HOTP replay")
        .isEmpty();
    assertThat(driver.findElements(By.id("hotpReplayAdvancedLabel")))
        .as("Advanced label input should not render")
        .isEmpty();
    assertThat(driver.findElements(By.id("hotpReplayAdvancedNotes")))
        .as("Advanced notes textarea should not render")
        .isEmpty();
  }

  @Test
  @DisplayName("HOTP inline replay panel omits redundant heading")
  void hotpInlineReplayOmitsHeading() {
    navigateToReplayPanel();

    waitForClickable(By.id("hotpReplayModeInline")).click();

    WebElement inlinePanel =
        waitForVisible(By.cssSelector("[data-testid='hotp-replay-inline-panel']"));
    assertThat(inlinePanel.getAttribute("hidden")).isNull();

    assertThat(inlinePanel.findElements(By.id("hotpReplayInlineHeading")))
        .as("Inline replay heading should be removed; mode selector conveys active state")
        .isEmpty();
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

  private void waitForAttribute(By locator, String attribute, String expected, String message) {
    new WebDriverWait(driver, WAIT_TIMEOUT)
        .until(ExpectedConditions.attributeToBe(locator, attribute, expected));
    String actual = driver.findElement(locator).getAttribute(attribute);
    assertThat(actual).as(message).isEqualTo(expected);
  }

  private String fetchConsoleStylesheet() {
    try {
      URL url = new URL("http://localhost:" + port + "/ui/console/console.css");
      try (InputStream inputStream = url.openStream()) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (IOException exception) {
      fail("Failed to fetch operator console stylesheet", exception);
      return "";
    }
  }
}
