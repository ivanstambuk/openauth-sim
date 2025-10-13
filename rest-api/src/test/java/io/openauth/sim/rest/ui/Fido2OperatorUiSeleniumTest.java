package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
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
 * Selenium scaffolding for the FIDO2/WebAuthn operator console coverage. Tests remain red until the
 * console implements the WebAuthn panel interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class Fido2OperatorUiSeleniumTest {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final String STORED_CREDENTIAL_ID = "generator-es256";

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("fido2-operator.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private CredentialStore credentialStore;
  @LocalServerPort private int port;

  private HtmlUnitDriver driver;

  private final io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter persistenceAdapter =
      new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter();

  @BeforeEach
  void setUp() {
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));

    credentialStore.delete(STORED_CREDENTIAL_ID);
    credentialStore.delete("fido2-packed-es256");
    seedStoredCredential();
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @DisplayName("Stored WebAuthn generation renders a PublicKeyCredential payload")
  void storedGenerationDisplaysGeneratedAssertion() {
    navigateToWebAuthnPanel();

    WebElement tabs = waitFor(By.cssSelector("[data-testid='fido2-panel-tabs']"));
    assertThat(tabs.isDisplayed()).isTrue();

    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
    assertThat(evaluateTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement evaluateModeToggle =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
    assertThat(evaluateModeToggle.getAttribute("data-mode")).isEqualTo("inline");

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    assertThat(credentialSelect.getOptions()).hasSizeGreaterThanOrEqualTo(2);
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    if (seedButton.isDisplayed() && seedButton.isEnabled()) {
      seedButton.click();
      awaitText(
          By.cssSelector("[data-testid='fido2-seed-status']"),
          text -> text != null && text.contains("addedCount"));
    }

    WebElement loadSample = waitFor(By.cssSelector("[data-testid='fido2-stored-load-sample']"));
    loadSample.click();

    WebElement storedChallengeField = waitFor(By.id("fido2StoredChallenge"));
    assertThat(storedChallengeField.getAttribute("rows")).isEqualTo("1");

    WebElement originInput = driver.findElement(By.id("fido2StoredOrigin"));
    assertThat(originInput.getAttribute("value")).isEqualTo("https://example.org");

    WebElement privateKeyField = waitFor(By.id("fido2StoredPrivateKey"));
    assertThat(privateKeyField.findElement(By.xpath("..")).getAttribute("class"))
        .contains("field-group--stacked");
    assertThat(privateKeyField.getAttribute("value")).contains("\"kty\"");
    assertThat(privateKeyField.getAttribute("value")).contains("\n  \"kty\"");
    assertThat(privateKeyField.getAttribute("value")).contains("\n  \"kty\"");

    WebElement submitButton =
        driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
    submitButton.click();

    By storedAssertionSelector = By.cssSelector("[data-testid='fido2-generated-assertion-json']");
    awaitText(storedAssertionSelector, text -> text.contains("\"type\": \"public-key\""));
    WebElement assertionJson = waitFor(storedAssertionSelector);
    assertThat(assertionJson.getText()).contains("\"type\": \"public-key\"");
    assertThat(assertionJson.getText()).contains("\"clientDataJSON\"");

    WebElement copyButton =
        driver.findElement(By.cssSelector("[data-testid='fido2-copy-assertion']"));
    assertThat(copyButton.isDisplayed()).isTrue();

    By storedMetadataSelector =
        By.cssSelector("[data-testid='fido2-generated-assertion-metadata']");
    awaitText(storedMetadataSelector, text -> text.contains("credentialSource=stored"));
    WebElement metadata = waitFor(storedMetadataSelector);
    assertThat(metadata.getText()).contains("credentialSource=stored");
  }

  @Test
  @DisplayName("Generated assertion panel stays within the clamped width")
  void generatedAssertionPanelStaysClamped() {
    navigateToWebAuthnPanel();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    if (seedButton.isDisplayed() && seedButton.isEnabled()) {
      seedButton.click();
      awaitText(
          By.cssSelector("[data-testid='fido2-seed-status']"),
          text -> text != null && text.contains("addedCount"));
    }

    WebElement loadSample = waitFor(By.cssSelector("[data-testid='fido2-stored-load-sample']"));
    loadSample.click();

    WebElement submitButton =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
    submitButton.click();

    By storedAssertionSelector = By.cssSelector("[data-testid='fido2-generated-assertion-json']");
    awaitText(storedAssertionSelector, text -> text.contains("\"type\": \"public-key\""));

    WebElement formColumn =
        waitFor(
            By.cssSelector("[data-testid='fido2-evaluate-panel'] .section-columns > .stack-lg"));
    WebElement statusColumn =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel'] .status-column"));

    int formWidth = formColumn.getRect().getWidth();
    int statusWidth = statusColumn.getRect().getWidth();
    int formClientWidth =
        ((Number)
                ((JavascriptExecutor) driver)
                    .executeScript("return arguments[0].clientWidth;", formColumn))
            .intValue();
    int statusClientWidth =
        ((Number)
                ((JavascriptExecutor) driver)
                    .executeScript("return arguments[0].clientWidth;", statusColumn))
            .intValue();

    assertThat(statusClientWidth)
        .as(
            "status column width must remain clamped (client=%d, rect=%d)",
            statusClientWidth, statusWidth)
        .isLessThanOrEqualTo(600);
    assertThat(statusClientWidth)
        .as(
            "status column should remain narrower than the evaluation form column (statusClient=%d, formClient=%d, statusRect=%d, formRect=%d)",
            statusClientWidth, formClientWidth, statusWidth, formWidth)
        .isLessThan(formClientWidth);
  }

  @Test
  @DisplayName("Seed sample credential control hides outside stored mode")
  void seedControlHidesOutsideStoredMode() {
    credentialStore.delete(STORED_CREDENTIAL_ID);
    credentialStore.delete("fido2-packed-es256");

    navigateToWebAuthnPanel();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    By seedActionsSelector = By.cssSelector("[data-testid='fido2-seed-actions']");
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "false"));
    WebElement seedActions = waitFor(seedActionsSelector);
    assertThat(seedActions.getAttribute("hidden")).isNull();

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    assertThat(seedButton.getAttribute("disabled")).isNull();

    WebElement inlineRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"));
    inlineRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "true"));
    assertThat(seedActions.getAttribute("hidden")).isNotNull();
    assertThat(seedButton.getAttribute("disabled")).isNotNull();

    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "false"));
    assertThat(seedActions.getAttribute("hidden")).isNull();
    assertThat(seedButton.getAttribute("disabled")).isNull();
  }

  @Test
  @DisplayName("Evaluate CTA uses mode-specific copy and attributes")
  void evaluateButtonCopyMatchesMode() {
    navigateToWebAuthnPanel();

    WebElement inlineButton =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-inline-submit']"));
    assertThat(inlineButton.getAttribute("data-inline-label"))
        .isEqualTo("Generate inline assertion");
    assertThat(inlineButton.getText().trim()).isEqualTo("Generate inline assertion");

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    WebElement storedButton =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
    assertThat(storedButton.getAttribute("data-stored-label"))
        .isEqualTo("Generate stored assertion");
    assertThat(storedButton.getText().trim()).isEqualTo("Generate stored assertion");
  }

  @Test
  @DisplayName("Inline WebAuthn generation renders a PublicKeyCredential payload")
  void inlineGenerationDisplaysGeneratedAssertion() {
    navigateToWebAuthnPanel();

    WebElement inlineRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"));
    inlineRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");

    List<String> expectedLabels =
        WebAuthnGeneratorSamples.samples().stream()
            .map(Fido2OperatorUiSeleniumTest::expectedInlineLabel)
            .toList();
    assertThat(expectedLabels).isNotEmpty();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(
            webDriver -> {
              Select select = new Select(webDriver.findElement(By.id("fido2InlineSampleSelect")));
              return select.getOptions().size() - 1 >= expectedLabels.size();
            });
    WebElement inlineSelectElement = waitFor(By.id("fido2InlineSampleSelect"));
    Select inlineSelect = new Select(inlineSelectElement);
    assertThat(
            inlineSelect.getOptions().stream()
                .skip(1)
                .map(WebElement::getText)
                .map(String::trim)
                .toList())
        .containsExactlyElementsOf(expectedLabels);
    if (inlineSelect.getOptions().size() > 1) {
      inlineSelect.selectByIndex(1);
      dispatchChange(inlineSelectElement);
    }

    WebElement challengeField = waitFor(By.id("fido2InlineChallenge"));
    assertThat(challengeField.getAttribute("rows")).isEqualTo("1");

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(
            webDriver ->
                !new Select(webDriver.findElement(By.id("fido2InlineAlgorithm")))
                    .getFirstSelectedOption()
                    .getAttribute("value")
                    .isBlank());
    Select algorithmField = new Select(waitFor(By.id("fido2InlineAlgorithm")));
    assertThat(algorithmField.getFirstSelectedOption().getAttribute("value")).isNotBlank();

    WebElement privateKeyField = waitFor(By.id("fido2InlinePrivateKey"));
    assertThat(privateKeyField.findElement(By.xpath("..")).getAttribute("class"))
        .contains("field-group--stacked");
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(driver1 -> privateKeyField.getAttribute("value").contains("\"kty\""));
    assertThat(privateKeyField.getAttribute("value")).contains("\"kty\"");

    WebElement evaluateButton =
        driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-inline-submit']"));
    evaluateButton.click();

    By inlineAssertionSelector = By.cssSelector("[data-testid='fido2-inline-generated-json']");
    awaitText(inlineAssertionSelector, text -> text.contains("\"type\": \"public-key\""));
    WebElement assertionJson = waitFor(inlineAssertionSelector);
    assertThat(assertionJson.getText()).contains("\"type\": \"public-key\"");
    WebElement inlineError =
        driver.findElement(By.cssSelector("[data-testid='fido2-inline-error']"));
    assertThat(inlineError.isDisplayed()).isFalse();
  }

  @Test
  @DisplayName("Inline WebAuthn generation reports invalid private key errors")
  void inlineGenerationReportsInvalidPrivateKey() {
    navigateToWebAuthnPanel();

    WebElement inlineRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"));
    inlineRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.elementToBeClickable(By.id("fido2InlineSampleSelect")));
    WebElement inlineSelectElement = waitFor(By.id("fido2InlineSampleSelect"));
    Select inlineSelect = new Select(inlineSelectElement);
    if (inlineSelect.getOptions().size() > 1) {
      inlineSelect.selectByIndex(1);
      dispatchChange(inlineSelectElement);
    }

    WebElement privateKeyField = waitFor(By.id("fido2InlinePrivateKey"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(driver1 -> privateKeyField.getAttribute("value").contains("\"kty\""));
    privateKeyField.clear();
    privateKeyField.sendKeys("{\"kty\":\"EC\",\"crv\":\"P-256\",\"d\":\"invalid\"}");

    WebElement evaluateButton =
        driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-inline-submit']"));
    evaluateButton.click();

    By inlineErrorSelector = By.cssSelector("[data-testid='fido2-inline-error']");
    awaitText(inlineErrorSelector, text -> text.contains("private_key_invalid"));
    WebElement errorBanner = waitFor(inlineErrorSelector);
    assertThat(errorBanner.getText()).contains("private_key_invalid");
  }

  @Test
  @DisplayName("Inline signature counter snapshots refresh via reset helper")
  void inlineCounterResetUpdatesEpochSeconds() throws InterruptedException {
    navigateToWebAuthnPanel();

    By counterSelector = By.id("fido2InlineCounter");
    WebElement counterInput = waitFor(counterSelector);
    long initialValue = Long.parseLong(counterInput.getAttribute("value"));
    assertThat(initialValue).isGreaterThan(0L);

    WebElement toggleCluster = waitFor(By.cssSelector(".field-group--counter-toggle"));
    assertThat(toggleCluster.getAttribute("class")).contains("field-group--checkbox");
    List<WebElement> clusterChildren = toggleCluster.findElements(By.xpath("./*"));
    assertThat(clusterChildren).hasSize(3);
    assertThat(clusterChildren.get(0).getTagName()).isEqualTo("input");
    assertThat(clusterChildren.get(1).getTagName()).isEqualTo("label");
    assertThat(clusterChildren.get(2).getTagName()).isEqualTo("button");
    assertThat(clusterChildren.get(1).getAttribute("for")).isEqualTo("fido2InlineCounterAuto");
    assertThat(clusterChildren.get(2).getAttribute("data-testid"))
        .isEqualTo("fido2-inline-counter-reset");

    WebElement resetButton = waitFor(By.cssSelector("[data-testid='fido2-inline-counter-reset']"));
    Thread.sleep(1_050L);
    resetButton.click();
    awaitCounterValueChange(counterSelector, initialValue);

    long afterReset = Long.parseLong(waitFor(counterSelector).getAttribute("value"));
    assertThat(afterReset).isGreaterThanOrEqualTo(initialValue);
    assertThat(Math.abs(afterReset - Instant.now().getEpochSecond())).isLessThanOrEqualTo(5L);

    WebElement toggle = waitFor(By.cssSelector("[data-testid='fido2-inline-counter-toggle']"));
    toggle.click();
    awaitCounterEditable(counterSelector);

    WebElement hint = waitFor(By.cssSelector("[data-testid='fido2-inline-counter-hint']"));
    assertThat(hint.getText()).contains("Manual entry");

    WebElement editableCounter = waitFor(counterSelector);
    editableCounter.clear();
    editableCounter.sendKeys("123");

    Thread.sleep(1_050L);
    resetButton.click();
    awaitCounterValueChange(counterSelector, 123L);

    long manualReset = Long.parseLong(waitFor(counterSelector).getAttribute("value"));
    assertThat(Math.abs(manualReset - Instant.now().getEpochSecond())).isLessThanOrEqualTo(5L);
    assertThat(waitFor(counterSelector).getAttribute("readonly")).isNull();
    assertThat(hint.getText()).contains("Manual entry enabled");
  }

  @Test
  @DisplayName("Stored WebAuthn replay reports match status")
  void storedReplayReportsMatchStatus() {
    navigateToWebAuthnPanel();

    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

    WebElement replayModeToggle =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"));
    assertThat(replayModeToggle.getAttribute("data-mode")).isEqualTo("inline");

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2ReplayCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    By storedResultSelector = By.cssSelector("[data-testid='fido2-replay-result']");
    waitUntilAttribute(storedResultSelector, "aria-hidden", "true");
    WebElement storedResultPanel = waitFor(storedResultSelector);
    assertThat(storedResultPanel.getAttribute("aria-hidden")).isEqualTo("true");

    WebElement replaySubmit =
        driver.findElement(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
    replaySubmit.click();

    awaitVisible(By.cssSelector("[data-testid='fido2-replay-result']"));
    awaitText(
        By.cssSelector("[data-testid='fido2-replay-result'] [data-testid='fido2-replay-status']"),
        text -> !"pending".equalsIgnoreCase(text));

    WebElement status =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-result'] [data-testid='fido2-replay-status']"));
    WebElement telemetry = waitFor(By.cssSelector("[data-testid='fido2-replay-telemetry']"));
    WebElement reasonElement =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-result'] [data-testid='fido2-replay-reason']"));
    String replayStatus = status.getText();
    String replayReason = reasonElement.getText();
    String replayTelemetry = telemetry.getText();
    assertThat(replayStatus).isEqualToIgnoringCase("match");
    assertThat(replayReason).isEqualToIgnoringCase("validated");
    assertThat(replayTelemetry).doesNotContain("challenge=").doesNotContain("signature=");
  }

  @Test
  @DisplayName("Replay CTA uses mode-specific copy and attributes")
  void replayButtonCopyMatchesMode() {
    navigateToWebAuthnPanel();

    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

    WebElement inlineButton = waitFor(By.cssSelector("[data-testid='fido2-replay-inline-submit']"));
    assertThat(inlineButton.getAttribute("data-inline-label")).isEqualTo("Replay inline assertion");
    assertThat(inlineButton.getText().trim()).isEqualTo("Replay inline assertion");

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

    WebElement storedButton = waitFor(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
    assertThat(storedButton.getAttribute("data-stored-label")).isEqualTo("Replay stored assertion");
    assertThat(storedButton.getText().trim()).isEqualTo("Replay stored assertion");
  }

  @Test
  @DisplayName("Replay tab exposes inline mode with sample vectors")
  void inlineReplayLoadsSampleVectors() {
    navigateToWebAuthnPanel();

    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

    WebElement inlineRadio =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-inline']"));
    inlineRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "inline");

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.elementToBeClickable(By.id("fido2ReplayInlineSampleSelect")));
    Select sampleSelect = new Select(driver.findElement(By.id("fido2ReplayInlineSampleSelect")));
    if (sampleSelect.getOptions().size() > 1) {
      sampleSelect.selectByIndex(1);
    }

    By inlineResultSelector = By.cssSelector("[data-testid='fido2-replay-inline-result']");
    waitUntilAttribute(inlineResultSelector, "aria-hidden", "true");
    WebElement inlineResultPanel = waitFor(inlineResultSelector);
    assertThat(inlineResultPanel.getAttribute("aria-hidden")).isEqualTo("true");

    WebElement credentialIdField = waitFor(By.id("fido2ReplayInlineCredentialId"));
    assertThat(credentialIdField.getAttribute("value")).isNotEmpty();
    Select algorithmSelect =
        new Select(waitFor(By.cssSelector("[data-testid='fido2-replay-inline-algorithm']")));
    assertThat(algorithmSelect.getFirstSelectedOption().getAttribute("value")).isNotEmpty();

    WebElement submit =
        driver.findElement(By.cssSelector("[data-testid='fido2-replay-inline-submit']"));
    submit.click();

    awaitVisible(By.cssSelector("[data-testid='fido2-replay-inline-result']"));
    awaitText(
        By.cssSelector(
            "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-status']"),
        text -> !"pending".equalsIgnoreCase(text));

    WebElement status =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-status']"));
    assertThat(status.getText()).isEqualToIgnoringCase("match");

    WebElement telemetry = waitFor(By.cssSelector("[data-testid='fido2-replay-inline-telemetry']"));
    assertThat(telemetry.getText()).doesNotContain("challenge=").doesNotContain("signature=");
  }

  @Test
  @DisplayName("FIDO2 deep-link mode stays active across refresh and history navigation")
  void deepLinkReplayModePersistsAcrossRefresh() {
    String url = baseUrl("/ui/console?protocol=fido2&fido2Mode=replay");
    driver.get(url);
    waitFor(By.cssSelector("[data-protocol-panel='fido2']"));

    assertReplayTabSelected();

    driver.navigate().refresh();
    waitFor(By.cssSelector("[data-protocol-panel='fido2']"));
    assertReplayTabSelected();

    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
    evaluateTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"), "aria-selected", "true");
    assertEvaluateTabSelected();

    driver.navigate().back();
    waitFor(By.cssSelector("[data-protocol-panel='fido2']"));
    assertReplayTabSelected();
  }

  private void seedStoredCredential() {
    Sample sample =
        WebAuthnGeneratorSamples.findByKey(STORED_CREDENTIAL_ID)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Generator sample not found for stored credential: "
                            + STORED_CREDENTIAL_ID));
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name(sample.key())
            .relyingPartyId(sample.relyingPartyId())
            .credentialId(sample.credentialId())
            .publicKeyCose(sample.publicKeyCose())
            .signatureCounter(sample.signatureCounter())
            .userVerificationRequired(sample.userVerificationRequired())
            .algorithm(sample.algorithm())
            .build();
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
    credentialStore.save(credential);
  }

  private void navigateToWebAuthnPanel() {
    driver.get(baseUrl("/ui/console?protocol=fido2"));
    waitFor(By.cssSelector("[data-testid='protocol-tab-fido2']"));

    WebElement tab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']"));
    if (!"true".equals(tab.getAttribute("aria-selected"))) {
      tab.click();
    }

    waitFor(By.cssSelector("[data-protocol-panel='fido2']"));
  }

  private void awaitVisible(By selector) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.visibilityOfElementLocated(selector));
  }

  private WebElement waitFor(By selector) {
    return new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.presenceOfElementLocated(selector));
  }

  private void dispatchChange(WebElement element) {
    ((JavascriptExecutor) driver)
        .executeScript(
            "var event = new Event('change', { bubbles: true }); arguments[0].dispatchEvent(event);",
            element);
  }

  private void waitUntilAttribute(By selector, String attribute, String expectedValue) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(selector, attribute, expectedValue));
  }

  private void awaitText(By selector, java.util.function.Predicate<String> predicate) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            webDriver -> {
              String text = webDriver.findElement(selector).getText().trim();
              return predicate.test(text);
            });
  }

  private void waitForOption(By selector, String value) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(
            webDriver -> {
              try {
                Select select = new Select(webDriver.findElement(selector));
                return select.getOptions().stream()
                    .anyMatch(option -> value.equals(option.getAttribute("value")));
              } catch (StaleElementReferenceException ignored) {
                return false;
              }
            });
  }

  private void awaitCounterValueChange(By selector, long previousValue) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(
            webDriver -> {
              String value = webDriver.findElement(selector).getAttribute("value");
              if (value == null || value.isBlank()) {
                return false;
              }
              try {
                long parsed = Long.parseLong(value);
                return parsed != previousValue;
              } catch (NumberFormatException ignored) {
                return false;
              }
            });
  }

  private void awaitCounterEditable(By selector) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(webDriver -> webDriver.findElement(selector).getAttribute("readonly") == null);
  }

  private void assertReplayTabSelected() {
    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
    assertThat(replayTab.getAttribute("aria-selected"))
        .as("replay tab aria-selected")
        .isEqualTo("true");
    assertThat(evaluateTab.getAttribute("aria-selected"))
        .as("evaluate tab aria-selected")
        .isEqualTo("false");
    WebElement replayPanel = waitFor(By.cssSelector("[data-testid='fido2-replay-panel']"));
    assertThat(replayPanel.getAttribute("hidden")).as("replay panel hidden attribute").isNull();
    WebElement evaluatePanel = waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel']"));
    assertThat(evaluatePanel.getAttribute("hidden"))
        .as("evaluate panel hidden attribute")
        .isNotNull();
    WebElement replayModeToggle =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"));
    assertThat(replayModeToggle.getAttribute("data-mode"))
        .as("replay mode toggle data-mode")
        .isEqualTo("inline");
    WebElement replayInlineSection =
        waitFor(By.cssSelector("[data-testid='fido2-replay-inline-section']"));
    assertThat(replayInlineSection.getAttribute("hidden"))
        .as("replay inline section hidden attribute")
        .isNull();
    WebElement replayStoredSection =
        waitFor(By.cssSelector("[data-testid='fido2-replay-stored-section']"));
    assertThat(replayStoredSection.getAttribute("hidden"))
        .as("replay stored section hidden attribute")
        .isNotNull();
    WebElement credentialIdField = waitFor(By.id("fido2ReplayInlineCredentialId"));
    assertThat(credentialIdField.isDisplayed()).as("inline credential id visible").isTrue();
    WebElement publicKeyField = waitFor(By.id("fido2ReplayInlinePublicKey"));
    assertThat(publicKeyField.isDisplayed()).as("inline public key visible").isTrue();
  }

  private void assertEvaluateTabSelected() {
    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
    assertThat(evaluateTab.getAttribute("aria-selected"))
        .as("evaluate tab aria-selected")
        .isEqualTo("true");
    assertThat(replayTab.getAttribute("aria-selected"))
        .as("replay tab aria-selected")
        .isEqualTo("false");
    WebElement replayPanel = waitFor(By.cssSelector("[data-testid='fido2-replay-panel']"));
    assertThat(replayPanel.getAttribute("hidden")).as("replay panel hidden attribute").isNotNull();
    WebElement evaluatePanel = waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel']"));
    assertThat(evaluatePanel.getAttribute("hidden")).as("evaluate panel hidden attribute").isNull();
  }

  private static String expectedInlineLabel(Sample sample) {
    String baseLabel =
        sample.algorithm().label()
            + " - "
            + (sample.userVerificationRequired() ? "UV required" : "UV optional");
    if ("w3c".equalsIgnoreCase(sample.metadata().get("source"))) {
      return baseLabel + " (W3C Level 3)";
    }
    return baseLabel;
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
