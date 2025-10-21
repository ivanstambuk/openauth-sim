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
import java.util.List;
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
final class OperatorConsoleReplaySeleniumTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final String STORED_CREDENTIAL_ID = "operator-demo";
    private static final String STORED_SUITE = "OCRA-1:HOTP-SHA256-8:QA08-S064";
    private static final String STORED_SECRET_HEX = "3132333435363738393031323334353637383930313233343536373839303132";
    private static final String STORED_CHALLENGE = "SESSION01";
    private static final String STORED_SESSION_HEX = "00112233445566778899AABBCCDDEEFF102132435465768798A9BACBDCEDF0EF"
            + "112233445566778899AABBCCDDEEFF0089ABCDEF0123456789ABCDEF01234567";
    private static final String STORED_EXPECTED_OTP = "17477202";
    private static final String CUSTOM_CREDENTIAL_ID = "custom-no-sample";
    private static final String CUSTOM_SUITE = "OCRA-1:HOTP-SHA1-6:QA08";

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("credentials.db");
        Path samplePath = Path.of("data/credentials.db");
        if (!Files.exists(samplePath)) {
            samplePath = Path.of("data/ocra-credentials.db");
        }
        if (!Files.exists(samplePath)) {
            samplePath = Path.of("../data/credentials.db");
        }
        if (!Files.exists(samplePath)) {
            samplePath = Path.of("../data/ocra-credentials.db");
        }
        try {
            Files.copy(samplePath, databasePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // fall back to empty store when sample copy unavailable
        }
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

    @BeforeEach
    void setUp() {
        driver = new HtmlUnitDriver(true);
        driver.setJavascriptEnabled(true);
        driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
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
        navigateToReplayConsole();
        waitForReplayBootstrap();

        assertThat(credentialStore.exists(STORED_CREDENTIAL_ID))
                .as("Stored replay credential should exist after fallback seeding")
                .isTrue();

        WebElement inlineMode = driver.findElement(By.id("replayModeInline"));
        WebElement storedMode = driver.findElement(By.id("replayModeStored"));
        assertThat(inlineMode.isSelected())
                .as("Inline mode should be selected by default")
                .isTrue();
        assertThat(storedMode.isSelected()).isFalse();

        WebElement otpFieldBefore = driver.findElement(By.id("replayOtp"));
        assertThat(otpFieldBefore.getAttribute("value")).isEmpty();

        String presetVisibilityScript = "var el = document.querySelector('[data-replay-inline-preset]');"
                + "if (!el) { return false; }"
                + "var hidden = el.hasAttribute('hidden');"
                + "var ariaHidden = el.getAttribute('aria-hidden') === 'true';"
                + "var displayNone = el.offsetParent === null;"
                + "return !hidden && !ariaHidden && !displayNone;";

        Boolean presetVisibleInline = (Boolean) ((JavascriptExecutor) driver).executeScript(presetVisibilityScript);
        assertThat(presetVisibleInline)
                .as("inline preset selector should render when inline mode is active")
                .isTrue();

        if (!storedMode.isSelected()) {
            storedMode.click();
            waitForBackgroundJavaScript();
        }

        Boolean presetVisibleStored = (Boolean) ((JavascriptExecutor) driver).executeScript(presetVisibilityScript);
        assertThat(presetVisibleStored)
                .as("inline preset selector should be hidden when stored mode is active")
                .isFalse();

        waitForElementEnabled(By.id("replayCredentialId"));
        waitForStoredCredentialOptions();
        Select credentialSelect = new Select(driver.findElement(By.id("replayCredentialId")));
        credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

        waitForSampleStatusContains("Sample");
        waitUntilFieldValue(By.id("replayOtp"), STORED_EXPECTED_OTP);
        waitUntilFieldValue(By.id("replayChallenge"), STORED_CHALLENGE);
        waitUntilFieldValue(By.id("replaySessionHex"), STORED_SESSION_HEX);

        driver.findElement(By.cssSelector("button[data-testid='ocra-replay-submit']"))
                .click();

        JsonNode response = awaitReplayResponse();
        String payload = currentPayload();
        assertThat(response.path("ok").asBoolean())
                .as("replay response should succeed: %s payload=%s", response, payload)
                .isTrue();

        WebElement resultPanel = new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("[data-testid='ocra-replay-result']")));
        assertThat(resultPanel.getAttribute("hidden")).isNull();

        WebElement status = resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-status']"));
        assertThat(status.getText()).isEqualTo("Match");
        assertThat(telemetryValue(resultPanel, "ocra-replay-reason-code")).isEqualTo("match");
        assertThat(telemetryValue(resultPanel, "ocra-replay-outcome")).isEqualTo("match");
        List<WebElement> metadataRows = resultPanel.findElements(By.cssSelector(".result-metadata .result-row"));
        assertThat(metadataRows)
                .as("Replay result should render at least two metadata rows")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-telemetry-id']")))
                .as("Telemetry ID field should be removed from replay result")
                .isEmpty();
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-credential-source']")))
                .as("Credential Source field should be removed from replay result")
                .isEmpty();
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-fingerprint']")))
                .as("Context Fingerprint field should be removed from replay result")
                .isEmpty();
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-sanitized']")))
                .as("Sanitized field should be removed from replay result")
                .isEmpty();

        WebElement errorPanel = driver.findElement(By.cssSelector("[data-testid='ocra-replay-error']"));
        assertThat(errorPanel.getAttribute("hidden")).isNotNull();
    }

    @Test
    @DisplayName("Stored sample loader populates OTP and context for curated credential")
    void storedSampleLoaderAppliesContext() {
        navigateToReplayConsole();
        waitForReplayBootstrap();
        WebElement storedMode = driver.findElement(By.id("replayModeStored"));
        if (!storedMode.isSelected()) {
            storedMode.click();
            waitForBackgroundJavaScript();
        }
        waitForStoredCredentialOptions();

        Select credentialSelect = new Select(driver.findElement(By.id("replayCredentialId")));
        credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

        waitForSampleStatusContains("Sample");
        waitUntilFieldValue(By.id("replayOtp"), STORED_EXPECTED_OTP);
        assertThat(driver.findElement(By.id("replayChallenge")).getAttribute("value"))
                .isEqualTo(STORED_CHALLENGE);
        assertThat(driver.findElement(By.id("replaySessionHex")).getAttribute("value"))
                .isEqualTo(STORED_SESSION_HEX);

        WebElement advancedPanel = driver.findElement(By.cssSelector("[data-testid='ocra-replay-advanced-panel']"));
        assertThat(advancedPanel.getAttribute("data-open")).isEqualTo("true");
        assertThat(advancedPanel.getAttribute("hidden")).isNull();
    }

    @Test
    @DisplayName("Sample loader remains disabled when curated data is unavailable")
    void sampleLoaderDisabledForUnknownCredential() {
        navigateToReplayConsole();
        waitForReplayBootstrap();
        WebElement storedMode = driver.findElement(By.id("replayModeStored"));
        if (!storedMode.isSelected()) {
            storedMode.click();
            waitForBackgroundJavaScript();
        }
        waitForStoredCredentialOptions();

        Select credentialSelect = new Select(driver.findElement(By.id("replayCredentialId")));
        credentialSelect.selectByValue(CUSTOM_CREDENTIAL_ID);

        waitForSampleStatusContains("No curated sample data");
        assertThat(driver.findElement(By.id("replayOtp")).getAttribute("value")).isEmpty();
    }

    @Test
    @DisplayName("Inline replay renders match outcome and sanitized telemetry")
    void inlineReplayRendersMatchOutcome() {
        navigateToReplayConsole();
        waitForReplayBootstrap();

        driver.findElement(By.id("replayModeInline")).click();
        waitForBackgroundJavaScript();

        waitForElementEnabled(By.cssSelector("select[data-testid='replay-inline-policy-select']"));
        waitForElementEnabled(By.id("replaySuite"));
        waitForElementEnabled(By.id("replaySharedSecretHex"));
        waitForElementEnabled(By.id("replayOtp"));
        waitForElementEnabled(By.id("replayChallenge"));

        WebElement presetSelect = new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("select[data-testid='replay-inline-policy-select']")));
        assertThat(presetSelect.isDisplayed()).isTrue();
        WebElement replayForm = driver.findElement(By.cssSelector("[data-testid='ocra-replay-form']"));
        WebElement modeToggle = replayForm.findElement(By.cssSelector("fieldset.mode-toggle"));
        WebElement presetContainer = replayForm.findElement(By.cssSelector("[data-replay-inline-preset]"));
        Boolean presetAfterMode = (Boolean) ((JavascriptExecutor) driver)
                .executeScript(
                        "var preset = arguments[0];"
                                + "var mode = arguments[1];"
                                + "if (!preset || !mode) { return false; }"
                                + "var position = mode.compareDocumentPosition(preset);"
                                + "return (position & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;",
                        presetContainer,
                        modeToggle);
        assertThat(presetAfterMode)
                .as("Sample preset block should render after the replay mode selector")
                .isTrue();
        Boolean presetBeforeOtp = (Boolean) ((JavascriptExecutor) driver)
                .executeScript(
                        "var preset = arguments[0];"
                                + "var otp = document.getElementById('replayOtp');"
                                + "if (!preset || !otp) { return false; }"
                                + "var position = preset.compareDocumentPosition(otp);"
                                + "return (position & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;",
                        presetContainer);
        assertThat(presetBeforeOtp)
                .as("OTP field group should appear after the sample preset block")
                .isTrue();
        assertThat(presetContainer.getAttribute("hidden")).isNull();
        assertThat(presetContainer.isDisplayed()).isTrue();

        Select preset = new Select(presetSelect);
        preset.selectByValue("qa08-s064");
        waitForBackgroundJavaScript();

        WebElement otpField = driver.findElement(By.id("replayOtp"));
        String autoOtp = otpField.getAttribute("value");
        assertThat(autoOtp).isNotBlank();
        assertThat(driver.findElement(By.id("replaySuite")).getAttribute("value"))
                .isEqualTo(STORED_SUITE);
        assertThat(driver.findElement(By.id("replaySharedSecretHex")).getAttribute("value"))
                .isEqualTo(STORED_SECRET_HEX);
        assertThat(driver.findElement(By.id("replayChallenge")).getAttribute("value"))
                .isEqualTo(STORED_CHALLENGE);

        WebElement advancedToggle = driver.findElement(By.cssSelector("button[data-testid='replay-advanced-toggle']"));
        if ("false".equals(advancedToggle.getAttribute("aria-expanded"))) {
            advancedToggle.click();
            waitForBackgroundJavaScript();
        }

        waitForElementEnabled(By.id("replaySessionHex"));
        assertThat(driver.findElement(By.id("replaySessionHex")).getAttribute("value"))
                .isEqualTo(STORED_SESSION_HEX);

        driver.findElement(By.cssSelector("button[data-testid='ocra-replay-submit']"))
                .click();

        JsonNode response = awaitReplayResponse();
        String payload = currentPayload();
        assertThat(response.path("ok").asBoolean())
                .as("inline replay should succeed: %s payload=%s", response, payload)
                .isTrue();

        WebElement resultPanel = new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("[data-testid='ocra-replay-result']")));
        assertThat(resultPanel.getAttribute("hidden")).isNull();

        assertThat(telemetryValue(resultPanel, "ocra-replay-reason-code")).isEqualTo("match");
        assertThat(telemetryValue(resultPanel, "ocra-replay-outcome")).isEqualTo("match");
        assertThat(resultPanel.findElements(By.cssSelector(".result-metadata .result-row")))
                .as("Replay result should render one metadata row per entry")
                .hasSize(2);
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-telemetry-id']")))
                .as("Telemetry ID field should be removed from replay result")
                .isEmpty();
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-credential-source']")))
                .as("Credential Source field should be removed from replay result")
                .isEmpty();
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-fingerprint']")))
                .as("Context Fingerprint field should be removed from replay result")
                .isEmpty();
        assertThat(resultPanel.findElements(By.cssSelector("[data-testid='ocra-replay-sanitized']")))
                .as("Sanitized field should be removed from replay result")
                .isEmpty();

        WebElement status = resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-status']"));
        assertThat(status.getText()).isEqualTo("Match");
    }

    @Test
    @DisplayName("Inline replay surfaces validation error when OTP is missing")
    void inlineReplaySurfacesValidationError() {
        navigateToReplayConsole();
        waitForReplayBootstrap();

        driver.findElement(By.id("replayModeInline")).click();
        waitForBackgroundJavaScript();

        waitForElementEnabled(By.id("replaySuite"));
        waitForElementEnabled(By.id("replaySharedSecretHex"));
        waitForElementEnabled(By.id("replayChallenge"));
        driver.findElement(By.id("replaySuite")).sendKeys(STORED_SUITE);
        driver.findElement(By.id("replaySharedSecretHex")).sendKeys(STORED_SECRET_HEX);
        driver.findElement(By.id("replayChallenge")).sendKeys(STORED_CHALLENGE);

        driver.findElement(By.cssSelector("button[data-testid='ocra-replay-submit']"))
                .click();

        JsonNode response = awaitReplayResponse();
        assertThat(response.path("ok").asBoolean())
                .as("inline replay missing OTP should fail: %s", response)
                .isFalse();

        WebElement errorPanel = new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("[data-testid='ocra-replay-error']")));
        assertThat(errorPanel.getAttribute("hidden")).isNull();
        assertThat(errorPanel.getText()).contains("validation_failure").contains("otp");

        WebElement secretField = driver.findElement(By.id("replaySharedSecretHex"));
        assertThat(secretField.getAttribute("value")).isEqualTo(STORED_SECRET_HEX);

        WebElement resultPanel = driver.findElement(By.cssSelector("[data-testid='ocra-replay-result']"));
        assertThat(resultPanel.getAttribute("hidden")).isNotNull();
    }

    @Test
    @DisplayName("OCRA replay defaults to inline parameters listed before stored credential")
    void ocraReplayDefaultsToInlineMode() {
        navigateToReplayConsole();
        waitForReplayBootstrap();

        WebElement inlineMode = driver.findElement(By.id("replayModeInline"));
        WebElement storedMode = driver.findElement(By.id("replayModeStored"));

        assertThat(inlineMode.isSelected()).isTrue();
        assertThat(storedMode.isSelected()).isFalse();

        List<String> labels = driver.findElements(By.cssSelector("fieldset.mode-toggle .mode-option label")).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();

        assertThat(labels).as("Replay mode labels: %s", labels).hasSize(2);
        assertThat(labels.get(0)).startsWith("Inline");
        assertThat(labels.get(1)).isEqualTo("Stored credential");
    }

    private void waitForReplayBootstrap() {
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d)
                        .executeScript("return typeof window.__ocraReplayReady !== 'undefined'"
                                + " && window.__ocraReplayReady === true;")));
    }

    private void waitForStoredCredentialOptions() {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
            Object count = ((JavascriptExecutor) d)
                    .executeScript("var select = document.getElementById('replayCredentialId');"
                            + "return select ? select.options.length : 0;");
            return count instanceof Number && ((Number) count).intValue() > 1;
        });
    }

    private WebElement sampleStatusElement() {
        return driver.findElement(By.cssSelector("[data-testid='replay-sample-status']"));
    }

    private void waitForSampleStatusContains(String fragment) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
            WebElement status = sampleStatusElement();
            if (status.getAttribute("hidden") != null) {
                return false;
            }
            return status.getText().contains(fragment);
        });
    }

    private void waitUntilFieldValue(By locator, String expectedValue) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
            WebElement element = d.findElement(locator);
            return expectedValue.equals(element.getAttribute("value"));
        });
    }

    private void waitForElementEnabled(By locator) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
    }

    private void waitForBackgroundJavaScript() {
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());
    }

    private JsonNode awaitReplayResponse() {
        String payload = new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
            Object value = ((JavascriptExecutor) d).executeScript("return window.__ocraReplayLastResponse;");
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
        Object value = ((JavascriptExecutor) driver).executeScript("return window.__ocraReplayLastPayload;");
        return value instanceof String ? (String) value : null;
    }

    private String telemetryValue(WebElement panel, String testId) {
        return panel.findElement(By.cssSelector("[data-testid='" + testId + "']"))
                .getText()
                .trim();
    }

    private void waitForPanelVisibility(By locator, PanelVisibility expectedVisibility) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
            WebElement element = d.findElement(locator);
            boolean hidden = element.getAttribute("hidden") != null;
            return expectedVisibility == PanelVisibility.VISIBLE ? !hidden : hidden;
        });
    }

    private enum PanelVisibility {
        VISIBLE,
        HIDDEN
    }

    private void navigateToReplayConsole() {
        driver.get(baseUrl("/ui/console"));
        WebElement modeToggle = new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-testid='ocra-mode-toggle']")));

        if (!"replay".equals(modeToggle.getAttribute("data-mode"))) {
            driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-replay']"))
                    .click();
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(d -> "replay".equals(modeToggle.getAttribute("data-mode")));
        }

        waitForPanelVisibility(By.cssSelector("[data-testid='ocra-replay-panel']"), PanelVisibility.VISIBLE);
        waitForPanelVisibility(By.cssSelector("[data-testid='ocra-evaluate-panel']"), PanelVisibility.HIDDEN);
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private void seedStoredCredential() {
        if (!credentialStore.exists(STORED_CREDENTIAL_ID)) {
            persistCredential(STORED_CREDENTIAL_ID, STORED_SUITE);
        }
        if (!credentialStore.exists(CUSTOM_CREDENTIAL_ID)) {
            persistCredential(CUSTOM_CREDENTIAL_ID, CUSTOM_SUITE);
        }
    }

    private void persistCredential(String credentialId, String suite) {
        credentialStore.delete(credentialId);
        OcraCredentialFactory factory = new OcraCredentialFactory();
        OcraCredentialRequest request = new OcraCredentialRequest(
                credentialId,
                suite,
                STORED_SECRET_HEX,
                SecretEncoding.HEX,
                null,
                null,
                null,
                Map.of("source", "selenium-replay-test"));
        OcraCredentialDescriptor descriptor = factory.createDescriptor(request);
        Credential credential = VersionedCredentialRecordMapper.toCredential(
                new OcraCredentialPersistenceAdapter().serialize(descriptor));
        credentialStore.save(credential);
        assertThat(credentialStore.exists(credentialId)).isTrue();
    }
}
