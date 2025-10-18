package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
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
  private static final WebAuthnAttestationVerifier ATTESTATION_VERIFIER =
      new WebAuthnAttestationVerifier();

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

    clearCredentialStore();
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

    By seedActionsSelector = By.cssSelector("[data-testid='fido2-seed-actions']");
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "false"));
    WebElement seedActions = waitFor(seedActionsSelector);
    assertThat(seedActions.getAttribute("hidden")).isNull();
    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    assertThat(seedButton.getAttribute("disabled")).isNull();

    seedButton.click();
    waitForNonBlankText(By.cssSelector("[data-testid='fido2-seed-status']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "false"));
    assertThat(seedActions.getAttribute("hidden")).isNull();

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    assertThat(credentialSelect.getOptions()).hasSizeGreaterThanOrEqualTo(2);
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement storedChallengeField = waitFor(By.id("fido2StoredChallenge"));
    awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());
    assertThat(storedChallengeField.getAttribute("rows")).isEqualTo("1");

    WebElement rpIdInput = waitFor(By.id("fido2StoredRpId"));
    assertThat(rpIdInput.getAttribute("readonly")).isEqualTo("true");
    assertThat(rpIdInput.getAttribute("value")).isEqualTo("example.org");

    WebElement originInput = driver.findElement(By.id("fido2StoredOrigin"));
    assertThat(originInput.getAttribute("value")).isEqualTo("https://example.org");

    WebElement hiddenPrivateKey =
        waitFor(By.cssSelector("[data-testid='fido2-stored-private-key']"));
    assertThat(hiddenPrivateKey.getAttribute("type")).isEqualTo("hidden");
    awaitValue(
        By.cssSelector("[data-testid='fido2-stored-private-key']"),
        value -> value != null && value.contains("\"kty\""));
    String storedPrivateKeyValue = hiddenPrivateKey.getAttribute("value");
    assertThat(storedPrivateKeyValue)
        .as("stored private key should render as pretty-printed JWK")
        .contains("\n")
        .contains("\"kty\"");

    WebElement submitButton =
        driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
    submitButton.click();

    By storedAssertionSelector = By.cssSelector("[data-testid='fido2-generated-assertion-json']");
    awaitText(storedAssertionSelector, text -> text.contains("\"type\": \"public-key\""));
    WebElement assertionJson = waitFor(storedAssertionSelector);
    assertThat(assertionJson.getText()).contains("\"type\": \"public-key\"");
    assertThat(assertionJson.getText()).contains("\"clientDataJSON\"");

    assertThat(
            driver.findElements(
                By.cssSelector("[data-testid='fido2-generated-assertion-metadata']")))
        .as("stored telemetry metadata should be removed")
        .isEmpty();
  }

  @Test
  @DisplayName("Stored credential dropdown uses stacked styling with dark background")
  void storedCredentialDropdownUsesStackedStyling() {
    navigateToWebAuthnPanel();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    if (seedButton.isDisplayed() && seedButton.isEnabled()) {
      seedButton.click();
    }

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(webDriver -> webDriver.findElement(By.id("fido2StoredCredentialId")).isEnabled());

    WebElement selectElement = waitFor(By.id("fido2StoredCredentialId"));
    WebElement fieldGroup = selectElement.findElement(By.xpath(".."));
    assertThat(fieldGroup.getAttribute("class"))
        .as("stored credential field group should use stacked styling")
        .contains("field-group--stacked");

    String backgroundColor = selectElement.getCssValue("background-color");
    assertThat(backgroundColor)
        .as("stored credential dropdown should use dark background")
        .isNotBlank()
        .isNotEqualTo("rgba(255, 255, 255, 1)")
        .isNotEqualTo("rgb(255, 255, 255)");
  }

  @Test
  @DisplayName("Inline mode continues to expose authenticator private-key textarea for editing")
  void inlineModeStillShowsPrivateKeyField() {
    navigateToWebAuthnPanel();

    awaitVisible(By.cssSelector("[data-testid='fido2-evaluate-inline-section']"));

    WebElement inlineTextarea = waitFor(By.id("fido2InlinePrivateKey"));
    assertThat(inlineTextarea.isDisplayed()).isTrue();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    WebElement inlineSection =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-inline-section']"));
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(d -> inlineSection.getAttribute("hidden") != null);
    assertThat(inlineSection.getAttribute("hidden"))
        .as("Inline section should be hidden after switching to stored mode")
        .isNotNull();
  }

  @Test
  @DisplayName("Stored credential dropdown uses algorithm-first preset labels")
  void storedCredentialDropdownUsesAlgorithmFirstLabels() {
    clearCredentialStore();
    seedAllCuratedCredentials();

    navigateToWebAuthnPanel();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    if (seedButton.isDisplayed() && seedButton.isEnabled()) {
      seedButton.click();
    }

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    List<String> optionLabels =
        credentialSelect.getOptions().stream()
            .map(WebElement::getText)
            .map(String::trim)
            .filter(text -> !text.isBlank())
            .filter(text -> !"Select a stored credential".equals(text))
            .toList();

    List<String> expectedLabels =
        Fido2OperatorSampleData.seedDefinitions().stream()
            .sorted(
                Comparator.comparing(
                    Fido2OperatorSampleData.SeedDefinition::credentialId,
                    String::compareToIgnoreCase))
            .map(Fido2OperatorSampleData.SeedDefinition::label)
            .toList();

    assertThat(optionLabels)
        .as("stored credential dropdown labels")
        .containsExactlyElementsOf(expectedLabels);
    assertThat(optionLabels).allMatch(label -> !label.startsWith("Seed "));
    assertThat(optionLabels).allMatch(label -> !label.contains("generator preset"));
  }

  @Test
  @DisplayName("Seeding warns when all curated WebAuthn credentials already exist")
  void seedingWarnsWhenCuratedCredentialsAlreadyExist() {
    clearCredentialStore();
    seedAllCuratedCredentials();

    navigateToWebAuthnPanel();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    assertThat(seedButton.isDisplayed()).isTrue();
    assertThat(seedButton.getAttribute("disabled")).isNull();

    seedButton.click();

    By seedStatusSelector = By.cssSelector("[data-testid='fido2-seed-status']");
    String seedStatusText = waitForNonBlankText(seedStatusSelector);
    String expectedMessage =
        "Seeded 0 sample credentials. All sample credentials are already present.";
    assertThat(seedStatusText).isEqualTo(expectedMessage);

    WebElement seedStatus = waitFor(seedStatusSelector);
    assertThat(seedStatus.getAttribute("hidden")).isNull();
    assertThat(seedStatus.getText().trim()).isEqualTo(expectedMessage);
    assertThat(seedStatus.getAttribute("class"))
        .contains("credential-status")
        .contains("credential-status--warning");
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

    WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
    if (seedButton.isDisplayed() && seedButton.isEnabled()) {
      seedButton.click();
      waitForNonBlankText(By.cssSelector("[data-testid='fido2-seed-status']"));
      waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    }

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);
    awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());
    awaitValue(By.id("fido2StoredPrivateKey"), value -> value != null && value.contains("\"kty\""));

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
    clearCredentialStore();

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
    String seedContainerDisplay =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return window.getComputedStyle(arguments[0]).display;", seedActions);
    assertThat(seedContainerDisplay).isIn("block", "inline", "inline-block");
    String seedButtonDisplay =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript("return window.getComputedStyle(arguments[0]).display;", seedButton);
    assertThat(seedButtonDisplay).isEqualTo("inline-flex");

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
  @DisplayName("Evaluate tab exposes attestation toggle with inline-only mode")
  void attestationToggleDefaultsToInline() {
    navigateToWebAuthnPanel();

    WebElement ceremonyToggle =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"));
    assertThat(ceremonyToggle.getAttribute("data-mode")).isEqualTo("assertion");

    WebElement assertionButton =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-select-assertion']"));
    WebElement attestationButton =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-select-attestation']"));

    assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("true");
    assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("false");

    attestationButton.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"),
        "data-mode",
        "attestation");

    assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("false");
    assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("true");

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
    assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("inline");
    assertThat(modeToggle.getAttribute("data-locked")).isEqualTo("true");

    WebElement storedOption =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    assertThat(storedOption.getAttribute("aria-hidden")).isEqualTo("true");
  }

  @Test
  @DisplayName("Attestation inline form exposes payload inputs and trust-anchor helper")
  void attestationInlineFormExposesInputs() {
    navigateToWebAuthnPanel();
    switchToAttestationEvaluateMode();

    WebAuthnAttestationVector vector = resolveAttestationVector();

    waitForOption(By.id("fido2AttestationSampleSelect"), vector.vectorId());
    WebElement sampleSelectElement = waitFor(By.id("fido2AttestationSampleSelect"));
    Select sampleSelect = new Select(sampleSelectElement);
    sampleSelect.selectByValue(vector.vectorId());
    dispatchChange(sampleSelectElement);

    WebElement credentialKeyField = waitFor(By.id("fido2AttestationCredentialKey"));
    WebElement attestationKeyField = waitFor(By.id("fido2AttestationPrivateKey"));
    WebElement certificateSerialField = waitFor(By.id("fido2AttestationSerial"));
    WebElement signingModeSelect = waitFor(By.id("fido2AttestationSigningMode"));
    WebElement customRootField = waitFor(By.id("fido2AttestationCustomRoot"));
    assertThat(credentialKeyField.getAttribute("rows")).isEqualTo("6");
    assertThat(attestationKeyField.getAttribute("rows")).isEqualTo("6");
    assertThat(certificateSerialField.getAttribute("rows")).isEqualTo("2");
    assertThat(signingModeSelect.getTagName()).isEqualTo("select");
    assertThat(customRootField.getAttribute("rows")).isEqualTo("6");
    assertThat(customRootField.getAttribute("placeholder")).contains("PEM");

    WebElement customRootHelp =
        waitFor(By.cssSelector("[data-testid='fido2-attestation-custom-root-help']"));
    assertThat(customRootHelp.getText()).contains("Optional custom root");

    awaitValue(
        By.id("fido2AttestationCredentialKey"),
        value -> value != null && value.contains("\"kty\""));
    assertThat(credentialKeyField.getAttribute("value"))
        .as("credential key field should use pretty-printed JWK formatting")
        .contains("\n")
        .contains("\"kty\"");
    awaitValue(
        By.id("fido2AttestationPrivateKey"), value -> value != null && value.contains("\"kty\""));
    assertThat(attestationKeyField.getAttribute("value"))
        .as("attestation key field should use pretty-printed JWK formatting")
        .contains("\n")
        .contains("\"kty\"");

    WebElement generateButton = waitFor(By.cssSelector("[data-testid='fido2-attestation-submit']"));
    assertThat(generateButton.getText()).contains("Generate attestation");

    WebElement resultPanel = waitFor(By.cssSelector("[data-testid='fido2-attestation-result']"));
    assertThat(resultPanel.getAttribute("aria-hidden")).isEqualTo("true");
  }

  @Test
  @DisplayName("Attestation generation emits deterministic payloads")
  void attestationGenerationProducesDeterministicPayload() {
    WebAuthnAttestationVector vector = resolveAttestationVector();
    String expectedAttestation = encodeBase64Url(vector.registration().attestationObject());
    String expectedClientData = encodeBase64Url(vector.registration().clientDataJson());
    String expectedCredentialId = encodeBase64Url(vector.registration().credentialId());

    navigateToWebAuthnPanel();
    switchToAttestationEvaluateMode();

    waitForOption(By.id("fido2AttestationSampleSelect"), vector.vectorId());
    WebElement sampleSelectElement = waitFor(By.id("fido2AttestationSampleSelect"));
    Select sampleSelect = new Select(sampleSelectElement);
    sampleSelect.selectByValue(vector.vectorId());
    dispatchChange(sampleSelectElement);

    WebElement generateButton = waitFor(By.cssSelector("[data-testid='fido2-attestation-submit']"));
    generateButton.click();

    By statusSelector = By.cssSelector("[data-testid='fido2-attestation-status']");
    awaitText(
        statusSelector,
        text -> text != null && !text.trim().isEmpty() && !"pending".equalsIgnoreCase(text.trim()));
    WebElement statusBadge = waitFor(statusSelector);
    assertThat(statusBadge.getText().trim()).isEqualToIgnoringCase("success");

    assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-attestation-result-id']")))
        .isEmpty();
    assertThat(
            driver.findElements(By.cssSelector("[data-testid='fido2-attestation-result-format']")))
        .isEmpty();
    assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-attestation-result-mode']")))
        .isEmpty();

    String attestationHtml = driver.getPageSource();
    assertThat(attestationHtml)
        .contains("\"type\": \"public-key\"")
        .contains("\"id\": \"" + expectedCredentialId + "\"")
        .contains("\"rawId\": \"" + expectedCredentialId + "\"")
        .contains("\"attestationObject\": \"" + expectedAttestation + "\"")
        .contains("\"clientDataJSON\": \"" + expectedClientData + "\"")
        .contains("-----BEGIN CERTIFICATE-----");
    assertThat(attestationHtml).doesNotContain("Signature: ");

    List<X509Certificate> certificateChain = verifyAttestation(vector).certificateChain();
    WebElement certificateSection =
        waitFor(By.cssSelector("[data-testid='fido2-attestation-certificate-chain-section']"));
    assertThat(certificateSection.getAttribute("hidden")).isNull();
    WebElement certificateHeading =
        certificateSection.findElement(
            By.cssSelector("[data-testid='fido2-attestation-certificate-heading']"));
    assertThat(certificateHeading.getText().trim())
        .isEqualTo("Certificate chain (" + certificateChain.size() + ")");
    assertThat(certificateHeading.getTagName()).isEqualToIgnoringCase("h3");
    assertThat(certificateHeading.getAttribute("class")).contains("section-title");
    assertThat(
            driver.findElements(
                By.cssSelector("[data-testid='fido2-attestation-result'] .result-subtitle")))
        .as("legacy subtitles should not remain in the attestation result panel")
        .isEmpty();
    WebElement certificateBlock =
        certificateSection.findElement(
            By.cssSelector("[data-testid='fido2-attestation-certificate-chain']"));
    String certificateText = certificateBlock.getText().trim();
    assertThat(certificateText).contains("-----BEGIN CERTIFICATE-----");
    long pemCount =
        certificateText
            .lines()
            .filter(line -> line.startsWith("-----BEGIN CERTIFICATE-----"))
            .count();
    assertThat((int) pemCount).isEqualTo(certificateChain.size());

    assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-attestation-telemetry']")))
        .isEmpty();
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
  @DisplayName("Stored signature counter controls mirror inline behaviour")
  void storedCounterControlsMirrorInlineBehaviour() throws InterruptedException {
    navigateToWebAuthnPanel();

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    long expectedCounter =
        Fido2OperatorSampleData.seedDefinitions().stream()
            .filter(definition -> STORED_CREDENTIAL_ID.equals(definition.credentialId()))
            .mapToLong(Fido2OperatorSampleData.SeedDefinition::signatureCounter)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Stored credential not found in seed definitions: "
                            + STORED_CREDENTIAL_ID));

    By counterSelector = By.id("fido2StoredCounter");
    WebElement counterInput = waitFor(counterSelector);
    assertThat(counterInput.getAttribute("value")).isEqualTo(Long.toString(expectedCounter));
    assertThat(counterInput.getAttribute("readonly")).isNull();

    WebElement toggleCluster =
        waitFor(
            By.cssSelector(
                "[data-testid='fido2-stored-counter-group'] .field-group--counter-toggle"));
    assertThat(toggleCluster.getAttribute("class")).contains("field-group--checkbox");

    WebElement toggle = waitFor(By.cssSelector("[data-testid='fido2-stored-counter-toggle']"));
    assertThat(toggle.isSelected()).isFalse();

    WebElement resetButton = waitFor(By.cssSelector("[data-testid='fido2-stored-counter-reset']"));
    WebElement hint = waitFor(By.cssSelector("[data-testid='fido2-stored-counter-hint']"));
    assertThat(hint.getText()).contains("Manual entry");

    toggle.click();
    awaitCounterReadOnly(counterSelector);
    Thread.sleep(1_050L);
    long beforeReset = Long.parseLong(waitFor(counterSelector).getAttribute("value"));
    resetButton.click();
    awaitCounterValueChange(counterSelector, beforeReset);

    long afterReset = Long.parseLong(waitFor(counterSelector).getAttribute("value"));
    assertThat(Math.abs(afterReset - Instant.now().getEpochSecond())).isLessThanOrEqualTo(5L);
    assertThat(hint.getText()).contains("Last autofill");

    toggle.click();
    awaitCounterEditable(counterSelector);

    WebElement editableCounter = waitFor(counterSelector);
    editableCounter.clear();
    editableCounter.sendKeys("1234");
    assertThat(editableCounter.getAttribute("value")).isEqualTo("1234");
    assertThat(hint.getText()).contains("Manual entry");
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
    WebElement outcomeElement =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-result'] [data-testid='fido2-replay-outcome']"));
    WebElement reasonElement =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-result'] [data-testid='fido2-replay-reason']"));
    String replayStatus = status.getText();
    String replayReason = reasonElement.getText();
    String replayOutcome = outcomeElement.getText();
    assertThat(replayStatus).isEqualToIgnoringCase("match");
    assertThat(status.getAttribute("class")).contains("status-badge");
    assertThat(replayReason).isEqualToIgnoringCase("match");
    assertThat(replayOutcome).isEqualToIgnoringCase("match");
  }

  @Test
  @DisplayName("Stored WebAuthn replay result layout matches HOTP/TOTP/OCRA panels")
  void storedReplayResultLayoutMatchesHotpPanels() {
    navigateToWebAuthnPanel();

    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2ReplayCredentialId"), STORED_CREDENTIAL_ID);
    Select credentialSelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement replaySubmit =
        driver.findElement(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
    replaySubmit.click();

    By storedResultSelector = By.cssSelector("[data-testid='fido2-replay-result']");
    awaitVisible(storedResultSelector);
    WebElement resultPanel = driver.findElement(storedResultSelector);

    assertThat(resultPanel.getAttribute("class")).contains("result-panel");
    WebElement header = resultPanel.findElement(By.cssSelector(".result-header"));
    assertThat(header.findElement(By.tagName("h3")).getText().trim()).isEqualTo("Replay result");
    WebElement statusBadge = header.findElement(By.cssSelector(".status-badge"));
    assertThat(statusBadge.getText().trim()).isNotEmpty();

    WebElement metadata = resultPanel.findElement(By.cssSelector(".result-metadata"));
    List<WebElement> rows = metadata.findElements(By.cssSelector(".result-row"));
    assertThat(rows).hasSize(2);

    WebElement reasonRow = rows.get(0);
    assertThat(reasonRow.findElement(By.tagName("dt")).getText().trim()).isEqualTo("Reason Code");
    WebElement reasonValue =
        reasonRow.findElement(By.cssSelector("[data-testid='fido2-replay-reason']"));
    assertThat(reasonValue.getText().trim()).isNotEmpty();

    WebElement outcomeRow = rows.get(1);
    assertThat(outcomeRow.findElement(By.tagName("dt")).getText().trim()).isEqualTo("Outcome");
    WebElement outcomeValue =
        outcomeRow.findElement(By.cssSelector("[data-testid='fido2-replay-outcome']"));
    assertThat(outcomeValue.getText().trim()).isNotEmpty();

    assertThat(resultPanel.findElements(By.cssSelector("[data-testid='fido2-replay-telemetry']")))
        .as("telemetry block should be removed from the replay result panel")
        .isEmpty();
  }

  @Test
  @DisplayName("Replay tab exposes attestation toggle with inline-only mode")
  void attestationReplayToggleDefaultsToInline() {
    navigateToWebAuthnPanel();
    switchToReplayTab();

    WebElement ceremonyToggle =
        waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"));
    assertThat(ceremonyToggle.getAttribute("data-mode")).isEqualTo("assertion");

    WebElement assertionButton =
        waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-select-assertion']"));
    WebElement attestationButton =
        waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-select-attestation']"));

    assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("true");
    assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("false");

    attestationButton.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"), "data-mode", "attestation");

    assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("false");
    assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("true");

    WebElement modeToggle = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"));
    assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("inline");
    assertThat(modeToggle.getAttribute("data-locked")).isEqualTo("true");

    WebElement storedOption =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    assertThat(storedOption.getAttribute("aria-hidden")).isEqualTo("true");

    WebElement attestationSection =
        waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-section']"));
    assertThat(attestationSection.getAttribute("hidden")).isNull();

    WebElement assertionInlineSection =
        waitFor(By.cssSelector("[data-testid='fido2-replay-inline-section']"));
    assertThat(assertionInlineSection.getAttribute("hidden")).isNotNull();

    WebElement assertionStoredSection =
        waitFor(By.cssSelector("[data-testid='fido2-replay-stored-section']"));
    assertThat(assertionStoredSection.getAttribute("hidden")).isNotNull();
  }

  @Test
  @DisplayName("Attestation replay verifies payload with provided trust anchors")
  void attestationReplayVerifiesWithTrustAnchors() {
    WebAuthnAttestationVector vector = resolveAttestationVector();
    WebAuthnAttestationVerification verification = verifyAttestation(vector);
    List<X509Certificate> anchors =
        verification.certificateChain().isEmpty()
            ? List.of()
            : List.of(
                verification.certificateChain().get(verification.certificateChain().size() - 1));

    navigateToWebAuthnPanel();
    switchToAttestationReplayMode();
    populateReplayAttestationForm(vector);
    applyReplayTrustAnchors(anchors);

    WebElement submitButton =
        waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-submit']"));
    submitButton.click();

    By resultSelector = By.cssSelector("[data-testid='fido2-replay-attestation-result']");
    awaitVisible(resultSelector);

    By statusSelector = By.cssSelector("[data-testid='fido2-replay-attestation-status']");
    awaitText(
        statusSelector,
        text -> {
          String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
          return !normalized.isEmpty()
              && !normalized.contains("awaiting")
              && !"pending".equalsIgnoreCase(normalized);
        });
    assertThat(waitFor(statusSelector).getText().trim()).isEqualToIgnoringCase("success");

    WebElement reason = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-reason']"));
    assertThat(reason.getText().trim()).isEqualToIgnoringCase("match");

    By anchorSourceSelector =
        By.cssSelector("[data-testid='fido2-replay-attestation-anchor-source']");
    awaitText(anchorSourceSelector, text -> !text.toLowerCase(Locale.ROOT).contains("awaiting"));
    WebElement anchorSource = waitFor(anchorSourceSelector);
    String anchorSourceText = anchorSource.getText().trim().toLowerCase(Locale.ROOT);
    assertThat(anchorSourceText).containsAnyOf("provided", "metadata");

    By anchorTrustedSelector =
        By.cssSelector("[data-testid='fido2-replay-attestation-anchor-trusted']");
    awaitText(anchorTrustedSelector, text -> !text.toLowerCase(Locale.ROOT).contains("awaiting"));
    WebElement anchorTrusted = waitFor(anchorTrustedSelector);
    assertThat(anchorTrusted.getText().trim()).isNotBlank();
  }

  @Test
  @DisplayName("Attestation replay surfaces trust anchor validation errors")
  void attestationReplaySurfacesTrustAnchorErrors() {
    WebAuthnAttestationVector vector = resolveAttestationVector();

    navigateToWebAuthnPanel();
    switchToAttestationReplayMode();
    populateReplayAttestationForm(vector);

    WebElement trustAnchorField = waitFor(By.id("fido2ReplayAttestationTrustAnchors"));
    trustAnchorField.clear();
    trustAnchorField.sendKeys("-----BEGIN CERTIFICATE-----\ninvalid\n-----END CERTIFICATE-----");

    WebElement submitButton =
        waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-submit']"));
    submitButton.click();

    By errorSelector = By.cssSelector("[data-testid='fido2-replay-attestation-error']");
    By statusSelector = By.cssSelector("[data-testid='fido2-replay-attestation-status']");
    awaitText(
        statusSelector,
        text -> {
          String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
          return !normalized.isEmpty()
              && !normalized.contains("awaiting")
              && !"pending".equalsIgnoreCase(normalized);
        });
    WebElement status = waitFor(statusSelector);
    assertThat(status.getText().trim()).isEqualToIgnoringCase("success");

    String errorText = waitFor(errorSelector).getText().trim();
    if (!errorText.isBlank()) {
      assertThat(errorText).contains("trust");
    }
  }

  @Test
  @DisplayName("Stored credential selection starts empty and refreshes evaluate and replay forms")
  void storedSelectionDefaultsToPlaceholderAndRefreshesForms() {
    clearCredentialStore();
    seedAllCuratedCredentials();

    List<Fido2OperatorSampleData.SeedDefinition> definitions =
        Fido2OperatorSampleData.seedDefinitions();
    assertThat(definitions.size())
        .as("expected at least two curated WebAuthn stored credentials")
        .isGreaterThan(1);

    Fido2OperatorSampleData.SeedDefinition firstDefinition = definitions.get(0);
    Fido2OperatorSampleData.SeedDefinition secondDefinition = definitions.get(1);

    Map<String, Fido2OperatorSampleData.InlineVector> inlineVectorIndex = new LinkedHashMap<>();
    for (Fido2OperatorSampleData.InlineVector vector : Fido2OperatorSampleData.inlineVectors()) {
      inlineVectorIndex.put(vector.key(), vector);
    }

    Fido2OperatorSampleData.InlineVector firstVector =
        inlineVectorIndex.get(firstDefinition.metadata().get("presetKey"));
    Fido2OperatorSampleData.InlineVector secondVector =
        inlineVectorIndex.get(secondDefinition.metadata().get("presetKey"));

    assertThat(firstVector).as("first curated inline vector").isNotNull();
    assertThat(secondVector).as("second curated inline vector").isNotNull();
    assertThat(secondVector.signatureBase64Url())
        .as("sanity check second vector signature differs from first")
        .isNotEqualTo(firstVector.signatureBase64Url());

    navigateToWebAuthnPanel();

    WebElement evaluateStoredRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    evaluateStoredRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2StoredCredentialId"), firstDefinition.credentialId());
    waitForOption(By.id("fido2StoredCredentialId"), secondDefinition.credentialId());
    Select evaluateSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    assertThat(evaluateSelect.getFirstSelectedOption().getAttribute("value")).isEqualTo("");

    WebElement evaluateSubmit =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
    assertThat(evaluateSubmit.getAttribute("disabled")).isNull();

    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

    WebElement replayStoredRadio =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    replayStoredRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2ReplayCredentialId"), firstDefinition.credentialId());
    waitForOption(By.id("fido2ReplayCredentialId"), secondDefinition.credentialId());
    Select replaySelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
    assertThat(replaySelect.getFirstSelectedOption().getAttribute("value")).isEqualTo("");

    WebElement replaySubmit = waitFor(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
    assertThat(replaySubmit.getAttribute("disabled")).isNull();

    WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
    evaluateTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"), "aria-selected", "true");

    evaluateStoredRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    evaluateStoredRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    WebElement evaluateSelectElement = waitFor(By.id("fido2StoredCredentialId"));
    evaluateSelect = new Select(evaluateSelectElement);
    evaluateSelect.selectByValue(firstDefinition.credentialId());
    dispatchChange(evaluateSelectElement);

    awaitValue(
        By.id("fido2StoredChallenge"),
        value -> firstVector.expectedChallengeBase64Url().equals(value));
    awaitValue(
        By.id("fido2StoredCounter"),
        value -> value != null && value.equals(Long.toString(firstDefinition.signatureCounter())));

    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

    replayStoredRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    replayStoredRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

    replaySelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
    assertThat(replaySelect.getFirstSelectedOption().getAttribute("value"))
        .isEqualTo(firstDefinition.credentialId());
    awaitValue(
        By.id("fido2ReplaySignature"), value -> firstVector.signatureBase64Url().equals(value));
    awaitValue(
        By.id("fido2ReplayChallenge"),
        value -> firstVector.expectedChallengeBase64Url().equals(value));

    replaySelect.selectByValue(secondDefinition.credentialId());
    dispatchChange(waitFor(By.id("fido2ReplayCredentialId")));

    awaitValue(
        By.id("fido2ReplaySignature"), value -> secondVector.signatureBase64Url().equals(value));
    awaitValue(
        By.id("fido2ReplayChallenge"),
        value -> secondVector.expectedChallengeBase64Url().equals(value));

    evaluateTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"), "aria-selected", "true");

    evaluateStoredRadio =
        waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
    evaluateStoredRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

    evaluateSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    assertThat(evaluateSelect.getFirstSelectedOption().getAttribute("value"))
        .isEqualTo(secondDefinition.credentialId());
    awaitValue(
        By.id("fido2StoredChallenge"),
        value -> secondVector.expectedChallengeBase64Url().equals(value));
    awaitValue(
        By.id("fido2StoredCounter"),
        value -> value != null && value.equals(Long.toString(secondDefinition.signatureCounter())));
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
    WebElement publicKeyField = waitFor(By.id("fido2ReplayInlinePublicKey"));
    String publicKeyValue = publicKeyField.getAttribute("value");
    assertThat(publicKeyValue).isNotBlank();
    assertThat(publicKeyValue.trim()).startsWith("{");
    assertThat(publicKeyValue).contains("\"kty\"");
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
    assertThat(status.getAttribute("class")).contains("status-badge");

    WebElement reason =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-reason']"));
    WebElement outcome =
        driver.findElement(
            By.cssSelector(
                "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-outcome']"));
    assertThat(reason.getText()).isEqualToIgnoringCase("match");
    assertThat(outcome.getText()).isEqualToIgnoringCase("match");
  }

  @Test
  @DisplayName("Replay forms expose assertion payload textareas")
  void replayFormsExposeAssertionPayloadTextareas() {
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

    By inlineSampleSelectSelector = By.id("fido2ReplayInlineSampleSelect");
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.elementToBeClickable(inlineSampleSelectSelector));
    WebElement inlineSampleElement = driver.findElement(inlineSampleSelectSelector);
    Select inlineSampleSelect = new Select(inlineSampleElement);
    if (inlineSampleSelect.getOptions().size() > 1) {
      inlineSampleSelect.selectByIndex(1);
      dispatchChange(inlineSampleElement);
    }

    WebElement inlineChallenge = waitFor(By.id("fido2ReplayInlineChallenge"));
    WebElement inlineClientData = waitFor(By.id("fido2ReplayInlineClientData"));
    WebElement inlineAuthenticatorData = waitFor(By.id("fido2ReplayInlineAuthenticatorData"));
    WebElement inlineSignature = waitFor(By.id("fido2ReplayInlineSignature"));

    assertThat(inlineChallenge.isDisplayed()).isTrue();
    assertThat(inlineClientData.isDisplayed()).isTrue();
    assertThat(inlineAuthenticatorData.isDisplayed()).isTrue();
    assertThat(inlineSignature.isDisplayed()).isTrue();

    awaitValue(By.id("fido2ReplayInlineChallenge"), value -> value != null && !value.isBlank());
    awaitValue(By.id("fido2ReplayInlineClientData"), value -> value != null && !value.isBlank());
    awaitValue(
        By.id("fido2ReplayInlineAuthenticatorData"), value -> value != null && value.length() > 16);
    awaitValue(By.id("fido2ReplayInlineSignature"), value -> value != null && !value.isBlank());

    WebElement storedRadio =
        waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
    storedRadio.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

    waitForOption(By.id("fido2ReplayCredentialId"), STORED_CREDENTIAL_ID);
    WebElement storedSelectElement = waitFor(By.id("fido2ReplayCredentialId"));
    Select storedSelect = new Select(storedSelectElement);
    storedSelect.selectByValue(STORED_CREDENTIAL_ID);
    dispatchChange(storedSelectElement);

    WebElement storedChallenge = waitFor(By.id("fido2ReplayChallenge"));
    WebElement storedClientData = waitFor(By.id("fido2ReplayClientData"));
    WebElement storedAuthenticatorData = waitFor(By.id("fido2ReplayAuthenticatorData"));
    WebElement storedSignature = waitFor(By.id("fido2ReplaySignature"));

    assertThat(storedChallenge.isDisplayed()).isTrue();
    assertThat(storedClientData.isDisplayed()).isTrue();
    assertThat(storedAuthenticatorData.isDisplayed()).isTrue();
    assertThat(storedSignature.isDisplayed()).isTrue();

    awaitValue(By.id("fido2ReplayChallenge"), value -> value != null && !value.isBlank());
    awaitValue(By.id("fido2ReplayClientData"), value -> value != null && !value.isBlank());
    awaitValue(
        By.id("fido2ReplayAuthenticatorData"), value -> value != null && value.length() > 16);
    awaitValue(By.id("fido2ReplaySignature"), value -> value != null && !value.isBlank());
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

  private void switchToReplayTab() {
    WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    replayTab.click();
    waitUntilAttribute(
        By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");
  }

  private void switchToAttestationReplayMode() {
    switchToReplayTab();
    WebElement ceremonyToggle =
        waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"));
    if (!"attestation".equals(ceremonyToggle.getAttribute("data-mode"))) {
      WebElement attestationButton =
          waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-select-attestation']"));
      attestationButton.click();
      waitUntilAttribute(
          By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"),
          "data-mode",
          "attestation");
    }
  }

  private WebAuthnAttestationVector resolveAttestationVector() {
    return WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Packed attestation fixture not available for replay"));
  }

  private WebAuthnAttestationVerification verifyAttestation(WebAuthnAttestationVector vector) {
    WebAuthnAttestationRequest request =
        new WebAuthnAttestationRequest(
            vector.format(),
            vector.registration().attestationObject(),
            vector.registration().clientDataJson(),
            vector.registration().challenge(),
            vector.relyingPartyId(),
            vector.origin());
    return ATTESTATION_VERIFIER.verify(request);
  }

  private void populateReplayAttestationForm(WebAuthnAttestationVector vector) {
    setFieldValue(By.id("fido2ReplayAttestationRpId"), vector.relyingPartyId());
    setFieldValue(By.id("fido2ReplayAttestationOrigin"), vector.origin());

    WebElement formatElement = waitFor(By.id("fido2ReplayAttestationFormat"));
    Select formatSelect = new Select(formatElement);
    formatSelect.selectByValue(vector.format().label());
    dispatchChange(formatElement);

    setFieldValue(
        By.id("fido2ReplayAttestationChallenge"),
        encodeBase64Url(vector.registration().challenge()));
    setFieldValue(
        By.id("fido2ReplayAttestationClientDataJson"),
        encodeBase64Url(vector.registration().clientDataJson()));
    setFieldValue(
        By.id("fido2ReplayAttestationObject"),
        encodeBase64Url(vector.registration().attestationObject()));
  }

  private void applyReplayTrustAnchors(List<X509Certificate> anchors) {
    WebElement trustAnchorField = waitFor(By.id("fido2ReplayAttestationTrustAnchors"));
    trustAnchorField.clear();
    String content =
        anchors == null || anchors.isEmpty()
            ? ""
            : anchors.stream().map(this::encodeCertificatePem).collect(Collectors.joining("\n\n"));
    if (!content.isBlank()) {
      trustAnchorField.sendKeys(content);
    }
    dispatchChange(trustAnchorField);
  }

  private void setFieldValue(By selector, String value) {
    WebElement element = waitFor(selector);
    element.clear();
    if (value != null && !value.isBlank()) {
      element.sendKeys(value);
    }
    dispatchChange(element);
  }

  private String encodeCertificatePem(X509Certificate certificate) {
    try {
      byte[] encoded = certificate.getEncoded();
      String body =
          Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
              .encodeToString(encoded);
      return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----";
    } catch (CertificateEncodingException ex) {
      throw new IllegalStateException("Unable to encode certificate to PEM", ex);
    }
  }

  private String encodeBase64Url(byte[] value) {
    return URL_ENCODER.encodeToString(value);
  }

  private void switchToAttestationEvaluateMode() {
    WebElement toggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"));
    if (!"attestation".equals(toggle.getAttribute("data-mode"))) {
      WebElement attestationButton =
          waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-select-attestation']"));
      attestationButton.click();
      waitUntilAttribute(
          By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"),
          "data-mode",
          "attestation");
    }
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

    Credential serialized =
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));

    Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
    Fido2OperatorSampleData.seedDefinitions().stream()
        .filter(definition -> definition.credentialId().equals(STORED_CREDENTIAL_ID))
        .findFirst()
        .ifPresent(
            definition ->
                definition
                    .metadata()
                    .forEach((key, value) -> attributes.put("fido2.metadata." + key, value)));

    Credential persisted =
        new Credential(
            serialized.name(),
            CredentialType.FIDO2,
            serialized.secret(),
            attributes,
            serialized.createdAt(),
            serialized.updatedAt());

    credentialStore.save(persisted);
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
    new WebDriverWait(driver, Duration.ofSeconds(8))
        .until(
            webDriver -> {
              String text = webDriver.findElement(selector).getText().trim();
              return predicate.test(text);
            });
  }

  private void awaitValue(By selector, java.util.function.Predicate<String> predicate) {
    new WebDriverWait(driver, Duration.ofSeconds(8))
        .until(
            webDriver -> {
              String value = webDriver.findElement(selector).getAttribute("value");
              return predicate.test(value);
            });
  }

  private String waitForNonBlankText(By selector) {
    return new WebDriverWait(driver, Duration.ofSeconds(8))
        .until(
            webDriver -> {
              String text = webDriver.findElement(selector).getText();
              if (text == null) {
                return null;
              }
              String normalized = text.trim();
              return normalized.isEmpty() ? null : normalized;
            });
  }

  private void waitForOption(By selector, String value) {
    new WebDriverWait(driver, Duration.ofSeconds(5))
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

  private void awaitCounterReadOnly(By selector) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(webDriver -> webDriver.findElement(selector).getAttribute("readonly") != null);
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

  private void clearCredentialStore() {
    credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
  }

  private void seedAllCuratedCredentials() {
    WebAuthnSeedApplicationService seedService = new WebAuthnSeedApplicationService();
    List<WebAuthnSeedApplicationService.SeedCommand> commands =
        Fido2OperatorSampleData.seedDefinitions().stream()
            .map(
                definition ->
                    new WebAuthnSeedApplicationService.SeedCommand(
                        definition.credentialId(),
                        definition.relyingPartyId(),
                        Base64.getUrlDecoder().decode(definition.credentialIdBase64Url()),
                        Base64.getUrlDecoder().decode(definition.publicKeyCoseBase64Url()),
                        definition.signatureCounter(),
                        definition.userVerificationRequired(),
                        definition.algorithm(),
                        definition.metadata()))
            .toList();
    seedService.seed(commands, credentialStore);
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
