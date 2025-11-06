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

        WebElement evaluateModeToggle =
                waitForVisible(By.cssSelector("fieldset[data-testid='emv-evaluate-mode-toggle']"));
        WebElement storedPresetSelect = waitForVisible(By.cssSelector("#emvStoredCredentialId"));
        WebElement evaluatePresetContainer =
                storedPresetSelect.findElement(By.xpath("ancestor::div[contains(@class,'stack-offset-top-lg')][1]"));
        assertThat(evaluatePresetContainer.getAttribute("class"))
                .as("Sample vector block should reuse shared spacing helper")
                .contains("stack-offset-top-lg");
        WebElement evaluateSampleBlock = waitForVisible(By.cssSelector("[data-testid='emv-evaluate-sample-vector']"));
        assertThat(evaluateSampleBlock.getAttribute("class"))
                .as("Sample vector block should reuse shared inline preset styling")
                .contains("inline-preset");
        WebElement seedActions = driver.findElement(By.cssSelector("[data-testid='emv-seed-actions']"));
        assertThat(seedActions.getAttribute("hidden"))
                .as("Seed actions should remain hidden while inline mode is active")
                .isNotNull();
        WebElement seedActionsParent =
                seedActions.findElement(By.xpath("ancestor::*[@data-testid='emv-evaluate-sample-vector']"));
        assertThat(seedActionsParent)
                .as("Seed actions should remain inside the preset block beneath the dropdown")
                .isEqualTo(evaluateSampleBlock);
        String evaluateSelectBackground = storedPresetSelect.getCssValue("background-color");
        assertThat(evaluateSelectBackground.replace(" ", ""))
                .as("Sample vector dropdown should not fall back to a white background")
                .doesNotContain("255,255,255");
        assertElementOrder(
                evaluateModeToggle,
                storedPresetSelect,
                "Evaluate mode selector should render before stored credential controls");

        selectStoredEvaluateMode();

        WebElement evaluateButton = waitForVisible(By.cssSelector("button[data-testid='emv-evaluate-submit']"));
        assertThat(evaluateButton.getAttribute("disabled"))
                .as("Evaluate button should be disabled until a credential is selected in stored mode")
                .isNotNull();
        Select credentialSelect = waitForStoredCredentialSelect();
        waitForCredential(credentialSelect, STORED_CREDENTIAL_ID, STORED_PRESET_LABEL);

        assertThat(seedActions.getAttribute("hidden"))
                .as("Seed actions should become visible when stored mode is active")
                .isNull();

        evaluateButton = waitForClickable(By.cssSelector("button[data-testid='emv-evaluate-submit']"));
        assertThat(evaluateButton.getAttribute("disabled"))
                .as("Evaluate button should become enabled once a credential is active in stored mode")
                .isNull();

        WebElement evaluateForm = driver.findElement(By.cssSelector("[data-testid='emv-form']"));
        assertThat(evaluateForm.getAttribute("class"))
                .as("Evaluate form should mark stored mode via CSS class")
                .contains("emv-stored-mode");

        WebElement masterKeyGroup = driver.findElement(By.cssSelector("[data-testid='emv-master-key']"));
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key field group should not be displayed in stored mode")
                .isFalse();
        WebElement masterKeyInput = driver.findElement(By.id("emvMasterKey"));
        assertThat(masterKeyInput.isDisplayed())
                .as("Master key input should not be displayed while stored mode is active")
                .isFalse();
        assertThat(masterKeyInput.getAttribute("data-secret-mode"))
                .as("Master key input should record stored mode state")
                .isEqualTo("stored");
        assertThat(masterKeyInput.getAttribute("value"))
                .as("Master key input should remain empty in stored mode to avoid secret leakage")
                .isBlank();

        WebElement cdol1Input = driver.findElement(By.id("emvCdol1"));
        WebElement cdol1Group = driver.findElement(By.cssSelector("[data-testid='emv-cdol1']"));
        assertThat(cdol1Group.isDisplayed())
                .as("CDOL1 field group should not be displayed in stored mode")
                .isFalse();
        assertThat(cdol1Input.getAttribute("value"))
                .as("CDOL1 textarea should remain empty in stored mode to avoid secret leakage")
                .isBlank();

        WebElement issuerDataInput = driver.findElement(By.id("emvIssuerApplicationData"));
        WebElement issuerGroup = driver.findElement(By.cssSelector("[data-testid='emv-issuer-application-data']"));
        assertThat(issuerGroup.isDisplayed())
                .as("Issuer application data field group should not be displayed in stored mode")
                .isFalse();
        assertThat(issuerDataInput.getAttribute("value"))
                .as("Issuer application data textarea should remain empty in stored mode")
                .isBlank();

        WebElement ipbInput = driver.findElement(By.id("emvIpb"));
        WebElement ipbGroup = driver.findElement(By.cssSelector("[data-testid='emv-ipb']"));
        assertThat(ipbGroup.isDisplayed())
                .as("Issuer bitmap field group should not be displayed in stored mode")
                .isFalse();

        WebElement iccTemplateInput = driver.findElement(By.id("emvIccTemplate"));
        WebElement iccGroup = driver.findElement(By.cssSelector("[data-testid='emv-icc-template']"));
        assertThat(iccGroup.isDisplayed())
                .as("ICC template field group should not be displayed in stored mode")
                .isFalse();

        WebElement masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(masterKeyMask.isDisplayed())
                .as("Master key mask should not render placeholders in stored mode")
                .isFalse();

        WebElement cdol1Mask = driver.findElement(By.cssSelector("[data-testid='emv-cdol1-mask']"));
        assertThat(cdol1Mask.getAttribute("hidden"))
                .as("CDOL1 mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(cdol1Mask.isDisplayed())
                .as("CDOL1 mask should not render placeholders in stored mode")
                .isFalse();

        WebElement issuerMaskContainer =
                driver.findElement(By.cssSelector("[data-testid='emv-issuer-application-data-mask']"));
        assertThat(issuerMaskContainer.getAttribute("hidden"))
                .as("Issuer application data mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(issuerMaskContainer.isDisplayed())
                .as("Issuer application data mask should not render placeholders in stored mode")
                .isFalse();

        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-terminal-data']")))
                .as("Evaluate panel should no longer expose terminal override input")
                .isEmpty();
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-icc-override']")))
                .as("Evaluate panel should no longer expose ICC override input")
                .isEmpty();
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-icc-resolved']")))
                .as("Evaluate panel should rely on verbose trace for resolved ICC payload")
                .isEmpty();

        WebElement storedHint = driver.findElement(By.cssSelector("[data-testid='emv-stored-empty']"));
        assertThat(storedHint.getText())
                .as("Stored preset hint should describe inline override fallback")
                .contains("Modify any field to fall back to inline parameters");

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

        setNumericInput(By.cssSelector("[data-testid='emv-window-backward']"), 1);
        setNumericInput(By.cssSelector("[data-testid='emv-window-forward']"), 2);

        evaluateButton.click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        waitForStatus("Success");
        WebElement otpNode =
                driver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-otp']"));
        assertThat(otpNode.getText().trim()).isEqualTo(BASELINE_VECTOR.outputs().otpDecimal());

        List<WebElement> previewRows = driver.findElements(By.cssSelector("[data-testid='emv-preview-body'] tr"));
        assertThat(previewRows)
                .as("Preview table should reflect backward and forward offsets")
                .hasSize(4);
        assertThat(previewRows.stream()
                        .map(row -> row.getAttribute("data-delta"))
                        .toList())
                .containsExactly("-1", "0", "1", "2");
        assertThat(previewRows.get(1).getAttribute("class"))
                .as("Delta 0 row should be marked active")
                .contains("result-preview__row--active");

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
                .contains("metadata.height = " + height)
                .contains("metadata.credentialSource = stored")
                .contains("metadata.credentialId = " + STORED_CREDENTIAL_ID);
        assertThat(traceText)
                .as("Trace steps should surface session key and Generate AC details")
                .contains(masterKeyDigest)
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
    @DisplayName("Selecting a sample vector in inline mode keeps inline controls editable")
    void inlineModeRetainsEditableStateAfterSampleSelection() {
        navigateToEmvConsole();

        WebElement inlineRadio = waitForClickable(By.cssSelector("[data-testid='emv-mode-select-inline']"));
        WebElement storedRadio = waitForClickable(By.cssSelector("[data-testid='emv-mode-select-stored']"));
        assertThat(inlineRadio.isSelected()).isTrue();
        assertThat(storedRadio.isSelected()).isFalse();

        Select storedSelect = waitForStoredCredentialSelect();
        waitForCredential(storedSelect, STORED_CREDENTIAL_ID, STORED_PRESET_LABEL);
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        assertThat(inlineRadio.isSelected())
                .as("Inline mode should remain selected after choosing a sample vector")
                .isTrue();
        assertThat(storedRadio.isSelected())
                .as("Stored mode should remain unselected when inline mode is active")
                .isFalse();

        WebElement masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should stay hidden while inline mode is active")
                .isNotNull();

        WebElement masterKeyInput = waitForVisible(By.id("emvMasterKey"));
        assertThat(masterKeyInput.getAttribute("data-secret-mode")).isEqualTo("inline");
        assertThat(masterKeyInput.getAttribute("aria-hidden"))
                .as("Sensitive inputs should remain visible in inline mode")
                .isNull();
        WebElement masterKeyGroup =
                masterKeyInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key container should remain visible in inline mode")
                .isTrue();

        WebElement seedActions = driver.findElement(By.cssSelector("[data-testid='emv-seed-actions']"));
        assertThat(seedActions.getAttribute("hidden"))
                .as("Seed actions should stay hidden while inline mode is active")
                .isNotNull();

        WebElement hint = driver.findElement(By.cssSelector("[data-testid='emv-stored-empty']"));
        assertThat(hint.getText().trim())
                .as("Inline hint should explain preset selection without mode switching")
                .isEqualTo(
                        "Inline evaluation uses the parameters entered above. Selecting a sample populates inline fields; switch to stored mode to evaluate the preset.");

        WebElement evaluateButton = waitForClickable(By.cssSelector("button[data-testid='emv-evaluate-submit']"));
        evaluateButton.click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        waitForStatus("Success");
        WebElement otpNode =
                driver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-otp']"));
        assertThat(otpNode.getText().trim()).isEqualTo(BASELINE_VECTOR.outputs().otpDecimal());
        WebElement telemetryBadge =
                driver.findElement(By.cssSelector("[data-testid='emv-result-card'] [data-testid='emv-status']"));
        assertThat(telemetryBadge.getText()).isEqualTo("Success");
    }

    @Test
    @DisplayName("Disabling verbose trace hides the panel and omits trace content")
    void includeTraceToggleOmitTrace() {
        navigateToEmvConsole();

        selectStoredEvaluateMode();

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
    @DisplayName("Modifying stored preset fields falls back to inline evaluation")
    void storedOverridesFallbackToInline() {
        navigateToEmvConsole();

        selectStoredEvaluateMode();

        Select storedSelect = waitForStoredCredentialSelect();
        waitForCredential(storedSelect, STORED_CREDENTIAL_ID, STORED_PRESET_LABEL);

        WebElement evaluateButton = waitForClickable(By.cssSelector("button[data-testid='emv-evaluate-submit']"));
        assertThat(evaluateButton.getAttribute("disabled")).isNull();

        WebElement evaluateForm = driver.findElement(By.cssSelector("[data-testid='emv-form']"));
        assertThat(evaluateForm.getAttribute("class"))
                .as("Evaluate form should mark stored mode via CSS class")
                .contains("emv-stored-mode");
        WebElement masterKeyGroup = driver.findElement(By.cssSelector("[data-testid='emv-master-key']"));
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key field group should not be displayed in stored mode")
                .isFalse();
        WebElement masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should remain hidden while stored mode is active")
                .isNotNull();
        assertThat(masterKeyMask.isDisplayed())
                .as("Master key mask should not render placeholders in stored mode")
                .isFalse();

        waitForClickable(By.cssSelector("[data-testid='emv-mode-select-inline']"))
                .click();

        evaluateForm = driver.findElement(By.cssSelector("[data-testid='emv-form']"));
        assertThat(evaluateForm.getAttribute("class"))
                .as("Evaluate form should drop stored mode CSS class after switching to inline")
                .doesNotContain("emv-stored-mode");
        masterKeyGroup = driver.findElement(By.cssSelector("[data-testid='emv-master-key']"));
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key field group should reappear after switching to inline mode")
                .isTrue();
        masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should stay hidden once inline mode is selected")
                .isNotNull();
        WebElement seedActions = driver.findElement(By.cssSelector("[data-testid='emv-seed-actions']"));
        assertThat(seedActions.getAttribute("hidden"))
                .as("Seed actions should be hidden after switching back to inline mode")
                .isNotNull();

        WebElement masterKeyInput = waitForVisible(By.id("emvMasterKey"));
        assertThat(masterKeyInput.isDisplayed())
                .as("Master key input should be visible in inline mode")
                .isTrue();
        assertThat(masterKeyInput.getAttribute("readonly"))
                .as("Master key input should be editable in inline mode")
                .isNull();
        String baselineMasterKey = BASELINE_VECTOR.input().masterKeyHex();
        char replacement = baselineMasterKey.charAt(0) == 'F' ? 'E' : 'F';
        String mutatedMasterKey = replacement + baselineMasterKey.substring(1);
        masterKeyInput.clear();
        masterKeyInput.sendKeys(mutatedMasterKey);
        setHexInput(By.id("emvCdol1"), BASELINE_VECTOR.input().cdol1Hex());
        setHexInput(By.id("emvIpb"), BASELINE_VECTOR.input().issuerProprietaryBitmapHex());
        setHexInput(By.id("emvIccTemplate"), BASELINE_VECTOR.input().iccDataTemplateHex());
        setHexInput(By.id("emvIssuerApplicationData"), BASELINE_VECTOR.input().issuerApplicationDataHex());

        WebElement globalVerboseCheckbox = waitForClickable(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        if (!globalVerboseCheckbox.isSelected()) {
            globalVerboseCheckbox.click();
        }

        evaluateButton.click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        waitForStatus("Success");
        WebElement tracePanel = waitForTracePanelVisibility(true);
        assertThat(tracePanel.getAttribute("hidden")).isNull();

        WebElement traceContent = waitForVisible(By.cssSelector("[data-testid='verbose-trace-content']"));
        String traceText = traceContent.getText();
        assertThat(traceText)
                .as("Inline fallback should identify credentialSource as inline")
                .contains("metadata.credentialSource = inline")
                .doesNotContain("metadata.credentialSource = stored");

        evaluateButton = waitForClickable(By.cssSelector("button[data-testid='emv-evaluate-submit']"));
        assertThat(evaluateButton.getAttribute("disabled")).isNull();
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
        WebElement replaySampleBlock = waitForVisible(By.cssSelector("[data-testid='emv-replay-sample-vector']"));
        assertThat(replaySampleBlock.getAttribute("class"))
                .as("Replay preset block should reuse shared inline preset styling")
                .contains("inline-preset");
        WebElement replaySelectElement = storedSelect.getWrappedElement();
        String replaySelectBackground = replaySelectElement.getCssValue("background-color");
        assertThat(replaySelectBackground.replace(" ", ""))
                .as("Replay sample vector dropdown should not fall back to a white background")
                .doesNotContain("255,255,255");
        WebElement replayModeToggle = waitForVisible(By.cssSelector("fieldset[data-testid='emv-replay-mode-toggle']"));
        WebElement storedReplayRadio = replayModeToggle.findElement(By.cssSelector("#emvReplayModeStored"));
        if (!storedReplayRadio.isSelected()) {
            storedReplayRadio.click();
        }
        WebElement replayPresetContainer =
                replaySelectElement.findElement(By.xpath("ancestor::div[contains(@class,'stack-offset-top-lg')][1]"));
        assertThat(replayPresetContainer.getAttribute("class"))
                .as("Replay preset block should reuse shared spacing helper")
                .contains("stack-offset-top-lg");
        waitForReplayCredential(storedSelect, fixture.credentialId(), "CAP Respond baseline");

        WebElement replayForm = driver.findElement(By.cssSelector("[data-testid='emv-replay-form']"));
        assertThat(replayForm.getAttribute("class"))
                .as("Replay form should mark stored mode via CSS class")
                .contains("emv-stored-mode");
        WebElement replayMasterKeyGroup = driver.findElement(By.cssSelector("[data-testid='emv-replay-master-key']"));
        assertThat(replayMasterKeyGroup.isDisplayed())
                .as("Replay master key field group should not be displayed in stored mode")
                .isFalse();
        WebElement replayMasterKeyInput = driver.findElement(By.id("emvReplayMasterKey"));
        assertThat(replayMasterKeyInput.isDisplayed())
                .as("Replay master key input should not be displayed in stored mode")
                .isFalse();
        assertThat(replayMasterKeyInput.getAttribute("data-secret-mode"))
                .as("Replay master key input should track stored mode state")
                .isEqualTo("stored");
        assertThat(replayMasterKeyInput.getAttribute("value"))
                .as("Replay master key input should remain empty in stored mode")
                .isBlank();
        WebElement replayMasterKeyMask =
                driver.findElement(By.cssSelector("[data-testid='emv-replay-master-key-mask']"));
        assertThat(replayMasterKeyMask.getAttribute("hidden"))
                .as("Replay master key mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(replayMasterKeyMask.isDisplayed())
                .as("Replay master key mask should not render placeholders in stored mode")
                .isFalse();

        WebElement replayIssuerMaskContainer =
                driver.findElement(By.cssSelector("[data-testid='emv-replay-issuer-application-data-mask']"));
        assertThat(replayIssuerMaskContainer.getAttribute("hidden"))
                .as("Replay issuer application data mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(replayIssuerMaskContainer.isDisplayed())
                .as("Replay issuer application data mask should not render placeholders in stored mode")
                .isFalse();

        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-replay-terminal-data']")))
                .as("Replay panel should no longer expose terminal override input")
                .isEmpty();
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-replay-icc-override']")))
                .as("Replay panel should no longer expose ICC override input")
                .isEmpty();
        assertThat(driver.findElements(By.cssSelector("[data-testid='emv-replay-icc-resolved']")))
                .as("Replay panel should rely on verbose traces for resolved ICC payload")
                .isEmpty();

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
                .contains(expectedMasterKeyDigest(vector));
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

        WebElement replayModeToggle = waitForVisible(By.cssSelector("fieldset[data-testid='emv-replay-mode-toggle']"));
        List<WebElement> replayModeOptions = replayModeToggle.findElements(By.cssSelector(".mode-option"));
        assertThat(replayModeOptions)
                .as("Replay mode toggle should expose inline and stored options in order")
                .hasSize(2);
        WebElement inlineOption = replayModeOptions.get(0);
        WebElement storedOption = replayModeOptions.get(1);
        assertThat(inlineOption.getAttribute("data-testid")).isEqualTo("emv-replay-mode-inline");
        assertThat(storedOption.getAttribute("data-testid")).isEqualTo("emv-replay-mode-stored");

        WebElement inlineRadio = inlineOption.findElement(By.cssSelector("input[type='radio']"));
        WebElement inlineHint = inlineOption.findElement(By.cssSelector(".hint"));
        assertThat(inlineRadio.isSelected())
                .as("Inline replay mode should be selected by default")
                .isTrue();
        assertThat(inlineHint.getText()).isEqualTo("Manual replay with full CAP derivation inputs.");

        WebElement storedRadio = storedOption.findElement(By.cssSelector("input[type='radio']"));
        WebElement storedHint = storedOption.findElement(By.cssSelector(".hint"));
        assertThat(storedRadio.isSelected())
                .as("Stored replay mode should not be selected by default")
                .isFalse();
        assertThat(storedHint.getText()).isEqualTo("Replay a seeded preset without advancing ATC.");

        // Toggle away and back to inline mode to ensure sensitive fields are revealed for interaction.
        storedRadio.click();
        inlineRadio.click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());
        assertThat(inlineRadio.isSelected())
                .as("Inline replay mode should be active after toggling")
                .isTrue();
        assertThat(storedRadio.isSelected())
                .as("Stored replay mode should be inactive after toggling back to inline")
                .isFalse();

        JavascriptExecutor styleExecutor = driver;
        styleExecutor.executeScript(
                "if (window.EmvConsoleTestHooks && typeof window.EmvConsoleTestHooks.setReplayMode === 'function') {"
                        + "window.EmvConsoleTestHooks.setReplayMode('inline');" + "}");
        styleExecutor.executeScript(
                "(function(){var form=document.querySelector('[data-testid=\\'emv-replay-form\\']');"
                        + "if(form){form.classList.remove('emv-stored-mode');}"
                        + "['emvReplayMasterKey','emvReplayCdol1',"
                        + "'emvReplayIssuerBitmap','emvReplayIccTemplate','emvReplayIssuerApplicationData']"
                        + ".forEach(function(id){var el=document.getElementById(id);"
                        + "if(el){el.style.pointerEvents='';el.removeAttribute('aria-hidden');"
                        + "el.removeAttribute('tabindex');el.setAttribute('data-secret-mode','inline');}});}());");
        Object inlineDisplay = styleExecutor.executeScript(
                "return window.getComputedStyle(document.getElementById('emvReplayMasterKey')).display;");
        Object inlineVisibility = styleExecutor.executeScript(
                "return window.getComputedStyle(document.getElementById('emvReplayMasterKey')).visibility;");
        Object inlinePointer = styleExecutor.executeScript(
                "return window.getComputedStyle(document.getElementById('emvReplayMasterKey')).pointerEvents;");
        Object inlineMode = styleExecutor.executeScript("var input = document.getElementById('emvReplayMasterKey');"
                + "return input ? input.getAttribute('data-secret-mode') : null;");
        Object replayDataMode = styleExecutor.executeScript(
                "var toggle = document.querySelector('[data-testid=\\'emv-replay-mode-toggle\\']');"
                        + "return toggle ? toggle.getAttribute('data-mode') : null;");
        Object replayFormClass =
                styleExecutor.executeScript("var form = document.querySelector('[data-testid=\\'emv-replay-form\\']');"
                        + "return form ? form.className : null;");
        Object containerDisplay = styleExecutor.executeScript(
                "var container = document.querySelector('[data-testid=\\'emv-replay-master-key\\']');"
                        + "return container ? window.getComputedStyle(container).display : null;");
        Object containerHidden = styleExecutor.executeScript(
                "var container = document.querySelector('[data-testid=\\'emv-replay-master-key\\']');"
                        + "return container ? container.hasAttribute('hidden') : null;");
        System.out.println("inline display: " + inlineDisplay);
        System.out.println("inline visibility: " + inlineVisibility);
        System.out.println("inline pointer events: " + inlinePointer);
        System.out.println("inline secret mode: " + inlineMode);
        System.out.println("replay mode toggle data-mode: " + replayDataMode);
        System.out.println("replay form class: " + replayFormClass);
        System.out.println("replay container display: " + containerDisplay);
        System.out.println("replay container hidden attr: " + containerHidden);

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
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> select.getOptions().stream().anyMatch(option -> id.equals(option.getAttribute("value"))));
        assertThat(select.getOptions().stream()
                        .anyMatch(option -> label.equals(option.getText().trim())))
                .as("Stored credential dropdown should expose friendly preset label")
                .isTrue();
        select.selectByValue(id);
    }

    private void waitForReplayCredential(Select select, String id, String label) {
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> select.getOptions().stream().anyMatch(option -> id.equals(option.getAttribute("value"))));
        assertThat(select.getOptions().stream()
                        .anyMatch(option -> label.equals(option.getText().trim())))
                .as("Replay stored credential dropdown should expose preset label")
                .isTrue();
        select.selectByValue(id);
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
        WebElement element =
                new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
        element.clear();
        element.sendKeys(value);
    }

    private void setDecimalInput(By locator, String value) {
        WebElement element =
                new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
        element.clear();
        if (value != null && !value.isBlank()) {
            element.sendKeys(value);
        }
    }

    private void setNumericInput(By locator, int value) {
        WebElement element =
                new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
        element.clear();
        element.sendKeys(String.valueOf(value));
    }

    private void selectStoredEvaluateMode() {
        WebElement storedRadio = waitForClickable(By.cssSelector("[data-testid='emv-mode-select-stored']"));
        if (!storedRadio.isSelected()) {
            storedRadio.click();
        }
    }

    private void assertElementOrder(WebElement first, WebElement second, String message) {
        JavascriptExecutor javascriptExecutor = driver;
        Number positionMask = (Number) javascriptExecutor.executeScript(
                "return arguments[0].compareDocumentPosition(arguments[1]);", first, second);
        long mask = positionMask == null ? 0L : positionMask.longValue();
        assertThat(mask & 2L)
                .as(message + " (second element should not precede the first)")
                .isEqualTo(0L);
        assertThat(mask & 4L)
                .as(message + " (second element should follow the first)")
                .isNotEqualTo(0L);
    }

    private WebElement waitForVisible(By locator) {
        return new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private WebElement waitForClickable(By locator) {
        return new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
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
