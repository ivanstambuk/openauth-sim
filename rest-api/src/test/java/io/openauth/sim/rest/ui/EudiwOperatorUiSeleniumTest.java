package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class EudiwOperatorUiSeleniumTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);

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
    }

    @AfterEach
    void tearDown() {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (WebDriverException ignored) {
            // already disposed
        }
    }

    @Test
    @DisplayName("Baseline banner appears when HAIP enforcement is disabled and DCQL preview stays read-only")
    void baselineBannerTogglesWithProfile() {
        navigateToEudiwConsole();

        WebElement profileSelect = waitForVisible(By.cssSelector("[data-testid='eudiw-profile-select']"));
        WebElement banner = driver.findElement(By.cssSelector("[data-testid='eudiw-baseline-banner']"));
        assertThat(banner.getAttribute("hidden"))
                .as("HAIP profile keeps banner hidden")
                .isNotNull();

        if (driver instanceof JavascriptExecutor) {
            ((JavascriptExecutor) driver)
                    .executeScript(
                            "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
                            profileSelect,
                            "BASELINE");
        } else {
            new Select(profileSelect).selectByValue("BASELINE");
        }
        waitUntilAttribute(banner, "hidden", null);
        assertThat(banner.isDisplayed()).isTrue();

        WebElement dcqlPreview = waitForVisible(By.cssSelector("[data-testid='eudiw-dcql-preview']"));
        assertThat(dcqlPreview.getAttribute("readonly")).isEqualTo("true");
        String dcqlText = extractTextContent("[data-testid='eudiw-dcql-preview']");
        assertThat(dcqlText).contains("\"trusted_authorities\"");

        By trustedAuthoritiesLocator = By.cssSelector("[data-testid='eudiw-trusted-authorities']");
        waitForVisible(trustedAuthoritiesLocator);
        String trustedText = extractTextContent("[data-testid='eudiw-trusted-authorities']");
        assertThat(trustedText).contains("aki:s9tIpP7qrS9=");
    }

    @Test
    @DisplayName("Inline sample selector pre-fills SD-JWT fields for Evaluate mode")
    void inlineSampleAutofill() {
        navigateToEudiwConsole();

        WebElement inlineRadio = waitForVisible(By.cssSelector("[data-testid='eudiw-evaluate-mode-select-inline']"));
        assertThat(inlineRadio.isSelected()).isTrue();

        Select sampleSelect = new Select(waitForVisible(By.cssSelector("[data-testid='eudiw-inline-sample-select']")));
        sampleSelect.selectByIndex(0);

        WebElement applyButton = waitForVisible(By.cssSelector("[data-testid='eudiw-inline-sample-apply']"));
        applyButton.click();
        WebElement sdJwtField = waitForVisible(By.cssSelector("[data-testid='eudiw-inline-sdjwt']"));
        waitForSelectorTextContains("[data-testid='eudiw-inline-sdjwt']", "eyJhbGci");

        WebElement disclosuresField = waitForVisible(By.cssSelector("[data-testid='eudiw-inline-disclosures']"));
        waitForSelectorTextContains("[data-testid='eudiw-inline-disclosures']", "credentialSubject.family_name");

        WebElement credentialId = waitForVisible(By.cssSelector("[data-testid='eudiw-inline-credential-id']"));
        assertThat(credentialId.getAttribute("value")).isEqualTo("pid-haip-baseline");
    }

    @Test
    @DisplayName("Simulate wallet response routes verbose trace to the global dock without leaking VP Token")
    void simulateFlowEmitsGlobalTrace() {
        navigateToEudiwConsole();

        WebElement verboseToggle = waitForVisible(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        if (!verboseToggle.isSelected()) {
            verboseToggle.click();
        }

        WebElement storedRadio = waitForVisible(By.cssSelector("[data-testid='eudiw-evaluate-mode-select-stored']"));
        storedRadio.click();
        WebElement storedSection = waitForVisible(By.cssSelector("[data-testid='eudiw-evaluate-stored-section']"));
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> storedSection.isDisplayed());

        Select presetSelect = new Select(waitForVisible(By.cssSelector("[data-testid='eudiw-wallet-preset-select']")));
        presetSelect.selectByIndex(0);

        WebElement simulateButton = waitForVisible(By.cssSelector("[data-testid='eudiw-simulate-button']"));
        simulateButton.click();

        waitForSelectorTextContains("[data-testid='eudiw-result-status']", "SUCCESS");
        waitForSelectorTextContains("[data-testid='eudiw-result-vp-token']", "\"vpToken\"");

        WebElement tracePanel = waitForVisible(By.cssSelector("[data-testid='verbose-trace-panel']"));
        waitUntilAttribute(tracePanel, "hidden", null);
        assertThat(tracePanel.getAttribute("hidden"))
                .as("trace panel should be visible")
                .isNull();

        String traceContent = extractTextContent("[data-testid='verbose-trace-content']");
        assertThat(traceContent).contains("wallet.presentation");
        assertThat(traceContent).doesNotContain("\"vp_token\"");
    }

    @Test
    @DisplayName("Multi-presentation results render collapsible cards with copy/download controls")
    void multiPresentationResultsRenderCollapsibleSections() {
        navigateToEudiwConsole();
        injectClipboardAndDownloadHooks();
        injectMultiPresentationFetchStub();
        enableVerboseTrace();
        selectStoredWalletPreset();

        WebElement simulateButton = waitForVisible(By.cssSelector("[data-testid='eudiw-simulate-button']"));
        simulateButton.click();

        waitForMultiPresentationSections();

        List<WebElement> sections = driver.findElements(By.cssSelector("[data-testid='eudiw-result-presentation']"));
        assertThat(sections).hasSize(2);

        WebElement firstSection = sections.get(0);
        assertThat(firstSection.getAttribute("data-trace-id")).isEqualTo("trace-sdjwt");

        WebElement summary = firstSection.findElement(By.cssSelector("summary"));
        summary.click();

        WebElement copyButton = firstSection.findElement(By.cssSelector("[data-testid='eudiw-result-copy']"));
        copyButton.click();
        waitForJavascriptCondition("return (window.__eudiwCopiedValues || []).length;");
        Object copiedPayload = executeScript("return window.__eudiwCopiedValues.slice(-1)[0];");
        assertThat(String.valueOf(copiedPayload)).contains("wallet-sdjwt");

        WebElement downloadButton = firstSection.findElement(By.cssSelector("[data-testid='eudiw-result-download']"));
        downloadButton.click();
        waitForJavascriptCondition("return (window.__eudiwDownloadEvents || []).length;");
        Object downloadPayload = executeScript("return window.__eudiwDownloadEvents.slice(-1)[0];");
        assertThat(String.valueOf(downloadPayload)).contains("wallet-sdjwt");
    }

    @Test
    @DisplayName("Deep-link query params hydrate Replay tab and stored mode")
    void deepLinkHydratesReplayStoredMode() {
        navigateToEudiwConsoleWithQuery("?protocol=eudiw&tab=replay&mode=stored");
        waitForUrlContains("protocol=eudiw");
        waitForUrlContains("tab=replay");

        WebElement replayTab = waitForVisible(By.cssSelector("[data-testid='eudiw-panel-tab-replay']"));
        waitUntilAttribute(replayTab, "aria-selected", "true");
        assertThat(replayTab.getAttribute("class")).contains("mode-pill--active");

        WebElement replayPanel = waitForVisible(By.cssSelector("[data-testid='eudiw-replay-panel']"));
        assertThat(replayPanel.getAttribute("aria-hidden")).isNull();
        WebElement storedRadio = waitForVisible(By.cssSelector("[data-testid='eudiw-replay-mode-select-stored']"));
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> storedRadio.isSelected());
        WebElement storedSection = waitForVisible(By.cssSelector("[data-testid='eudiw-replay-stored-section']"));
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> storedSection.isDisplayed());

        WebElement inlineRadio = waitForVisible(By.cssSelector("[data-testid='eudiw-replay-mode-select-inline']"));
        inlineRadio.click();
        waitForUrlContains("mode=inline");
        WebElement replayInlineSection = waitForVisible(By.cssSelector("[data-testid='eudiw-replay-inline-section']"));
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> replayInlineSection.isDisplayed());

        driver.navigate().back();
        waitForUrlContains("mode=stored");
        WebElement storedRadioAfterBack =
                waitForVisible(By.cssSelector("[data-testid='eudiw-replay-mode-select-stored']"));
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> storedRadioAfterBack.isSelected());

        driver.navigate().forward();
        waitForUrlContains("mode=inline");
        WebElement inlineRadioAfterForward =
                waitForVisible(By.cssSelector("[data-testid='eudiw-replay-mode-select-inline']"));
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> inlineRadioAfterForward.isSelected());
    }

    private void navigateToEudiwConsole() {
        navigateToEudiwConsoleWithQuery("?protocol=eudiw");
    }

    private void navigateToEudiwConsoleWithQuery(String query) {
        try {
            String suffix = (query == null || query.isBlank()) ? "?protocol=eudiw" : query;
            URL url = new URL("http", "localhost", port, "/ui/console" + suffix);
            driver.get(url.toString());
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Unable to build console URL", ex);
        }
        waitForVisible(By.cssSelector("[data-protocol-panel='eudi-openid4vp']"));
        if (driver instanceof JavascriptExecutor) {
            ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
        }
    }

    private WebElement waitForVisible(By locator) {
        return new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private void waitUntilAttribute(WebElement element, String attribute, String expectedValue) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> {
            String value = element.getAttribute(attribute);
            if (expectedValue == null) {
                return value == null;
            }
            return expectedValue.equals(value);
        });
    }

    private void waitForSelectorTextContains(String selector, String expected) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(driver -> {
            String text = extractTextContent(selector);
            return text != null && text.contains(expected);
        });
    }

    private void waitForUrlContains(String fragment) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(ExpectedConditions.urlContains(fragment));
    }

    private String extractTextContent(String selector) {
        if (driver instanceof JavascriptExecutor) {
            Object value = ((JavascriptExecutor) driver)
                    .executeScript(
                            "var el = document.querySelector(arguments[0]);"
                                    + "if (!el) { return null; }"
                                    + "if (typeof el.value === 'string' && el.value.length) { return el.value; }"
                                    + "return el.textContent;",
                            selector);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        WebElement element = driver.findElement(By.cssSelector(selector));
        String fallback = element.getDomProperty("value");
        if (fallback == null || fallback.isBlank()) {
            fallback = element.getText();
        }
        return fallback;
    }

    private void enableVerboseTrace() {
        WebElement verboseToggle = waitForVisible(By.cssSelector("[data-testid='verbose-trace-checkbox']"));
        if (!verboseToggle.isSelected()) {
            verboseToggle.click();
        }
    }

    private void selectStoredWalletPreset() {
        WebElement storedRadio = waitForVisible(By.cssSelector("[data-testid='eudiw-evaluate-mode-select-stored']"));
        storedRadio.click();
        Select presetSelect = new Select(waitForVisible(By.cssSelector("[data-testid='eudiw-wallet-preset-select']")));
        presetSelect.selectByIndex(0);
    }

    private void waitForMultiPresentationSections() {
        new WebDriverWait(driver, WAIT_TIMEOUT)
                .until(d -> d.findElements(By.cssSelector("[data-testid='eudiw-result-presentation']"))
                                .size()
                        >= 2);
    }

    private void waitForJavascriptCondition(String script) {
        new WebDriverWait(driver, WAIT_TIMEOUT).until(d -> {
            Object value = ((JavascriptExecutor) d).executeScript(script);
            if (value instanceof Number number) {
                return number.longValue() > 0;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            return value != null;
        });
    }

    private Object executeScript(String script) {
        if (!(driver instanceof JavascriptExecutor)) {
            return null;
        }
        return ((JavascriptExecutor) driver).executeScript(script);
    }

    private void injectClipboardAndDownloadHooks() {
        if (!(driver instanceof JavascriptExecutor)) {
            return;
        }
        ((JavascriptExecutor) driver).executeScript("""
                (function () {
                  window.__eudiwCopiedValues = [];
                  window.__eudiwDownloadEvents = [];
                  if (!window.navigator) {
                    window.navigator = {};
                  }
                  if (!window.navigator.clipboard) {
                    window.navigator.clipboard = {};
                  }
                  window.navigator.clipboard.writeText = function (value) {
                    window.__eudiwCopiedValues.push(String(value));
                    return Promise.resolve();
                  };
                  window.EudiwConsoleTestHooks = window.EudiwConsoleTestHooks || {};
                  window.EudiwConsoleTestHooks.onDownload = function (event) {
                    window.__eudiwDownloadEvents.push(event);
                  };
                })();
                """);
    }

    private void injectMultiPresentationFetchStub() {
        if (!(driver instanceof JavascriptExecutor)) {
            return;
        }
        String authResponseJson = """
                {
                  "requestId": "REQ-STUB",
                  "profile": "HAIP",
                  "responseMode": "DIRECT_POST_JWT",
                  "qr": { "ascii": "STUB", "uri": "https://example.org/req/stub" }
                }
                """;
        String walletResponseJson = """
                {
                  "requestId": "REQ-STUB",
                  "status": "SUCCESS",
                  "profile": "HAIP",
                  "responseMode": "DIRECT_POST_JWT",
                  "presentations": [
                    {
                      "credentialId": "pid-haip-multi",
                      "format": "dc+sd-jwt",
                      "holderBinding": true,
                      "trustedAuthorityMatch": "aki:s9tIpP7qrS9=",
                      "vpToken": {
                        "vpToken": "wallet-sdjwt",
                        "presentationSubmission": {
                          "descriptor_map": [{ "id": "pid-haip-multi", "path": "$.vp_token" }]
                        }
                      },
                      "disclosureHashes": ["sha-256:sdjwt"]
                    },
                    {
                      "credentialId": "pid-mdoc-multi",
                      "format": "mso_mdoc",
                      "holderBinding": false,
                      "trustedAuthorityMatch": null,
                      "vpToken": {
                        "vpToken": "wallet-mdoc",
                        "presentationSubmission": {
                          "descriptor_map": [{ "id": "pid-mdoc-multi", "path": "$.vp_token" }]
                        }
                      },
                      "disclosureHashes": ["sha-256:mdoc"]
                    }
                  ],
                  "trace": {
                    "vpTokenHash": "sha-256:root",
                    "presentations": [
                      { "id": "trace-sdjwt", "credentialId": "pid-haip-multi", "vpTokenHash": "sha-256:sdjwt" },
                      { "id": "trace-mdoc", "credentialId": "pid-mdoc-multi", "vpTokenHash": "sha-256:mdoc" }
                    ]
                  }
                }
                """;
        ((JavascriptExecutor) driver).executeScript("""
                (function () {
                  var authPayload = %s;
                  var walletPayload = %s;
                  var originalFetch = window.fetch;
                  window.fetch = function (resource, options) {
                    var url = typeof resource === 'string' ? resource : (resource && resource.url) || '';
                    if (url.indexOf('/api/v1/eudiw/openid4vp/requests') !== -1) {
                      return Promise.resolve({
                        ok: true,
                        status: 200,
                        text: function () { return Promise.resolve(JSON.stringify(authPayload)); }
                      });
                    }
                    if (url.indexOf('/api/v1/eudiw/openid4vp/wallet/simulate') !== -1) {
                      return Promise.resolve({
                        ok: true,
                        status: 200,
                        text: function () { return Promise.resolve(JSON.stringify(walletPayload)); }
                      });
                    }
                    return originalFetch(resource, options);
                  };
                })();
                """.formatted(authResponseJson, walletResponseJson));
    }
}
