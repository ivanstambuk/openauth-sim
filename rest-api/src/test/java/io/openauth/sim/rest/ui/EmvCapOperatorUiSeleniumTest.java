package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.application.emv.cap.EmvCapSeedApplicationService;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples;
import io.openauth.sim.application.emv.cap.EmvCapSeedSamples.SeedSample;
import io.openauth.sim.core.emv.cap.EmvCapReplayFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures;
import io.openauth.sim.core.emv.cap.EmvCapVectorFixtures.EmvCapVector;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
 * Failing Selenium scaffold for the EMV/CAP operator console tab. The test captures the expected UX
 * so the panel implementation can drive the UI increment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class EmvCapOperatorUiSeleniumTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(12);
    private static final String STORED_CREDENTIAL_ID = "emv-cap-identify-baseline";
    private static final String STORED_PRESET_LABEL = "CAP Identify baseline";
    private static final EmvCapVector BASELINE_VECTOR = EmvCapVectorFixtures.load("identify-baseline");

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
        seedCredentials();
    }

    @AfterEach
    void tearDown() {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (WebDriverException ignored) {
            // driver already disposed
        }
    }

    @Test
    @DisplayName("Stored EMV/CAP preset auto-fills parameters and returns OTP with verbose trace")
    void storedPresetEvaluatesIdentifyMode() {
        navigateToEmvConsole();

        Select credentialSelect = waitForStoredCredentialSelect();
        waitForCredential(credentialSelect, STORED_CREDENTIAL_ID, STORED_PRESET_LABEL);

        WebElement globalVerboseCheckbox = waitForClickable(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertThat(globalVerboseCheckbox.isSelected())
                .as("Global verbose trace checkbox should default to unchecked")
                .isFalse();
        globalVerboseCheckbox.click();
        assertThat(globalVerboseCheckbox.isSelected())
                .as("Global verbose trace checkbox should be enabled for verbose requests")
                .isTrue();
        waitForTracePanelVisibility(false);
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-include-trace']")))
                .as("EMV-specific include-trace checkbox should be removed in favour of global toggle")
                .isEmpty();

        waitForClickable(By.cssSelector("button[data-testid='emv-evaluate-submit']"))
                .click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        waitForStatus("Success");
        WebElement otpNode =
                driver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-otp']"));
        assertThat(otpNode.getText().trim()).isEqualTo(BASELINE_VECTOR.outputs().otpDecimal());

        WebElement telemetryBadge =
                driver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-status']"));
        assertThat(telemetryBadge.getText()).isEqualTo("Success");

        WebElement tracePanel = waitForTracePanelVisibility(true);
        assertThat(tracePanel.getAttribute("hidden"))
                .as("Verbose trace panel should not be hidden after verbose evaluation")
                .isNull();
        WebElement traceContent = waitForVisible(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        int maskLength = expectedMaskLength(BASELINE_VECTOR);
        int maskedDigitsCount =
                expectedMaskedDigitsCount(BASELINE_VECTOR.outputs().maskedDigitsOverlay());
        String sessionKey = BASELINE_VECTOR.outputs().sessionKeyHex();
        String generateAcResult = BASELINE_VECTOR.outputs().generateAcResultHex();
        String terminalHex = BASELINE_VECTOR.outputs().generateAcInputTerminalHex();
        String iccTemplateHex = BASELINE_VECTOR.input().iccDataTemplateHex();
        String iccResolvedHex = BASELINE_VECTOR.outputs().generateAcInputIccHex();
        String bitmaskOverlay = BASELINE_VECTOR.outputs().bitmaskOverlay();
        String maskedDigitsOverlay = BASELINE_VECTOR.outputs().maskedDigitsOverlay();
        String issuerApplicationDataHex = BASELINE_VECTOR.input().issuerApplicationDataHex();
        String atcHex = BASELINE_VECTOR.input().atcHex();
        int branchFactor = BASELINE_VECTOR.input().branchFactor();
        int height = BASELINE_VECTOR.input().height();
        String masterKeyDigest = expectedMasterKeyDigest(BASELINE_VECTOR);
        assertThat(traceText)
                .as("Trace content should include EMV/CAP metadata")
                .contains("operation = emv.cap.evaluate")
                .contains("metadata.maskLength = " + maskLength)
                .contains("metadata.maskedDigitsCount = " + maskedDigitsCount)
                .contains("metadata.atc = " + atcHex)
                .contains("metadata.branchFactor = " + branchFactor)
                .contains("metadata.height = " + height);
        assertThat(traceText)
                .as("Trace steps should surface session key and Generate AC details")
                .contains("masterKey.sha256 = " + masterKeyDigest)
                .contains("sessionKey = " + sessionKey)
                .contains("generateAcResult = " + generateAcResult)
                .contains("terminal = " + terminalHex)
                .contains("iccTemplate = " + iccTemplateHex)
                .contains("iccResolved = " + iccResolvedHex)
                .contains("bitmask = " + bitmaskOverlay)
                .contains("maskedDigitsOverlay = " + maskedDigitsOverlay)
                .contains("issuerApplicationData = " + issuerApplicationDataHex);
        assertThat(driver.findElements(By.cssSelector("[data-testid='verbose-trace-copy']")))
                .as("Shared verbose trace controls should expose copy button")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Disabling verbose trace hides the panel and omits trace content")
    void includeTraceToggleOmitTrace() {
        navigateToEmvConsole();

        Select credentialSelect = waitForStoredCredentialSelect();
        waitForCredential(credentialSelect, STORED_CREDENTIAL_ID, STORED_PRESET_LABEL);

        WebElement globalVerboseCheckbox = waitForClickable(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertThat(globalVerboseCheckbox.isSelected())
                .as("Global verbose toggle should default to unchecked")
                .isFalse();
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-include-trace']")))
                .as("EMV-specific include-trace checkbox should be removed")
                .isEmpty();

        waitForClickable(By.cssSelector("button[data-testid='emv-evaluate-submit']"))
                .click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        waitForStatus("Success");
        WebElement resultCard =
                driver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-otp']"));
        assertThat(resultCard.getText().trim())
                .isEqualTo(BASELINE_VECTOR.outputs().otpDecimal());

        WebElement tracePanel = waitForTracePanelVisibility(false);
        assertThat(tracePanel.getAttribute("hidden"))
                .as("Verbose trace panel should remain hidden when includeTrace is unchecked")
                .isNotNull();
    }

    @Test
    @DisplayName("Stored EMV/CAP replay surfaces match outcome with verbose trace")
    void storedReplayDisplaysMatchOutcome() {
        EmvCapReplayFixtures.ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());

        navigateToEmvConsole();

        WebElement globalVerboseCheckbox = waitForClickable(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        if (!globalVerboseCheckbox.isSelected()) {
            globalVerboseCheckbox.click();
        }
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-replay-include-trace']")))
                .as("Replay-specific include-trace checkbox should be removed")
                .isEmpty();

        waitForClickable(By.cssSelector("[data-testid='emv-console-tab-replay']"))
                .click();

        Select storedSelect = waitForReplayStoredCredentialSelect();
        waitForReplayCredential(storedSelect, fixture.credentialId(), "CAP Respond baseline");

        WebElement otpInput = waitForVisible(By.cssSelector("[data-testid='emv-replay-otp'] input[type='text']"));
        otpInput.clear();
        otpInput.sendKeys(fixture.otpDecimal());

        setNumericInput(
                By.cssSelector("[data-testid='emv-replay-drift-backward'] input"),
                fixture.previewWindow().backward());
        setNumericInput(
                By.cssSelector("[data-testid='emv-replay-drift-forward'] input"),
                fixture.previewWindow().forward());

        waitForClickable(By.cssSelector("button[data-testid='emv-replay-submit']"))
                .click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        WebElement resultCard = waitForVisible(By.cssSelector("[data-testid='emv-replay-result-card']"));
        WebElement statusBadge = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-status']"));
        assertThat(statusBadge.getText()).isEqualTo("Match");

        WebElement otpNode = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-otp']"));
        assertThat(otpNode.getText()).contains(fixture.otpDecimal());

        WebElement deltaNode = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-matched-delta']"));
        assertThat(deltaNode.getText()).contains("Δ = 0");

        WebElement tracePanel = waitForTracePanelVisibility(true);
        assertThat(tracePanel.getAttribute("hidden"))
                .as("Trace panel should be visible after verbose replay")
                .isNull();
        WebElement traceContent = waitForVisible(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertThat(traceText)
                .contains("operation = emv.cap.replay")
                .contains("mode = RESPOND")
                .contains("credentialSource = stored")
                .contains("matchedDelta = 0")
                .contains("suppliedOtp = " + fixture.otpDecimal())
                .contains("masterKey.sha256 = " + expectedMasterKeyDigest(vector));
    }

    @Test
    @DisplayName("Inline EMV/CAP replay with mismatched OTP renders mismatch status")
    void inlineReplayShowsMismatchOutcome() {
        EmvCapReplayFixtures.ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());

        navigateToEmvConsole();
        waitForClickable(By.cssSelector("[data-testid='emv-console-tab-replay']"))
                .click();

        WebElement globalVerboseCheckbox = waitForClickable(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertThat(globalVerboseCheckbox.isSelected())
                .as("Global verbose toggle should remain unchecked by default")
                .isFalse();

        waitForClickable(By.cssSelector("[data-testid='emv-replay-mode-inline'] input[type='radio']"))
                .click();

        populateReplayInlineForm(vector);

        WebElement otpInput = waitForVisible(By.cssSelector("[data-testid='emv-replay-otp'] input[type='text']"));
        otpInput.clear();
        otpInput.sendKeys(fixture.mismatchOtpDecimal());

        setNumericInput(
                By.cssSelector("[data-testid='emv-replay-drift-backward'] input"),
                fixture.previewWindow().backward());
        setNumericInput(
                By.cssSelector("[data-testid='emv-replay-drift-forward'] input"),
                fixture.previewWindow().forward());

        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-replay-include-trace']")))
                .as("Replay panel should not expose its own include-trace checkbox")
                .isEmpty();

        waitForClickable(By.cssSelector("button[data-testid='emv-replay-submit']"))
                .click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        WebElement resultCard = waitForVisible(By.cssSelector("[data-testid='emv-replay-result-card']"));
        WebElement statusBadge = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-status']"));
        assertThat(statusBadge.getText()).isEqualTo("Mismatch");

        WebElement reasonNode = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-reason']"));
        assertThat(reasonNode.getText()).contains("OTP mismatch");

        WebElement tracePanel = waitForTracePanelVisibility(false);
        assertThat(tracePanel.getAttribute("hidden"))
                .as("Trace panel should remain hidden when includeTrace is unchecked")
                .isNotNull();
    }

    private Select waitForStoredCredentialSelect() {
        WebElement element = new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.presenceOfElementLocated(By.id("emvStoredCredentialId")));
        return new Select(element);
    }

    private Select waitForReplayStoredCredentialSelect() {
        WebElement element = new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.presenceOfElementLocated(By.id("emvReplayStoredCredentialId")));
        return new Select(element);
    }

    private void navigateToEmvConsole() {
        driver.get("http://localhost:" + port + "/ui/console?protocol=emv");
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-protocol-panel='emv']")));
    }

    private void seedCredentials() {
        credentialStore.findAll().stream()
                .filter(credential -> credential.name().startsWith("emv-cap-"))
                .map(credential -> credential.name())
                .forEach(credentialStore::delete);
        EmvCapSeedApplicationService seedService = new EmvCapSeedApplicationService();
        List<SeedSample> samples = EmvCapSeedSamples.samples();
        seedService.seed(samples.stream().map(SeedSample::toSeedCommand).toList(), credentialStore);
    }

    private void waitForCredential(Select select, String id, String label) {
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> select.getOptions().stream().anyMatch(option -> id.equals(option.getAttribute("value"))));
        assertThat(select.getOptions().stream()
                        .anyMatch(option -> label.equals(option.getText().trim())))
                .as("Stored credential dropdown should expose friendly preset label")
                .isTrue();
        select.selectByValue(id);
        waitForNonEmptyValue(By.id("emvMasterKey"), "Master key should auto-populate from stored preset");
    }

    private void waitForReplayCredential(Select select, String id, String label) {
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> select.getOptions().stream().anyMatch(option -> id.equals(option.getAttribute("value"))));
        assertThat(select.getOptions().stream()
                        .anyMatch(option -> label.equals(option.getText().trim())))
                .as("Replay stored credential dropdown should expose preset label")
                .isTrue();
        select.selectByValue(id);
        waitForNonEmptyValue(
                By.cssSelector("[data-testid='emv-replay-master-key'] input"), "Master key should populate");
    }

    private void populateReplayInlineForm(EmvCapVector vector) {
        setHexInput(
                By.cssSelector("[data-testid='emv-replay-master-key'] input"),
                vector.input().masterKeyHex());
        setHexInput(
                By.cssSelector("[data-testid='emv-replay-atc'] input"),
                vector.input().atcHex());
        setNumericInput(
                By.cssSelector("[data-testid='emv-replay-branch-factor'] input"),
                vector.input().branchFactor());
        setNumericInput(
                By.cssSelector("[data-testid='emv-replay-height'] input"),
                vector.input().height());
        setHexInput(
                By.cssSelector("[data-testid='emv-replay-iv'] input"),
                vector.input().ivHex());
        setHexInput(
                By.cssSelector("[data-testid='emv-replay-cdol1'] textarea"),
                vector.input().cdol1Hex());
        setHexInput(
                By.cssSelector("[data-testid='emv-replay-issuer-bitmap'] textarea"),
                vector.input().issuerProprietaryBitmapHex());

        setDecimalInput(
                By.cssSelector("[data-testid='emv-replay-challenge'] input"),
                vector.input().customerInputs().challenge());
        setDecimalInput(
                By.cssSelector("[data-testid='emv-replay-reference'] input"),
                vector.input().customerInputs().reference());
        setDecimalInput(
                By.cssSelector("[data-testid='emv-replay-amount'] input"),
                vector.input().customerInputs().amount());

        setHexInput(
                By.cssSelector("[data-testid='emv-replay-icc-template'] textarea"),
                vector.input().iccDataTemplateHex());
        setHexInput(
                By.cssSelector("[data-testid='emv-replay-issuer-application-data'] textarea"),
                vector.input().issuerApplicationDataHex());
    }

    private void setHexInput(By locator, String value) {
        WebElement element = waitForVisible(locator);
        element.clear();
        element.sendKeys(value);
    }

    private void setDecimalInput(By locator, String value) {
        WebElement element = waitForVisible(locator);
        element.clear();
        if (value != null && !value.isBlank()) {
            element.sendKeys(value);
        }
    }

    private void setNumericInput(By locator, int value) {
        WebElement element = waitForVisible(locator);
        element.clear();
        element.sendKeys(String.valueOf(value));
    }

    private WebElement waitForVisible(By locator) {
        return new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private WebElement waitForClickable(By locator) {
        return new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
    }

    private void waitForNonEmptyValue(By locator, String message) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> {
            WebElement element = driver.findElement(locator);
            return element != null && !element.getAttribute("value").trim().isEmpty();
        });
        WebElement element = driver.findElement(locator);
        assertThat(element.getAttribute("value")).as(message).isNotBlank();
    }

    private void waitForStatus(String expected) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(webDriver -> {
            WebElement badge =
                    webDriver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-status']"));
            return badge != null && expected.equals(badge.getText().trim());
        });
    }

    private static int expectedMaskLength(EmvCapVector vector) {
        return expectedMaskedDigitsCount(vector.outputs().maskedDigitsOverlay());
    }

    private static int expectedMaskedDigitsCount(String overlay) {
        if (overlay == null) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < overlay.length(); index++) {
            if (overlay.charAt(index) != '.') {
                count++;
            }
        }
        return count;
    }

    private static String expectedMasterKeyDigest(EmvCapVector vector) {
        return sha256Digest(vector.input().masterKeyHex());
    }

    private static String sha256Digest(String hex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = hexToBytes(hex);
            byte[] hashed = digest.digest(bytes);
            return "sha256:" + toHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String normalized = hex.trim().toUpperCase(Locale.ROOT);
        if ((normalized.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex input must contain an even number of characters");
        }
        byte[] data = new byte[normalized.length() / 2];
        for (int index = 0; index < normalized.length(); index += 2) {
            data[index / 2] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
        }
        return data;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02X", value));
        }
        return builder.toString();
    }

    private WebElement waitForTracePanelVisibility(boolean visible) {
        return new WebDriverWait(driver, WAIT_TIMEOUT).until(webDriver -> {
            WebElement panel = webDriver.findElement(By.cssSelector("[data-testid='verbose-trace-panel']"));
            String state = panel.getAttribute("data-trace-visible");
            if (visible) {
                return "true".equals(state) ? panel : null;
            }
            return !"true".equals(state) ? panel : null;
        });
    }
}
