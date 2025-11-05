package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.application.fido2.WebAuthnAttestationSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples;
import io.openauth.sim.application.fido2.WebAuthnGeneratorSamples.Sample;
import io.openauth.sim.application.fido2.WebAuthnSeedApplicationService;
import io.openauth.sim.core.fido2.WebAuthnAttestationCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures;
import io.openauth.sim.core.fido2.WebAuthnAttestationFixtures.WebAuthnAttestationVector;
import io.openauth.sim.core.fido2.WebAuthnAttestationFormat;
import io.openauth.sim.core.fido2.WebAuthnAttestationGenerator;
import io.openauth.sim.core.fido2.WebAuthnAttestationRequest;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerification;
import io.openauth.sim.core.fido2.WebAuthnAttestationVerifier;
import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.json.SimpleJson;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecord;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final String STORED_CREDENTIAL_ID = "packed-es256";
    private static final List<String> CANONICAL_ATTESTATION_SEED_IDS = canonicalAttestationSeedIds();
    private static final WebAuthnAttestationVerifier ATTESTATION_VERIFIER = new WebAuthnAttestationVerifier();
    private static final String EXTENSIONS_CBOR_HEX =
            "a4696372656450726f7073a162726bf56a6372656450726f74656374a166706f6c696379026c6c61726765426c6f624b657958200102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f206b686d61632d736563726574f5";
    private static final byte[] EXTENSIONS_CBOR = hexToBytes(EXTENSIONS_CBOR_HEX);
    private static final String LARGE_BLOB_KEY_B64U = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("fido2-operator.db");
        registry.add(
                "openauth.sim.persistence.database-path",
                () -> databasePath.toAbsolutePath().toString());
        registry.add("openauth.sim.persistence.enable-store", () -> "true");
    }

    @Autowired
    private CredentialStore credentialStore;

    @LocalServerPort
    private int port;

    private HtmlUnitDriver driver;

    private final io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter persistenceAdapter =
            new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter();

    @BeforeEach
    void setUp() {
        driver = new HtmlUnitDriver(true);
        driver.setJavascriptEnabled(true);
        driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
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
    @DisplayName("Canonical stored attestation IDs include PS256 preset")
    void canonicalStoredAttestationIdsIncludePs256Preset() {
        String expectedKey = WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> sample.algorithm() == WebAuthnSignatureAlgorithm.PS256)
                .map(WebAuthnGeneratorSamples.Sample::key)
                .findFirst()
                .orElseThrow(() -> new AssertionError("PS256 generator preset unavailable"));

        assertThat(CANONICAL_ATTESTATION_SEED_IDS)
                .as("canonical stored attestation seed identifiers")
                .contains(expectedKey);
    }

    @Test
    @DisplayName("Stored PS256 attestation hydrates expected challenge in evaluate flow")
    void storedPs256AttestationHydratesExpectedChallenge() {
        WebAuthnAttestationVector vector = WebAuthnAttestationFixtures.findById("synthetic-packed-ps256")
                .orElseThrow(() -> new AssertionError("Expected synthetic-packed-ps256 fixture to exist"));
        WebAuthnGeneratorSamples.Sample ps256Sample = WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> sample.algorithm() == WebAuthnSignatureAlgorithm.PS256)
                .findFirst()
                .orElseThrow(() -> new AssertionError("PS256 generator preset unavailable"));
        String ps256Key = ps256Sample.key();
        String expectedChallenge = ps256Sample.challengeBase64Url();
        assertThat(vector.algorithm())
                .as("PS256 attestation vector uses PS256 algorithm")
                .isEqualTo(WebAuthnSignatureAlgorithm.PS256);
        assertThat(URL_ENCODER.encodeToString(vector.registration().challenge()))
                .as("PS256 attestation fixture provides a challenge")
                .isNotBlank();

        navigateToWebAuthnPanel();

        WebElement evaluateModeToggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
        if (!"inline".equals(evaluateModeToggle.getAttribute("data-mode"))) {
            waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");
        }

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        seedButton.click();
        waitForNonBlankText(By.cssSelector("[data-testid='fido2-seed-status']"));

        By storedCredentialSelector = By.id("fido2StoredCredentialId");
        waitForOption(storedCredentialSelector, ps256Key);
        Select credentialSelect = new Select(waitFor(storedCredentialSelector));
        selectOptionByValue(credentialSelect, ps256Key);

        awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());
        String hydratedChallenge =
                waitFor(By.id("fido2StoredChallenge")).getAttribute("value").trim();
        assertThat(hydratedChallenge)
                .as("stored challenge for PS256 credential")
                .isEqualTo(expectedChallenge);
    }

    @Test
    @DisplayName("Stored evaluation masks credential private key material")
    void storedEvaluationMasksCredentialPrivateKeys() {
        navigateToWebAuthnPanel();

        WebElement evaluateModeToggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
        if (!"inline".equals(evaluateModeToggle.getAttribute("data-mode"))) {
            waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");
        }

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        seedButton.click();
        waitForNonBlankText(By.cssSelector("[data-testid='fido2-seed-status']"));

        By storedCredentialSelector = By.id("fido2StoredCredentialId");
        waitForOption(storedCredentialSelector, STORED_CREDENTIAL_ID);
        Select storedCredentialSelect = new Select(waitFor(storedCredentialSelector));
        selectOptionByValue(storedCredentialSelect, STORED_CREDENTIAL_ID);

        awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());

        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-stored-private-key']")))
                .as("stored evaluation should not include hidden private key inputs")
                .isEmpty();

        WebElement placeholder = waitFor(By.cssSelector("[data-testid='fido2-stored-private-key-placeholder']"));
        assertThat(placeholder.getAttribute("value").trim())
                .as("stored evaluation should display a sanitized placeholder")
                .isEqualTo("[stored-server-side]");

        WebElement handleElement = waitFor(By.cssSelector("[data-testid='fido2-stored-key-handle']"));
        assertThat(handleElement.getText().trim())
                .as("stored evaluation should expose signing key handle")
                .matches("[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Stored WebAuthn generation renders a PublicKeyCredential payload")
    void storedGenerationDisplaysGeneratedAssertion() {
        navigateToWebAuthnPanel();

        WebElement tabs = waitFor(By.cssSelector("[data-testid='fido2-panel-tabs']"));
        assertThat(tabs.isDisplayed()).isTrue();

        WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
        assertThat(evaluateTab.getAttribute("aria-selected")).isEqualTo("true");

        WebElement evaluateModeToggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
        assertThat(evaluateModeToggle.getAttribute("data-mode")).isEqualTo("inline");

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

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
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);

        WebElement storedChallengeField = waitFor(By.id("fido2StoredChallenge"));
        awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());
        assertThat(storedChallengeField.getAttribute("rows")).isEqualTo("1");

        WebElement rpIdInput = waitFor(By.id("fido2StoredRpId"));
        assertThat(rpIdInput.getAttribute("readonly")).isEqualTo("true");
        assertThat(rpIdInput.getAttribute("value")).isEqualTo("example.org");

        WebElement originInput = driver.findElement(By.id("fido2StoredOrigin"));
        assertThat(originInput.getAttribute("value")).isEqualTo("https://example.org");

        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-stored-private-key']")))
                .as("stored evaluation should not emit hidden private-key inputs")
                .isEmpty();
        WebElement placeholder = waitFor(By.cssSelector("[data-testid='fido2-stored-private-key-placeholder']"));
        assertThat(placeholder.getAttribute("value").trim()).isEqualTo("[stored-server-side]");
        WebElement signingKeyHandle = waitFor(By.cssSelector("[data-testid='fido2-stored-key-handle']"));
        awaitText(
                By.cssSelector("[data-testid='fido2-stored-key-handle']"),
                text -> text != null && text.trim().matches("[0-9a-f]{12}"));

        WebElement submitButton = driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
        submitButton.click();

        By storedAssertionSelector = By.cssSelector("[data-testid='fido2-generated-assertion-json']");
        awaitText(storedAssertionSelector, text -> text.contains("\"type\": \"public-key\""));
        WebElement assertionJson = waitFor(storedAssertionSelector);
        assertThat(assertionJson.getText()).contains("\"type\": \"public-key\"");
        assertThat(assertionJson.getText()).contains("\"clientDataJSON\"");

        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-generated-assertion-metadata']")))
                .as("stored telemetry metadata should be removed")
                .isEmpty();
    }

    @Test
    @DisplayName("Stored WebAuthn generation verbose trace lists build steps")
    void storedGenerationVerboseTraceListsBuildSteps() {
        navigateToWebAuthnPanel();

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        if (seedButton.isDisplayed() && seedButton.isEnabled()) {
            seedButton.click();
            waitForNonBlankText(By.cssSelector("[data-testid='fido2-seed-status']"));
        }
        waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);
        awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());
        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-stored-private-key']")))
                .as("stored evaluation verbose trace path should omit hidden private-key inputs")
                .isEmpty();

        WebElement verboseCheckbox = waitFor(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        if (!verboseCheckbox.isSelected()) {
            verboseCheckbox.click();
        }

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
        submitButton.click();

        By storedAssertionSelector = By.cssSelector("[data-testid='fido2-generated-assertion-json']");
        awaitText(storedAssertionSelector, text -> text.contains("\"type\": \"public-key\""));

        WebElement tracePanel = waitFor(By.cssSelector("[data-testid='verbose-trace-panel']"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(webDriver -> "true".equals(tracePanel.getAttribute("data-trace-visible")));
        WebElement traceOperation = waitFor(By.cssSelector("[data-testid='verbose-trace-operation']"));
        assertThat(traceOperation.getText().trim()).isEqualTo("fido2.assertion.evaluate.stored");

        WebElement traceContent = waitFor(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertThat(traceText).contains("build.clientData");
        assertThat(traceText).contains("build.authenticatorData");
        assertThat(traceText).contains("build.signatureBase");
        assertThat(traceText).contains("generate.signature");
        assertThat(traceText).contains("  clientData.sha256 = ");
        assertThat(traceText).doesNotContain("privateKey.sha256");
    }

    @Test
    @DisplayName("Stored credential dropdown uses stacked styling with dark background")
    void storedCredentialDropdownUsesStackedStyling() {
        navigateToWebAuthnPanel();

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        if (seedButton.isDisplayed() && seedButton.isEnabled()) {
            seedButton.click();
        }

        waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(webDriver ->
                        webDriver.findElement(By.id("fido2StoredCredentialId")).isEnabled());

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
        assertThat(inlineTextarea.getAttribute("rows")).isEqualTo("10");

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement inlineSection = waitFor(By.cssSelector("[data-testid='fido2-evaluate-inline-section']"));
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> inlineSection.getAttribute("hidden") != null);
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

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        if (seedButton.isDisplayed() && seedButton.isEnabled()) {
            seedButton.click();
        }

        waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
        List<String> optionLabels = credentialSelect.getOptions().stream()
                .map(WebElement::getText)
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .filter(text -> !"Select a stored credential".equals(text))
                .toList();
        System.out.println("Stored credential option labels: " + optionLabels);

        List<String> expectedLabels = Fido2OperatorSampleData.seedDefinitions().stream()
                .sorted(Comparator.comparingInt((Fido2OperatorSampleData.SeedDefinition definition) ->
                                definition.algorithm().ordinal())
                        .thenComparing(Fido2OperatorSampleData.SeedDefinition::label, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                Fido2OperatorSampleData.SeedDefinition::credentialId, String.CASE_INSENSITIVE_ORDER))
                .map(Fido2OperatorSampleData.SeedDefinition::label)
                .toList();

        assertThat(optionLabels).as("stored credential dropdown labels").containsExactlyElementsOf(expectedLabels);
        assertThat(optionLabels).allMatch(label -> !label.startsWith("Seed "));
        assertThat(optionLabels).allMatch(label -> !label.contains("generator preset"));
    }

    @Test
    @DisplayName("Seeding warns when all curated WebAuthn credentials already exist")
    void seedingWarnsWhenCuratedCredentialsAlreadyExist() {
        clearCredentialStore();
        seedAllCuratedCredentials();

        navigateToWebAuthnPanel();

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        assertThat(seedButton.isDisplayed()).isTrue();
        assertThat(seedButton.getAttribute("disabled")).isNull();

        seedButton.click();

        By seedStatusSelector = By.cssSelector("[data-testid='fido2-seed-status']");
        String seedStatusText = waitForNonBlankText(seedStatusSelector);
        String expectedMessage = "Seeded 0 sample credentials. All sample credentials are already present.";
        assertThat(seedStatusText).isEqualTo(expectedMessage);

        WebElement seedStatus = waitFor(seedStatusSelector);
        assertThat(seedStatus.getAttribute("hidden")).isNull();
        assertThat(seedStatus.getText().trim()).isEqualTo(expectedMessage);
        assertThat(seedStatus.getAttribute("class"))
                .contains("credential-status")
                .contains("credential-status--warning");
    }

    @Test
    @DisplayName("Attestation seeding action populates stored attestation selector")
    void attestationSeedPopulatesStoredCredentials() {
        clearCredentialStore();

        navigateToWebAuthnPanel();
        switchToAttestationEvaluateMode();

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-attestation-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-attestation-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-attestation-seed']"));
        assertThat(seedButton.getAttribute("disabled")).isNull();
        seedButton.click();

        By seedStatusSelector = By.cssSelector("[data-testid='fido2-attestation-seed-status']");
        waitForNonBlankText(seedStatusSelector);

        WebElement selectElement = waitFor(By.id("fido2AttestationStoredCredentialId"));
        Select storedSelect = new Select(selectElement);
        List<String> optionValues = storedSelect.getOptions().stream()
                .map(option -> option.getAttribute("value"))
                .filter(value -> value != null && !value.isBlank())
                .toList();

        assertThat(optionValues).containsAll(CANONICAL_ATTESTATION_SEED_IDS);
    }

    @Test
    @DisplayName("Generated assertion panel stays within the clamped width")
    void generatedAssertionPanelStaysClamped() {
        navigateToWebAuthnPanel();

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        if (seedButton.isDisplayed() && seedButton.isEnabled()) {
            seedButton.click();
            waitForNonBlankText(By.cssSelector("[data-testid='fido2-seed-status']"));
            waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
        }

        waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);
        awaitValue(By.id("fido2StoredChallenge"), value -> value != null && !value.isBlank());
        WebElement placeholder = waitFor(By.cssSelector("[data-testid='fido2-stored-private-key-placeholder']"));
        assertThat(placeholder.getAttribute("value").trim()).isEqualTo("[stored-server-side]");
        awaitText(
                By.cssSelector("[data-testid='fido2-stored-key-handle']"),
                text -> text != null && text.trim().matches("[0-9a-f]{12}"));

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
        submitButton.click();

        By storedAssertionSelector = By.cssSelector("[data-testid='fido2-generated-assertion-json']");
        awaitText(storedAssertionSelector, text -> text.contains("\"type\": \"public-key\""));

        WebElement formColumn =
                waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel'] .section-columns > .stack-lg"));
        WebElement statusColumn = waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel'] .status-column"));

        int formWidth = formColumn.getRect().getWidth();
        int statusWidth = statusColumn.getRect().getWidth();
        int formClientWidth = ((Number)
                        ((JavascriptExecutor) driver).executeScript("return arguments[0].clientWidth;", formColumn))
                .intValue();
        int statusClientWidth = ((Number)
                        ((JavascriptExecutor) driver).executeScript("return arguments[0].clientWidth;", statusColumn))
                .intValue();

        assertThat(statusClientWidth)
                .as("status column width must remain clamped (client=%d, rect=%d)", statusClientWidth, statusWidth)
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

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        By seedActionsSelector = By.cssSelector("[data-testid='fido2-seed-actions']");
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "false"));
        WebElement seedActions = waitFor(seedActionsSelector);
        assertThat(seedActions.getAttribute("hidden")).isNull();

        WebElement seedButton = waitFor(By.cssSelector("[data-testid='fido2-seed-credentials']"));
        assertThat(seedButton.getAttribute("disabled")).isNull();
        String seedContainerDisplay = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.getComputedStyle(arguments[0]).display;", seedActions);
        assertThat(seedContainerDisplay).isIn("block", "inline", "inline-block");
        String seedButtonDisplay = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.getComputedStyle(arguments[0]).display;", seedButton);
        assertThat(seedButtonDisplay).isEqualTo("inline-flex");

        WebElement inlineRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"));
        inlineRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");

        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "true"));
        assertThat(seedActions.getAttribute("hidden")).isNotNull();
        assertThat(seedButton.getAttribute("disabled")).isNotNull();

        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.attributeToBe(seedActionsSelector, "aria-hidden", "false"));
        assertThat(seedActions.getAttribute("hidden")).isNull();
        assertThat(seedButton.getAttribute("disabled")).isNull();
    }

    @Test
    @DisplayName("Evaluate CTA uses mode-specific copy and attributes")
    void evaluateButtonCopyMatchesMode() {
        navigateToWebAuthnPanel();

        WebElement inlineButton = waitFor(By.cssSelector("[data-testid='fido2-evaluate-inline-submit']"));
        assertThat(inlineButton.getAttribute("data-inline-label")).isEqualTo("Generate inline assertion");
        assertThat(inlineButton.getText().trim()).isEqualTo("Generate inline assertion");

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement storedButton = waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
        assertThat(storedButton.getAttribute("data-stored-label")).isEqualTo("Generate stored assertion");
        assertThat(storedButton.getText().trim()).isEqualTo("Generate stored assertion");
    }

    @Test
    @DisplayName("Evaluate tab exposes attestation toggle with inline-only mode")
    void attestationToggleDefaultsToInline() {
        navigateToWebAuthnPanel();

        WebElement ceremonyToggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"));
        assertThat(ceremonyToggle.getAttribute("data-mode")).isEqualTo("assertion");

        WebElement assertionButton =
                waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-select-assertion']"));
        WebElement attestationButton =
                waitFor(By.cssSelector("[data-testid='fido2-evaluate-ceremony-select-attestation']"));

        assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("true");
        assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("false");

        attestationButton.click();
        waitUntilAttribute(
                By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"), "data-mode", "attestation");

        assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("false");
        assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("true");

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
        assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("inline");
        assertThat(modeToggle.getAttribute("data-locked")).isEqualTo("true");

        WebElement storedOption = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
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
        selectOptionByValue(sampleSelect, vector.vectorId());
        dispatchChange(sampleSelectElement);

        WebElement credentialKeyField = waitFor(By.id("fido2AttestationCredentialKey"));
        WebElement attestationKeyField = waitFor(By.id("fido2AttestationPrivateKey"));
        WebElement certificateSerialField = waitFor(By.id("fido2AttestationSerial"));
        WebElement signingModeSelect = waitFor(By.id("fido2AttestationSigningMode"));
        WebElement customRootField = waitFor(By.id("fido2AttestationCustomRoot"));
        assertThat(credentialKeyField.getAttribute("rows")).isEqualTo("8");
        assertThat(attestationKeyField.getAttribute("rows")).isEqualTo("8");
        assertThat(certificateSerialField.getAttribute("rows")).isEqualTo("2");
        assertThat(signingModeSelect.getTagName()).isEqualTo("select");
        assertThat(customRootField.getAttribute("rows")).isEqualTo("6");
        assertThat(customRootField.getAttribute("placeholder")).contains("PEM");

        WebElement customRootHelp = waitFor(By.cssSelector("[data-testid='fido2-attestation-custom-root-help']"));
        assertThat(customRootHelp.getText()).contains("Optional custom root");

        awaitValue(By.id("fido2AttestationCredentialKey"), value -> value != null && value.contains("\"kty\""));
        assertThat(credentialKeyField.getAttribute("value"))
                .as("credential key field should use pretty-printed JWK formatting")
                .contains("\n")
                .contains("\"kty\"");
        awaitValue(By.id("fido2AttestationPrivateKey"), value -> value != null && value.contains("\"kty\""));
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
        selectOptionByValue(sampleSelect, vector.vectorId());
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
        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-attestation-result-format']")))
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
                certificateSection.findElement(By.cssSelector("[data-testid='fido2-attestation-certificate-heading']"));
        assertThat(certificateHeading.getText().trim())
                .isEqualTo("Certificate chain (" + certificateChain.size() + ")");
        assertThat(certificateHeading.getTagName()).isEqualToIgnoringCase("h3");
        assertThat(certificateHeading.getAttribute("class")).contains("section-title");
        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-attestation-result'] .result-subtitle")))
                .as("legacy subtitles should not remain in the attestation result panel")
                .isEmpty();
        WebElement certificateBlock =
                certificateSection.findElement(By.cssSelector("[data-testid='fido2-attestation-certificate-chain']"));
        String certificateText = certificateBlock.getText().trim();
        assertThat(certificateText).contains("-----BEGIN CERTIFICATE-----");
        long pemCount = certificateText
                .lines()
                .filter(line -> line.startsWith("-----BEGIN CERTIFICATE-----"))
                .count();
        assertThat((int) pemCount).isEqualTo(certificateChain.size());

        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-attestation-telemetry']")))
                .isEmpty();
    }

    @Test
    @DisplayName("Attestation generation verbose trace lists build steps")
    void attestationGenerationVerboseTraceListsBuildSteps() {
        WebAuthnAttestationVector vector = resolveAttestationVector();

        navigateToWebAuthnPanel();
        switchToAttestationEvaluateMode();

        waitForOption(By.id("fido2AttestationSampleSelect"), vector.vectorId());
        WebElement sampleSelectElement = waitFor(By.id("fido2AttestationSampleSelect"));
        Select sampleSelect = new Select(sampleSelectElement);
        selectOptionByValue(sampleSelect, vector.vectorId());
        dispatchChange(sampleSelectElement);

        awaitValue(By.id("fido2AttestationCredentialKey"), value -> value != null && value.contains("\"kty\""));
        awaitValue(By.id("fido2AttestationPrivateKey"), value -> value != null && value.contains("\"kty\""));

        WebElement verboseCheckbox = waitFor(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        if (!verboseCheckbox.isSelected()) {
            verboseCheckbox.click();
        }

        WebElement generateButton = waitFor(By.cssSelector("[data-testid='fido2-attestation-submit']"));
        generateButton.click();

        By statusSelector = By.cssSelector("[data-testid='fido2-attestation-status']");
        awaitText(
                statusSelector,
                text -> text != null && !text.trim().isEmpty() && !"pending".equalsIgnoreCase(text.trim()));

        WebElement tracePanel = waitFor(By.cssSelector("[data-testid='verbose-trace-panel']"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(webDriver -> "true".equals(tracePanel.getAttribute("data-trace-visible")));
        WebElement traceOperation = waitFor(By.cssSelector("[data-testid='verbose-trace-operation']"));
        assertThat(traceOperation.getText().trim()).isEqualTo("fido2.attestation.generate");

        WebElement traceContent = waitFor(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertThat(traceText).contains("build.clientData");
        assertThat(traceText).contains("build.authenticatorData");
        assertThat(traceText).contains("build.signatureBase");
        assertThat(traceText).contains("generate.signature");
        assertThat(traceText).contains("compose.attestationObject");
        assertThat(traceText).contains("  clientData.sha256 = ");
        assertThat(traceText).contains("  privateKey.sha256 = ");
        assertThat(traceText).contains("  attObj.sha256 = ");
    }

    @Test
    @DisplayName("Attestation inline invalid payload surfaces ResultCard message")
    void attestationInlineInvalidPayloadSurfacesMessage() {
        WebAuthnAttestationVector vector = resolveAttestationVector();

        navigateToWebAuthnPanel();
        switchToAttestationEvaluateMode();

        waitForOption(By.id("fido2AttestationSampleSelect"), vector.vectorId());
        WebElement sampleSelectElement = waitFor(By.id("fido2AttestationSampleSelect"));
        Select sampleSelect = new Select(sampleSelectElement);
        selectOptionByValue(sampleSelect, vector.vectorId());
        dispatchChange(sampleSelectElement);

        WebElement privateKeyField = waitFor(By.id("fido2AttestationPrivateKey"));
        awaitValue(By.id("fido2AttestationPrivateKey"), value -> value != null && value.contains("\"kty\""));
        privateKeyField.clear();
        privateKeyField.sendKeys("{\"kty\":\"EC\",\"d\":\"invalid\"}");

        WebElement generateButton = waitFor(By.cssSelector("[data-testid='fido2-attestation-submit']"));
        generateButton.click();

        By messageSelector = By.cssSelector("[data-testid='fido2-attestation-result'] [data-result-message]");
        awaitText(messageSelector, text -> text != null && !text.isBlank());
        WebElement messageNode = waitFor(messageSelector);
        assertThat(messageNode.getText()).isNotBlank();

        By hintSelector = By.cssSelector("[data-testid='fido2-attestation-result'] [data-result-hint]");
        awaitText(hintSelector, text -> text != null && text.startsWith("Reason: "));
        WebElement hintNode = waitFor(hintSelector);
        assertThat(hintNode.getText()).startsWith("Reason: ");
    }

    @Test
    @DisplayName("Attestation stored mode surfaces credential selector and hides manual inputs")
    void attestationStoredModeRendersStoredForm() {
        clearCredentialStore();
        seedAllCuratedCredentials();

        navigateToWebAuthnPanel();
        switchToAttestationEvaluateMode();

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-attestation-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-attestation-mode-toggle']"), "data-mode", "stored");

        WebElement storedSection = waitFor(By.cssSelector("[data-testid='fido2-attestation-stored-section']"));
        assertThat(storedSection.getAttribute("hidden")).isNull();

        WebElement inlineSection = waitFor(By.cssSelector("[data-testid='fido2-attestation-inline-section']"));
        assertThat(inlineSection.getAttribute("hidden")).isNotNull();

        Select storedSelect = new Select(waitFor(By.id("fido2AttestationStoredCredentialId")));
        assertThat(storedSelect.getOptions().size()).isGreaterThan(1);

        WebElement storedChallenge = waitFor(By.id("fido2AttestationStoredChallenge"));
        assertThat(storedChallenge.getAttribute("placeholder")).contains("Base64URL");

        WebElement manualKeyField = waitFor(By.id("fido2AttestationCredentialKey"));
        assertThat(manualKeyField.isDisplayed()).isFalse();
    }

    @Test
    @DisplayName("Inline WebAuthn generation renders a PublicKeyCredential payload")
    void inlineGenerationDisplaysGeneratedAssertion() {
        navigateToWebAuthnPanel();

        WebElement inlineRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"));
        inlineRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");

        List<String> expectedLabels = WebAuthnGeneratorSamples.samples().stream()
                .map(Fido2OperatorUiSeleniumTest::expectedInlineLabel)
                .toList();
        assertThat(expectedLabels).isNotEmpty();
        new WebDriverWait(driver, Duration.ofSeconds(3)).until(webDriver -> {
            Select select = new Select(webDriver.findElement(By.id("fido2InlineSampleSelect")));
            return select.getOptions().size() - 1 >= expectedLabels.size();
        });
        WebElement inlineSelectElement = waitFor(By.id("fido2InlineSampleSelect"));
        Select inlineSelect = new Select(inlineSelectElement);
        assertThat(inlineSelect.getOptions().stream()
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
                .until(webDriver -> !new Select(webDriver.findElement(By.id("fido2InlineAlgorithm")))
                        .getFirstSelectedOption()
                        .getAttribute("value")
                        .isBlank());
        Select algorithmField = new Select(waitFor(By.id("fido2InlineAlgorithm")));
        assertThat(algorithmField.getFirstSelectedOption().getAttribute("value"))
                .isNotBlank();

        WebElement privateKeyField = waitFor(By.id("fido2InlinePrivateKey"));
        assertThat(privateKeyField.findElement(By.xpath("..")).getAttribute("class"))
                .contains("field-group--stacked");
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(driver1 -> privateKeyField.getAttribute("value").contains("\"kty\""));
        assertThat(privateKeyField.getAttribute("value")).contains("\"kty\"");

        WebElement evaluateButton = driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-inline-submit']"));
        evaluateButton.click();

        By inlineAssertionSelector = By.cssSelector("[data-testid='fido2-inline-generated-json']");
        awaitText(inlineAssertionSelector, text -> text.contains("\"type\": \"public-key\""));
        WebElement assertionJson = waitFor(inlineAssertionSelector);
        assertThat(assertionJson.getText()).contains("\"type\": \"public-key\"");
        WebElement inlineResultPanel = waitFor(By.cssSelector("[data-testid='fido2-inline-result']"));
        WebElement messageNode = inlineResultPanel.findElement(By.cssSelector("[data-result-message]"));
        WebElement hintNode = inlineResultPanel.findElement(By.cssSelector("[data-result-hint]"));
        assertThat(messageNode.getAttribute("hidden")).isNotNull();
        assertThat(hintNode.getAttribute("hidden")).isNotNull();
    }

    @Test
    @DisplayName("Inline WebAuthn generation reports invalid private key errors")
    void inlineGenerationReportsInvalidPrivateKey() {
        navigateToWebAuthnPanel();

        WebElement inlineRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"));
        inlineRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "inline");

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

        WebElement evaluateButton = driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-inline-submit']"));
        evaluateButton.click();

        By messageSelector = By.cssSelector("[data-testid='fido2-inline-result'] [data-result-message]");
        awaitText(messageSelector, text -> text != null && !text.isBlank());
        WebElement messageNode = waitFor(messageSelector);
        assertThat(messageNode.getText()).isNotBlank();

        By hintSelector = By.cssSelector("[data-testid='fido2-inline-result'] [data-result-hint]");
        awaitText(hintSelector, text -> text != null && text.contains("private_key_invalid"));
        WebElement hintNode = waitFor(hintSelector);
        assertThat(hintNode.getText()).isEqualTo("Reason: private_key_invalid");
    }

    @Test
    @DisplayName("Inline signature counter snapshots refresh via reset helper")
    void inlineCounterResetUpdatesEpochSeconds() throws InterruptedException {
        navigateToWebAuthnPanel();

        By counterSelector = By.id("fido2InlineCounter");
        WebElement counterInput = waitFor(counterSelector);
        long initialValue = Long.parseLong(counterInput.getAttribute("value"));
        assertThat(initialValue).isGreaterThan(0L);

        WebElement inlineCounterToggle = waitFor(By.cssSelector("[data-testid='fido2-inline-counter-toggle']"));
        WebElement toggleCluster = inlineCounterToggle.findElement(By.xpath(".."));
        assertThat(toggleCluster.getAttribute("class")).contains("field-group--checkbox");
        List<WebElement> clusterChildren = toggleCluster.findElements(By.xpath("./*"));
        assertThat(clusterChildren).hasSize(3);
        assertThat(clusterChildren.get(0).getTagName()).isEqualTo("input");
        assertThat(clusterChildren.get(1).getTagName()).isEqualTo("label");
        assertThat(clusterChildren.get(2).getTagName()).isEqualTo("button");
        assertThat(clusterChildren.get(1).getAttribute("for")).isEqualTo("fido2InlineCounterAuto");
        assertThat(clusterChildren.get(2).getAttribute("data-testid")).isEqualTo("fido2-inline-counter-reset");

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

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2StoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);

        long expectedCounter = Fido2OperatorSampleData.seedDefinitions().stream()
                .filter(definition -> STORED_CREDENTIAL_ID.equals(definition.credentialId()))
                .mapToLong(Fido2OperatorSampleData.SeedDefinition::signatureCounter)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Stored credential not found in seed definitions: " + STORED_CREDENTIAL_ID));

        By counterSelector = By.id("fido2StoredCounter");
        WebElement counterInput = waitFor(counterSelector);
        assertThat(counterInput.getAttribute("value")).isEqualTo(Long.toString(expectedCounter));
        assertThat(counterInput.getAttribute("readonly")).isNull();

        WebElement toggleCluster =
                waitFor(By.cssSelector("[data-testid='fido2-stored-counter-group'] .field-group--counter-toggle"));
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
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        WebElement replayModeToggle = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"));
        assertThat(replayModeToggle.getAttribute("data-mode")).isEqualTo("inline");

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2ReplayCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);

        By storedResultSelector = By.cssSelector("[data-testid='fido2-replay-result']");
        waitUntilAttribute(storedResultSelector, "aria-hidden", "true");
        WebElement storedResultPanel = waitFor(storedResultSelector);
        assertThat(storedResultPanel.getAttribute("aria-hidden")).isEqualTo("true");

        WebElement replaySubmit = driver.findElement(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
        replaySubmit.click();

        awaitVisible(By.cssSelector("[data-testid='fido2-replay-result']"));
        awaitText(
                By.cssSelector("[data-testid='fido2-replay-result'] [data-testid='fido2-replay-status']"),
                text -> !"pending".equalsIgnoreCase(text));

        WebElement status = driver.findElement(
                By.cssSelector("[data-testid='fido2-replay-result'] [data-testid='fido2-replay-status']"));
        WebElement outcomeElement = driver.findElement(
                By.cssSelector("[data-testid='fido2-replay-result'] [data-testid='fido2-replay-outcome']"));
        WebElement reasonElement = driver.findElement(
                By.cssSelector("[data-testid='fido2-replay-result'] [data-testid='fido2-replay-reason']"));
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
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2ReplayCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);

        WebElement replaySubmit = driver.findElement(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
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
        WebElement reasonValue = reasonRow.findElement(By.cssSelector("[data-testid='fido2-replay-reason']"));
        assertThat(reasonValue.getText().trim()).isNotEmpty();

        WebElement outcomeRow = rows.get(1);
        assertThat(outcomeRow.findElement(By.tagName("dt")).getText().trim()).isEqualTo("Outcome");
        WebElement outcomeValue = outcomeRow.findElement(By.cssSelector("[data-testid='fido2-replay-outcome']"));
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

        WebElement ceremonyToggle = waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"));
        assertThat(ceremonyToggle.getAttribute("data-mode")).isEqualTo("assertion");

        WebElement assertionButton = waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-select-assertion']"));
        WebElement attestationButton =
                waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-select-attestation']"));

        assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("true");
        assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("false");

        attestationButton.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"), "data-mode", "attestation");

        assertThat(assertionButton.getAttribute("aria-pressed")).isEqualTo("false");
        assertThat(attestationButton.getAttribute("aria-pressed")).isEqualTo("true");

        WebElement attestationModeToggle =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-mode-toggle']"));
        assertThat(attestationModeToggle.getAttribute("data-mode")).isEqualTo("manual");
        assertThat(driver.findElements(By.cssSelector("[data-testid='fido2-replay-attestation-mode-select-preset']")))
                .as("Preset attestation replay mode should be removed")
                .isEmpty();

        WebElement manualOption =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-mode-select-manual']"));
        assertThat(manualOption.isSelected()).isTrue();
        WebElement manualLabel = waitFor(By.cssSelector("label[for='fido2ReplayAttestationModeManual']"));
        assertThat(manualLabel.getText().trim()).isEqualTo("Inline parameters");

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"));
        assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("inline");
        assertThat(modeToggle.getAttribute("data-locked")).isEqualTo("true");

        WebElement storedOption = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        assertThat(storedOption.getAttribute("aria-hidden")).isEqualTo("true");

        WebElement attestationSection = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-section']"));
        assertThat(attestationSection.getAttribute("hidden")).isNull();

        WebElement assertionInlineSection = waitFor(By.cssSelector("[data-testid='fido2-replay-inline-section']"));
        assertThat(assertionInlineSection.getAttribute("hidden")).isNotNull();

        WebElement assertionStoredSection = waitFor(By.cssSelector("[data-testid='fido2-replay-stored-section']"));
        assertThat(assertionStoredSection.getAttribute("hidden")).isNotNull();
    }

    @Test
    @DisplayName("Attestation replay verifies payload with provided trust anchors")
    void attestationReplayVerifiesWithTrustAnchors() {
        WebAuthnAttestationVector vector = resolveAttestationVector();
        WebAuthnAttestationVerification verification = verifyAttestation(vector);
        List<X509Certificate> anchors = verification.certificateChain().isEmpty()
                ? List.of()
                : List.of(verification
                        .certificateChain()
                        .get(verification.certificateChain().size() - 1));

        navigateToWebAuthnPanel();
        switchToAttestationReplayMode();
        populateReplayAttestationForm(vector);
        applyReplayTrustAnchors(anchors);

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-submit']"));
        submitButton.click();

        By resultSelector = By.cssSelector("[data-testid='fido2-replay-attestation-result']");
        awaitVisible(resultSelector);

        By statusSelector = By.cssSelector("[data-testid='fido2-replay-attestation-status']");
        awaitText(statusSelector, text -> {
            String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            return !normalized.isEmpty() && !normalized.contains("awaiting") && !"pending".equalsIgnoreCase(normalized);
        });
        assertThat(waitFor(statusSelector).getText().trim()).isEqualToIgnoringCase("success");

        WebElement reason = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-reason']"));
        String reasonText = reason.getText().trim().toLowerCase(Locale.ROOT);
        assertThat(reasonText).contains("match");

        By anchorSourceSelector = By.cssSelector("[data-testid='fido2-replay-attestation-anchor-source']");
        awaitText(anchorSourceSelector, text -> !text.toLowerCase(Locale.ROOT).contains("awaiting"));
        WebElement anchorSource = waitFor(anchorSourceSelector);
        String anchorSourceText = anchorSource.getText().trim().toLowerCase(Locale.ROOT);
        assertThat(anchorSourceText).containsAnyOf("provided", "metadata");

        By anchorTrustedSelector = By.cssSelector("[data-testid='fido2-replay-attestation-anchor-trusted']");
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

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-submit']"));
        submitButton.click();

        By statusSelector = By.cssSelector("[data-testid='fido2-replay-attestation-status']");
        awaitText(statusSelector, text -> {
            String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            return !normalized.isEmpty() && !normalized.contains("awaiting") && !"pending".equalsIgnoreCase(normalized);
        });
        WebElement status = waitFor(statusSelector);
        String statusText = status.getText().trim();
        assertThat(statusText).isNotBlank();

        WebElement messageElement =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-result'] [data-result-message]"));
        String messageText = messageElement.getText().trim();
        if (!messageText.isBlank()) {
            assertThat(messageText.toLowerCase(Locale.ROOT)).contains("trust");
        }

        WebElement hintElement =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-result'] [data-result-hint]"));
        String hintText = hintElement.getText().trim();
        if (!hintText.isBlank()) {
            assertThat(hintText).startsWith("Reason: ");
            assertThat(hintText.toLowerCase(Locale.ROOT)).contains("trust");
        }
    }

    @Test
    @DisplayName("Attestation replay manual mode lists curated metadata anchors")
    void attestationReplayManualMetadataAnchors() {
        navigateToWebAuthnPanel();
        switchToAttestationReplayMode();

        WebElement manualToggle =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-mode-select-manual']"));
        manualToggle.click();
        waitUntilAttribute(
                By.cssSelector("[data-testid='fido2-replay-attestation-mode-toggle']"), "data-mode", "manual");

        WebElement metadataAnchorMultiSelect =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-metadata-anchors']"));
        assertThat(metadataAnchorMultiSelect.getTagName()).isEqualToIgnoringCase("select");
        List<WebElement> options = new Select(metadataAnchorMultiSelect).getOptions();
        assertThat(options)
                .as("Expected curated metadata options to be present")
                .anyMatch(option -> option.getText().toLowerCase(Locale.ROOT).contains("packed"));
    }

    @Test
    @DisplayName("Attestation replay stored mode replays persisted credentials without identifier input")
    void attestationReplayStoredModeReplaysPersistedCredentials() {
        clearCredentialStore();
        StoredAttestationReplayExpectation expectation = seedStoredAttestationReplayCredential();

        navigateToWebAuthnPanel();
        switchToAttestationReplayMode();

        WebElement storedToggle =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-mode-select-stored']"));
        storedToggle.click();
        waitUntilAttribute(
                By.cssSelector("[data-testid='fido2-replay-attestation-mode-toggle']"), "data-mode", "stored");

        assertThat(driver.findElements(By.id("fido2ReplayAttestationId"))).isEmpty();

        waitForOption(By.id("fido2ReplayAttestationStoredCredentialId"), STORED_CREDENTIAL_ID);
        waitForOption(By.id("fido2ReplayAttestationStoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2ReplayAttestationStoredCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);

        awaitValue(
                By.id("fido2ReplayAttestationStoredRpId"),
                value -> value != null && value.equals(expectation.relyingPartyId()));
        WebElement rpField = waitFor(By.id("fido2ReplayAttestationStoredRpId"));
        assertThat(rpField.getAttribute("readonly")).isNotNull();
        assertThat(rpField.getAttribute("value")).isEqualTo(expectation.relyingPartyId());

        awaitValue(
                By.id("fido2ReplayAttestationStoredOrigin"),
                value -> value != null && value.equals(expectation.origin()));
        WebElement originField = waitFor(By.id("fido2ReplayAttestationStoredOrigin"));
        assertThat(originField.getAttribute("readonly")).isNotNull();
        assertThat(originField.getAttribute("value")).isEqualTo(expectation.origin());

        awaitValue(
                By.id("fido2ReplayAttestationStoredChallenge"),
                value -> value != null && value.equals(expectation.challenge()));
        WebElement challengeField = waitFor(By.id("fido2ReplayAttestationStoredChallenge"));
        assertThat(challengeField.getAttribute("readonly")).isNotNull();
        assertThat(challengeField.getAttribute("value")).isEqualTo(expectation.challenge());

        awaitValue(
                By.id("fido2ReplayAttestationStoredFormat"),
                value -> value != null && value.equals(expectation.format()));
        WebElement formatField = waitFor(By.id("fido2ReplayAttestationStoredFormat"));
        assertThat(formatField.getAttribute("value")).isEqualTo(expectation.format());

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-stored-submit']"));
        assertThat(submitButton.getAttribute("disabled")).isNull();
        submitButton.click();

        By statusSelector = By.cssSelector("[data-testid='fido2-replay-attestation-status']");
        awaitText(statusSelector, text -> {
            if (text == null) {
                return false;
            }
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            return !normalized.isEmpty() && !normalized.contains("awaiting") && !"pending".equals(normalized);
        });

        assertThat(waitFor(statusSelector).getText().trim()).isEqualToIgnoringCase("success");
        WebElement reason = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-reason']"));
        assertThat(reason.getText().trim().toLowerCase(Locale.ROOT)).contains("match");
    }

    @Test
    @DisplayName("Stored attestation dropdown renders algorithm and format labels")
    void attestationReplayStoredLabels() {
        clearCredentialStore();
        seedStoredAttestationReplayCredential();

        navigateToWebAuthnPanel();
        switchToAttestationReplayMode();

        WebElement storedToggle =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-mode-select-stored']"));
        storedToggle.click();
        waitUntilAttribute(
                By.cssSelector("[data-testid='fido2-replay-attestation-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2ReplayAttestationStoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2ReplayAttestationStoredCredentialId")));

        List<String> labels = credentialSelect.getOptions().stream()
                .map(WebElement::getText)
                .map(String::trim)
                .filter(text -> !text.isBlank() && !"Select a stored credential".equalsIgnoreCase(text))
                .collect(Collectors.toList());

        assertThat(labels).anyMatch(label -> label.equals(expectedAttestationLabel(STORED_CREDENTIAL_ID)));
        assertThat(labels).allMatch(label -> !label.toLowerCase(Locale.ROOT).startsWith("w3c-"));
    }

    @Test
    @DisplayName("Attestation replay stored mode displays persisted attestation payloads read-only")
    void attestationReplayStoredModeDisplaysPersistedPayloads() {
        clearCredentialStore();
        StoredAttestationReplayExpectation expectation = seedStoredAttestationReplayCredential();

        navigateToWebAuthnPanel();
        switchToAttestationReplayMode();

        WebElement storedToggle =
                waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-mode-select-stored']"));
        storedToggle.click();
        waitUntilAttribute(
                By.cssSelector("[data-testid='fido2-replay-attestation-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2ReplayAttestationStoredCredentialId"), STORED_CREDENTIAL_ID);
        Select credentialSelect = new Select(waitFor(By.id("fido2ReplayAttestationStoredCredentialId")));
        selectOptionByValue(credentialSelect, STORED_CREDENTIAL_ID);

        awaitValue(
                By.id("fido2ReplayAttestationStoredChallenge"),
                value -> value != null && value.equals(expectation.challenge()));

        By trustAnchorSelector = By.id("fido2ReplayAttestationStoredTrustAnchors");
        WebElement trustAnchorField = waitFor(trustAnchorSelector);
        assertThat(trustAnchorField.getAttribute("readonly")).isNotNull();
        awaitValue(
                trustAnchorSelector,
                value -> value != null && !value.isBlank() && value.contains("Sample Authenticator"));

        By attestationObjectSelector = By.id("fido2ReplayAttestationStoredAttestationObject");
        WebElement attestationObjectField = waitFor(attestationObjectSelector);
        assertThat(attestationObjectField.getAttribute("readonly")).isNotNull();
        awaitValue(attestationObjectSelector, value -> value != null && !value.isBlank());

        By clientDataSelector = By.id("fido2ReplayAttestationStoredClientData");
        WebElement clientDataField = waitFor(clientDataSelector);
        assertThat(clientDataField.getAttribute("readonly")).isNotNull();
        awaitValue(clientDataSelector, value -> value != null && !value.isBlank());
    }

    @Test
    @DisplayName("Stored credential selection starts empty and refreshes evaluate and replay forms")
    void storedSelectionDefaultsToPlaceholderAndRefreshesForms() {
        clearCredentialStore();
        seedAllCuratedCredentials();

        List<Fido2OperatorSampleData.SeedDefinition> definitions = Fido2OperatorSampleData.seedDefinitions();
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

        WebElement evaluateStoredRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        evaluateStoredRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2StoredCredentialId"), firstDefinition.credentialId());
        waitForOption(By.id("fido2StoredCredentialId"), secondDefinition.credentialId());
        Select evaluateSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
        assertThat(evaluateSelect.getFirstSelectedOption().getAttribute("value"))
                .isEqualTo("");

        WebElement evaluateSubmit = waitFor(By.cssSelector("[data-testid='fido2-evaluate-stored-submit']"));
        assertThat(evaluateSubmit.getAttribute("disabled")).isNull();

        WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
        replayTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        WebElement replayStoredRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        replayStoredRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2ReplayCredentialId"), firstDefinition.credentialId());
        waitForOption(By.id("fido2ReplayCredentialId"), secondDefinition.credentialId());
        Select replaySelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
        assertThat(replaySelect.getFirstSelectedOption().getAttribute("value")).isEqualTo("");

        WebElement replaySubmit = waitFor(By.cssSelector("[data-testid='fido2-replay-stored-submit']"));
        assertThat(replaySubmit.getAttribute("disabled")).isNull();

        WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
        evaluateTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"), "aria-selected", "true");

        evaluateStoredRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        evaluateStoredRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

        WebElement evaluateSelectElement = waitFor(By.id("fido2StoredCredentialId"));
        evaluateSelect = new Select(evaluateSelectElement);
        selectOptionByValue(evaluateSelect, firstDefinition.credentialId());
        dispatchChange(evaluateSelectElement);

        awaitValue(
                By.id("fido2StoredChallenge"),
                value -> firstVector.expectedChallengeBase64Url().equals(value));
        awaitValue(
                By.id("fido2StoredCounter"),
                value -> value != null && value.equals(Long.toString(firstDefinition.signatureCounter())));

        replayTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        replayStoredRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        replayStoredRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

        replaySelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
        assertThat(replaySelect.getFirstSelectedOption().getAttribute("value"))
                .isEqualTo(firstDefinition.credentialId());
        awaitValue(
                By.id("fido2ReplaySignature"),
                value -> firstVector.signatureBase64Url().equals(value));
        awaitValue(
                By.id("fido2ReplayChallenge"),
                value -> firstVector.expectedChallengeBase64Url().equals(value));

        selectOptionByValue(replaySelect, secondDefinition.credentialId());
        dispatchChange(waitFor(By.id("fido2ReplayCredentialId")));

        awaitValue(
                By.id("fido2ReplaySignature"),
                value -> secondVector.signatureBase64Url().equals(value));
        awaitValue(
                By.id("fido2ReplayChallenge"),
                value -> secondVector.expectedChallengeBase64Url().equals(value));

        evaluateTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"), "aria-selected", "true");

        evaluateStoredRadio = waitFor(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"));
        evaluateStoredRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"), "data-mode", "stored");

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
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        WebElement inlineButton = waitFor(By.cssSelector("[data-testid='fido2-replay-inline-submit']"));
        assertThat(inlineButton.getAttribute("data-inline-label")).isEqualTo("Replay inline assertion");
        assertThat(inlineButton.getText().trim()).isEqualTo("Replay inline assertion");

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

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
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        WebElement inlineRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-inline']"));
        inlineRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "inline");

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
        Select algorithmSelect = new Select(waitFor(By.cssSelector("[data-testid='fido2-replay-inline-algorithm']")));
        assertThat(algorithmSelect.getFirstSelectedOption().getAttribute("value"))
                .isNotEmpty();

        WebElement submit = driver.findElement(By.cssSelector("[data-testid='fido2-replay-inline-submit']"));
        submit.click();

        awaitVisible(By.cssSelector("[data-testid='fido2-replay-inline-result']"));
        awaitText(
                By.cssSelector("[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-status']"),
                text -> text != null && !text.isBlank() && !"pending".equalsIgnoreCase(text));

        WebElement status = driver.findElement(By.cssSelector(
                "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-status']"));
        assertThat(status.getText()).isEqualToIgnoringCase("match");
        assertThat(status.getAttribute("class")).contains("status-badge");

        WebElement reason = driver.findElement(By.cssSelector(
                "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-reason']"));
        WebElement outcome = driver.findElement(By.cssSelector(
                "[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-outcome']"));
        assertThat(reason.getText()).isEqualToIgnoringCase("match");
        assertThat(outcome.getText()).isEqualToIgnoringCase("match");
    }

    @Test
    @DisplayName("Attestation replay inline mode loads sample vectors")
    void attestationReplayInlineSampleLoadsPreset() {
        navigateToWebAuthnPanel();
        switchToAttestationReplayMode();

        By sampleSelectSelector = By.id("fido2ReplayAttestationSampleSelect");
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.elementToBeClickable(sampleSelectSelector));
        WebElement sampleSelectElement = waitFor(sampleSelectSelector);
        Select sampleSelect = new Select(sampleSelectElement);
        String vectorId = sampleSelect.getOptions().stream()
                .map(option -> option.getAttribute("value"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No attestation replay samples available"));
        WebAuthnAttestationVector vector = WebAuthnAttestationFixtures.findById(vectorId)
                .orElseThrow(() -> new IllegalStateException("Missing attestation vector " + vectorId));
        selectOptionByValue(sampleSelect, vectorId);
        dispatchChange(sampleSelectElement);

        Select formatSelect = new Select(waitFor(By.id("fido2ReplayAttestationFormat")));
        assertThat(formatSelect.getFirstSelectedOption().getAttribute("value"))
                .isEqualToIgnoringCase(vector.format().label());

        WebElement rpField = waitFor(By.id("fido2ReplayAttestationRpId"));
        assertThat(rpField.getAttribute("value")).isEqualTo(vector.relyingPartyId());

        WebElement originField = waitFor(By.id("fido2ReplayAttestationOrigin"));
        assertThat(originField.getAttribute("value")).isEqualTo(vector.origin());

        WebElement challengeField = waitFor(By.id("fido2ReplayAttestationChallenge"));
        assertThat(challengeField.getAttribute("value"))
                .isEqualTo(encodeBase64Url(vector.registration().challenge()));

        WebElement clientDataField = waitFor(By.id("fido2ReplayAttestationClientDataJson"));
        assertThat(clientDataField.getAttribute("value"))
                .isEqualTo(encodeBase64Url(vector.registration().clientDataJson()));

        WebElement attestationObjectField = waitFor(By.id("fido2ReplayAttestationObject"));
        assertThat(attestationObjectField.getAttribute("value"))
                .isEqualTo(encodeBase64Url(vector.registration().attestationObject()));

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-submit']"));
        submitButton.click();

        awaitText(
                By.cssSelector("[data-testid='fido2-replay-attestation-status']"),
                text -> text != null && !text.isBlank() && !"pending".equalsIgnoreCase(text));

        WebElement status = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-status']"));
        String statusText = status.getText().trim();
        assertThat(statusText).isEqualToIgnoringCase("success");
        WebElement reason = waitFor(By.cssSelector("[data-testid='fido2-replay-attestation-reason']"));
        String reasonText = reason.getText().trim().toLowerCase(Locale.ROOT);
        assertThat(reasonText).contains("self_attested");
    }

    @Test
    @DisplayName("Verbose trace surfaces WebAuthn extension metadata for inline replay")
    void verboseTraceSurfacesExtensionMetadataForInlineReplay() throws Exception {
        navigateToWebAuthnPanel();

        switchToReplayTab();

        WebElement inlineRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-inline']"));
        inlineRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "inline");

        By sampleSelectSelector = By.id("fido2ReplayInlineSampleSelect");
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(ExpectedConditions.elementToBeClickable(sampleSelectSelector));
        WebElement sampleElement = driver.findElement(sampleSelectSelector);
        Select sampleSelect = new Select(sampleElement);
        boolean hasSample = sampleSelect.getOptions().stream()
                .anyMatch(option -> STORED_CREDENTIAL_ID.equals(option.getAttribute("value")));
        if (!hasSample) {
            throw new IllegalStateException("Missing inline sample option for " + STORED_CREDENTIAL_ID);
        }
        selectOptionByValue(sampleSelect, STORED_CREDENTIAL_ID);
        dispatchChange(sampleElement);

        awaitValue(By.id("fido2ReplayInlineAuthenticatorData"), value -> value != null && !value.isBlank());
        awaitValue(By.id("fido2ReplayInlineSignature"), value -> value != null && !value.isBlank());

        Sample sample = WebAuthnGeneratorSamples.findByKey(STORED_CREDENTIAL_ID)
                .orElseThrow(() -> new IllegalStateException("Missing generator sample " + STORED_CREDENTIAL_ID));

        byte[] extendedAuthenticator = extendAuthenticatorData(sample.authenticatorData(), EXTENSIONS_CBOR);
        byte[] signature = signAssertion(
                sample.privateKeyJwk(), sample.algorithm(), extendedAuthenticator, sample.clientDataJson());

        String extendedAuthenticatorBase64 = URL_ENCODER.encodeToString(extendedAuthenticator);
        String signatureBase64 = URL_ENCODER.encodeToString(signature);

        List<WebElement> verboseCheckboxes =
                driver.findElements(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertThat(verboseCheckboxes).isNotEmpty();
        WebElement verboseCheckbox = verboseCheckboxes.get(0);
        if (!verboseCheckbox.isSelected()) {
            verboseCheckbox.click();
        }
        assertThat(verboseCheckbox.isSelected())
                .as("Verbose trace checkbox should be enabled before replay submission")
                .isTrue();

        setFieldValue(By.id("fido2ReplayInlineAuthenticatorData"), extendedAuthenticatorBase64);
        setFieldValue(By.id("fido2ReplayInlineSignature"), signatureBase64);

        awaitValue(By.id("fido2ReplayInlineAuthenticatorData"), value -> extendedAuthenticatorBase64.equals(value));
        awaitValue(By.id("fido2ReplayInlineSignature"), value -> signatureBase64.equals(value));

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='fido2-replay-inline-submit']"));
        submitButton.click();

        By inlineStatusSelector =
                By.cssSelector("[data-testid='fido2-replay-inline-result'] [data-testid='fido2-replay-inline-status']");
        awaitText(inlineStatusSelector, text -> !text.isBlank() && !"pending".equalsIgnoreCase(text));
        WebElement status = waitFor(inlineStatusSelector);
        assertThat(status.getText()).isEqualToIgnoringCase("match");

        WebElement tracePanel = waitFor(By.cssSelector("[data-testid='verbose-trace-panel']"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(webDriver -> "true".equals(tracePanel.getAttribute("data-trace-visible")));
        assertThat(tracePanel.getAttribute("data-trace-visible")).isEqualTo("true");

        WebElement traceOperation = waitFor(By.cssSelector("[data-testid='verbose-trace-operation']"));
        assertThat(traceOperation.getText().trim()).isEqualTo("fido2.assertion.evaluate.inline");

        WebElement traceContent = waitFor(By.cssSelector("[data-testid='verbose-trace-content']"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(webDriver -> traceContent.getText().contains("ext.hmac-secret = requested"));

        String traceText = traceContent.getText();
        assertThat(traceText).contains("parse.clientData");
        assertThat(traceText).contains("parse.authenticatorData");
        assertThat(traceText).contains("build.signatureBase");
        assertThat(traceText).contains("verify.signature");
        assertThat(traceText).contains("evaluate.counter");
        assertThat(traceText).contains("  tokenBinding.status = ");
        assertThat(traceText).contains("  tokenBinding.id = ");
        assertThat(traceText).contains("  signedBytes.preview = ");
        assertThat(traceText).contains("parse.extensions");
        assertThat(traceText).contains("  extensions.present = true");
        assertThat(traceText).contains("  extensions.cbor.hex = " + EXTENSIONS_CBOR_HEX);
        assertThat(traceText).contains("  ext.credProps.rk = true");
        assertThat(traceText).contains("  ext.credProtect.policy = required");
        assertThat(traceText).contains("  ext.largeBlobKey.b64u = " + LARGE_BLOB_KEY_B64U);
        assertThat(traceText).contains("  ext.hmac-secret = requested");
    }

    @Test
    @DisplayName("Replay forms expose assertion payload textareas")
    void replayFormsExposeAssertionPayloadTextareas() {
        navigateToWebAuthnPanel();

        WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
        replayTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");

        WebElement inlineRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-inline']"));
        inlineRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "inline");

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
        awaitValue(By.id("fido2ReplayInlineAuthenticatorData"), value -> value != null && value.length() > 16);
        awaitValue(By.id("fido2ReplayInlineSignature"), value -> value != null && !value.isBlank());

        WebElement storedRadio = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"), "data-mode", "stored");

        waitForOption(By.id("fido2ReplayCredentialId"), STORED_CREDENTIAL_ID);
        WebElement storedSelectElement = waitFor(By.id("fido2ReplayCredentialId"));
        Select storedSelect = new Select(storedSelectElement);
        selectOptionByValue(storedSelect, STORED_CREDENTIAL_ID);
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
        awaitValue(By.id("fido2ReplayAuthenticatorData"), value -> value != null && value.length() > 16);
        awaitValue(By.id("fido2ReplaySignature"), value -> value != null && !value.isBlank());
    }

    @Test
    @DisplayName("FIDO2 deep-link mode stays active across refresh and history navigation")
    void deepLinkReplayModePersistsAcrossRefresh() {
        String url = baseUrl("/ui/console?protocol=fido2&tab=replay");
        driver.get(url);
        waitFor(By.cssSelector("[data-protocol-panel='fido2']"));

        assertReplayTabSelected();

        driver.navigate().refresh();
        waitFor(By.cssSelector("[data-protocol-panel='fido2']"));
        assertReplayTabSelected();

        WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
        evaluateTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"), "aria-selected", "true");
        assertEvaluateTabSelected();

        driver.navigate().back();
        waitFor(By.cssSelector("[data-protocol-panel='fido2']"));
        assertReplayTabSelected();
    }

    private void switchToReplayTab() {
        WebElement replayTab = waitFor(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
        replayTab.click();
        waitUntilAttribute(By.cssSelector("[data-testid='fido2-panel-tab-replay']"), "aria-selected", "true");
    }

    private void switchToAttestationReplayMode() {
        switchToReplayTab();
        WebElement ceremonyToggle = waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"));
        if (!"attestation".equals(ceremonyToggle.getAttribute("data-mode"))) {
            WebElement attestationButton =
                    waitFor(By.cssSelector("[data-testid='fido2-replay-ceremony-select-attestation']"));
            attestationButton.click();
            waitUntilAttribute(
                    By.cssSelector("[data-testid='fido2-replay-ceremony-toggle']"), "data-mode", "attestation");
        }
    }

    private WebAuthnAttestationVector resolveAttestationVector() {
        return WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Packed attestation fixture not available for replay"));
    }

    private WebAuthnAttestationVerification verifyAttestation(WebAuthnAttestationVector vector) {
        WebAuthnAttestationRequest request = new WebAuthnAttestationRequest(
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
        selectOptionByValue(formatSelect, vector.format().label());
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
        String content = anchors == null || anchors.isEmpty()
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
            String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
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
                    By.cssSelector("[data-testid='fido2-evaluate-ceremony-toggle']"), "data-mode", "attestation");
        }
    }

    private void seedStoredCredential() {
        Sample sample = WebAuthnGeneratorSamples.findByKey(STORED_CREDENTIAL_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Generator sample not found for stored credential: " + STORED_CREDENTIAL_ID));

        WebAuthnCredentialDescriptor descriptor = WebAuthnCredentialDescriptor.builder()
                .name(sample.key())
                .relyingPartyId(sample.relyingPartyId())
                .credentialId(sample.credentialId())
                .publicKeyCose(sample.publicKeyCose())
                .signatureCounter(sample.signatureCounter())
                .userVerificationRequired(sample.userVerificationRequired())
                .algorithm(sample.algorithm())
                .build();

        Credential serialized = VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));

        Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
        Fido2OperatorSampleData.seedDefinitions().stream()
                .filter(definition -> definition.credentialId().equals(STORED_CREDENTIAL_ID))
                .findFirst()
                .ifPresent(definition ->
                        definition.metadata().forEach((key, value) -> attributes.put("fido2.metadata." + key, value)));

        Credential persisted = new Credential(
                serialized.name(),
                CredentialType.FIDO2,
                serialized.secret(),
                attributes,
                serialized.createdAt(),
                serialized.updatedAt());

        credentialStore.save(persisted);
    }

    private StoredAttestationReplayExpectation seedStoredAttestationReplayCredential() {
        WebAuthnAttestationVector vector =
                WebAuthnAttestationFixtures.vectorsFor(WebAuthnAttestationFormat.PACKED).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing packed attestation vector for replay"));

        WebAuthnAttestationGenerator generator = new WebAuthnAttestationGenerator();
        WebAuthnFixtures.WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
        String credentialName = STORED_CREDENTIAL_ID;
        WebAuthnAttestationGenerator.GenerationResult seedResult =
                generator.generate(new WebAuthnAttestationGenerator.GenerationCommand.Inline(
                        vector.vectorId(),
                        vector.format(),
                        vector.relyingPartyId(),
                        vector.origin(),
                        vector.registration().challenge(),
                        vector.keyMaterial().credentialPrivateKeyBase64Url(),
                        vector.keyMaterial().attestationPrivateKeyBase64Url(),
                        vector.keyMaterial().attestationCertificateSerialBase64Url(),
                        WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED,
                        List.of()));

        WebAuthnCredentialDescriptor credentialDescriptor = WebAuthnCredentialDescriptor.builder()
                .name(credentialName)
                .relyingPartyId(fixture.storedCredential().relyingPartyId())
                .credentialId(fixture.storedCredential().credentialId())
                .publicKeyCose(fixture.storedCredential().publicKeyCose())
                .signatureCounter(fixture.storedCredential().signatureCounter())
                .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
                .algorithm(fixture.algorithm())
                .build();

        WebAuthnAttestationCredentialDescriptor descriptor = WebAuthnAttestationCredentialDescriptor.builder()
                .name(credentialName)
                .format(vector.format())
                .signingMode(WebAuthnAttestationGenerator.SigningMode.SELF_SIGNED)
                .credentialDescriptor(credentialDescriptor)
                .relyingPartyId(vector.relyingPartyId())
                .origin(vector.origin())
                .attestationId(vector.vectorId())
                .credentialPrivateKeyBase64Url(vector.keyMaterial().credentialPrivateKeyBase64Url())
                .attestationPrivateKeyBase64Url(vector.keyMaterial().attestationPrivateKeyBase64Url())
                .attestationCertificateSerialBase64Url(vector.keyMaterial().attestationCertificateSerialBase64Url())
                .certificateChainPem(seedResult.certificateChainPem())
                .customRootCertificatesPem(List.of())
                .build();

        VersionedCredentialRecord serialized = persistenceAdapter.serializeAttestation(descriptor);
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
        attributes.put("fido2.attestation.stored.attestationObject", encodeBase64Url(seedResult.attestationObject()));
        attributes.put("fido2.attestation.stored.clientDataJson", encodeBase64Url(seedResult.clientDataJson()));
        attributes.put(
                "fido2.attestation.stored.expectedChallenge",
                encodeBase64Url(vector.registration().challenge()));
        attributes.put(
                WebAuthnCredentialPersistenceAdapter.ATTR_METADATA_LABEL, expectedAttestationLabel(credentialName));

        Credential persisted = VersionedCredentialRecordMapper.toCredential(new VersionedCredentialRecord(
                serialized.schemaVersion(),
                serialized.name(),
                serialized.type(),
                serialized.secret(),
                serialized.createdAt(),
                serialized.updatedAt(),
                attributes));

        credentialStore.save(persisted);
        return new StoredAttestationReplayExpectation(
                encodeBase64Url(vector.registration().challenge()),
                vector.relyingPartyId(),
                vector.origin(),
                vector.format().label());
    }

    private static String expectedAttestationLabel(String credentialId) {
        WebAuthnAttestationVector vector;
        try {
            vector = WebAuthnAttestationSamples.require(credentialId);
        } catch (IllegalArgumentException primary) {
            if (credentialId.startsWith("w3c-")) {
                throw primary;
            }
            vector = WebAuthnAttestationSamples.require("w3c-" + credentialId);
        }
        String algorithm = vector.algorithm() == null ? "" : vector.algorithm().label();
        String format = vector.format() == null ? "" : vector.format().label();
        String section = vector.w3cSection();
        String origin = vector.origin() == null ? "" : vector.origin().trim();
        if (!algorithm.isBlank() && !format.isBlank() && section != null && !section.isBlank()) {
            return algorithm + " (" + format + ", W3C " + section.trim() + ")";
        }
        if (!algorithm.isBlank() && !format.isBlank() && !origin.isBlank()) {
            return algorithm + " (" + format + ", " + origin + ")";
        }
        if (!algorithm.isBlank() && !format.isBlank()) {
            return algorithm + " (" + format + ")";
        }
        if (!algorithm.isBlank()) {
            return algorithm;
        }
        return credentialId;
    }

    private static final class StoredAttestationReplayExpectation {
        private final String challenge;
        private final String relyingPartyId;
        private final String origin;
        private final String format;

        private StoredAttestationReplayExpectation(
                String challenge, String relyingPartyId, String origin, String format) {
            this.challenge = challenge;
            this.relyingPartyId = relyingPartyId;
            this.origin = origin;
            this.format = format;
        }

        private String challenge() {
            return challenge;
        }

        private String relyingPartyId() {
            return relyingPartyId;
        }

        private String origin() {
            return origin;
        }

        private String format() {
            return format;
        }
    }

    private static byte[] extendAuthenticatorData(byte[] authenticatorData, byte[] extensions) {
        byte[] original = authenticatorData == null ? new byte[0] : authenticatorData;
        if (original.length < 33) {
            throw new IllegalArgumentException("Authenticator data must include RP ID hash and flags");
        }
        byte[] extended = Arrays.copyOf(original, original.length + extensions.length);
        extended[32] = (byte) (extended[32] | 0x80);
        System.arraycopy(extensions, 0, extended, original.length, extensions.length);
        return extended;
    }

    private static byte[] signAssertion(
            String privateKeyJwk,
            WebAuthnSignatureAlgorithm algorithm,
            byte[] authenticatorData,
            byte[] clientDataJson) {
        try {
            PrivateKey privateKey = privateKeyFromJwk(privateKeyJwk, algorithm);
            Signature signature = signatureFor(algorithm);
            signature.initSign(privateKey);
            byte[] clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJson);
            signature.update(concat(authenticatorData, clientDataHash));
            return signature.sign();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign assertion with extensions", ex);
        }
    }

    private static PrivateKey privateKeyFromJwk(String jwk, WebAuthnSignatureAlgorithm algorithm)
            throws GeneralSecurityException {
        Map<String, Object> map = parseJwk(jwk);
        String curve = requireString(map, "crv");
        if (!curve.equalsIgnoreCase(namedCurveLabel(algorithm))) {
            throw new IllegalArgumentException(
                    "JWK curve " + curve + " does not match expected " + namedCurveLabel(algorithm));
        }
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curveName(algorithm)));
        ECParameterSpec parameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
        byte[] scalar = decodeField(map, "d");
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(new BigInteger(1, scalar), parameterSpec);
        try {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        } catch (InvalidKeySpecException ex) {
            throw new GeneralSecurityException("Unable to materialise EC private key from JWK", ex);
        }
    }

    private static Map<String, Object> parseJwk(String jwk) {
        Object parsed = SimpleJson.parse(jwk);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Expected JSON object for JWK");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static byte[] decodeField(Map<String, Object> jwk, String field) {
        Object value = jwk.get(field);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalStateException("Missing JWK field: " + field);
        }
        return Base64.getUrlDecoder().decode(str);
    }

    private static Signature signatureFor(WebAuthnSignatureAlgorithm algorithm) throws GeneralSecurityException {
        return switch (algorithm) {
            case ES256 -> Signature.getInstance("SHA256withECDSA");
            case ES384 -> Signature.getInstance("SHA384withECDSA");
            case ES512 -> Signature.getInstance("SHA512withECDSA");
            case RS256 -> Signature.getInstance("SHA256withRSA");
            case PS256 -> Signature.getInstance("RSASSA-PSS");
            case EDDSA -> Signature.getInstance("Ed25519");
        };
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] combined = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return combined;
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String str && !str.isBlank()) {
            return str;
        }
        throw new IllegalArgumentException("Missing JWK field: " + key);
    }

    private static String namedCurveLabel(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "P-256";
            case ES384 -> "P-384";
            case ES512 -> "P-521";
            case RS256, PS256, EDDSA ->
                throw new IllegalArgumentException("Unsupported algorithm for EC curve: " + algorithm);
        };
    }

    private static String curveName(WebAuthnSignatureAlgorithm algorithm) {
        return switch (algorithm) {
            case ES256 -> "secp256r1";
            case ES384 -> "secp384r1";
            case ES512 -> "secp521r1";
            default -> throw new IllegalArgumentException("Unsupported EC algorithm for PEM conversion: " + algorithm);
        };
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            bytes[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return bytes;
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
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.visibilityOfElementLocated(selector));
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
        new WebDriverWait(driver, Duration.ofSeconds(8)).until(webDriver -> {
            String text = webDriver.findElement(selector).getText().trim();
            return predicate.test(text);
        });
    }

    private void awaitValue(By selector, java.util.function.Predicate<String> predicate) {
        new WebDriverWait(driver, Duration.ofSeconds(8)).until(webDriver -> {
            String value = webDriver.findElement(selector).getAttribute("value");
            return predicate.test(value);
        });
    }

    private String waitForNonBlankText(By selector) {
        return new WebDriverWait(driver, Duration.ofSeconds(8)).until(webDriver -> {
            String text = webDriver.findElement(selector).getText();
            if (text == null) {
                return null;
            }
            String normalized = text.trim();
            return normalized.isEmpty() ? null : normalized;
        });
    }

    private void waitForOption(By selector, String value) {
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(webDriver -> {
            try {
                Select select = new Select(webDriver.findElement(selector));
                return select.getOptions().stream().anyMatch(option -> value.equals(option.getAttribute("value")));
            } catch (StaleElementReferenceException ignored) {
                return false;
            }
        });
    }

    private void selectOptionByValue(Select select, String value) {
        for (WebElement option : select.getOptions()) {
            if (value.equals(option.getAttribute("value"))) {
                option.click();
                return;
            }
        }
        throw new IllegalStateException("Option not found: " + value);
    }

    private void awaitCounterValueChange(By selector, long previousValue) {
        new WebDriverWait(driver, Duration.ofSeconds(3)).until(webDriver -> {
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
        assertThat(replayPanel.getAttribute("hidden"))
                .as("replay panel hidden attribute")
                .isNull();
        WebElement evaluatePanel = waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel']"));
        assertThat(evaluatePanel.getAttribute("hidden"))
                .as("evaluate panel hidden attribute")
                .isNotNull();
        WebElement replayModeToggle = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-toggle']"));
        assertThat(replayModeToggle.getAttribute("data-mode"))
                .as("replay mode toggle data-mode")
                .isEqualTo("inline");
        WebElement replayInlineSection = waitFor(By.cssSelector("[data-testid='fido2-replay-inline-section']"));
        assertThat(replayInlineSection.getAttribute("hidden"))
                .as("replay inline section hidden attribute")
                .isNull();
        WebElement replayStoredSection = waitFor(By.cssSelector("[data-testid='fido2-replay-stored-section']"));
        assertThat(replayStoredSection.getAttribute("hidden"))
                .as("replay stored section hidden attribute")
                .isNotNull();
        WebElement credentialIdField = waitFor(By.id("fido2ReplayInlineCredentialId"));
        assertThat(credentialIdField.isDisplayed())
                .as("inline credential id visible")
                .isTrue();
        WebElement publicKeyField = waitFor(By.id("fido2ReplayInlinePublicKey"));
        assertThat(publicKeyField.isDisplayed()).as("inline public key visible").isTrue();
    }

    private static List<String> canonicalAttestationSeedIds() {
        Set<WebAuthnSignatureAlgorithm> algorithms = new LinkedHashSet<>();
        return WebAuthnAttestationSamples.vectors().stream()
                .filter(vector -> algorithms.add(vector.algorithm()))
                .map(Fido2OperatorUiSeleniumTest::resolveGeneratorSample)
                .map(WebAuthnGeneratorSamples.Sample::key)
                .toList();
    }

    private static WebAuthnGeneratorSamples.Sample resolveGeneratorSample(WebAuthnAttestationVector vector) {
        return WebAuthnGeneratorSamples.samples().stream()
                .filter(sample -> Arrays.equals(
                        sample.credentialId(), vector.registration().credentialId()))
                .findFirst()
                .or(() -> WebAuthnGeneratorSamples.samples().stream()
                        .filter(sample -> sample.algorithm() == vector.algorithm())
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException("Missing generator sample for " + vector.vectorId()));
    }

    private void clearCredentialStore() {
        credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
    }

    private void seedAllCuratedCredentials() {
        WebAuthnSeedApplicationService seedService = new WebAuthnSeedApplicationService();
        List<WebAuthnSeedApplicationService.SeedCommand> commands = Fido2OperatorSampleData.seedDefinitions().stream()
                .map(definition -> new WebAuthnSeedApplicationService.SeedCommand(
                        definition.credentialId(),
                        definition.relyingPartyId(),
                        Base64.getUrlDecoder().decode(definition.credentialIdBase64Url()),
                        Base64.getUrlDecoder().decode(definition.publicKeyCoseBase64Url()),
                        definition.signatureCounter(),
                        definition.userVerificationRequired(),
                        definition.algorithm(),
                        definition.privateKeyJwk(),
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
        assertThat(replayPanel.getAttribute("hidden"))
                .as("replay panel hidden attribute")
                .isNotNull();
        WebElement evaluatePanel = waitFor(By.cssSelector("[data-testid='fido2-evaluate-panel']"));
        assertThat(evaluatePanel.getAttribute("hidden"))
                .as("evaluate panel hidden attribute")
                .isNull();
    }

    private static String expectedInlineLabel(Sample sample) {
        String baseLabel = sample.algorithm().label()
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
