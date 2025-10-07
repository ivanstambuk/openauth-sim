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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
  private static final String INLINE_SHA256_PRESET_KEY = "inline-demo-sha256";
  private static final String INLINE_SHA256_PRESET_LABEL = "Inline demo vector (SHA-256)";
  private static final int INLINE_SHA256_DIGITS = 8;
  private static final long INLINE_SHA256_COUNTER = 5L;
  private static final HotpDescriptor DESCRIPTOR =
      HotpDescriptor.create(
          STORED_CREDENTIAL_ID, SecretMaterial.fromHex(SECRET_HEX), HotpHashAlgorithm.SHA1, DIGITS);
  private static final String EXPECTED_STORED_OTP =
      HotpGenerator.generate(DESCRIPTOR, INITIAL_COUNTER);
  private static final HotpDescriptor INLINE_SHA256_DESCRIPTOR =
      HotpDescriptor.create(
          INLINE_SHA256_PRESET_KEY,
          SecretMaterial.fromHex(SECRET_HEX),
          HotpHashAlgorithm.SHA256,
          INLINE_SHA256_DIGITS);
  private static final String EXPECTED_INLINE_SHA256_OTP =
      HotpGenerator.generate(INLINE_SHA256_DESCRIPTOR, INLINE_SHA256_COUNTER);

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

    WebElement modeToggle = assertHotpInlineDefaultState();
    By storedPanelLocator = By.cssSelector("[data-testid='hotp-stored-evaluation-panel']");
    By inlinePanelLocator = By.cssSelector("[data-testid='hotp-inline-evaluation-panel']");

    WebElement storedToggle =
        modeToggle.findElement(By.cssSelector("[data-testid='hotp-mode-select-stored']"));
    WebElement inlineToggle =
        modeToggle.findElement(By.cssSelector("[data-testid='hotp-mode-select-inline']"));

    storedToggle.click();

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(d -> "stored".equals(modeToggle.getAttribute("data-mode")));

    if (!storedToggle.isSelected()) {
      throw new AssertionError("Stored HOTP mode toggle should become selected after activation");
    }
    if (inlineToggle.isSelected()) {
      throw new AssertionError(
          "Inline HOTP mode toggle should be deselected after stored activation");
    }

    WebElement storedPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.visibilityOfElementLocated(storedPanelLocator));
    WebElement inlinePanel = driver.findElement(inlinePanelLocator);

    if (inlinePanel.isDisplayed()) {
      throw new AssertionError(
          "Inline HOTP evaluation panel should hide once stored mode is active");
    }

    String storedAriaLabel = storedPanel.getAttribute("aria-label");
    if (storedAriaLabel == null || storedAriaLabel.isBlank()) {
      throw new AssertionError("Expected HOTP stored panel to expose aria-label metadata");
    }

    if (!storedPanel.findElements(By.tagName("h2")).isEmpty()) {
      throw new AssertionError("HOTP stored panel should not render redundant headings");
    }

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.elementToBeClickable(By.id("hotpStoredCredentialId")));

    Select credentialSelect = new Select(driver.findElement(By.id("hotpStoredCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    if (!driver.findElements(By.id("hotpStoredOtp")).isEmpty()) {
      throw new AssertionError("Stored HOTP evaluation should not render an OTP input");
    }

    driver.findElement(By.cssSelector("button[data-testid='hotp-stored-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-stored-result-panel']")));
    WebElement statusColumn = locateStatusColumn();
    WebElement firstVisible = firstVisibleChild(statusColumn);
    if (!"hotp-stored-result-panel".equals(firstVisible.getAttribute("data-testid"))) {
      throw new AssertionError(
          "Expected HOTP stored result panel to be the first visible child of the status column");
    }
    assertResultRowsMatchExpectations(resultPanel, EXPECTED_STORED_OTP);
  }

  @Test
  @DisplayName("HOTP evaluate defaults to inline mode and persists after refresh")
  void hotpEvaluateDefaultsToInlineModeAfterRefresh() {
    navigateToHotpPanel();
    assertHotpInlineDefaultState();

    driver.navigate().refresh();

    WebElement hotpTab =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='protocol-tab-hotp']")));
    if (!"true".equals(hotpTab.getAttribute("aria-selected"))) {
      hotpTab.click();
      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(d -> "true".equals(hotpTab.getAttribute("aria-selected")));
    }

    assertHotpInlineDefaultState();
  }

  @Test
  @DisplayName("Inline HOTP form preserves spacing between stored toggle and sample preset")
  void hotpInlineSpacingMatchesOcra() {
    navigateToHotpPanel();
    assertHotpInlineDefaultState();

    WebElement modeToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-mode-toggle']")));

    WebElement presetLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-preset'] label")));

    double gap = topOf(presetLabel) - bottomOf(modeToggle);
    if (gap < 0.0 || gap > 20.0) {
      throw new AssertionError(
          "Expected non-negative spacing between HOTP mode toggle and sample preset up to 20px; found "
              + gap
              + "px");
    }
  }

  @Test
  @DisplayName("HOTP evaluate sample vector baseline matches replay tab")
  void sampleVectorLabelAlignsWithReplay() {
    navigateToHotpPanel();
    assertHotpInlineDefaultState();

    WebElement evaluateHeading =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-evaluate-panel'] .section-title")));
    double evaluateHeadingTop = topOf(evaluateHeading);
    WebElement evaluateLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-preset'] label")));
    double evaluateLabelTop = topOf(evaluateLabel);
    double evaluateOffset = evaluateLabelTop - evaluateHeadingTop;

    WebElement replayTab =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='hotp-panel-tab-replay']")));
    replayTab.click();

    WebElement replayHeading =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-replay-panel'] .section-title")));
    double replayHeadingTop = topOf(replayHeading);
    WebElement replayLabel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-replay-inline-preset'] label")));
    double replayLabelTop = topOf(replayLabel);
    double replayOffset = replayLabelTop - replayHeadingTop;

    double delta = Math.abs(evaluateOffset - replayOffset);
    if (delta > 1.0d) {
      throw new AssertionError(
          "Expected HOTP evaluate sample vector label to align with replay baseline within 1px but delta was "
              + delta
              + "px");
    }
  }

  @Test
  @DisplayName("HOTP inline preset hints use shared illustrative data copy")
  void hotpPresetHintMatchesRequirement() {
    navigateToHotpPanel();
    assertHotpInlineDefaultState();

    String expectedHint = "Selecting a preset auto-fills the inline fields with illustrative data.";

    WebElement evaluateHint =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-preset'] .hint")));
    String evaluateHintCopy = evaluateHint.getText().trim();
    if (!expectedHint.equals(evaluateHintCopy)) {
      throw new AssertionError(
          "Expected HOTP inline evaluation hint to read \""
              + expectedHint
              + "\" but found \""
              + evaluateHintCopy
              + "\"");
    }

    WebElement replayTab =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='hotp-panel-tab-replay']")));
    replayTab.click();

    WebElement replayHint =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-replay-inline-preset'] .hint")));
    String replayHintCopy = replayHint.getText().trim();
    if (!expectedHint.equals(replayHintCopy)) {
      throw new AssertionError(
          "Expected HOTP inline replay hint to read \""
              + expectedHint
              + "\" but found \""
              + replayHintCopy
              + "\"");
    }
  }

  @Test
  @DisplayName("Inline HOTP parameter controls render in compact row")
  void inlineHotpParametersRenderInCompactRow() {
    navigateToHotpPanel();
    assertHotpInlineDefaultState();

    WebElement parameterGrid =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-parameter-grid']")));

    String columnTemplate = parameterGrid.getCssValue("grid-template-columns");
    if (columnTemplate == null || columnTemplate.isBlank()) {
      throw new AssertionError("Expected inline HOTP parameter grid to define column tracks");
    }

    String[] columnDefinitions = columnTemplate.trim().split("\\s+");
    if (columnDefinitions.length < 3) {
      throw new AssertionError(
          "Expected inline HOTP parameter grid to expose three columns but found: "
              + columnTemplate);
    }

    WebElement algorithmSelect = parameterGrid.findElement(By.id("hotpInlineAlgorithm"));
    WebElement digitsInput = parameterGrid.findElement(By.id("hotpInlineDigits"));
    WebElement counterInput = parameterGrid.findElement(By.id("hotpInlineCounter"));

    if (columnDefinitions[0].contains("5.5rem")) {
      throw new AssertionError(
          "Expected inline HOTP parameter grid to dedicate the widest track to the algorithm select but found: "
              + columnTemplate);
    }

    long compactTrackCount =
        java.util.Arrays.stream(columnDefinitions).filter(def -> def.contains("5.5rem")).count();
    if (compactTrackCount < 2) {
      throw new AssertionError(
          "Expected inline HOTP parameter grid to expose compact tracks for digits and counter but found: "
              + columnTemplate);
    }

    String algorithmClass = algorithmSelect.getAttribute("class");
    if (algorithmClass == null || !algorithmClass.contains("select-compact")) {
      throw new AssertionError(
          "Expected inline HOTP algorithm select to apply compact styling class");
    }

    String digitsClass = digitsInput.getAttribute("class");
    if (digitsClass == null || !digitsClass.contains("input-compact")) {
      throw new AssertionError("Expected inline HOTP digits input to apply compact styling class");
    }

    String counterClass = counterInput.getAttribute("class");
    if (counterClass == null || !counterClass.contains("input-compact")) {
      throw new AssertionError("Expected inline HOTP counter input to apply compact styling class");
    }
  }

  @Test
  @DisplayName("Inline HOTP evaluation succeeds via operator console presets")
  void inlineHotpEvaluationSucceeds() {
    navigateToHotpPanel();

    driver.findElement(By.cssSelector("[data-testid='hotp-mode-select-inline']")).click();

    WebElement inlinePanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-evaluation-panel']")));

    String inlineAriaLabel = inlinePanel.getAttribute("aria-label");
    if (inlineAriaLabel == null || inlineAriaLabel.isBlank()) {
      throw new AssertionError("Expected HOTP inline panel to expose aria-label metadata");
    }

    if (!inlinePanel.findElements(By.tagName("h2")).isEmpty()) {
      throw new AssertionError("HOTP inline panel should not render redundant headings");
    }

    if (!driver.findElements(By.id("hotpInlineIdentifier")).isEmpty()) {
      throw new AssertionError("HOTP inline identifier field should not be rendered");
    }

    WebElement presetContainer =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-preset']")));

    WebElement presetLabel = presetContainer.findElement(By.tagName("label"));
    if (!presetLabel.getText().contains("Load a sample vector")) {
      throw new AssertionError("Expected inline preset label to mention sample vectors");
    }

    Select inlinePresetSelect =
        new Select(driver.findElement(By.cssSelector("[data-testid='hotp-inline-preset-select']")));
    if (inlinePresetSelect.getOptions().size() < 3) {
      throw new AssertionError("HOTP inline presets should expose at least two sample options");
    }
    boolean sha1Present =
        inlinePresetSelect.getOptions().stream()
            .anyMatch(option -> "demo-inline".equals(option.getAttribute("value")));
    boolean sha256Present =
        inlinePresetSelect.getOptions().stream()
            .anyMatch(option -> INLINE_SHA256_PRESET_KEY.equals(option.getAttribute("value")));
    if (!sha1Present || !sha256Present) {
      throw new AssertionError("Expected inline presets to expose SHA-1 and SHA-256 demo vectors");
    }

    inlinePresetSelect.selectByValue(INLINE_SHA256_PRESET_KEY);

    WebElement secretField = driver.findElement(By.id("hotpInlineSecretHex"));
    WebElement algorithmField = driver.findElement(By.id("hotpInlineAlgorithm"));
    WebElement digitsField = driver.findElement(By.id("hotpInlineDigits"));
    WebElement counterField = driver.findElement(By.id("hotpInlineCounter"));

    if (!SECRET_HEX.equals(secretField.getAttribute("value"))) {
      throw new AssertionError("Inline preset should populate shared secret material");
    }
    Select algorithmSelect = new Select(algorithmField);
    String algorithmValue =
        algorithmSelect.getFirstSelectedOption() != null
            ? algorithmSelect.getFirstSelectedOption().getAttribute("value")
            : null;
    if (!"SHA256".equals(algorithmValue)) {
      throw new AssertionError("Inline preset should select SHA-256 algorithm");
    }
    if (!Integer.toString(INLINE_SHA256_DIGITS).equals(digitsField.getAttribute("value"))) {
      throw new AssertionError("Inline preset should populate digit count");
    }
    if (!Long.toString(INLINE_SHA256_COUNTER).equals(counterField.getAttribute("value"))) {
      throw new AssertionError("Inline preset should populate counter value");
    }

    if (!driver.findElements(By.id("hotpInlineOtp")).isEmpty()) {
      throw new AssertionError("HOTP inline evaluation should not render an OTP input");
    }
    driver.findElement(By.cssSelector("button[data-testid='hotp-inline-evaluate-button']")).click();

    WebElement resultPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-testid='hotp-inline-result-panel']")));

    WebElement heading =
        resultPanel.findElement(By.cssSelector("[data-testid='hotp-result-heading']"));
    if (!"Evaluation result".equals(heading.getText().trim())) {
      throw new AssertionError("Expected HOTP inline result heading to read 'Evaluation result'");
    }

    WebElement statusColumn = locateStatusColumn();
    WebElement firstVisible = firstVisibleChild(statusColumn);
    if (!"hotp-inline-result-panel".equals(firstVisible.getAttribute("data-testid"))) {
      throw new AssertionError(
          "Expected HOTP inline result panel to be the first visible child of the status column");
    }

    assertResultRowsMatchExpectations(resultPanel, EXPECTED_INLINE_SHA256_OTP);
  }

  private WebElement assertHotpInlineDefaultState() {
    By modeToggleLocator = By.cssSelector("[data-testid='hotp-mode-toggle']");
    WebElement modeToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.presenceOfElementLocated(modeToggleLocator));
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              String mode = modeToggle.getAttribute("data-mode");
              return mode != null && !mode.isBlank();
            });

    if (!"inline".equals(modeToggle.getAttribute("data-mode"))) {
      throw new AssertionError("Expected HOTP evaluate mode to default to inline parameters");
    }

    WebElement inlineToggle =
        modeToggle.findElement(By.cssSelector("[data-testid='hotp-mode-select-inline']"));
    WebElement storedToggle =
        modeToggle.findElement(By.cssSelector("[data-testid='hotp-mode-select-stored']"));

    if (!inlineToggle.isSelected()) {
      throw new AssertionError("Inline HOTP mode toggle should be selected by default");
    }
    if (storedToggle.isSelected()) {
      throw new AssertionError("Stored HOTP mode toggle should be deselected by default");
    }

    By inlinePanelLocator = By.cssSelector("[data-testid='hotp-inline-evaluation-panel']");
    By storedPanelLocator = By.cssSelector("[data-testid='hotp-stored-evaluation-panel']");
    WebElement inlinePanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.visibilityOfElementLocated(inlinePanelLocator));
    WebElement storedPanel = driver.findElement(storedPanelLocator);

    if (!inlinePanel.isDisplayed()) {
      throw new AssertionError("Inline HOTP evaluation panel should be visible by default");
    }
    if (storedPanel.isDisplayed()) {
      throw new AssertionError("Stored HOTP evaluation panel should remain hidden until selected");
    }

    return modeToggle;
  }

  private void navigateToHotpPanel() {
    driver.get("http://localhost:" + port + "/ui/console?protocol=hotp");
    WebElement tab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-hotp']"));
    tab.click();
  }

  private WebElement locateStatusColumn() {
    WebElement cardBody =
        driver.findElement(By.cssSelector("[data-testid='hotp-evaluate-panel'] .card-shell__body"));
    List<WebElement> columnContainers =
        cardBody.findElements(
            By.xpath(
                "./*[contains(concat(' ', normalize-space(@class), ' '), ' section-columns ')]"));
    if (columnContainers.size() != 1) {
      throw new AssertionError(
          "Expected HOTP evaluate panel to expose a single section-columns container");
    }
    List<WebElement> statusColumns =
        columnContainers
            .get(0)
            .findElements(
                By.xpath(
                    "./div[contains(concat(' ', normalize-space(@class), ' '), ' status-column ')]"));
    if (statusColumns.size() != 1) {
      throw new AssertionError(
          "Expected HOTP evaluate panel to expose a single status column alongside the form");
    }
    return statusColumns.get(0);
  }

  private WebElement firstVisibleChild(WebElement statusColumn) {
    List<WebElement> children = statusColumn.findElements(By.xpath("./*"));
    for (WebElement child : children) {
      if (child.getAttribute("hidden") == null) {
        return child;
      }
    }
    throw new AssertionError("Status column should expose at least one visible child panel");
  }

  private void assertResultRowsMatchExpectations(WebElement resultPanel, String expectedOtp) {
    WebElement otpRow = resultPanel.findElement(By.cssSelector(".result-inline"));
    String otpRowText = otpRow.getText().trim().replaceAll("\\s+", " ");
    if (!otpRowText.startsWith("OTP:")) {
      throw new AssertionError("Expected OTP row to start with 'OTP:'");
    }
    WebElement otpValueNode = otpRow.findElement(By.tagName("strong"));
    if (!expectedOtp.equals(otpValueNode.getText().trim())) {
      throw new AssertionError("Expected OTP row value to match generated OTP");
    }

    List<WebElement> rows =
        resultPanel.findElements(By.cssSelector(".result-metadata .result-row"));
    if (rows.size() != 1) {
      throw new AssertionError("Expected a single status row in result metadata");
    }

    WebElement statusRow = rows.get(0);
    String statusLabel =
        statusRow.findElement(By.tagName("dt")).getText().trim().replace(":", "").trim();
    if (!"Status".equalsIgnoreCase(statusLabel)) {
      throw new AssertionError("Expected Status row label to read 'Status'");
    }
    WebElement statusValueNode =
        statusRow.findElement(By.cssSelector("dd [data-testid='hotp-result-status']"));
    String statusValue = statusValueNode.getText().trim();
    if (!"Success".equalsIgnoreCase(statusValue)) {
      throw new AssertionError("Expected Status row value to read 'Success'");
    }
    String statusClass = statusValueNode.getAttribute("class");
    if (statusClass == null || !statusClass.contains("status-badge--success")) {
      throw new AssertionError("Expected Status badge to carry success styling");
    }

    if (!resultPanel
        .findElements(By.cssSelector("[data-testid='hotp-result-metadata']"))
        .isEmpty()) {
      throw new AssertionError("HOTP result panel should not render additional metadata copy");
    }
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

  private double topOf(WebElement element) {
    Object result =
        ((JavascriptExecutor) driver)
            .executeScript("return arguments[0].getBoundingClientRect().top;", element);
    if (result instanceof Number number) {
      return number.doubleValue();
    }
    throw new AssertionError("Expected numeric bounding top but received: " + result);
  }

  private double bottomOf(WebElement element) {
    Object result =
        ((JavascriptExecutor) driver)
            .executeScript("return arguments[0].getBoundingClientRect().bottom;", element);
    if (result instanceof Number number) {
      return number.doubleValue();
    }
    throw new AssertionError("Expected numeric bounding bottom but received: " + result);
  }
}
