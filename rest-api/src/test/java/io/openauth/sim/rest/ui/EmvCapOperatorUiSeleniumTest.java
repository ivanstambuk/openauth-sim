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
        WebElement sessionFieldset = driver.findElement(By.cssSelector("fieldset[data-testid='emv-session-block']"));
        WebElement sessionLegend = sessionFieldset.findElement(By.tagName("legend"));
        assertThat(sessionLegend.getText())
                .as("Session key derivation legend should describe the grouped inputs")
                .contains("Session key derivation");
        WebElement sessionHint = driver.findElement(By.cssSelector("[data-testid='emv-session-hint']"));
        assertThat(sessionHint.getText().trim())
                .as("Session helper copy should call out ICC master key, ATC, branch factor, height, and IV")
                .isEqualTo(
                        "Provide the ICC master key, ATC, branch factor, height, and IV to derive the session key used for Generate AC.");
        assertFieldGroupVisible("#emvAtc", "ATC");
        assertFieldGroupVisible("#emvBranchFactor", "Branch factor");
        assertFieldGroupVisible("#emvHeight", "Height");
        assertFieldGroupVisible("#emvIv", "IV");
        assertMasterAtcRow("emv-master-atc-row", "#emvMasterKey", "#emvAtc", "Evaluate master/ATC row");
        assertBranchHeightRow("emv-branch-height-row", "#emvBranchFactor", "#emvHeight", "Evaluate session key pair");

        WebElement masterKeyInput = driver.findElement(By.id("emvMasterKey"));
        WebElement masterKeyGroup =
                masterKeyInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(masterKeyGroup.getAttribute("hidden"))
                .as("Master key field group should be hidden in stored mode")
                .isNotNull();
        assertThat(masterKeyGroup.getAttribute("aria-hidden"))
                .as("Master key field group should mark aria-hidden when stored mode is active")
                .isEqualTo("true");
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key field group should not be displayed in stored mode")
                .isFalse();
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
        WebElement cdol1Group = cdol1Input.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(cdol1Group.getAttribute("hidden"))
                .as("CDOL1 field group should be hidden in stored mode")
                .isNotNull();
        assertThat(cdol1Group.getAttribute("aria-hidden"))
                .as("CDOL1 field group should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(cdol1Group.isDisplayed())
                .as("CDOL1 field group should not be displayed in stored mode")
                .isFalse();
        assertThat(cdol1Input.getAttribute("value"))
                .as("CDOL1 textarea should remain empty in stored mode to avoid secret leakage")
                .isBlank();

        WebElement issuerDataInput = driver.findElement(By.id("emvIssuerApplicationData"));
        WebElement issuerGroup =
                issuerDataInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(issuerGroup.getAttribute("hidden"))
                .as("Issuer application data field group should be hidden in stored mode")
                .isNotNull();
        assertThat(issuerGroup.getAttribute("aria-hidden"))
                .as("Issuer application data field group should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(issuerGroup.isDisplayed())
                .as("Issuer application data field group should not be displayed in stored mode")
                .isFalse();
        assertThat(issuerDataInput.getAttribute("value"))
                .as("Issuer application data textarea should remain empty in stored mode")
                .isBlank();

        WebElement ipbInput = driver.findElement(By.id("emvIpb"));
        WebElement ipbGroup = ipbInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(ipbGroup.getAttribute("hidden"))
                .as("Issuer bitmap field group should be hidden in stored mode")
                .isNotNull();
        assertThat(ipbGroup.getAttribute("aria-hidden"))
                .as("Issuer bitmap field group should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(ipbGroup.isDisplayed())
                .as("Issuer bitmap field group should not be displayed in stored mode")
                .isFalse();

        WebElement iccTemplateInput = driver.findElement(By.id("emvIccTemplate"));
        WebElement iccGroup =
                iccTemplateInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(iccGroup.getAttribute("hidden"))
                .as("ICC template field group should be hidden in stored mode")
                .isNotNull();
        assertThat(iccGroup.getAttribute("aria-hidden"))
                .as("ICC template field group should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(iccGroup.isDisplayed())
                .as("ICC template field group should not be displayed in stored mode")
                .isFalse();

        WebElement masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(masterKeyMask.getAttribute("aria-hidden"))
                .as("Master key mask should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(masterKeyMask.isDisplayed())
                .as("Master key mask should not render placeholders in stored mode")
                .isFalse();

        WebElement cdol1Mask = driver.findElement(By.cssSelector("[data-testid='emv-cdol1-mask']"));
        assertThat(cdol1Mask.getAttribute("hidden"))
                .as("CDOL1 mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(cdol1Mask.getAttribute("aria-hidden"))
                .as("CDOL1 mask should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(cdol1Mask.isDisplayed())
                .as("CDOL1 mask should not render placeholders in stored mode")
                .isFalse();

        WebElement issuerMaskContainer =
                driver.findElement(By.cssSelector("[data-testid='emv-issuer-application-data-mask']"));
        assertThat(issuerMaskContainer.getAttribute("hidden"))
                .as("Issuer application data mask should remain hidden in stored mode")
                .isNotNull();
        assertThat(issuerMaskContainer.getAttribute("aria-hidden"))
                .as("Issuer application data mask should mark aria-hidden in stored mode")
                .isEqualTo("true");
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
        WebElement cardFieldset = driver.findElement(By.cssSelector("fieldset[data-testid='emv-card-block']"));
        assertThat(cardFieldset.findElements(By.cssSelector("fieldset[data-testid='emv-transaction-block']")))
                .as("Card configuration fieldset should not wrap the transaction block")
                .isEmpty();
        assertThat(cardFieldset.findElements(By.cssSelector("fieldset[data-testid='emv-customer-block']")))
                .as("Card configuration fieldset should not wrap the customer block")
                .isEmpty();
        WebElement transactionFieldset =
                driver.findElement(By.cssSelector("fieldset[data-testid='emv-transaction-block']"));
        assertThat(cardFieldset.findElements(
                        By.xpath("following-sibling::fieldset[@data-testid='emv-transaction-block']")))
                .as("Transaction fieldset should render as a sibling immediately after card configuration")
                .isNotEmpty();
        WebElement transactionLegend = transactionFieldset.findElement(By.tagName("legend"));
        assertThat(transactionLegend.getText())
                .as("Transaction legend should reflect the grouped ICC template inputs")
                .contains("Transaction");
        WebElement transactionHint = driver.findElement(By.cssSelector("[data-testid='emv-transaction-hint']"));
        assertThat(transactionHint.getText())
                .as("Transaction helper copy should describe the ATC substitution")
                .contains("\"xxxx\" is replaced by the ATC");
        assertThat(transactionFieldset.findElements(By.cssSelector("[data-testid='emv-icc-template']")))
                .as("Transaction block should expose the ICC payload template textarea")
                .isNotEmpty();
        assertThat(transactionFieldset.findElements(By.cssSelector("[data-testid='emv-issuer-application-data']")))
                .as("Transaction block should expose the issuer application data textarea")
                .isNotEmpty();

        WebElement customerFieldset = driver.findElement(By.cssSelector("fieldset[data-testid='emv-customer-block']"));
        WebElement customerLegend = customerFieldset.findElement(By.tagName("legend"));
        assertThat(customerLegend.getText())
                .as("Customer input fieldset should advertise the grouped section")
                .contains("Input from customer");
        WebElement customerHint = customerFieldset.findElement(By.cssSelector("[data-testid='emv-customer-hint']"));
        assertThat(customerHint.getText())
                .as("Identify mode hint should describe disabled customer inputs")
                .contains("Identify mode does not accept customer inputs.");

        WebElement customerGrid = customerFieldset.findElement(By.cssSelector("[data-testid='emv-customer-inputs']"));
        assertThat(customerGrid.findElements(By.cssSelector("[data-field='challenge']")))
                .as("Customer grid should expose a single challenge field group")
                .hasSize(1);
        assertThat(customerGrid.findElements(By.cssSelector("[data-field='reference']")))
                .as("Customer grid should expose a single reference field group")
                .hasSize(1);
        assertThat(customerGrid.findElements(By.cssSelector("[data-field='amount']")))
                .as("Customer grid should expose a single amount field group")
                .hasSize(1);

        WebElement challengeInput = driver.findElement(By.id("emvChallenge"));
        WebElement challengeGroup =
                challengeInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        WebElement referenceInput = driver.findElement(By.id("emvReference"));
        WebElement referenceGroup =
                referenceInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        WebElement amountInput = driver.findElement(By.id("emvAmount"));
        WebElement amountGroup = amountInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));

        assertThat(challengeInput.isEnabled())
                .as("Identify mode should disable challenge input")
                .isFalse();
        assertThat(referenceInput.isEnabled())
                .as("Identify mode should disable reference input")
                .isFalse();
        assertThat(amountInput.isEnabled())
                .as("Identify mode should disable amount input")
                .isFalse();
        assertThat(challengeGroup.getAttribute("aria-disabled"))
                .as("Identify mode should mark challenge container aria-disabled")
                .isEqualTo("true");
        assertThat(referenceGroup.getAttribute("aria-disabled"))
                .as("Identify mode should mark reference container aria-disabled")
                .isEqualTo("true");
        assertThat(amountGroup.getAttribute("aria-disabled"))
                .as("Identify mode should mark amount container aria-disabled")
                .isEqualTo("true");

        WebElement respondRadio = driver.findElement(By.cssSelector("[data-testid='emv-mode-respond']"));
        if (!respondRadio.isSelected()) {
            respondRadio.click();
        }
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> challengeInput.isEnabled() && !referenceInput.isEnabled() && !amountInput.isEnabled());
        assertThat(customerHint.getText())
                .as("Respond mode hint should describe challenge-only entry")
                .contains("Respond mode enables Challenge");

        WebElement signRadio = driver.findElement(By.cssSelector("[data-testid='emv-mode-sign']"));
        if (!signRadio.isSelected()) {
            signRadio.click();
        }
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> !challengeInput.isEnabled() && referenceInput.isEnabled() && amountInput.isEnabled());
        assertThat(customerHint.getText())
                .as("Sign mode hint should describe reference and amount inputs")
                .contains("Sign mode enables Reference and Amount");

        WebElement identifyRadio = driver.findElement(By.cssSelector("[data-testid='emv-mode-identify']"));
        if (!identifyRadio.isSelected()) {
            identifyRadio.click();
        }
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> !challengeInput.isEnabled() && !referenceInput.isEnabled() && !amountInput.isEnabled());

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
                .as("Master key mask should clear hidden attribute while inline mode is active")
                .isNull();
        assertThat(masterKeyMask.getAttribute("aria-hidden"))
                .as("Master key mask should clear aria-hidden while inline mode is active")
                .isNull();
        assertThat(masterKeyMask.isDisplayed())
                .as("Master key mask should remain visually hidden via inline styles in inline mode")
                .isFalse();

        WebElement masterKeyInput = waitForVisible(By.id("emvMasterKey"));
        assertThat(masterKeyInput.getAttribute("data-secret-mode")).isEqualTo("inline");
        assertThat(masterKeyInput.getAttribute("aria-hidden"))
                .as("Sensitive inputs should remain visible in inline mode")
                .isNull();
        WebElement masterKeyGroup =
                masterKeyInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(masterKeyGroup.getAttribute("hidden"))
                .as("Master key field group should clear hidden attribute in inline mode")
                .isNull();
        assertThat(masterKeyGroup.getAttribute("aria-hidden"))
                .as("Master key field group should clear aria-hidden in inline mode")
                .isNull();
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
        WebElement masterKeyInput = driver.findElement(By.id("emvMasterKey"));
        WebElement masterKeyGroup =
                masterKeyInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(masterKeyGroup.getAttribute("hidden"))
                .as("Master key field group should be hidden in stored mode")
                .isNotNull();
        assertThat(masterKeyGroup.getAttribute("aria-hidden"))
                .as("Master key field group should mark aria-hidden in stored mode")
                .isEqualTo("true");
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key field group should not be displayed in stored mode")
                .isFalse();
        WebElement masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should remain hidden while stored mode is active")
                .isNotNull();
        assertThat(masterKeyMask.getAttribute("aria-hidden"))
                .as("Master key mask should mark aria-hidden while stored mode is active")
                .isEqualTo("true");
        assertThat(masterKeyMask.isDisplayed())
                .as("Master key mask should not render placeholders in stored mode")
                .isFalse();

        waitForClickable(By.cssSelector("[data-testid='emv-mode-select-inline']"))
                .click();

        evaluateForm = driver.findElement(By.cssSelector("[data-testid='emv-form']"));
        assertThat(evaluateForm.getAttribute("class"))
                .as("Evaluate form should drop stored mode CSS class after switching to inline")
                .doesNotContain("emv-stored-mode");
        masterKeyInput = driver.findElement(By.id("emvMasterKey"));
        masterKeyGroup = masterKeyInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(masterKeyGroup.getAttribute("hidden"))
                .as("Master key field group should clear hidden attribute after switching to inline mode")
                .isNull();
        assertThat(masterKeyGroup.getAttribute("aria-hidden"))
                .as("Master key field group should clear aria-hidden after switching to inline mode")
                .isNull();
        assertThat(masterKeyGroup.isDisplayed())
                .as("Master key field group should reappear after switching to inline mode")
                .isTrue();
        masterKeyMask = driver.findElement(By.cssSelector("[data-testid='emv-master-key-mask']"));
        assertThat(masterKeyMask.getAttribute("hidden"))
                .as("Master key mask should clear hidden attribute after switching to inline mode")
                .isNull();
        assertThat(masterKeyMask.getAttribute("aria-hidden"))
                .as("Master key mask should clear aria-hidden after switching to inline mode")
                .isNull();
        assertThat(masterKeyMask.isDisplayed())
                .as("Master key mask should remain visually hidden after switching to inline mode")
                .isFalse();
        WebElement seedActions = driver.findElement(By.cssSelector("[data-testid='emv-seed-actions']"));
        assertThat(seedActions.getAttribute("hidden"))
                .as("Seed actions should be hidden after switching back to inline mode")
                .isNotNull();

        masterKeyInput = waitForVisible(By.id("emvMasterKey"));
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
        WebElement replayModeToggle = waitForVisible(By.cssSelector("fieldset[data-testid='emv-replay-mode-toggle']"));
        WebElement storedReplayRadio = replayModeToggle.findElement(By.cssSelector("#emvReplayModeStored"));
        if (!storedReplayRadio.isSelected()) {
            storedReplayRadio.click();
        }
        WebElement replaySampleBlock = waitForVisible(By.cssSelector("[data-testid='emv-replay-sample-vector']"));
        assertThat(replaySampleBlock.getAttribute("class"))
                .as("Replay preset block should reuse shared inline preset styling")
                .contains("inline-preset");
        WebElement replaySelectElement = storedSelect.getWrappedElement();
        String replaySelectBackground = replaySelectElement.getCssValue("background-color");
        assertThat(replaySelectBackground.replace(" ", ""))
                .as("Replay sample vector dropdown should not fall back to a white background")
                .doesNotContain("255,255,255");
        WebElement replayPresetContainer =
                replaySelectElement.findElement(By.xpath("ancestor::div[contains(@class,'stack-offset-top-lg')][1]"));
        assertThat(replayPresetContainer.getAttribute("class"))
                .as("Replay preset block should reuse shared spacing helper")
                .contains("stack-offset-top-lg");
        waitForReplayCredential(storedSelect, fixture.credentialId(), "CAP Respond baseline");
        ((JavascriptExecutor) driver)
                .executeScript(
                        "var select = document.getElementById('emvReplayStoredCredentialId');"
                                + "if (select) {"
                                + "  select.value = arguments[0];"
                                + "  select.dispatchEvent(new Event('change', { bubbles: true }));"
                                + "}",
                        fixture.credentialId());

        WebElement replayForm = driver.findElement(By.cssSelector("[data-testid='emv-replay-form']"));
        assertThat(replayForm.getAttribute("class"))
                .as("Replay form should mark stored mode via CSS class")
                .contains("emv-stored-mode");
        WebElement replaySessionBlock =
                driver.findElement(By.cssSelector("fieldset[data-testid='emv-replay-session-block']"));
        WebElement replaySessionLegend = replaySessionBlock.findElement(By.tagName("legend"));
        assertThat(replaySessionLegend.getText())
                .as("Replay session legend should describe the grouped derivation inputs")
                .contains("Session key derivation");
        WebElement replaySessionHint = driver.findElement(By.cssSelector("[data-testid='emv-replay-session-hint']"));
        assertThat(replaySessionHint.getText().trim())
                .as("Replay session helper copy should outline the reproduction guidance")
                .isEqualTo("Match the credential's session key inputs to reproduce CAP replay calculations.");
        assertFieldGroupVisible("#emvReplayAtc", "Replay ATC");
        assertFieldGroupVisible("#emvReplayBranchFactor", "Replay branch factor");
        assertFieldGroupVisible("#emvReplayHeight", "Replay height");
        assertFieldGroupVisible("#emvReplayIv", "Replay IV");
        assertMasterAtcRow(
                "emv-replay-master-atc-row", "#emvReplayMasterKey", "#emvReplayAtc", "Replay master/ATC row");
        assertBranchHeightRow(
                "emv-replay-branch-height-row",
                "#emvReplayBranchFactor",
                "#emvReplayHeight",
                "Replay session key pair");
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
        WebElement replayCardBlock =
                driver.findElement(By.cssSelector("fieldset[data-testid='emv-replay-card-block']"));
        assertThat(replayCardBlock.findElements(By.cssSelector("fieldset[data-testid='emv-replay-transaction-block']")))
                .as("Replay card configuration should not wrap the transaction block")
                .isEmpty();
        assertThat(replayCardBlock.findElements(By.cssSelector("fieldset[data-testid='emv-replay-customer-block']")))
                .as("Replay card configuration should not wrap the customer block")
                .isEmpty();
        assertThat(replayCardBlock.findElements(
                        By.xpath("following-sibling::fieldset[@data-testid='emv-replay-transaction-block']")))
                .as("Replay transaction block should render as a sibling of card configuration")
                .isNotEmpty();
        WebElement replayTransactionBlock =
                driver.findElement(By.cssSelector("fieldset[data-testid='emv-replay-transaction-block']"));
        WebElement replayTransactionLegend = replayTransactionBlock.findElement(By.tagName("legend"));
        assertThat(replayTransactionLegend.getText())
                .as("Replay transaction legend should reflect grouped ICC template inputs")
                .contains("Transaction");
        WebElement replayTransactionHint =
                driver.findElement(By.cssSelector("[data-testid='emv-replay-transaction-hint']"));
        assertThat(replayTransactionHint.getText())
                .as("Replay transaction helper copy should describe the ATC substitution")
                .contains("\"xxxx\" is replaced by the ATC");
        assertThat(replayTransactionBlock.findElements(By.cssSelector("[data-testid='emv-replay-icc-template']")))
                .as("Replay transaction block should present the ICC payload textarea")
                .isNotEmpty();
        assertThat(replayTransactionBlock.findElements(
                        By.cssSelector("[data-testid='emv-replay-issuer-application-data']")))
                .as("Replay transaction block should present issuer application data textarea")
                .isNotEmpty();

        WebElement replayCustomerGrid =
                driver.findElement(By.cssSelector("[data-testid='emv-replay-customer-inputs']"));
        assertThat(replayCustomerGrid.findElements(By.cssSelector("[data-field='challenge']")))
                .as("Replay customer grid should expose a single challenge field group")
                .hasSize(1);
        assertThat(replayCustomerGrid.findElements(By.cssSelector("[data-field='reference']")))
                .as("Replay customer grid should expose a single reference field group")
                .hasSize(1);
        assertThat(replayCustomerGrid.findElements(By.cssSelector("[data-field='amount']")))
                .as("Replay customer grid should expose a single amount field group")
                .hasSize(1);

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
        Object capturedPayload = ((JavascriptExecutor) driver).executeScript("return window.__emvLastReplayPayload;");
        System.out.println("Captured replay payload: " + capturedPayload);

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
        EmvCapReplayFixtures.ReplayFixture fixture = EmvCapReplayFixtures.load("replay-respond-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());

        navigateToEmvConsole();
        installHydrationListener();
        Object xhrType = ((JavascriptExecutor) driver).executeScript("return typeof XMLHttpRequest;");
        assertThat(String.valueOf(xhrType))
                .as("HtmlUnit should expose XMLHttpRequest for hydration fallback")
                .isEqualTo("function");
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

        Select replayStoredSelect = waitForReplayStoredCredentialSelect();
        waitForReplayCredential(replayStoredSelect, fixture.credentialId(), "CAP Respond baseline");
        ((JavascriptExecutor) driver)
                .executeScript(
                        "var select = document.getElementById('emvReplayStoredCredentialId');"
                                + "if (select) {"
                                + "  select.value = arguments[0];"
                                + "  select.dispatchEvent(new Event('change', { bubbles: true }));"
                                + "}",
                        fixture.credentialId());
        Object hookStatus = ((JavascriptExecutor) driver)
                .executeScript(
                        "if (window.EmvConsoleTestHooks && typeof window.EmvConsoleTestHooks.hydrateCredentialDetails === 'function') {"
                                + "window.EmvConsoleTestHooks.hydrateCredentialDetails(arguments[0]);"
                                + "return 'invoked';"
                                + "}"
                                + "return 'missing';",
                        fixture.credentialId());
        System.out.println("Hydrate hook status (mismatch): " + hookStatus);
        ((JavascriptExecutor) driver)
                .executeScript(
                        "if (window.EmvConsoleTestHooks && typeof window.EmvConsoleTestHooks.hydrateCredentialDetails === 'function') {"
                                + "window.EmvConsoleTestHooks.hydrateCredentialDetails(arguments[0]);"
                                + "}",
                        fixture.credentialId());
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());
        waitForReplayHydration(vector);
        assertThat(inlineRadio.isSelected())
                .as("Inline replay mode should remain active after selecting a preset")
                .isTrue();
        assertThat(storedRadio.isSelected())
                .as("Stored replay mode should stay inactive while inline mode is selected")
                .isFalse();

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
        String statusText = statusBadge.getText();
        WebElement reasonNode = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-reason']"));
        String reasonText = reasonNode.getText();
        System.out.println("Replay status badge: " + statusText);
        System.out.println("Replay reason text: " + reasonText);
        assertThat(statusText).isEqualTo("Mismatch");
        assertThat(reasonText).contains("OTP mismatch");

        WebElement tracePanel = waitForTracePanelVisibility(false);
        assertThat(tracePanel.getAttribute("hidden"))
                .as("Trace panel should remain hidden when includeTrace is unchecked")
                .isNotNull();
    }

    @Test
    @DisplayName("Inline EMV/CAP Sign replay hydrates presets and reports success")
    void inlineSignReplayMatchesPreset() {
        EmvCapReplayFixtures.ReplayFixture fixture = EmvCapReplayFixtures.load("replay-sign-baseline");
        EmvCapVector vector = EmvCapVectorFixtures.load(fixture.vectorId());

        navigateToEmvConsole();
        installHydrationListener();
        ((JavascriptExecutor) driver).executeScript("window.__emvHydrationDebug = true;");
        waitForClickable(By.cssSelector("[data-testid='emv-console-tab-replay']"))
                .click();
        Object replayMode = ((JavascriptExecutor) driver)
                .executeScript("var toggle = document.querySelector(\"[data-testid='emv-replay-mode-toggle']\");"
                        + "return toggle ? toggle.getAttribute('data-mode') : 'missing';");
        System.out.println("Replay mode (sign) before selection: " + replayMode);
        Object selectedModeBefore = ((JavascriptExecutor) driver)
                .executeScript(
                        "if (window.EmvConsoleTestHooks && typeof window.EmvConsoleTestHooks.selectedReplayMode === 'function') {"
                                + "return window.EmvConsoleTestHooks.selectedReplayMode();"
                                + "}"
                                + "return 'missing';");
        System.out.println("JS selectedReplayMode (sign) before selection: " + selectedModeBefore);

        Select replayStoredSelect = waitForReplayStoredCredentialSelect();
        waitForReplayCredential(replayStoredSelect, fixture.credentialId(), "CAP Sign baseline");
        ((JavascriptExecutor) driver)
                .executeScript(
                        "var select = document.getElementById('emvReplayStoredCredentialId');"
                                + "if (select) {"
                                + "  select.value = arguments[0];"
                                + "  select.dispatchEvent(new Event('change', { bubbles: true }));"
                                + "}",
                        fixture.credentialId());
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());
        Object replayModeAfter = ((JavascriptExecutor) driver)
                .executeScript("var toggle = document.querySelector(\"[data-testid='emv-replay-mode-toggle']\");"
                        + "return toggle ? toggle.getAttribute('data-mode') : 'missing';");
        System.out.println("Replay mode (sign) after selection: " + replayModeAfter);
        Object selectedMode = ((JavascriptExecutor) driver)
                .executeScript(
                        "if (window.EmvConsoleTestHooks && typeof window.EmvConsoleTestHooks.selectedReplayMode === 'function') {"
                                + "return window.EmvConsoleTestHooks.selectedReplayMode();"
                                + "}"
                                + "return 'missing';");
        System.out.println("JS selectedReplayMode (sign) after selection: " + selectedMode);
        Object credentialsSnapshot = ((JavascriptExecutor) driver)
                .executeScript(
                        "if (window.EmvConsoleTestHooks && typeof window.EmvConsoleTestHooks.listCredentials === 'function') {"
                                + "return window.EmvConsoleTestHooks.listCredentials();"
                                + "}"
                                + "return [];");
        System.out.println("Credential summaries: " + credentialsSnapshot);
        Object replaySelectHtml = ((JavascriptExecutor) driver)
                .executeScript("var select = document.getElementById('emvReplayStoredCredentialId');"
                        + "return select ? select.innerHTML : 'missing';");
        System.out.println("Replay select HTML: " + replaySelectHtml);
        Object hydrationDebug =
                ((JavascriptExecutor) driver).executeScript("return window.__emvLastReplayHydration || null;");
        System.out.println("Replay hydration debug (sign): " + hydrationDebug);
        waitForReplayHydration(vector);

        WebElement signRadio = waitForClickable(By.cssSelector("[data-testid='emv-replay-mode-sign']"));
        assertThat(signRadio.isSelected())
                .as("Selecting a Sign preset should switch the CAP mode to Sign automatically")
                .isTrue();
        WebElement challengeInput = driver.findElement(By.id("emvReplayChallenge"));
        WebElement referenceInput = driver.findElement(By.id("emvReplayReference"));
        WebElement amountInput = driver.findElement(By.id("emvReplayAmount"));
        assertThat(challengeInput.isEnabled())
                .as("Sign mode should keep challenge disabled")
                .isFalse();
        assertThat(referenceInput.isEnabled())
                .as("Reference input should be enabled in Sign mode")
                .isTrue();
        assertThat(amountInput.isEnabled())
                .as("Amount input should be enabled in Sign mode")
                .isTrue();
        assertThat(referenceInput.getAttribute("value"))
                .isEqualTo(vector.input().customerInputs().reference());
        assertThat(amountInput.getAttribute("value"))
                .isEqualTo(vector.input().customerInputs().amount());

        WebElement otpInput = waitForVisible(By.cssSelector("[data-testid='emv-replay-otp'] input[type='text']"));
        otpInput.clear();
        otpInput.sendKeys(fixture.otpDecimal());

        waitForClickable(By.cssSelector("button[data-testid='emv-replay-submit']"))
                .click();
        driver.getWebClient().waitForBackgroundJavaScript(WAIT_TIMEOUT.toMillis());

        WebElement resultCard = waitForVisible(By.cssSelector("[data-testid='emv-replay-result-card']"));
        WebElement statusBadge = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-status']"));
        assertThat(statusBadge.getText())
                .as("Replay status should reflect a successful match")
                .isEqualTo("Match");
        WebElement reasonNode = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-reason']"));
        String reasonText = reasonNode.getText();
        if (reasonText != null && !reasonText.isBlank()) {
            assertThat(reasonText).contains("MATCH_FOUND");
        }
        WebElement matchedNode = resultCard.findElement(By.cssSelector("[data-testid='emv-replay-matched-delta']"));
        assertThat(matchedNode.getText()).contains("Δ = 0");
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
        boolean labelMatch = select.getOptions().stream()
                .anyMatch(option ->
                        label.equals(option.getText().trim()) || label.equals(option.getAttribute("data-label")));
        if (!labelMatch) {
            System.out.println("Replay options: "
                    + select.getOptions().stream()
                            .map(option -> option.getAttribute("value") + "="
                                    + option.getText().trim())
                            .toList());
            System.out.println(
                    "Replay select innerHTML: " + select.getWrappedElement().getAttribute("innerHTML"));
        }
        assertThat(labelMatch)
                .as("Replay stored credential dropdown should expose preset label")
                .isTrue();
    }

    private void waitForReplayHydration(EmvCapVector vector) {
        JavascriptExecutor executor = driver;
        try {
            new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
                Object count = executor.executeScript("return (window.__emvHydrationLog || []).filter(function(entry){"
                        + "return entry && entry.kind === 'replay';"
                        + "}).length;");
                return count instanceof Number && ((Number) count).intValue() > 0;
            });
        } catch (org.openqa.selenium.TimeoutException ex) {
            Object logSnapshot = executor.executeScript("return window.__emvHydrationLog || [];");
            throw new AssertionError("Hydration log: " + logSnapshot, ex);
        }
        WebElement masterKey = driver.findElement(By.id("emvReplayMasterKey"));
        assertThat(masterKey.getAttribute("value"))
                .as("Replay master key should hydrate inline input")
                .isEqualTo(vector.input().masterKeyHex());
    }

    private void installHydrationListener() {
        waitForConsoleHooks();
        ((JavascriptExecutor) driver)
                .executeScript("window.__emvHydrationLog = [];"
                        + "if (window.EmvConsoleTestHooks) {"
                        + "  window.EmvConsoleTestHooks.onHydration = function(entry) {"
                        + "    if (!window.__emvHydrationLog) { window.__emvHydrationLog = []; }"
                        + "    window.__emvHydrationLog.push(entry);"
                        + "  };"
                        + "}");
    }

    private void waitForConsoleHooks() {
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> Boolean.TRUE.equals(
                        ((JavascriptExecutor) d).executeScript("return !!window.EmvConsoleTestHooks;")));
    }

    private void setHexInput(By locator, String value) {
        WebElement element =
                new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
        element.clear();
        element.sendKeys(value);
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

    private void assertBranchHeightRow(String rowTestId, String branchSelector, String heightSelector, String context) {
        String selector = String.format("[data-testid='%s']", rowTestId);
        WebElement row = driver.findElement(By.cssSelector(selector));
        assertThat(row.getAttribute("class"))
                .as(context + " row should reuse the paired session styling")
                .contains("emv-session-pair-row");
        List<WebElement> fieldGroups = row.findElements(By.cssSelector(".field-group"));
        assertThat(fieldGroups)
                .as(context + " row should only include the branch and height field groups")
                .hasSize(2);
        WebElement branchInput = row.findElement(By.cssSelector(branchSelector));
        WebElement branchGroup = branchInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        WebElement branchWrapper =
                branchGroup.findElement(By.xpath("ancestor::div[contains(@class,'emv-session-pair-row')][1]"));
        assertThat(branchWrapper)
                .as(context + " should wrap branch factor within the pair row")
                .isEqualTo(row);
        WebElement heightInput = row.findElement(By.cssSelector(heightSelector));
        WebElement heightGroup = heightInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        WebElement heightWrapper =
                heightGroup.findElement(By.xpath("ancestor::div[contains(@class,'emv-session-pair-row')][1]"));
        assertThat(heightWrapper)
                .as(context + " should wrap height within the pair row")
                .isEqualTo(row);
    }

    private void assertMasterAtcRow(String rowTestId, String masterSelector, String atcSelector, String context) {
        String selector = String.format("[data-testid='%s']", rowTestId);
        WebElement row = driver.findElement(By.cssSelector(selector));
        assertThat(row.getAttribute("class"))
                .as(context + " should reuse the dedicated master/ATC styling")
                .contains("emv-session-master-row");
        List<WebElement> fieldGroups = row.findElements(By.cssSelector(".field-group"));
        assertThat(fieldGroups)
                .as(context + " row should only include the master key and ATC field groups")
                .hasSize(2);
        WebElement masterInput = driver.findElement(By.cssSelector(masterSelector));
        WebElement masterGroup = masterInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        WebElement masterWrapper =
                masterGroup.findElement(By.xpath("ancestor::div[contains(@class,'emv-session-master-row')][1]"));
        assertThat(masterWrapper)
                .as(context + " should wrap the master key within the dedicated row")
                .isEqualTo(row);
        WebElement atcInput = driver.findElement(By.cssSelector(atcSelector));
        WebElement atcGroup = atcInput.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        WebElement atcWrapper =
                atcGroup.findElement(By.xpath("ancestor::div[contains(@class,'emv-session-master-row')][1]"));
        assertThat(atcWrapper)
                .as(context + " should wrap the ATC within the dedicated row")
                .isEqualTo(row);
    }

    private void assertFieldGroupVisible(String selector, String label) {
        WebElement input = driver.findElement(By.cssSelector(selector));
        WebElement group = input.findElement(By.xpath("ancestor::div[contains(@class,'field-group')][1]"));
        assertThat(group.getAttribute("hidden"))
                .as(label + " field group should remain visible")
                .isNull();
        assertThat(group.getAttribute("aria-hidden"))
                .as(label + " field group should keep aria-hidden cleared")
                .isNull();
        assertThat(group.isDisplayed())
                .as(label + " field group should stay rendered while stored mode hides secrets")
                .isTrue();
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
