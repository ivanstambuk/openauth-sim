package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.credentials.ocra.OcraCredentialDescriptor;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory;
import io.openauth.sim.core.credentials.ocra.OcraCredentialFactory.OcraCredentialRequest;
import io.openauth.sim.core.credentials.ocra.OcraCredentialPersistenceAdapter;
import io.openauth.sim.core.credentials.ocra.OcraResponseCalculator;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretEncoding;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
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
 * Selenium coverage for the OCRA operator console verbose trace integration. Tests are expected to
 * fail until the UI wires the verbose flag and renders traces from the REST endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OcraOperatorUiSeleniumTest {

    private static final String STORED_CREDENTIAL_ID = "ui-ocra-selenium";
    private static final String STORED_SUITE = "OCRA-1:HOTP-SHA1-6:QA08";
    private static final String STORED_SECRET_HEX = "3132333435363738393031323334353637383930";
    private static final String STORED_CHALLENGE = "12345678";

    private static final OcraCredentialFactory CREDENTIAL_FACTORY = new OcraCredentialFactory();
    private static final OcraCredentialPersistenceAdapter PERSISTENCE_ADAPTER = new OcraCredentialPersistenceAdapter();
    private static final OcraCredentialDescriptor STORED_DESCRIPTOR =
            CREDENTIAL_FACTORY.createDescriptor(new OcraCredentialRequest(
                    STORED_CREDENTIAL_ID,
                    STORED_SUITE,
                    STORED_SECRET_HEX,
                    SecretEncoding.HEX,
                    null,
                    null,
                    null,
                    Map.of("ocra.metadata.label", "QA08 Selenium sample")));
    private static final String EXPECTED_OTP = OcraResponseCalculator.generate(
            STORED_DESCRIPTOR,
            new OcraResponseCalculator.OcraExecutionContext(null, STORED_CHALLENGE, null, null, null, null, null));

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("credentials.db");
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
        driver.getWebClient().getOptions().setThrowExceptionOnScriptError(true);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
        seedStoredCredential();
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
    @DisplayName("Verbose trace surfaces for stored OCRA evaluation when enabled")
    void verboseTraceSurfacesForStoredOcraEvaluation() {
        navigateToOcraPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='ocra-mode-toggle']"));
        waitUntilAttribute(modeToggle, "data-mode", "evaluate");

        WebElement storedOption = waitFor(By.id("mode-credential"));
        if (!storedOption.isSelected()) {
            storedOption.click();
        }

        waitForCredentialOptions();
        selectOption(By.id("credentialId"), STORED_CREDENTIAL_ID);

        WebElement challengeInput = waitFor(By.id("challenge"));
        challengeInput.clear();
        challengeInput.sendKeys(STORED_CHALLENGE);

        WebElement verboseCheckbox = waitFor(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertFalse(verboseCheckbox.isSelected(), "Verbose trace should be disabled by default");

        WebElement tracePanel = waitFor(By.cssSelector("[data-testid='verbose-trace-panel']"));
        assertEquals(
                "false",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should remain hidden before verbose evaluation");

        WebElement evaluateButton = waitFor(By.cssSelector("[data-testid='ocra-evaluate-button']"));
        evaluateButton.click();

        waitForVisible(By.cssSelector("[data-testid='ocra-result-panel']"));
        assertEquals(
                "false",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should stay hidden when verbose tracing is disabled");

        if (!verboseCheckbox.isSelected()) {
            verboseCheckbox.click();
        }
        assertTrue(verboseCheckbox.isSelected(), "Verbose trace checkbox should become selected after toggling");

        // Reapply deterministic inputs before re-submitting
        selectOption(By.id("credentialId"), STORED_CREDENTIAL_ID);
        challengeInput = waitFor(By.id("challenge"));
        challengeInput.clear();
        challengeInput.sendKeys(STORED_CHALLENGE);

        evaluateButton.click();

        WebElement traceOperation = waitForVisible(By.cssSelector("[data-testid='verbose-trace-operation']"));
        assertEquals(
                "ocra.evaluate.stored",
                traceOperation.getText().trim(),
                "Trace operation should reflect stored OCRA evaluation");

        WebElement traceContent = waitForVisible(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertTrue(
                traceText.contains("normalize.request"),
                () -> "Trace should include request normalization step\n" + traceText);
        assertTrue(
                traceText.contains("resolve.credential"),
                () -> "Trace should include credential resolution step\n" + traceText);
        assertTrue(
                traceText.contains("assemble.message"),
                () -> "Trace should include message assembly step\n" + traceText);
        assertTrue(
                traceText.contains("compute.hmac"), () -> "Trace should include HMAC computation step\n" + traceText);
        assertTrue(traceText.contains("mod.reduce"), () -> "Trace should include modulo reduction step\n" + traceText);
        assertTrue(
                traceText.contains("metadata.tier = educational"),
                () -> "Trace metadata should advertise educational tier\n" + traceText);
        assertTrue(
                traceText.contains("secret.hash = sha256:"),
                () -> "Trace should surface hashed secret material\n" + traceText);

        assertEquals(
                "true",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should report visible state after verbose evaluation");

        WebElement otpValue = waitFor(By.cssSelector("[data-testid='ocra-otp-value']"));
        assertEquals(EXPECTED_OTP, otpValue.getText().trim(), "Result panel should surface the evaluated OTP");
    }

    @Test
    @DisplayName("Verbose trace surfaces for stored OCRA replay when enabled")
    void verboseTraceSurfacesForStoredOcraReplay() {
        navigateToOcraPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='ocra-mode-toggle']"));
        waitUntilAttribute(modeToggle, "data-mode", "evaluate");

        WebElement replayPill = waitFor(By.cssSelector("[data-testid='ocra-mode-select-replay']"));
        replayPill.click();
        waitUntilAttribute(modeToggle, "data-mode", "replay");

        WebElement storedRadio = waitFor(By.id("replayModeStored"));
        if (!storedRadio.isSelected()) {
            storedRadio.click();
        }

        waitForElementEnabled(By.id("replayCredentialId"));
        selectOption(By.id("replayCredentialId"), STORED_CREDENTIAL_ID);

        WebElement otpInput = waitFor(By.id("replayOtp"));
        otpInput.clear();
        otpInput.sendKeys(EXPECTED_OTP);

        WebElement challengeInput = waitFor(By.id("replayChallenge"));
        challengeInput.clear();
        challengeInput.sendKeys(STORED_CHALLENGE);

        WebElement verboseCheckbox = waitFor(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertFalse(verboseCheckbox.isSelected(), "Verbose trace should start disabled");

        WebElement tracePanel = waitFor(By.cssSelector("[data-testid='verbose-trace-panel']"));
        assertEquals(
                "false",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should be hidden before verbose replay");

        WebElement submitButton = waitFor(By.cssSelector("[data-testid='ocra-replay-submit']"));
        submitButton.click();
        waitForVisible(By.cssSelector("[data-testid='ocra-replay-result']"));
        assertEquals(
                "false",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should remain hidden when verbose tracing is disabled");

        if (!verboseCheckbox.isSelected()) {
            verboseCheckbox.click();
        }
        assertTrue(verboseCheckbox.isSelected(), "Verbose trace checkbox should toggle on");

        // Ensure deterministic payload before replaying
        selectOption(By.id("replayCredentialId"), STORED_CREDENTIAL_ID);
        otpInput = waitFor(By.id("replayOtp"));
        otpInput.clear();
        otpInput.sendKeys(EXPECTED_OTP);
        challengeInput = waitFor(By.id("replayChallenge"));
        challengeInput.clear();
        challengeInput.sendKeys(STORED_CHALLENGE);

        submitButton.click();

        waitForVisible(By.cssSelector("[data-testid='ocra-replay-result']"));
        String replayPayloadJson = (String) driver.executeScript("return window.__ocraReplayLastPayload;");
        assertNotNull(replayPayloadJson, "Replay request payload should be captured for debugging");
        assertTrue(
                replayPayloadJson.contains("\"verbose\":true"),
                () -> "Replay request should include verbose flag when enabled: " + replayPayloadJson);

        String replayResponseJson = (String) driver.executeScript("return window.__ocraReplayLastResponse;");
        assertNotNull(replayResponseJson, "Replay response payload should be captured for debugging");
        assertTrue(
                replayResponseJson.contains("\"trace\""),
                () -> "Replay response should include verbose trace payload when enabled: " + replayResponseJson);

        WebElement traceOperation = waitForVisible(By.cssSelector("[data-testid='verbose-trace-operation']"));
        assertEquals(
                "ocra.verify.stored",
                traceOperation.getText().trim(),
                "Trace operation should reflect stored OCRA verification");

        WebElement traceContent = waitForVisible(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertTrue(
                traceText.contains("normalize.request"),
                () -> "Trace should include request normalization step\n" + traceText);
        assertTrue(
                traceText.contains("resolve.credential"),
                () -> "Trace should include credential resolution step\n" + traceText);
        assertTrue(
                traceText.contains("compare.expected"), () -> "Trace should include OTP comparison step\n" + traceText);
        assertTrue(
                traceText.contains("metadata.tier = educational"),
                () -> "Trace metadata should advertise educational tier\n" + traceText);

        assertEquals(
                "true",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should report visible state after verbose replay");

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='ocra-replay-result']"));
        WebElement statusBadge = resultPanel.findElement(By.cssSelector("[data-testid='ocra-replay-status']"));
        assertEquals("Match", statusBadge.getText().trim(), "Replay result should report match outcome");
    }

    private void navigateToOcraPanel() {
        driver.get("http://localhost:" + port + "/ui/console?protocol=ocra");
        waitFor(By.cssSelector("[data-testid='protocol-tab-ocra']"));
    }

    private void seedStoredCredential() {
        credentialStore.delete(STORED_CREDENTIAL_ID);
        Credential serialized =
                VersionedCredentialRecordMapper.toCredential(PERSISTENCE_ADAPTER.serialize(STORED_DESCRIPTOR));
        Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
        attributes.putIfAbsent("ocra.metadata.suite", STORED_SUITE);
        attributes.putIfAbsent("ocra.metadata.notes", "Seeded for Selenium verbose trace coverage");
        Credential enriched = new Credential(
                serialized.name(),
                serialized.type(),
                serialized.secret(),
                attributes,
                serialized.createdAt(),
                serialized.updatedAt());
        credentialStore.save(enriched);
    }

    private void waitForCredentialOptions() {
        By locator = By.id("credentialId");
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(driverRef -> {
            try {
                Select select = new Select(driverRef.findElement(locator));
                return select.getOptions().stream()
                        .anyMatch(option -> STORED_CREDENTIAL_ID.equals(option.getAttribute("value")));
            } catch (StaleElementReferenceException | NoSuchElementException ex) {
                return false;
            }
        });
    }

    private void waitForElementEnabled(By locator) {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.elementToBeClickable(locator));
    }

    private WebElement waitFor(By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    private WebElement waitForVisible(By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private void waitUntilAttribute(WebElement element, String attribute, String expectedValue) {
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.attributeToBe(element, attribute, expectedValue));
    }

    private void selectOption(By selectLocator, String value) {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(driverRef -> {
            try {
                Select select = new Select(driverRef.findElement(selectLocator));
                select.selectByValue(value);
                return true;
            } catch (StaleElementReferenceException | NoSuchElementException | UnsupportedOperationException ex) {
                return false;
            }
        });
    }
}
