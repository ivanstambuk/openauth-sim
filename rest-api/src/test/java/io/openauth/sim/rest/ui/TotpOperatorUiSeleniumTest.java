package io.openauth.sim.rest.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.SecretMaterial;
import io.openauth.sim.core.otp.totp.TotpCredentialPersistenceAdapter;
import io.openauth.sim.core.otp.totp.TotpDescriptor;
import io.openauth.sim.core.otp.totp.TotpDriftWindow;
import io.openauth.sim.core.otp.totp.TotpGenerator;
import io.openauth.sim.core.otp.totp.TotpHashAlgorithm;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
 * Selenium scaffolding for the TOTP operator console coverage. Tests are expected to fail until the
 * TOTP evaluation UI is implemented.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class TotpOperatorUiSeleniumTest {

    private static final String STORED_CREDENTIAL_ID = "ui-totp-sample-sha1-6";
    private static final SecretMaterial STORED_SECRET = SecretMaterial.fromStringUtf8("1234567890123456789012");
    private static final TotpDescriptor STORED_DESCRIPTOR = TotpDescriptor.create(
            STORED_CREDENTIAL_ID,
            STORED_SECRET,
            TotpHashAlgorithm.SHA1,
            6,
            Duration.ofSeconds(30),
            TotpDriftWindow.of(1, 1));
    private static final Instant STORED_TIMESTAMP = Instant.ofEpochSecond(1_111_111_111L);
    private static final String EXPECTED_STORED_OTP = TotpGenerator.generate(STORED_DESCRIPTOR, STORED_TIMESTAMP);

    private static final SecretMaterial INLINE_SECRET =
            SecretMaterial.fromStringUtf8("1234567890123456789012345678901234567890123456789012345678901234");
    private static final String INLINE_SECRET_BASE32 =
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA=";
    private static final TotpDescriptor INLINE_DESCRIPTOR = TotpDescriptor.create(
            "inline-demo",
            INLINE_SECRET,
            TotpHashAlgorithm.SHA512,
            8,
            Duration.ofSeconds(60),
            TotpDriftWindow.of(0, 0));
    private static final Instant INLINE_TIMESTAMP = Instant.ofEpochSecond(1_234_567_890L);
    private static final String INLINE_EXPECTED_OTP = TotpGenerator.generate(INLINE_DESCRIPTOR, INLINE_TIMESTAMP);

    private static final String INLINE_SAMPLE_PRESET_KEY = "inline-rfc6238-sha1";
    private static final SecretMaterial INLINE_SAMPLE_SECRET = SecretMaterial.fromStringUtf8("12345678901234567890");
    private static final TotpDescriptor INLINE_SAMPLE_DESCRIPTOR = TotpDescriptor.create(
            "inline-rfc6238-sample",
            INLINE_SAMPLE_SECRET,
            TotpHashAlgorithm.SHA1,
            8,
            Duration.ofSeconds(30),
            TotpDriftWindow.of(1, 1));
    private static final Instant INLINE_SAMPLE_TIMESTAMP = Instant.ofEpochSecond(59L);
    private static final String INLINE_SAMPLE_EXPECTED_OTP =
            TotpGenerator.generate(INLINE_SAMPLE_DESCRIPTOR, INLINE_SAMPLE_TIMESTAMP);
    private static final List<String> INLINE_SAMPLE_PRESET_KEYS = List.of(
            "inline-rfc6238-sha1",
            "inline-rfc6238-sha256-6",
            "inline-rfc6238-sha256-8",
            "inline-rfc6238-sha512-6",
            "inline-rfc6238-sha512-8",
            "inline-ui-totp-demo");
    private static final String SHARED_SECRET_HINT = "↔ Converts automatically when you switch modes.";

    @TempDir
    static Path tempDir;

    private static Path databasePath;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        databasePath = tempDir.resolve("credentials.db");
        registry.add("openauth.sim.persistence.database-path", () -> databasePath.toString());
        registry.add("openauth.sim.persistence.enable-store", () -> "true");
    }

    @Autowired
    private CredentialStore credentialStore;

    @LocalServerPort
    private int port;

    private HtmlUnitDriver driver;

    private final TotpCredentialPersistenceAdapter adapter = new TotpCredentialPersistenceAdapter();

    @BeforeEach
    void setUp() {
        driver = new HtmlUnitDriver(true);
        driver.setJavascriptEnabled(true);
        driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
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
    @DisplayName("Stored TOTP credential evaluation succeeds via operator console")
    void storedTotpCredentialEvaluationSucceeds() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        WebElement storedToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-stored']"));
        storedToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "stored");

        WebElement storedLabel = driver.findElement(By.cssSelector("label[for='totpStoredCredentialId']"));
        assertEquals("Stored credential", storedLabel.getText().trim());

        selectOption("totpStoredCredentialId", STORED_CREDENTIAL_ID);

        WebElement storedTimestampAuto =
                driver.findElement(By.cssSelector("[data-testid='totp-stored-timestamp-toggle']"));
        if (storedTimestampAuto.isSelected()) {
            storedTimestampAuto.click();
            waitUntilCondition(() -> !storedTimestampAuto.isSelected());
        }
        WebElement timestampInput = driver.findElement(By.id("totpStoredTimestamp"));
        timestampInput.clear();
        timestampInput.sendKeys(Long.toString(STORED_TIMESTAMP.getEpochSecond()));

        WebElement windowBackward = driver.findElement(By.id("totpStoredWindowBackward"));
        windowBackward.clear();
        windowBackward.sendKeys("1");

        WebElement windowForward = driver.findElement(By.id("totpStoredWindowForward"));
        windowForward.clear();
        windowForward.sendKeys("1");

        driver.findElement(By.cssSelector("[data-testid='totp-stored-evaluate-button']"))
                .click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-stored-result-panel']"));
        WebElement previewTable = resultPanel.findElement(By.cssSelector("[data-testid='totp-stored-preview-table']"));
        List<WebElement> headerCells = previewTable.findElements(By.cssSelector("thead th"));
        assertEquals(3, headerCells.size(), "Preview table should expose exactly three columns");
        assertTrue(
                headerCells.get(0).getAttribute("class").contains("result-preview__heading--counter"),
                "Counter heading should carry the counter heading class");
        assertTrue(
                headerCells.get(1).getAttribute("class").contains("result-preview__heading--delta"),
                "Δ heading should carry the delta heading class");
        assertEquals("Δ", headerCells.get(1).getText().trim(), "Δ heading should render the delta glyph");
        assertTrue(
                headerCells.get(2).getAttribute("class").contains("result-preview__heading--otp"),
                "OTP heading should carry the OTP heading class");
        List<String> headerLabels = headerCells.stream()
                .map(cell -> cell.getText().trim().toLowerCase(Locale.US))
                .collect(Collectors.toList());
        assertEquals("counter", headerLabels.get(0));
        assertEquals("otp", headerLabels.get(2));
        assertFalse(
                previewTable.findElements(By.cssSelector("tbody tr")).isEmpty(),
                "Stored preview table should render at least one row");
        List<WebElement> previewRows = previewTable.findElements(By.cssSelector("tbody tr"));
        assertTrue(
                previewRows.size() >= 3,
                "Preview table should contain evaluated row plus backward/forward entries when offsets > 0");
        WebElement activeRow = previewTable.findElement(By.cssSelector("tbody tr[data-delta='0']"));
        assertTrue(
                activeRow.getAttribute("class").contains("result-preview__row--active"),
                "Δ = 0 row should carry the active preview styling");
        assertEquals(
                "0",
                activeRow
                        .findElement(By.cssSelector(".result-preview__cell--delta"))
                        .getText()
                        .trim(),
                "Δ cell should render zero for the evaluated row");
        String otpText = activeRow
                .findElement(By.cssSelector(".result-preview__cell--otp"))
                .getText()
                .replace(" ", "");
        assertEquals(EXPECTED_STORED_OTP, otpText);

        WebElement statusBadge = resultPanel.findElement(By.cssSelector("[data-testid='totp-result-status']"));
        assertEquals("success", statusBadge.getText().trim().toLowerCase());

        assertTrue(
                driver.findElements(By.id("totpStoredOtp")).isEmpty(),
                "Stored evaluate form should not render an OTP input field");
    }

    @Test
    @DisplayName("Verbose trace toggle surfaces trace panel for stored TOTP evaluation")
    void verboseTraceToggleSurfacesTracePanelForStoredTotpEvaluation() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        WebElement storedToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-stored']"));
        storedToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "stored");

        selectOption("totpStoredCredentialId", STORED_CREDENTIAL_ID);

        var verboseCheckboxes = driver.findElements(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        assertFalse(verboseCheckboxes.isEmpty(), "Verbose trace checkbox should be present for TOTP evaluate panel");
        WebElement verboseCheckbox = verboseCheckboxes.get(0);
        assertFalse(verboseCheckbox.isSelected(), "Verbose trace should be disabled by default");

        var tracePanels = driver.findElements(By.cssSelector("[data-testid='verbose-trace-panel']"));
        assertFalse(tracePanels.isEmpty(), "Verbose trace panel container should exist");
        WebElement tracePanel = tracePanels.get(0);
        assertEquals(
                "false",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should remain hidden before verbose evaluation runs");

        driver.findElement(By.cssSelector("[data-testid='totp-stored-evaluate-button']"))
                .click();
        waitForVisible(By.cssSelector("[data-testid='totp-stored-result-panel']"));

        assertEquals(
                "false",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should stay hidden when verbose tracing is disabled");

        if (!verboseCheckbox.isSelected()) {
            verboseCheckbox.click();
        }
        assertTrue(verboseCheckbox.isSelected(), "Verbose trace checkbox should become selected after toggle");

        // Re-select credential to guard against UI resets before triggering verbose evaluation
        selectOption("totpStoredCredentialId", STORED_CREDENTIAL_ID);

        driver.findElement(By.cssSelector("[data-testid='totp-stored-evaluate-button']"))
                .click();

        WebElement traceOperation = waitForVisible(By.cssSelector("[data-testid='verbose-trace-operation']"));
        assertEquals(
                "totp.evaluate.stored",
                traceOperation.getText().trim(),
                "Trace operation should reflect stored TOTP evaluation");

        WebElement traceContent = waitForVisible(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertTrue(traceText.contains("resolve.credential"), "Trace content should include credential resolution step");
        assertTrue(
                traceText.contains("derive.time-counter"), "Trace content should include time-counter derivation step");
        assertTrue(traceText.contains("mod.reduce"), "Trace content should include modulo reduction step");
        assertTrue(traceText.contains("secret.hash = sha256:"), "Trace should surface hashed secret material");
        assertTrue(traceText.contains("time.counter.hex = "), "Trace should export the computed time counter in hex");
        assertTrue(traceText.contains("otp ="), "Trace should expose the evaluated OTP value");

        assertEquals(
                "true",
                tracePanel.getAttribute("data-trace-visible"),
                "Trace panel should mark itself visible after verbose evaluation");

        assertFalse(
                tracePanel
                        .findElements(By.cssSelector("[data-testid='verbose-trace-copy']"))
                        .isEmpty(),
                "Verbose trace panel should expose a copy control");

        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> "false".equals(tracePanel.getAttribute("data-trace-visible")));
        assertTrue(traceContent.getText().isEmpty(), "Trace content should reset after switching TOTP evaluation mode");
    }

    @Test
    @DisplayName("TOTP timestamp auto-fill controls quantise to the active step across panels")
    void totpAutoFillTimestampControls() {
        navigateToTotpPanel();

        WebElement inlineToggle = waitFor(By.cssSelector("[data-testid='totp-inline-timestamp-toggle']"));
        WebElement inlineTimestamp = driver.findElement(By.id("totpInlineTimestamp"));
        waitUntilValuePopulated(inlineTimestamp);
        assertTrue(inlineToggle.isSelected(), "Inline evaluate auto-fill toggle should default to on");
        assertTrue(
                isReadOnly(inlineTimestamp), "Inline evaluate timestamp should be read-only when auto-fill is enabled");

        long inlineStep = Long.parseLong(
                driver.findElement(By.id("totpInlineStepSeconds")).getAttribute("value"));
        long inlineValue = readLongValue(inlineTimestamp);
        assertQuantisedToCurrentStep(inlineValue, inlineStep);

        inlineToggle.click();
        waitUntilCondition(() -> !inlineToggle.isSelected());
        assertFalse(
                isReadOnly(inlineTimestamp),
                "Inline evaluate timestamp should become editable when auto-fill is disabled");
        inlineTimestamp.clear();
        inlineTimestamp.sendKeys("12345");
        assertEquals("12345", inlineTimestamp.getAttribute("value"));

        inlineToggle.click();
        waitUntilCondition(inlineToggle::isSelected);
        assertTrue(
                isReadOnly(inlineTimestamp),
                "Inline evaluate timestamp should be read-only once auto-fill is re-enabled");
        waitUntilCondition(() -> !"12345".equals(inlineTimestamp.getAttribute("value")));
        inlineValue = readLongValue(inlineTimestamp);
        assertQuantisedToCurrentStep(inlineValue, inlineStep);

        WebElement modeToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement storedRadio = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-stored']"));
        storedRadio.click();
        waitUntilAttribute(modeToggle, "data-mode", "stored");
        selectOption("totpStoredCredentialId", STORED_CREDENTIAL_ID);
        waitUntilSelectValue(By.id("totpStoredCredentialId"), STORED_CREDENTIAL_ID);

        WebElement storedToggle = waitFor(By.cssSelector("[data-testid='totp-stored-timestamp-toggle']"));
        WebElement storedTimestamp = driver.findElement(By.id("totpStoredTimestamp"));
        waitUntilValuePopulated(storedTimestamp);
        assertTrue(storedToggle.isSelected(), "Stored evaluate auto-fill toggle should default to on");
        assertTrue(isReadOnly(storedTimestamp), "Stored evaluate timestamp should be read-only with auto-fill enabled");

        long storedStep = STORED_DESCRIPTOR.stepSeconds();
        long storedValue = readLongValue(storedTimestamp);
        assertQuantisedToCurrentStep(storedValue, storedStep);

        storedToggle.click();
        waitUntilCondition(() -> !storedToggle.isSelected());
        assertFalse(
                isReadOnly(storedTimestamp),
                "Stored evaluate timestamp should become editable when auto-fill is disabled");
        storedTimestamp.clear();
        storedTimestamp.sendKeys("42");
        assertEquals("42", storedTimestamp.getAttribute("value"));

        storedToggle.click();
        waitUntilCondition(storedToggle::isSelected);
        assertTrue(
                isReadOnly(storedTimestamp),
                "Stored evaluate timestamp should be read-only once auto-fill is re-enabled");
        waitUntilCondition(() -> !"42".equals(storedTimestamp.getAttribute("value")));
        storedValue = readLongValue(storedTimestamp);
        assertQuantisedToCurrentStep(storedValue, storedStep);

        WebElement replayTab = driver.findElement(By.cssSelector("[data-testid='totp-panel-tab-replay']"));
        replayTab.click();
        waitUntilUrlContains("tab=replay");

        WebElement replayModeToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        waitUntilAttribute(replayModeToggle, "data-mode", "stored");

        selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);
        waitUntilSelectValue(By.id("totpReplayStoredCredentialId"), STORED_CREDENTIAL_ID);
        waitUntilFieldValue(By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));
        WebElement replayStoredToggle = waitFor(By.cssSelector("[data-testid='totp-replay-stored-timestamp-toggle']"));
        WebElement replayStoredTimestamp = driver.findElement(By.id("totpReplayStoredTimestamp"));
        assertFalse(
                replayStoredToggle.isSelected(),
                "Stored replay auto-fill toggle should default to off to preserve seeded sample data");
        assertFalse(
                isReadOnly(replayStoredTimestamp),
                "Stored replay timestamp should be editable while auto-fill is disabled");
        assertEquals(Long.toString(STORED_TIMESTAMP.getEpochSecond()), replayStoredTimestamp.getAttribute("value"));

        replayStoredToggle.click();
        waitUntilCondition(replayStoredToggle::isSelected);
        waitUntilCondition(() -> isReadOnly(replayStoredTimestamp));
        waitUntilCondition(() ->
                !Long.toString(STORED_TIMESTAMP.getEpochSecond()).equals(replayStoredTimestamp.getAttribute("value")));
        long replayStoredValue = readLongValue(replayStoredTimestamp);
        assertQuantisedToCurrentStep(replayStoredValue, storedStep);

        replayStoredToggle.click();
        waitUntilCondition(() -> !replayStoredToggle.isSelected());
        assertFalse(isReadOnly(replayStoredTimestamp));

        WebElement replayInlineRadio =
                driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
        replayInlineRadio.click();
        waitUntilAttribute(replayModeToggle, "data-mode", "inline");

        selectOption("totpReplayInlinePreset", INLINE_SAMPLE_PRESET_KEY);
        waitUntilSelectValue(By.id("totpReplayInlinePreset"), INLINE_SAMPLE_PRESET_KEY);
        waitUntilFieldValue(
                By.id("totpReplayInlineTimestamp"), Long.toString(INLINE_SAMPLE_TIMESTAMP.getEpochSecond()));
        WebElement replayInlineToggle = waitFor(By.cssSelector("[data-testid='totp-replay-inline-timestamp-toggle']"));
        WebElement replayInlineTimestamp = driver.findElement(By.id("totpReplayInlineTimestamp"));
        assertFalse(
                replayInlineToggle.isSelected(),
                "Inline replay auto-fill toggle should default to off to retain preset timestamp data");
        assertFalse(isReadOnly(replayInlineTimestamp));
        assertEquals(
                Long.toString(INLINE_SAMPLE_TIMESTAMP.getEpochSecond()), replayInlineTimestamp.getAttribute("value"));

        replayInlineToggle.click();
        waitUntilCondition(replayInlineToggle::isSelected);
        waitUntilCondition(() -> isReadOnly(replayInlineTimestamp));
        waitUntilCondition(() -> !Long.toString(INLINE_SAMPLE_TIMESTAMP.getEpochSecond())
                .equals(replayInlineTimestamp.getAttribute("value")));
        long replayInlineValue = readLongValue(replayInlineTimestamp);
        long inlineReplayStep = Long.parseLong(
                driver.findElement(By.id("totpReplayInlineStepSeconds")).getAttribute("value"));
        assertQuantisedToCurrentStep(replayInlineValue, inlineReplayStep);

        replayInlineToggle.click();
        waitUntilCondition(() -> !replayInlineToggle.isSelected());
        assertFalse(isReadOnly(replayInlineTimestamp));
    }

    @Test
    @DisplayName("TOTP evaluate tab defaults to inline parameters mode")
    void totpEvaluateDefaultsToInline() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        assertTrue(inlineToggle.isSelected(), "Inline mode toggle should be selected by default");
    }

    @Test
    @DisplayName("Inline TOTP evaluation generates OTP from supplied parameters")
    void inlineTotpEvaluationGeneratesOtp() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        SharedSecretField sharedSecret = totpInlineSharedSecret();
        sharedSecret.setSecret(INLINE_SECRET.asHex().toLowerCase(Locale.ROOT));
        sharedSecret.waitUntilValueEquals(INLINE_SECRET.asHex());
        assertEquals("sharedSecretHex", sharedSecret.submissionName());

        selectOption("totpInlineAlgorithm", "SHA512");

        WebElement digitsInput = driver.findElement(By.id("totpInlineDigits"));
        digitsInput.clear();
        digitsInput.sendKeys("8");

        WebElement stepInput = driver.findElement(By.id("totpInlineStepSeconds"));
        stepInput.clear();
        stepInput.sendKeys("60");

        WebElement windowBackward = driver.findElement(By.id("totpInlineWindowBackward"));
        windowBackward.clear();
        windowBackward.sendKeys("1");

        WebElement windowForward = driver.findElement(By.id("totpInlineWindowForward"));
        windowForward.clear();
        windowForward.sendKeys("1");

        WebElement inlineTimestampAuto =
                driver.findElement(By.cssSelector("[data-testid='totp-inline-timestamp-toggle']"));
        if (inlineTimestampAuto.isSelected()) {
            inlineTimestampAuto.click();
            waitUntilCondition(() -> !inlineTimestampAuto.isSelected());
        }
        WebElement timestampInput = driver.findElement(By.id("totpInlineTimestamp"));
        timestampInput.clear();
        timestampInput.sendKeys(Long.toString(INLINE_TIMESTAMP.getEpochSecond()));

        assertTrue(
                driver.findElements(By.id("totpInlineOtp")).isEmpty(),
                "Inline evaluate form should not render an OTP input field");

        driver.findElement(By.cssSelector("[data-testid='totp-inline-evaluate-button']"))
                .click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-inline-result-panel']"));
        WebElement previewTable = resultPanel.findElement(By.cssSelector("[data-testid='totp-inline-preview-table']"));
        WebElement activeRow = previewTable.findElement(By.cssSelector("tbody tr[data-delta='0']"));
        String renderedOtp = activeRow
                .findElement(By.cssSelector(".result-preview__cell--otp"))
                .getText()
                .replace(" ", "");
        assertEquals(INLINE_EXPECTED_OTP, renderedOtp);
        assertEquals(
                "0",
                activeRow
                        .findElement(By.cssSelector(".result-preview__cell--delta"))
                        .getText()
                        .trim(),
                "Δ column should display zero for the evaluated entry");
        WebElement statusBadge = resultPanel.findElement(By.cssSelector("[data-testid='totp-inline-result-status']"));
        assertEquals("success", statusBadge.getText().trim().toLowerCase());

        assertTrue(
                driver.findElements(By.id("totpInlineOtp")).isEmpty(),
                "Inline evaluate form should continue to omit an OTP input field after generation");
    }

    @Test
    @DisplayName("Inline TOTP evaluation accepts Base32 shared secrets")
    void inlineTotpEvaluationAcceptsBase32Secret() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        SharedSecretField sharedSecret = totpInlineSharedSecret();
        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);
        sharedSecret.setSecret(INLINE_SECRET_BASE32.toLowerCase(Locale.ROOT));
        sharedSecret.waitUntilValueEquals(INLINE_SECRET_BASE32);
        assertEquals("sharedSecretBase32", sharedSecret.submissionName());

        sharedSecret.switchTo(SharedSecretField.Mode.HEX);
        sharedSecret.waitUntilValueEquals(INLINE_SECRET.asHex());
        assertEquals("sharedSecretHex", sharedSecret.submissionName());

        selectOption("totpInlineAlgorithm", "SHA512");

        WebElement digitsInput = driver.findElement(By.id("totpInlineDigits"));
        digitsInput.clear();
        digitsInput.sendKeys("8");

        WebElement stepInput = driver.findElement(By.id("totpInlineStepSeconds"));
        stepInput.clear();
        stepInput.sendKeys("60");

        WebElement windowBackward = driver.findElement(By.id("totpInlineWindowBackward"));
        windowBackward.clear();
        windowBackward.sendKeys("1");

        WebElement windowForward = driver.findElement(By.id("totpInlineWindowForward"));
        windowForward.clear();
        windowForward.sendKeys("1");

        WebElement inlineTimestampAuto =
                driver.findElement(By.cssSelector("[data-testid='totp-inline-timestamp-toggle']"));
        if (inlineTimestampAuto.isSelected()) {
            inlineTimestampAuto.click();
            waitUntilCondition(() -> !inlineTimestampAuto.isSelected());
        }

        WebElement timestampInput = driver.findElement(By.id("totpInlineTimestamp"));
        timestampInput.clear();
        timestampInput.sendKeys(Long.toString(INLINE_TIMESTAMP.getEpochSecond()));

        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);

        driver.findElement(By.cssSelector("[data-testid='totp-inline-evaluate-button']"))
                .click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-inline-result-panel']"));
        WebElement previewTable = resultPanel.findElement(By.cssSelector("[data-testid='totp-inline-preview-table']"));
        WebElement activeRow = previewTable.findElement(By.cssSelector("tbody tr[data-delta='0']"));
        String renderedOtp = activeRow
                .findElement(By.cssSelector(".result-preview__cell--otp"))
                .getText()
                .replace(" ", "");
        assertEquals(INLINE_EXPECTED_OTP, renderedOtp);
    }

    @Test
    @DisplayName("Inline TOTP secret toggle round-trips between Hex and Base32 modes")
    void inlineTotpSecretToggleRoundTrips() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        SharedSecretField sharedSecret = totpInlineSharedSecret();
        sharedSecret.setSecret(INLINE_SECRET.asHex().toLowerCase(Locale.ROOT));
        sharedSecret.waitUntilValueEquals(INLINE_SECRET.asHex());
        assertEquals("sharedSecretHex", sharedSecret.submissionName());

        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);
        sharedSecret.waitUntilValueEquals(INLINE_SECRET_BASE32);
        assertEquals("sharedSecretBase32", sharedSecret.submissionName());

        sharedSecret.switchTo(SharedSecretField.Mode.HEX);
        sharedSecret.waitUntilValueEquals(INLINE_SECRET.asHex());
        assertEquals("sharedSecretHex", sharedSecret.submissionName());
    }

    @Test
    @DisplayName("Inline TOTP shared secret message updates on conversion errors")
    void inlineTotpSharedSecretMessageUpdatesOnErrors() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        SharedSecretField sharedSecret = totpInlineSharedSecret();
        assertEquals(SHARED_SECRET_HINT, sharedSecret.message());

        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);
        waitUntilCondition(() -> SHARED_SECRET_HINT.equals(sharedSecret.message()));

        sharedSecret.setSecret("INVALID#");
        waitUntilCondition(() -> "error".equalsIgnoreCase(sharedSecret.messageState()));
        String errorCopy = sharedSecret.message();
        assertTrue(errorCopy.startsWith("⚠ "), () -> "Expected inline conversion error prefix but saw: " + errorCopy);
        assertTrue(
                errorCopy.contains("Base32 values may only contain A-Z and 2-7 characters."),
                () -> "Unexpected conversion error message: " + errorCopy);

        sharedSecret.setSecret(INLINE_SECRET_BASE32);
        sharedSecret.waitUntilValueEquals(INLINE_SECRET_BASE32);
        waitUntilCondition(() -> SHARED_SECRET_HINT.equals(sharedSecret.message()));
        assertEquals("", sharedSecret.messageState());
    }

    @Test
    @DisplayName("Inline TOTP shared secret surfaces validation for invalid Base32 input")
    void inlineTotpSharedSecretValidationSurfacesErrors() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        SharedSecretField sharedSecret = totpInlineSharedSecret();
        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);
        sharedSecret.setSecret(INLINE_SECRET_BASE32.toLowerCase(Locale.ROOT));
        sharedSecret.waitUntilValueEquals(INLINE_SECRET_BASE32);

        injectSecretConflict("/api/v1/totp/evaluate/inline", INLINE_SECRET.asHex(), INLINE_SECRET_BASE32);

        driver.findElement(By.cssSelector("[data-testid='totp-inline-evaluate-button']"))
                .click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-inline-result-panel']"));
        WebElement messageNode = resultPanel.findElement(By.cssSelector("[data-result-message]"));
        waitUntilTextPopulated(messageNode);
        String messageCopy = messageNode.getText();
        assertTrue(
                messageCopy.contains("Provide either sharedSecretHex or sharedSecretBase32"),
                () -> "Expected conflict validation message but saw: " + messageCopy);
    }

    @Test
    @DisplayName("Inline TOTP replay accepts Base32 shared secrets")
    void inlineTotpReplayAcceptsBase32Secret() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(replayToggle, "data-mode", "inline");

        SharedSecretField sharedSecret = totpReplaySharedSecret();
        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);
        sharedSecret.setSecret(INLINE_SECRET_BASE32.toLowerCase(Locale.ROOT));
        sharedSecret.waitUntilValueEquals(INLINE_SECRET_BASE32);
        assertEquals("sharedSecretBase32", sharedSecret.submissionName());

        sharedSecret.switchTo(SharedSecretField.Mode.HEX);
        sharedSecret.waitUntilValueEquals(INLINE_SECRET.asHex());
        assertEquals("sharedSecretHex", sharedSecret.submissionName());

        sharedSecret.switchTo(SharedSecretField.Mode.BASE32);

        selectOption("totpReplayInlineAlgorithm", "SHA512");

        WebElement digitsInput = driver.findElement(By.id("totpReplayInlineDigits"));
        digitsInput.clear();
        digitsInput.sendKeys("8");

        WebElement stepInput = driver.findElement(By.id("totpReplayInlineStepSeconds"));
        stepInput.clear();
        stepInput.sendKeys("60");

        WebElement driftBackward = driver.findElement(By.id("totpReplayInlineDriftBackward"));
        driftBackward.clear();
        driftBackward.sendKeys("0");

        WebElement driftForward = driver.findElement(By.id("totpReplayInlineDriftForward"));
        driftForward.clear();
        driftForward.sendKeys("0");

        WebElement timestampInput = driver.findElement(By.id("totpReplayInlineTimestamp"));
        timestampInput.clear();
        timestampInput.sendKeys(Long.toString(INLINE_TIMESTAMP.getEpochSecond()));

        WebElement otpInput = driver.findElement(By.id("totpReplayInlineOtp"));
        otpInput.clear();
        otpInput.sendKeys(INLINE_EXPECTED_OTP);

        WebElement replayButton = driver.findElement(By.cssSelector("[data-testid='totp-replay-inline-submit']"));
        replayButton.click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
        WebElement statusBadge = resultPanel.findElement(By.cssSelector("[data-testid='totp-replay-status']"));
        waitUntilTextPopulated(statusBadge);
        assertEquals("match", statusBadge.getText().trim().toLowerCase());

        WebElement reasonNode = resultPanel.findElement(By.cssSelector("[data-testid='totp-replay-reason-code']"));
        waitUntilTextPopulated(reasonNode);
        assertEquals("match", reasonNode.getText().trim().toLowerCase());

        WebElement hintNode = resultPanel.findElement(By.cssSelector("[data-result-hint]"));
        assertTrue(
                hintNode.getText() == null || hintNode.getText().trim().isEmpty(),
                "Successful replay should not render error hints");
    }

    @Test
    @DisplayName("Inline TOTP evaluate preset populates sample vector inputs")
    void totpInlineSamplePresetPopulatesForm() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        WebElement inlinePanel = waitForVisible(By.cssSelector("[data-testid='totp-inline-panel']"));

        WebElement presetContainer = waitForVisible(By.cssSelector("[data-testid='totp-inline-preset']"));
        WebElement presetLabel = presetContainer.findElement(By.tagName("label"));
        assertTrue(
                presetLabel.getText().contains("Load a sample vector"),
                "Inline preset label should mention sample vectors");

        Select presetSelect =
                new Select(presetContainer.findElement(By.cssSelector("[data-testid='totp-inline-preset-select']")));
        assertTrue(
                presetSelect.getOptions().size() >= INLINE_SAMPLE_PRESET_KEYS.size(),
                "Inline preset dropdown should expose all expected sample options");
        List<String> inlineOptionValues = presetSelect.getOptions().stream()
                .map(option -> option.getAttribute("value"))
                .collect(Collectors.toList());
        assertTrue(
                inlineOptionValues.containsAll(INLINE_SAMPLE_PRESET_KEYS),
                "Inline preset dropdown should list all RFC 6238 variants");

        presetSelect.selectByValue(INLINE_SAMPLE_PRESET_KEY);

        SharedSecretField presetSecret = totpInlineSharedSecret();
        presetSecret.waitUntilValueEquals(INLINE_SAMPLE_SECRET.asHex());
        assertEquals("sharedSecretHex", presetSecret.submissionName());

        Select algorithmSelect = new Select(driver.findElement(By.id("totpInlineAlgorithm")));
        String algorithmValue = algorithmSelect.getFirstSelectedOption() == null
                ? null
                : algorithmSelect.getFirstSelectedOption().getAttribute("value");
        assertEquals("SHA1", algorithmValue);

        WebElement digitsField = driver.findElement(By.id("totpInlineDigits"));
        assertEquals(Integer.toString(INLINE_SAMPLE_DESCRIPTOR.digits()), digitsField.getAttribute("value"));

        WebElement stepField = driver.findElement(By.id("totpInlineStepSeconds"));
        assertEquals(Long.toString(INLINE_SAMPLE_DESCRIPTOR.stepSeconds()), stepField.getAttribute("value"));

        WebElement timestampField = driver.findElement(By.id("totpInlineTimestamp"));
        long presetStepSeconds = INLINE_SAMPLE_DESCRIPTOR.stepSeconds();
        long autoFilledTimestamp = readLongValue(timestampField);
        assertQuantisedToCurrentStep(autoFilledTimestamp, presetStepSeconds);

        assertTrue(
                driver.findElements(By.id("totpInlineOtp")).isEmpty(),
                "Inline evaluate form should not render an OTP input field when presets apply");

        WebElement windowBackward = driver.findElement(By.id("totpInlineWindowBackward"));
        assertEquals(
                Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().backwardSteps()),
                windowBackward.getAttribute("value"));

        WebElement windowForward = driver.findElement(By.id("totpInlineWindowForward"));
        assertEquals(
                Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().forwardSteps()),
                windowForward.getAttribute("value"));

        WebElement inlineSection = inlinePanel.findElement(By.tagName("form"));
        assertEquals("totp-inline-form", inlineSection.getAttribute("data-testid"));
    }

    @Test
    @DisplayName("TOTP inline preset spacing matches HOTP baseline")
    void totpInlinePresetSpacingMatchesHotpBaseline() {
        navigateToTotpPanel();

        WebElement evaluateInlineSection = waitFor(By.cssSelector("[data-testid='totp-inline-panel']"));
        assertTrue(
                hasCssClass(evaluateInlineSection, "stack-offset-top-lg"),
                "TOTP evaluate inline section should reuse the shared spacing token");

        switchToReplayTab();

        WebElement replayInlineSection = waitFor(By.cssSelector("[data-testid='totp-replay-inline-section']"));
        assertTrue(
                hasCssClass(replayInlineSection, "stack-offset-top-lg"),
                "TOTP replay inline section should reuse the shared spacing token");
    }

    @Test
    @DisplayName("TOTP inline parameter controls align on a single row")
    void totpInlineParameterControlsAlignOnSingleRow() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        waitForVisible(By.cssSelector("[data-testid='totp-inline-panel']"));

        WebElement inlineParameterGrid = waitFor(By.cssSelector("[data-testid='totp-inline-parameters-grid']"));
        WebElement inlineAlgorithm = inlineParameterGrid.findElement(By.cssSelector("#totpInlineAlgorithm"));
        WebElement inlineDigits = inlineParameterGrid.findElement(By.cssSelector("#totpInlineDigits"));
        WebElement inlineStep = inlineParameterGrid.findElement(By.cssSelector("#totpInlineStepSeconds"));
        assertSameRow("evaluate inline parameters", inlineAlgorithm, inlineDigits, inlineStep);

        switchToReplayTab();

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        WebElement replayInlineToggle =
                driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
        replayInlineToggle.click();
        waitUntilAttribute(replayToggle, "data-mode", "inline");

        waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-section']"));

        WebElement replayParameterGrid = waitFor(By.cssSelector("[data-testid='totp-replay-inline-parameters-grid']"));
        WebElement replayAlgorithm = replayParameterGrid.findElement(By.cssSelector("#totpReplayInlineAlgorithm"));
        WebElement replayDigits = replayParameterGrid.findElement(By.cssSelector("#totpReplayInlineDigits"));
        WebElement replayStep = replayParameterGrid.findElement(By.cssSelector("#totpReplayInlineStepSeconds"));
        assertSameRow("replay inline parameters", replayAlgorithm, replayDigits, replayStep);
    }

    @Test
    @DisplayName("TOTP replay inline preset populates sample vector inputs")
    void totpReplayInlineSamplePresetPopulatesForm() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(replayToggle, "data-mode", "inline");

        waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-section']"));

        WebElement presetContainer = waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-preset']"));
        WebElement presetLabel = presetContainer.findElement(By.tagName("label"));
        assertTrue(
                presetLabel.getText().contains("Load a sample vector"),
                "Replay inline preset label should mention sample vectors");

        Select presetSelect = new Select(
                presetContainer.findElement(By.cssSelector("[data-testid='totp-replay-inline-preset-select']")));
        assertTrue(
                presetSelect.getOptions().size() >= INLINE_SAMPLE_PRESET_KEYS.size(),
                "Replay inline preset dropdown should expose all expected sample options");
        List<String> replayOptionValues = presetSelect.getOptions().stream()
                .map(option -> option.getAttribute("value"))
                .collect(Collectors.toList());
        assertTrue(
                replayOptionValues.containsAll(INLINE_SAMPLE_PRESET_KEYS),
                "Replay inline preset dropdown should list all RFC 6238 variants");

        presetSelect.selectByValue(INLINE_SAMPLE_PRESET_KEY);

        SharedSecretField replaySharedSecret = totpReplaySharedSecret();
        replaySharedSecret.waitUntilValueEquals(INLINE_SAMPLE_SECRET.asHex());
        assertEquals("sharedSecretHex", replaySharedSecret.submissionName());

        Select algorithmSelect = new Select(driver.findElement(By.id("totpReplayInlineAlgorithm")));
        String algorithmValue = algorithmSelect.getFirstSelectedOption() == null
                ? null
                : algorithmSelect.getFirstSelectedOption().getAttribute("value");
        assertEquals("SHA1", algorithmValue);

        WebElement digitsField = driver.findElement(By.id("totpReplayInlineDigits"));
        assertEquals(Integer.toString(INLINE_SAMPLE_DESCRIPTOR.digits()), digitsField.getAttribute("value"));

        WebElement stepField = driver.findElement(By.id("totpReplayInlineStepSeconds"));
        assertEquals(Long.toString(INLINE_SAMPLE_DESCRIPTOR.stepSeconds()), stepField.getAttribute("value"));

        WebElement timestampField = driver.findElement(By.id("totpReplayInlineTimestamp"));
        assertEquals(Long.toString(INLINE_SAMPLE_TIMESTAMP.getEpochSecond()), timestampField.getAttribute("value"));

        WebElement otpField = driver.findElement(By.id("totpReplayInlineOtp"));
        assertEquals(INLINE_SAMPLE_EXPECTED_OTP, otpField.getAttribute("value"));

        WebElement driftBackward = driver.findElement(By.id("totpReplayInlineDriftBackward"));
        assertEquals(
                Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().backwardSteps()),
                driftBackward.getAttribute("value"));

        WebElement driftForward = driver.findElement(By.id("totpReplayInlineDriftForward"));
        assertEquals(
                Integer.toString(INLINE_SAMPLE_DESCRIPTOR.driftWindow().forwardSteps()),
                driftForward.getAttribute("value"));
    }

    @Test
    @DisplayName("TOTP preview window controls align on a single row across modes")
    void totpPreviewWindowControlsAlignOnSingleRowAcrossModes() {
        navigateToTotpPanel();

        WebElement storedWindowGrid = waitFor(By.cssSelector("[data-testid='totp-stored-window-grid']"));
        WebElement storedBackward = storedWindowGrid.findElement(By.cssSelector("#totpStoredWindowBackward"));
        WebElement storedForward = storedWindowGrid.findElement(By.cssSelector("#totpStoredWindowForward"));
        assertSameRow("evaluate stored preview window controls", storedBackward, storedForward);

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineModeToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineModeToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");
        waitForVisible(By.cssSelector("[data-testid='totp-inline-panel']"));

        WebElement inlineWindowGrid = waitFor(By.cssSelector("[data-testid='totp-inline-window-grid']"));
        WebElement inlineBackward = inlineWindowGrid.findElement(By.cssSelector("#totpInlineWindowBackward"));
        WebElement inlineForward = inlineWindowGrid.findElement(By.cssSelector("#totpInlineWindowForward"));
        assertSameRow("evaluate inline preview window controls", inlineBackward, inlineForward);

        switchToReplayTab();

        WebElement replayStoredDriftGrid = waitFor(By.cssSelector("[data-testid='totp-replay-stored-drift-grid']"));
        WebElement replayStoredBackward =
                replayStoredDriftGrid.findElement(By.cssSelector("#totpReplayStoredDriftBackward"));
        WebElement replayStoredForward =
                replayStoredDriftGrid.findElement(By.cssSelector("#totpReplayStoredDriftForward"));
        assertSameRow("replay stored drift controls", replayStoredBackward, replayStoredForward);

        WebElement replayModeToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        WebElement replayInlineToggle =
                driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
        replayInlineToggle.click();
        waitUntilAttribute(replayModeToggle, "data-mode", "inline");
        waitForVisible(By.cssSelector("[data-testid='totp-replay-inline-section']"));

        WebElement replayInlineDriftGrid = waitFor(By.cssSelector("[data-testid='totp-replay-inline-drift-grid']"));
        WebElement replayInlineBackward =
                replayInlineDriftGrid.findElement(By.cssSelector("#totpReplayInlineDriftBackward"));
        WebElement replayInlineForward =
                replayInlineDriftGrid.findElement(By.cssSelector("#totpReplayInlineDriftForward"));
        assertSameRow("replay inline drift controls", replayInlineBackward, replayInlineForward);
    }

    @Test
    @DisplayName("TOTP mode selectors list inline before stored credentials")
    void totpModeSelectorsListInlineBeforeStored() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        List<WebElement> evaluateOptions = modeToggle.findElements(By.cssSelector(".mode-option label"));
        assertEquals("Inline parameters", evaluateOptions.get(0).getText().trim());
        assertEquals("Stored credential", evaluateOptions.get(1).getText().trim());

        switchToReplayTab();

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        List<WebElement> replayOptions = replayToggle.findElements(By.cssSelector(".mode-option label"));
        assertEquals("Inline parameters", replayOptions.get(0).getText().trim());
        assertEquals("Stored credential", replayOptions.get(1).getText().trim());
    }

    @Test
    @DisplayName("Stored TOTP replay returns match without mutating state")
    void storedTotpReplayReturnsMatch() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        waitUntilAttribute(replayToggle, "data-mode", "stored");

        WebElement credentialLabel = driver.findElement(By.cssSelector("label[for='totpReplayStoredCredentialId']"));
        assertEquals("Stored credential", credentialLabel.getText().trim());

        By replaySelectLocator = By.id("totpReplayStoredCredentialId");
        waitUntilSelectValue(replaySelectLocator, "");
        assertEquals(
                "",
                new Select(driver.findElement(replaySelectLocator))
                        .getFirstSelectedOption()
                        .getAttribute("value"),
                "Stored replay credential dropdown should default to placeholder option");
        selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);

        waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);
        waitUntilFieldValue(By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));
        waitUntilFieldValue(By.id("totpReplayStoredDriftBackward"), "1");
        waitUntilFieldValue(By.id("totpReplayStoredDriftForward"), "1");

        WebElement replayButton = driver.findElement(By.cssSelector("[data-testid='totp-replay-stored-submit']"));
        replayButton.click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
        String statusText = resultPanel
                .findElement(By.cssSelector("[data-testid='totp-replay-status']"))
                .getText()
                .trim();
        assertEquals("match", statusText.toLowerCase());
        String reasonCode = resultPanel
                .findElement(By.cssSelector("[data-testid='totp-replay-reason-code']"))
                .getText()
                .trim();
        assertEquals("match", reasonCode.toLowerCase());
        String outcome = resultPanel
                .findElement(By.cssSelector("[data-testid='totp-replay-outcome']"))
                .getText()
                .trim();
        assertEquals("match", outcome.toLowerCase());
    }

    @Test
    @DisplayName("Stored TOTP replay mismatch surfaces ResultCard message")
    void storedTotpReplayMismatchSurfacesMessage() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        waitUntilAttribute(replayToggle, "data-mode", "stored");

        selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);
        waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);

        WebElement otpInput = driver.findElement(By.id("totpReplayStoredOtp"));
        otpInput.clear();
        otpInput.sendKeys("000000");

        WebElement submitButton = driver.findElement(By.cssSelector("[data-testid='totp-replay-stored-submit']"));
        submitButton.click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
        WebElement messageNode = resultPanel.findElement(By.cssSelector("[data-result-message]"));
        waitUntilTextPopulated(messageNode);
        assertEquals(
                "Replay request returned status: Mismatch.",
                messageNode.getText().trim(),
                "Stored mismatch should render ResultCard message");

        WebElement hintNode = resultPanel.findElement(By.cssSelector("[data-result-hint]"));
        waitUntilTextPopulated(hintNode);
        assertEquals(
                "Reason: otp_out_of_window",
                hintNode.getText().trim(),
                "Stored mismatch should surface telemetry reason code");
    }

    @Test
    @DisplayName("Stored TOTP replay auto-fills OTP and timestamp fields")
    void storedTotpReplaySampleAutoFillsForm() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        waitUntilAttribute(replayToggle, "data-mode", "stored");

        waitUntilOptionsCount(By.id("totpReplayStoredCredentialId"), 2);
        selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);
        WebElement storedReplaySelect = driver.findElement(By.id("totpReplayStoredCredentialId"));
        ((JavascriptExecutor) driver)
                .executeScript(
                        "arguments[0].dispatchEvent(new Event('change', { bubbles: true }))", storedReplaySelect);
        WebElement sampleStatus = waitFor(By.cssSelector("[data-testid='totp-replay-sample-status']"));
        waitUntilTextPopulated(sampleStatus, Duration.ofSeconds(10));

        waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);
        waitUntilFieldValue(By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));

        String statusText = sampleStatus.getText().trim().toLowerCase();
        assertTrue(
                statusText.contains("sample"),
                () -> "Sample status message should reference applied preset: " + statusText);
    }

    @Test
    @DisplayName("TOTP replay result column preserves status badge without clipping")
    void totpReplayResultColumnPreservesStatusBadge() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        waitUntilAttribute(replayToggle, "data-mode", "stored");

        selectOption("totpReplayStoredCredentialId", STORED_CREDENTIAL_ID);

        waitUntilFieldValue(By.id("totpReplayStoredOtp"), EXPECTED_STORED_OTP);
        waitUntilFieldValue(By.id("totpReplayStoredTimestamp"), Long.toString(STORED_TIMESTAMP.getEpochSecond()));

        WebElement replayButton = driver.findElement(By.cssSelector("[data-testid='totp-replay-stored-submit']"));
        replayButton.click();

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
        assertTrue(resultPanel.isDisplayed(), "Replay result panel should be visible before layout checks");

        WebElement statusColumn = driver.findElement(By.cssSelector("#totp-replay-panel .status-column"));
        String overflowX = statusColumn.getCssValue("overflow-x");
        assertEquals("visible", overflowX, "Replay status column should allow horizontal content without clipping");
    }

    @Test
    @DisplayName("Inline TOTP replay outside drift returns mismatch")
    void inlineTotpReplayReportsMismatch() {
        navigateToTotpPanel();
        switchToReplayTab();
        waitUntilUrlContains("tab=replay");

        WebElement replayToggle = waitFor(By.cssSelector("[data-testid='totp-replay-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-replay-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(replayToggle, "data-mode", "inline");

        SharedSecretField mismatchSharedSecret = totpReplaySharedSecret();
        mismatchSharedSecret.setSecret(INLINE_SECRET.asHex().toLowerCase(Locale.ROOT));
        mismatchSharedSecret.waitUntilValueEquals(INLINE_SECRET.asHex());
        assertEquals("sharedSecretHex", mismatchSharedSecret.submissionName());

        selectOption("totpReplayInlineAlgorithm", "SHA512");

        WebElement digitsInput = driver.findElement(By.id("totpReplayInlineDigits"));
        digitsInput.clear();
        digitsInput.sendKeys("8");

        WebElement stepSecondsInput = driver.findElement(By.id("totpReplayInlineStepSeconds"));
        stepSecondsInput.clear();
        stepSecondsInput.sendKeys("60");

        WebElement driftBackward = driver.findElement(By.id("totpReplayInlineDriftBackward"));
        driftBackward.clear();
        driftBackward.sendKeys("0");

        WebElement driftForward = driver.findElement(By.id("totpReplayInlineDriftForward"));
        driftForward.clear();
        driftForward.sendKeys("0");

        WebElement timestampInputReplay = driver.findElement(By.id("totpReplayInlineTimestamp"));
        timestampInputReplay.clear();
        timestampInputReplay.sendKeys(
                Long.toString(INLINE_TIMESTAMP.plusSeconds(180).getEpochSecond()));

        WebElement timestampOverrideInput = driver.findElement(By.id("totpReplayInlineTimestampOverride"));
        timestampOverrideInput.clear();
        timestampOverrideInput.sendKeys(
                Long.toString(INLINE_TIMESTAMP.minusSeconds(120).getEpochSecond()));

        WebElement otpInput = driver.findElement(By.id("totpReplayInlineOtp"));
        otpInput.clear();
        otpInput.sendKeys(INLINE_EXPECTED_OTP);

        WebElement replayButton = driver.findElement(By.cssSelector("[data-testid='totp-replay-inline-submit']"));
        replayButton.click();

        waitUntilUrlContains("mode=inline");

        WebElement resultPanel = waitForVisible(By.cssSelector("[data-testid='totp-replay-result-panel']"));
        String statusText = resultPanel
                .findElement(By.cssSelector("[data-testid='totp-replay-status']"))
                .getText()
                .trim();
        assertEquals("mismatch", statusText.toLowerCase());
        String reasonCode = resultPanel
                .findElement(By.cssSelector("[data-testid='totp-replay-reason-code']"))
                .getText()
                .trim();
        assertEquals("otp_out_of_window", reasonCode.toLowerCase());
        String outcome = resultPanel
                .findElement(By.cssSelector("[data-testid='totp-replay-outcome']"))
                .getText()
                .trim();
        assertEquals("mismatch", outcome.toLowerCase());

        WebElement messageNode = resultPanel.findElement(By.cssSelector("[data-result-message]"));
        waitUntilTextPopulated(messageNode);
        assertEquals(
                "Replay request returned status: Mismatch.",
                messageNode.getText().trim(),
                "Mismatch result should surface ResultCard message");

        WebElement hintNode = resultPanel.findElement(By.cssSelector("[data-result-hint]"));
        waitUntilTextPopulated(hintNode);
        assertEquals(
                "Reason: otp_out_of_window",
                hintNode.getText().trim(),
                "Mismatch hint should surface telemetry reason code");
    }

    @Test
    @DisplayName("TOTP inline mode persists across refresh via query parameters")
    void totpInlineModePersistsAcrossRefresh() {
        navigateToTotpPanel();

        WebElement modeToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        WebElement inlineToggle = driver.findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"));
        inlineToggle.click();
        waitUntilAttribute(modeToggle, "data-mode", "inline");

        waitUntilUrlContains("protocol=totp");
        waitUntilUrlContains("tab=evaluate");
        waitUntilUrlContains("mode=inline");

        driver.navigate().refresh();

        WebElement refreshedToggle = waitFor(By.cssSelector("[data-testid='totp-mode-toggle']"));
        waitUntilAttribute(refreshedToggle, "data-mode", "inline");
        WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='totp-panel-tab-evaluate']"));
        waitUntilAttribute(evaluateTab, "aria-selected", "true");
    }

    private void navigateToTotpPanel() {
        driver.get("http://localhost:" + port + "/ui/console?protocol=totp");
        waitFor(By.cssSelector("[data-testid='protocol-tab-totp']"));
        WebElement evaluateTab = waitFor(By.cssSelector("[data-testid='totp-panel-tab-evaluate']"));
        if (!"true".equals(evaluateTab.getAttribute("aria-selected"))) {
            evaluateTab.click();
            waitUntilAttribute(evaluateTab, "aria-selected", "true");
        }
    }

    private void switchToReplayTab() {
        WebElement replayTab = waitFor(By.cssSelector("[data-testid='totp-panel-tab-replay']"));
        if (!"true".equals(replayTab.getAttribute("aria-selected"))) {
            replayTab.click();
            waitUntilAttribute(replayTab, "aria-selected", "true");
        }
    }

    private void assertSameRow(String context, WebElement anchor, WebElement... others) {
        int anchorY = anchor.getRect().getY();
        for (WebElement element : others) {
            int difference = Math.abs(element.getRect().getY() - anchorY);
            assertTrue(
                    difference <= 2,
                    () -> String.format(
                            "%s should align on single row: anchorY=%d, elementY=%d (difference=%d)",
                            context, anchorY, element.getRect().getY(), difference));
        }
    }

    private void waitUntilOptionsCount(By locator, int expectedCount) {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(driver1 -> {
            try {
                Select select = new Select(driver1.findElement(locator));
                return select.getOptions().size() >= expectedCount;
            } catch (StaleElementReferenceException ex) {
                return false;
            }
        });
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

    private void waitUntilUrlContains(String fragment) {
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.urlContains(fragment));
    }

    private void waitUntilFieldValue(By locator, String expectedValue) {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(driver1 -> {
            try {
                WebElement element = driver1.findElement(locator);
                return expectedValue.equals(element.getAttribute("value"));
            } catch (StaleElementReferenceException ex) {
                return false;
            }
        });
    }

    private void waitUntilTextPopulated(WebElement element) {
        waitUntilTextPopulated(element, Duration.ofSeconds(5));
    }

    private void waitUntilTextPopulated(WebElement element, Duration timeout) {
        new WebDriverWait(driver, timeout).until(driver1 -> {
            try {
                String text = element.getText();
                return text != null && !text.trim().isEmpty();
            } catch (StaleElementReferenceException ex) {
                return false;
            }
        });
    }

    private void waitUntilSelectValue(By locator, String expectedValue) {
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(driver1 -> {
            try {
                Select select = new Select(driver1.findElement(locator));
                WebElement option = select.getFirstSelectedOption();
                return option != null && expectedValue.equals(option.getAttribute("value"));
            } catch (StaleElementReferenceException ex) {
                return false;
            }
        });
    }

    private void waitUntilCondition(BooleanSupplier condition) {
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(driver1 -> {
            try {
                return condition.getAsBoolean();
            } catch (Exception ex) {
                return false;
            }
        });
    }

    private void waitUntilValuePopulated(WebElement element) {
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(driver1 -> {
            try {
                String value = element.getAttribute("value");
                return value != null && !value.isBlank();
            } catch (StaleElementReferenceException ex) {
                return false;
            }
        });
    }

    private boolean isReadOnly(WebElement element) {
        String attr = element.getAttribute("readonly");
        if (attr != null) {
            return true;
        }
        String property = element.getDomProperty("readOnly");
        if (property == null) {
            return false;
        }
        return Boolean.parseBoolean(property);
    }

    private long readLongValue(WebElement element) {
        return Long.parseLong(element.getAttribute("value"));
    }

    private void assertQuantisedToCurrentStep(long timestamp, long stepSeconds) {
        assertEquals(0L, timestamp % stepSeconds, "Timestamp should align to the TOTP step boundary");
        long now = Instant.now().getEpochSecond();
        long difference = Math.abs(now - timestamp);
        assertTrue(
                difference <= stepSeconds,
                "Timestamp should be within one step of the current time (difference=" + difference + ")");
    }

    private boolean hasCssClass(WebElement element, String className) {
        String classAttribute = element.getAttribute("class");
        if (classAttribute == null || classAttribute.isBlank()) {
            return false;
        }
        for (String candidate : classAttribute.split("\\s+")) {
            if (className.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private SharedSecretField totpInlineSharedSecret() {
        return new SharedSecretField(driver, By.cssSelector("[data-testid='totp-inline-shared-secret']"));
    }

    private SharedSecretField totpReplaySharedSecret() {
        return new SharedSecretField(driver, By.cssSelector("[data-testid='totp-replay-inline-shared-secret']"));
    }

    private void injectSecretConflict(String endpointFragment, String hexValue, String base32Value) {
        ((JavascriptExecutor) driver)
                .executeScript(
                        "(function(fragment, hex, base32) {"
                                + "window.__totpSecretConflict = window.__totpSecretConflict || {};"
                                + "if (window.__totpSecretConflict[fragment]) { return; }"
                                + "var originalFetch = window.fetch;"
                                + "window.fetch = function(input, init) {"
                                + "  if (typeof input === 'string' && input.indexOf(fragment) !== -1 && init && typeof init.body === 'string') {"
                                + "    try {"
                                + "      var payload = JSON.parse(init.body);"
                                + "      if (!payload.sharedSecretHex) { payload.sharedSecretHex = hex; }"
                                + "      if (!payload.sharedSecretBase32) { payload.sharedSecretBase32 = base32; }"
                                + "      init.body = JSON.stringify(payload);"
                                + "    } catch (error) {"
                                + "      if (window.console && typeof window.console.warn === 'function') {"
                                + "        window.console.warn('Failed to inject conflicting secrets', error);"
                                + "      }"
                                + "    }"
                                + "  }"
                                + "  return originalFetch.call(this, input, init);"
                                + "};"
                                + "window.__totpSecretConflict[fragment] = true;"
                                + "}(arguments[0], arguments[1], arguments[2]));",
                        endpointFragment,
                        hexValue,
                        base32Value);
    }

    private void selectOption(String selectId, String value) {
        By locator = By.id(selectId);
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(driver1 -> {
            try {
                new Select(driver1.findElement(locator)).selectByValue(value);
                return true;
            } catch (StaleElementReferenceException
                    | NullPointerException
                    | NoSuchElementException
                    | UnsupportedOperationException ex) {
                return false;
            }
        });
    }

    private Credential storedCredential() {
        Credential serialized = VersionedCredentialRecordMapper.toCredential(adapter.serialize(STORED_DESCRIPTOR));
        Map<String, String> attributes = new LinkedHashMap<>(serialized.attributes());
        attributes.put("totp.metadata.presetKey", "inline-ui-totp-demo");
        attributes.put("totp.metadata.presetLabel", "SHA-1, 6 digits, 30s");
        attributes.put("totp.metadata.notes", "Seeded TOTP credential (inline demo preset)");
        attributes.put("totp.metadata.sampleTimestamp", Long.toString(STORED_TIMESTAMP.getEpochSecond()));
        return new Credential(
                serialized.name(),
                serialized.type(),
                serialized.secret(),
                attributes,
                serialized.createdAt(),
                serialized.updatedAt());
    }
}
