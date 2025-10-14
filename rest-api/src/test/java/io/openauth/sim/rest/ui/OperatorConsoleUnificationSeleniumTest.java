package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class OperatorConsoleUnificationSeleniumTest {

  @LocalServerPort private int port;

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("console-unification.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  private HtmlUnitDriver driver;

  @BeforeEach
  void setUp() {
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
    driver.getWebClient().getOptions().setThrowExceptionOnScriptError(true);
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @DisplayName("Unified console exposes protocol tabs with OCRA active and others disabled")
  void unifiedConsoleRendersProtocolTabs() {
    driver.get(baseUrl("/ui/console"));

    WebElement tabList =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='operator-protocol-tabs']")));
    assertThat(tabList.getAttribute("role")).isEqualTo("tablist");

    List<WebElement> tabs = tabList.findElements(By.cssSelector("[data-protocol-tab]"));
    List<String> tabLabels =
        tabs.stream().map(WebElement::getText).map(String::trim).collect(Collectors.toList());
    assertThat(tabLabels)
        .containsExactly(
            "HOTP",
            "TOTP",
            "OCRA",
            "EMV / CAP",
            "FIDO2 / WebAuthn",
            "EUDIW OpenID4VP 1.0",
            "EUDIW ISO/IEC 18013-5",
            "EUDIW SIOPv2");

    WebElement hotpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-hotp']"));
    assertThat(hotpTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement totpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-totp']"));
    assertThat(totpTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement ocraTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-ocra']"));
    assertThat(ocraTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement emvTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-emv']"));
    assertThat(emvTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement fidoTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']"));
    assertThat(fidoTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement eudiOpenidTab =
        driver.findElement(By.cssSelector("[data-testid='protocol-tab-eudi-openid4vp']"));
    assertThat(eudiOpenidTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement eudiIsoTab =
        driver.findElement(By.cssSelector("[data-testid='protocol-tab-eudi-iso-18013-5']"));
    assertThat(eudiIsoTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement eudiSiopTab =
        driver.findElement(By.cssSelector("[data-testid='protocol-tab-eudi-siopv2']"));
    assertThat(eudiSiopTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement modeToggle = driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));
    assertThat(modeToggle.getAttribute("data-mode"))
        .as("OCRA mode toggle should default to evaluation")
        .isEqualTo("evaluate");

    WebElement ocraPanel = driver.findElement(By.cssSelector("[data-protocol-panel='ocra']"));
    WebElement evaluateSection =
        ocraPanel.findElement(By.cssSelector("[data-testid='ocra-evaluate-panel']"));
    assertThat(evaluateSection.getAttribute("hidden"))
        .as("Evaluation panel should be visible by default")
        .isNull();
    WebElement evaluateForm =
        evaluateSection.findElement(By.cssSelector("[data-testid='ocra-evaluate-form']"));
    assertThat(evaluateForm).isNotNull();

    WebElement replaySection =
        driver.findElement(By.cssSelector("[data-testid='ocra-replay-panel']"));
    assertThat(replaySection.getAttribute("hidden"))
        .as("Replay panel should start hidden")
        .isNotNull();
    WebElement replayForm =
        replaySection.findElement(By.cssSelector("[data-testid='ocra-replay-form']"));
    assertThat(replayForm).isNotNull();

    WebElement replayToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-replay']"));
    replayToggle.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "replay".equals(modeToggle.getAttribute("data-mode")));

    assertThat(evaluateSection.getAttribute("hidden"))
        .as("Evaluation panel should be hidden after switching to replay")
        .isNotNull();
    assertThat(replaySection.getAttribute("hidden"))
        .as("Replay panel should be visible after switch")
        .isNull();

    WebElement evaluateToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-evaluate']"));
    evaluateToggle.click();

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(d -> "evaluate".equals(modeToggle.getAttribute("data-mode")));

    assertThat(evaluateSection.getAttribute("hidden")).isNull();
    assertThat(replaySection.getAttribute("hidden")).isNotNull();

    hotpTab.click();
    By hotpPanelLocator = By.cssSelector("[data-protocol-panel='hotp']");
    WebElement hotpPanel = driver.findElement(hotpPanelLocator);
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(panel -> driver.findElement(hotpPanelLocator).getAttribute("hidden") == null);
    assertThat(modeToggle.getAttribute("hidden"))
        .as("Mode toggle should hide for non-OCRA protocols")
        .isNotNull();
    assertThat(ocraPanel.getAttribute("hidden")).isNotNull();

    WebElement hotpPanelTabs =
        hotpPanel.findElement(By.cssSelector("[data-testid='hotp-panel-tabs']"));
    WebElement hotpEvaluateTab =
        hotpPanelTabs.findElement(By.cssSelector("[data-testid='hotp-panel-tab-evaluate']"));
    WebElement hotpReplayTab =
        hotpPanelTabs.findElement(By.cssSelector("[data-testid='hotp-panel-tab-replay']"));

    assertThat(hotpEvaluateTab.getAttribute("aria-selected")).isEqualTo("true");
    assertThat(hotpReplayTab.getAttribute("aria-selected")).isEqualTo("false");

    WebElement hotpModeToggle =
        hotpPanel.findElement(By.cssSelector("[data-testid='hotp-mode-toggle']"));
    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(
            d -> {
              String mode = hotpModeToggle.getAttribute("data-mode");
              return mode != null && !mode.isBlank();
            });
    assertThat(hotpModeToggle.getAttribute("data-mode")).isEqualTo("inline");
    WebElement storedToggle =
        hotpModeToggle.findElement(By.cssSelector("[data-testid='hotp-mode-select-stored']"));
    WebElement inlineToggle =
        hotpModeToggle.findElement(By.cssSelector("[data-testid='hotp-mode-select-inline']"));
    assertThat(storedToggle.isSelected()).isFalse();
    assertThat(inlineToggle.isSelected()).isTrue();

    By storedPanelLocator = By.cssSelector("[data-testid='hotp-stored-evaluation-panel']");
    By inlinePanelLocator = By.cssSelector("[data-testid='hotp-inline-evaluation-panel']");

    WebElement inlineCard =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(ExpectedConditions.visibilityOfElementLocated(inlinePanelLocator));
    WebElement storedCard = hotpPanel.findElement(storedPanelLocator);
    assertThat(inlineCard.findElements(By.tagName("h2")))
        .as("Inline HOTP form should not render redundant headings")
        .isEmpty();
    assertThat(storedCard.isDisplayed()).isFalse();

    storedToggle.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "stored".equals(hotpModeToggle.getAttribute("data-mode")));
    assertThat(storedToggle.isSelected()).isTrue();
    assertThat(inlineToggle.isSelected()).isFalse();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.findElement(storedPanelLocator).isDisplayed());
    inlineCard = driver.findElement(inlinePanelLocator);
    storedCard = driver.findElement(storedPanelLocator);
    assertThat(storedCard.isDisplayed()).isTrue();
    assertThat(storedCard.findElements(By.tagName("h2")))
        .as("Stored HOTP form should not render redundant headings")
        .isEmpty();
    assertThat(inlineCard.isDisplayed()).isFalse();

    inlineToggle.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "inline".equals(hotpModeToggle.getAttribute("data-mode")));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.findElement(inlinePanelLocator).isDisplayed());
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> !d.findElement(storedPanelLocator).isDisplayed());
    storedCard = driver.findElement(storedPanelLocator);
    inlineCard = driver.findElement(inlinePanelLocator);
    assertThat(storedCard.isDisplayed()).isFalse();
    assertThat(inlineCard.isDisplayed()).isTrue();
    assertThat(inlineCard.findElements(By.tagName("h2")))
        .as("Inline HOTP form should not render redundant headings")
        .isEmpty();

    totpTab.click();
    WebElement totpPanel = driver.findElement(By.cssSelector("[data-protocol-panel='totp']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> totpPanel.getAttribute("hidden") == null);
    assertThat(totpPanel.findElement(By.tagName("h2")).getText()).contains("TOTP");

    emvTab.click();
    WebElement emvPanel = driver.findElement(By.cssSelector("[data-protocol-panel='emv']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> emvPanel.getAttribute("hidden") == null);
    assertThat(modeToggle.getAttribute("hidden"))
        .as("Mode toggle should hide for non-OCRA protocols")
        .isNotNull();
    assertThat(ocraPanel.getAttribute("hidden")).isNotNull();
    assertThat(emvPanel.getText()).contains("EMV");

    fidoTab.click();
    WebElement fidoPanel = driver.findElement(By.cssSelector("[data-protocol-panel='fido2']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> fidoPanel.getAttribute("hidden") == null);
    WebElement fidoEvaluateHeading =
        fidoPanel.findElement(By.cssSelector("#fido2-evaluate-heading"));
    assertThat(fidoEvaluateHeading.getText()).contains("Evaluate a WebAuthn assertion");
    WebElement fidoReplayTab =
        fidoPanel.findElement(By.cssSelector("[data-testid='fido2-panel-tab-replay']"));
    assertThat(fidoReplayTab.getText()).containsIgnoringCase("Replay");

    eudiOpenidTab.click();
    WebElement eudiOpenidPanel =
        driver.findElement(By.cssSelector("[data-protocol-panel='eudi-openid4vp']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> eudiOpenidPanel.getAttribute("hidden") == null);
    assertThat(eudiOpenidPanel.findElement(By.tagName("h2")).getText()).contains("OpenID4VP");

    eudiIsoTab.click();
    WebElement eudiIsoPanel =
        driver.findElement(By.cssSelector("[data-protocol-panel='eudi-iso-18013-5']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> eudiIsoPanel.getAttribute("hidden") == null);
    assertThat(eudiIsoPanel.findElement(By.tagName("h2")).getText()).contains("ISO/IEC 18013-5");

    eudiSiopTab.click();
    WebElement eudiSiopPanel =
        driver.findElement(By.cssSelector("[data-protocol-panel='eudi-siopv2']"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> eudiSiopPanel.getAttribute("hidden") == null);
    assertThat(eudiSiopPanel.findElement(By.tagName("h2")).getText()).contains("SIOPv2");

    ocraTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(panel -> modeToggle.getAttribute("hidden") == null);
    assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("evaluate");
    assertThat(ocraPanel.getAttribute("hidden")).isNull();
    assertThat(hotpPanel.getAttribute("hidden")).isNotNull();
    assertThat(totpPanel.getAttribute("hidden")).isNotNull();
    assertThat(emvPanel.getAttribute("hidden")).isNotNull();
    assertThat(fidoPanel.getAttribute("hidden")).isNotNull();
    assertThat(eudiOpenidPanel.getAttribute("hidden")).isNotNull();
    assertThat(eudiIsoPanel.getAttribute("hidden")).isNotNull();
    assertThat(eudiSiopPanel.getAttribute("hidden")).isNotNull();
  }

  @Test
  @DisplayName("OCRA and HOTP evaluation result headings share HOTP typography")
  void evaluationResultHeadingTypographyMatches() {
    driver.get(baseUrl("/ui/console"));

    WebElement ocraHeading =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='ocra-result-panel'] #result-heading")));
    String ocraFontSize = computeFontSize(ocraHeading);

    WebElement hotpTab =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='protocol-tab-hotp']")));
    hotpTab.click();

    WebElement hotpPanel =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-protocol-panel='hotp']")));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> hotpPanel.getAttribute("hidden") == null);

    WebElement hotpHeading =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='hotp-result-heading']")));
    String hotpFontSize = computeFontSize(hotpHeading);

    assertThat(ocraFontSize)
        .as("Expected evaluation result headings to share HOTP typography")
        .isEqualTo(hotpFontSize);
  }

  @Test
  @DisplayName("Query parameters restore protocol and tab selection on load")
  void queryParametersRestoreConsoleState() {
    driver.get(baseUrl("/ui/console?protocol=ocra&tab=replay"));

    WebElement modeToggle =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='ocra-mode-toggle']")));
    assertThat(driver.getCurrentUrl()).contains("protocol=ocra").contains("tab=replay");
    assertThat(modeToggle.getAttribute("data-mode")).isEqualTo("replay");

    WebElement replayPanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-replay-panel']"));
    assertThat(replayPanel.getAttribute("hidden"))
        .as("Replay panel should be visible when tab=replay")
        .isNull();
    WebElement evaluatePanel =
        driver.findElement(By.cssSelector("[data-testid='ocra-evaluate-panel']"));
    assertThat(evaluatePanel.getAttribute("hidden"))
        .as("Evaluation panel should hide when tab=replay")
        .isNotNull();

    List<String[]> protocolExpectations =
        List.of(
            new String[] {"hotp", "protocol-tab-hotp", "hotp"},
            new String[] {"totp", "protocol-tab-totp", "totp"},
            new String[] {"emv", "protocol-tab-emv", "emv"},
            new String[] {"fido2", "protocol-tab-fido2", "fido2"},
            new String[] {"eudi-openid4vp", "protocol-tab-eudi-openid4vp", "eudi-openid4vp"},
            new String[] {"eudi-iso-18013-5", "protocol-tab-eudi-iso-18013-5", "eudi-iso-18013-5"},
            new String[] {"eudi-siopv2", "protocol-tab-eudi-siopv2", "eudi-siopv2"});

    for (String[] expectation : protocolExpectations) {
      String protocol = expectation[0];
      String tabTestId = expectation[1];
      String panelKey = expectation[2];

      driver.get(baseUrl("/ui/console?protocol=" + protocol));

      WebElement tab =
          new WebDriverWait(driver, Duration.ofSeconds(3))
              .until(
                  ExpectedConditions.presenceOfElementLocated(
                      By.cssSelector("[data-testid='" + tabTestId + "']")));
      assertThat(driver.getCurrentUrl()).contains("protocol=" + protocol);
      assertThat(tab.getAttribute("aria-selected")).isEqualTo("true");

      WebElement panel =
          driver.findElement(By.cssSelector("[data-protocol-panel='" + panelKey + "']"));
      assertThat(panel.getAttribute("hidden")).isNull();

      WebElement ocraPanel = driver.findElement(By.cssSelector("[data-protocol-panel='ocra']"));
      if (!"ocra".equals(protocol)) {
        assertThat(ocraPanel.getAttribute("hidden"))
            .as("OCRA panel should hide when deep-linking to " + protocol)
            .isNotNull();
      }

      WebElement ocraModeToggle =
          driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));
      assertThat(ocraModeToggle.getAttribute("hidden"))
          .as("Mode toggle should be hidden for disabled protocols")
          .isNotNull();
    }
  }

  @Test
  @DisplayName("Tab navigation updates query parameters and browser history")
  void tabNavigationUpdatesHistory() {
    driver.get(baseUrl("/ui/console"));

    WebElement hotpTab =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='protocol-tab-hotp']")));
    WebElement totpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-totp']"));
    WebElement emvTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-emv']"));
    WebElement fidoTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']"));
    WebElement eudiOpenidTab =
        driver.findElement(By.cssSelector("[data-testid='protocol-tab-eudi-openid4vp']"));
    WebElement eudiIsoTab =
        driver.findElement(By.cssSelector("[data-testid='protocol-tab-eudi-iso-18013-5']"));
    WebElement eudiSiopTab =
        driver.findElement(By.cssSelector("[data-testid='protocol-tab-eudi-siopv2']"));
    WebElement ocraTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-ocra']"));

    hotpTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=hotp"));
    assertThat(driver.getCurrentUrl()).contains("protocol=hotp");

    totpTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=totp"));
    assertThat(driver.getCurrentUrl()).contains("protocol=totp");

    emvTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=emv"));
    assertThat(driver.getCurrentUrl()).contains("protocol=emv");

    fidoTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=fido2"));
    assertThat(driver.getCurrentUrl()).contains("protocol=fido2");

    driver.navigate().back();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=emv"));
    assertThat(driver.getCurrentUrl()).contains("protocol=emv");

    driver.navigate().forward();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=fido2"));
    assertThat(driver.getCurrentUrl()).contains("protocol=fido2");

    eudiOpenidTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=eudi-openid4vp"));
    assertThat(driver.getCurrentUrl()).contains("protocol=eudi-openid4vp");

    eudiIsoTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=eudi-iso-18013-5"));
    assertThat(driver.getCurrentUrl()).contains("protocol=eudi-iso-18013-5");

    eudiSiopTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=eudi-siopv2"));
    assertThat(driver.getCurrentUrl()).contains("protocol=eudi-siopv2");

    ocraTab.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("protocol=ocra"));
    assertThat(driver.getCurrentUrl()).contains("protocol=ocra");
    assertThat(driver.getCurrentUrl()).contains("tab=evaluate");

    WebElement replayToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-replay']"));
    WebElement modeToggle = driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));

    replayToggle.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "replay".equals(modeToggle.getAttribute("data-mode")));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("tab=replay"));
    assertThat(driver.getCurrentUrl()).contains("protocol=ocra").contains("tab=replay");

    driver.navigate().back();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "evaluate".equals(modeToggle.getAttribute("data-mode")));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("tab=evaluate"));
    assertThat(driver.getCurrentUrl()).contains("protocol=ocra").contains("tab=evaluate");

    driver.navigate().forward();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "replay".equals(modeToggle.getAttribute("data-mode")));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> driver.getCurrentUrl().contains("tab=replay"));
    assertThat(driver.getCurrentUrl()).contains("protocol=ocra").contains("tab=replay");
  }

  @Test
  @DisplayName("Protocol tab navigation omits legacy protocol-specific query parameters")
  void protocolTabNavigationOmitsLegacyQueryParameters() {
    driver.get(
        baseUrl(
            "/ui/console?protocol=ocra&tab=replay&mode=stored"
                + "&totpTab=replay&totpMode=stored&totpReplayMode=stored"
                + "&fido2Mode=replay"));

    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));

    List<WebElement> tabs =
        List.of(
            wait.until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='protocol-tab-hotp']"))),
            driver.findElement(By.cssSelector("[data-testid='protocol-tab-totp']")),
            driver.findElement(By.cssSelector("[data-testid='protocol-tab-ocra']")),
            driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']")));

    List<String> expectedKeys = List.of("mode", "protocol", "tab");

    tabs.forEach(
        tab -> {
          tab.click();
          wait.until(d -> queryKeys(d.getCurrentUrl()).containsAll(expectedKeys));
          List<String> sortedKeys = queryKeys(driver.getCurrentUrl());
          assertThat(sortedKeys).isEqualTo(expectedKeys);
          assertThat(driver.getCurrentUrl())
              .doesNotContain("totpTab=")
              .doesNotContain("totpMode=")
              .doesNotContain("totpReplayMode=")
              .doesNotContain("fido2Mode=");
        });
  }

  @Test
  @DisplayName("Protocol tab clicks reset evaluate inline defaults for active protocols")
  void protocolTabsResetEvaluateInlineDefaults() {
    driver.get(baseUrl("/ui/console?protocol=ocra&tab=replay"));

    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));

    WebElement ocraEvaluateButton =
        wait.until(
            ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='ocra-mode-select-evaluate']")));
    ocraEvaluateButton.click();
    wait.until(
        webDriver ->
            "evaluate"
                .equals(
                    webDriver
                        .findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"))
                        .getAttribute("data-mode")));

    WebElement ocraStoredRadio =
        wait.until(ExpectedConditions.elementToBeClickable(By.id("mode-credential")));
    ocraStoredRadio.click();
    wait.until(webDriver -> webDriver.findElement(By.id("mode-credential")).isSelected());

    WebElement ocraReplayButton =
        wait.until(
            ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='ocra-mode-select-replay']")));
    ocraReplayButton.click();
    wait.until(
        webDriver ->
            "replay"
                .equals(
                    webDriver
                        .findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"))
                        .getAttribute("data-mode")));

    driver.get(baseUrl("/ui/console?protocol=hotp&tab=replay&mode=stored"));
    waitUntilUrlContains("protocol=hotp");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");

    driver.get(baseUrl("/ui/console?protocol=totp&tab=replay&mode=stored&totpReplayMode=stored"));
    waitUntilUrlContains("protocol=totp");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");

    driver.get(baseUrl("/ui/console?protocol=fido2&tab=replay&mode=stored&fido2Mode=replay"));
    waitUntilUrlContains("protocol=fido2");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");

    WebElement ocraTab =
        wait.until(
            ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='protocol-tab-ocra']")));
    ocraTab.click();
    waitUntilUrlContains("protocol=ocra");
    waitUntilUrlContains("tab=evaluate");
    waitUntilUrlContains("mode=inline");

    WebElement ocraModeToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));
    assertThat(ocraModeToggle.getAttribute("data-mode")).isEqualTo("evaluate");
    assertThat(driver.findElement(By.id("mode-inline")).isSelected()).isTrue();
    assertThat(driver.findElement(By.id("mode-credential")).isSelected()).isFalse();

    WebElement hotpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-hotp']"));
    hotpTab.click();
    waitUntilUrlContains("protocol=hotp");
    waitUntilUrlContains("tab=evaluate");
    waitUntilUrlContains("mode=inline");

    WebElement hotpEvaluateTab =
        driver.findElement(By.cssSelector("[data-testid='hotp-panel-tab-evaluate']"));
    assertThat(hotpEvaluateTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement hotpModeToggle =
        driver.findElement(By.cssSelector("[data-testid='hotp-mode-toggle']"));
    assertThat(hotpModeToggle.getAttribute("data-mode")).isEqualTo("inline");
    assertThat(
            driver
                .findElement(By.cssSelector("[data-testid='hotp-mode-select-inline']"))
                .isSelected())
        .isTrue();
    assertThat(
            driver
                .findElement(By.cssSelector("[data-testid='hotp-mode-select-stored']"))
                .isSelected())
        .isFalse();

    WebElement totpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-totp']"));
    totpTab.click();
    waitUntilUrlContains("protocol=totp");
    waitUntilUrlContains("tab=evaluate");
    waitUntilUrlContains("mode=inline");

    WebElement totpEvaluateTab =
        driver.findElement(By.cssSelector("[data-testid='totp-panel-tab-evaluate']"));
    assertThat(totpEvaluateTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement totpModeToggle =
        driver.findElement(By.cssSelector("[data-testid='totp-mode-toggle']"));
    assertThat(totpModeToggle.getAttribute("data-mode")).isEqualTo("inline");
    assertThat(
            driver
                .findElement(By.cssSelector("[data-testid='totp-mode-select-inline']"))
                .isSelected())
        .isTrue();
    assertThat(
            driver
                .findElement(By.cssSelector("[data-testid='totp-mode-select-stored']"))
                .isSelected())
        .isFalse();

    WebElement fidoTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']"));
    fidoTab.click();
    waitUntilUrlContains("protocol=fido2");
    waitUntilUrlContains("tab=evaluate");
    waitUntilUrlContains("mode=inline");

    WebElement fidoEvaluateTab =
        driver.findElement(By.cssSelector("[data-testid='fido2-panel-tab-evaluate']"));
    assertThat(fidoEvaluateTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement fidoModeToggle =
        driver.findElement(By.cssSelector("[data-testid='fido2-evaluate-mode-toggle']"));
    assertThat(fidoModeToggle.getAttribute("data-mode")).isEqualTo("inline");
    assertThat(
            driver
                .findElement(By.cssSelector("[data-testid='fido2-evaluate-mode-select-inline']"))
                .isSelected())
        .isTrue();
    assertThat(
            driver
                .findElement(By.cssSelector("[data-testid='fido2-evaluate-mode-select-stored']"))
                .isSelected())
        .isFalse();
  }

  @Test
  @DisplayName("Deep-link stored mode remains active across supported protocols and refresh")
  void storedModeDeepLinksPersistAcrossProtocols() {
    List<StoredModeExpectation> expectations =
        List.of(
            new StoredModeExpectation(
                "ocra",
                "/ui/console?protocol=ocra&tab=evaluate&mode=stored",
                "[data-testid='ocra-evaluate-panel']",
                "#mode-credential",
                "#mode-inline",
                null,
                "[data-mode-section='credential']"),
            new StoredModeExpectation(
                "hotp",
                "/ui/console?protocol=hotp&tab=evaluate&mode=stored",
                "[data-testid='hotp-evaluate-panel']",
                "[data-testid='hotp-mode-select-stored']",
                "[data-testid='hotp-mode-select-inline']",
                "[data-testid='hotp-mode-toggle']",
                "[data-mode-section='stored']"),
            new StoredModeExpectation(
                "totp",
                "/ui/console?protocol=totp&tab=evaluate&mode=stored",
                "[data-testid='totp-evaluate-panel']",
                "[data-testid='totp-mode-select-stored']",
                "[data-testid='totp-mode-select-inline']",
                "[data-testid='totp-mode-toggle']",
                null),
            new StoredModeExpectation(
                "fido2",
                "/ui/console?protocol=fido2&tab=evaluate&mode=stored",
                "[data-testid='fido2-evaluate-panel']",
                "[data-testid='fido2-evaluate-mode-select-stored']",
                "[data-testid='fido2-evaluate-mode-select-inline']",
                "[data-testid='fido2-evaluate-mode-toggle']",
                null));

    expectations.forEach(this::assertStoredDeepLinkPersists);
  }

  @Test
  @DisplayName("HOTP replay stored deep link keeps stored selection after refresh")
  void hotpReplayStoredDeepLinkPersistsStoredSelection() {
    driver.get(baseUrl("/ui/console?protocol=hotp&tab=replay&mode=stored"));

    waitUntilUrlContains("protocol=hotp");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");
    waitForHotpReplayStoredState();

    driver.navigate().refresh();

    waitUntilUrlContains("protocol=hotp");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");
    waitForHotpReplayStoredState();
  }

  @Test
  @DisplayName("FIDO2 replay stored deep link keeps stored selection after refresh")
  void fido2ReplayStoredDeepLinkPersistsStoredSelection() {
    driver.get(baseUrl("/ui/console?protocol=fido2&tab=replay&mode=stored"));

    waitUntilUrlContains("protocol=fido2");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");
    waitForFido2ReplayStoredState();

    driver.navigate().refresh();

    waitUntilUrlContains("protocol=fido2");
    waitUntilUrlContains("tab=replay");
    waitUntilUrlContains("mode=stored");
    waitForFido2ReplayStoredState();
  }

  @Test
  @DisplayName("OCRA inline preset hints use shared illustrative data copy")
  void ocraPresetHintMatchesRequirement() {
    driver.get(baseUrl("/ui/console"));

    String expectedHint = "Selecting a preset auto-fills the inline fields with illustrative data.";

    WebElement inlineToggle =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(ExpectedConditions.elementToBeClickable(By.id("mode-inline")));
    if (!inlineToggle.isSelected()) {
      inlineToggle.click();
    }
    WebElement inlineParameters = driver.findElement(By.cssSelector("#inline-parameters"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> inlineParameters.getAttribute("hidden") == null);

    WebElement evaluateHint = inlineParameters.findElement(By.cssSelector("div > p.hint"));
    assertThat(evaluateHint.getText().trim()).isEqualTo(expectedHint);

    WebElement replayToggle =
        driver.findElement(By.cssSelector("[data-testid='ocra-mode-select-replay']"));
    WebElement modeToggle = driver.findElement(By.cssSelector("[data-testid='ocra-mode-toggle']"));
    replayToggle.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "replay".equals(modeToggle.getAttribute("data-mode")));

    WebElement replayHint =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[data-replay-inline-preset] .hint")));
    assertThat(replayHint.getText().trim()).isEqualTo(expectedHint);
  }

  @Test
  @DisplayName("HOTP evaluate and replay tabs persist URL query parameters")
  void hotpTabsPersistQueryParameters() {
    driver.get(baseUrl("/ui/console?protocol=hotp"));

    By hotpPanelLocator = By.cssSelector("[data-protocol-panel='hotp']");
    WebElement hotpPanel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.presenceOfElementLocated(hotpPanelLocator));
    setPanelVisibilityExpectation(hotpPanel, false);

    By evaluateTabLocator = By.cssSelector("[data-testid='hotp-panel-tab-evaluate']");
    By replayTabLocator = By.cssSelector("[data-testid='hotp-panel-tab-replay']");

    WebElement evaluateTabButton =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(ExpectedConditions.presenceOfElementLocated(evaluateTabLocator));
    WebElement replayTabButton = driver.findElement(replayTabLocator);

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("protocol=hotp"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("tab=evaluate"));

    replayTabButton.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("tab=replay"));
    setPanelVisibilityExpectation(
        driver.findElement(By.cssSelector("[data-hotp-panel='replay']")), false);

    driver.navigate().refresh();

    replayTabButton =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.presenceOfElementLocated(replayTabLocator));
    evaluateTabButton = driver.findElement(evaluateTabLocator);

    new WebDriverWait(driver, Duration.ofSeconds(5))
        .until(ExpectedConditions.attributeToBe(replayTabButton, "aria-selected", "true"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("protocol=hotp"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("tab=replay"));

    evaluateTabButton.click();
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("tab=evaluate"));
    setPanelVisibilityExpectation(
        driver.findElement(By.cssSelector("[data-hotp-panel='evaluate']")), false);
  }

  @Test
  @DisplayName("HOTP deep link to replay survives refresh")
  void hotpReplayDeepLinkSurvivesRefresh() {
    driver.get(baseUrl("/ui/console?protocol=hotp&tab=replay"));

    By hotpReplayTabLocator = By.cssSelector("[data-testid='hotp-panel-tab-replay']");

    WebElement replayTab =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.presenceOfElementLocated(hotpReplayTabLocator));

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("protocol=hotp"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("tab=replay"));
    assertThat(replayTab.getAttribute("aria-selected")).isEqualTo("true");

    driver.navigate().refresh();

    replayTab =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.presenceOfElementLocated(hotpReplayTabLocator));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("protocol=hotp"));
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains("tab=replay"));
    assertThat(replayTab.getAttribute("aria-selected")).isEqualTo("true");

    WebElement replayPanel = driver.findElement(By.cssSelector("[data-hotp-panel='replay']"));
    setPanelVisibilityExpectation(replayPanel, false);
  }

  @Test
  @DisplayName("Protocol info trigger tracks active protocol state")
  void protocolInfoTriggerTracksActiveProtocolState() {
    driver.get(baseUrl("/ui/console"));

    WebElement surface = waitForProtocolInfoSurface();
    if ("true".equals(surface.getAttribute("data-open"))) {
      WebElement closeButton =
          surface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
      closeButton.click();
      waitForAttribute(surface, "data-open", "false");
    }

    WebElement trigger =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='protocol-info-trigger']")));

    assertThat(trigger.getTagName()).isEqualTo("button");
    assertThat(trigger.isDisplayed()).isTrue();
    assertThat(trigger.getAttribute("aria-label")).isEqualTo("Protocol info");
    assertThat(trigger.getAttribute("aria-haspopup")).isEqualTo("dialog");
    assertThat(trigger.getAttribute("aria-controls")).isEqualTo("protocol-info-surface");
    assertThat(trigger.getAttribute("aria-expanded")).isEqualTo("false");
    assertThat(trigger.getAttribute("data-protocol")).isEqualTo("ocra");

    List<String> protocolOrder =
        List.of(
            "ocra",
            "hotp",
            "totp",
            "emv",
            "fido2",
            "eudi-openid4vp",
            "eudi-iso-18013-5",
            "eudi-siopv2");

    for (String protocol : protocolOrder) {
      if (!"ocra".equals(protocol)) {
        WebElement tab =
            driver.findElement(By.cssSelector("[data-testid='protocol-tab-" + protocol + "']"));
        tab.click();
      }

      new WebDriverWait(driver, Duration.ofSeconds(3))
          .until(d -> protocol.equals(trigger.getAttribute("data-protocol")));

      assertThat(trigger.getAttribute("data-protocol")).isEqualTo(protocol);
    }
  }

  @Test
  @DisplayName("Protocol info surface opens via trigger, keyboard shortcut, and expands to modal")
  void protocolInfoSurfaceSupportsShortcutsAndExpansion() {
    driver.get(baseUrl("/ui/console"));

    WebElement infoTrigger =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='protocol-info-trigger']")));

    WebElement initialSurface = waitForProtocolInfoSurface();
    if ("true".equals(initialSurface.getAttribute("data-open"))) {
      WebElement closeButton =
          initialSurface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
      closeButton.click();
      waitForAttribute(initialSurface, "data-open", "false");
    }

    infoTrigger.click();

    WebElement surface = waitForProtocolInfoSurface();
    waitForAttribute(surface, "data-open", "true");

    assertThat(surface.getAttribute("data-surface-mode")).isEqualTo("drawer");
    assertThat(surface.getAttribute("role")).isEqualTo("complementary");
    assertThat(surface.getAttribute("aria-modal")).isNull();

    dispatchKeyDown("Escape", false, "Escape");
    waitForAttribute(surface, "data-open", "false");
    assertThat(infoTrigger.getAttribute("aria-expanded")).isEqualTo("false");

    dispatchKeyDown("?", true, "Slash");
    waitForAttribute(surface, "data-open", "true");

    WebElement expandButton =
        surface.findElement(By.cssSelector("[data-testid='protocol-info-expand']"));
    expandButton.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "modal".equals(surface.getAttribute("data-surface-mode")));
    assertThat(surface.getAttribute("role")).isEqualTo("dialog");
    assertThat(surface.getAttribute("aria-modal")).isEqualTo("true");

    WebElement closeButton =
        surface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
    assertThat(activeElementTestId()).isEqualTo("protocol-info-close");

    closeButton.click();

    waitForAttribute(surface, "data-open", "false");
    assertThat(activeElementTestId()).isEqualTo("protocol-info-trigger");
    assertThat(infoTrigger.getAttribute("aria-expanded")).isEqualTo("false");
  }

  @Test
  @DisplayName("Protocol info surface stays open across protocol switches and updates content")
  void protocolInfoSurfaceUpdatesAcrossProtocols() {
    driver.get(baseUrl("/ui/console"));

    WebElement infoTrigger =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='protocol-info-trigger']")));

    WebElement initialSurface = waitForProtocolInfoSurface();
    if ("true".equals(initialSurface.getAttribute("data-open"))) {
      WebElement closeButton =
          initialSurface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
      closeButton.click();
      waitForAttribute(initialSurface, "data-open", "false");
    }
    infoTrigger.click();

    WebElement surface = waitForProtocolInfoSurface();
    waitForAttribute(surface, "data-open", "true");
    assertThat(surface.getAttribute("data-active-protocol")).isEqualTo("ocra");
    assertThat(infoTrigger.getAttribute("data-protocol")).isEqualTo("ocra");

    assertThat(sectionKeys(surface))
        .containsExactly("overview", "how-it-works", "parameters", "security", "references");
    assertThat(protocolHeadingText(surface)).contains("OCRA");

    WebElement hotpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-hotp']"));
    hotpTab.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "hotp".equals(surface.getAttribute("data-active-protocol")));
    assertThat(surface.getAttribute("data-open")).isEqualTo("true");
    assertThat(protocolHeadingText(surface)).contains("HOTP");
    assertThat(infoTrigger.getAttribute("data-protocol")).isEqualTo("hotp");

    WebElement totpTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-totp']"));
    totpTab.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "totp".equals(surface.getAttribute("data-active-protocol")));
    assertThat(protocolHeadingText(surface)).contains("TOTP");
    assertThat(infoTrigger.getAttribute("data-protocol")).isEqualTo("totp");

    assertThat(
            surface
                .findElement(By.cssSelector("[data-testid='protocol-info-panel-overview']"))
                .getAttribute("data-open"))
        .isEqualTo("true");

    WebElement ocraTab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-ocra']"));
    ocraTab.click();

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "ocra".equals(surface.getAttribute("data-active-protocol")));
    assertThat(protocolHeadingText(surface)).contains("OCRA");
    assertThat(infoTrigger.getAttribute("data-protocol")).isEqualTo("ocra");
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  private List<String> queryKeys(String url) {
    String query = URI.create(url).getQuery();
    if (query == null || query.isBlank()) {
      return List.of();
    }
    return Arrays.stream(query.split("&"))
        .map(pair -> pair.split("=", 2)[0])
        .sorted()
        .collect(Collectors.toList());
  }

  private void waitUntilUrlContains(String fragment) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> d.getCurrentUrl().contains(fragment));
  }

  private WebElement waitForProtocolInfoSurface() {
    return new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='protocol-info-surface']")));
  }

  private void waitForAttribute(WebElement element, String attributeName, String expectedValue) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> expectedValue.equals(element.getAttribute(attributeName)));
  }

  private void dispatchKeyDown(String key, boolean shiftKey, String code) {
    ((JavascriptExecutor) driver)
        .executeScript(
            "document.dispatchEvent(new KeyboardEvent('keydown', {"
                + "key: arguments[0], shiftKey: arguments[1], code: arguments[2]}));",
            key,
            shiftKey,
            code);
  }

  private String activeElementTestId() {
    return (String)
        ((JavascriptExecutor) driver)
            .executeScript(
                "return document.activeElement ? document.activeElement.getAttribute('data-testid') : null;");
  }

  private List<String> sectionKeys(WebElement surface) {
    return surface
        .findElements(By.cssSelector("[data-testid='protocol-info-accordion-header']"))
        .stream()
        .map(header -> header.getAttribute("data-section-key"))
        .collect(Collectors.toList());
  }

  private String protocolHeadingText(WebElement surface) {
    return surface.findElement(By.cssSelector("[data-testid='protocol-info-title']")).getText();
  }

  private void setPanelVisibilityExpectation(WebElement panel, boolean hidden) {
    if (hidden) {
      assertThat(panel.getAttribute("hidden")).as("Expected panel to be hidden").isNotNull();
    } else {
      assertThat(panel.getAttribute("hidden")).as("Expected panel to be visible").isNull();
    }
  }

  private String computeFontSize(WebElement element) {
    Object value =
        ((JavascriptExecutor) driver)
            .executeScript("return window.getComputedStyle(arguments[0]).fontSize;", element);
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      return stringValue.trim();
    }
    throw new AssertionError("Expected font-size string but received: " + value);
  }

  private void assertStoredDeepLinkPersists(StoredModeExpectation expectation) {
    driver.get(baseUrl(expectation.query()));
    waitUntilUrlContains("protocol=" + expectation.protocol());
    waitUntilUrlContains("tab=evaluate");
    waitUntilUrlContains("mode=stored");

    waitForStoredState(expectation);

    driver.navigate().refresh();

    waitUntilUrlContains("protocol=" + expectation.protocol());
    waitUntilUrlContains("mode=stored");

    waitForStoredState(expectation);
  }

  private void waitForStoredState(StoredModeExpectation expectation) {
    WebElement panel =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(expectation.panelSelector())));
    setPanelVisibilityExpectation(panel, false);

    if (expectation.modeToggleSelector() != null) {
      WebElement modeToggle =
          new WebDriverWait(driver, Duration.ofSeconds(5))
              .until(
                  ExpectedConditions.presenceOfElementLocated(
                      By.cssSelector(expectation.modeToggleSelector())));
      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(d -> "stored".equals(modeToggle.getAttribute("data-mode")));
    }

    WebElement storedRadio =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(expectation.storedRadioSelector())));
    WebElement inlineRadio =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(expectation.inlineRadioSelector())));

    assertThat(storedRadio.isSelected())
        .as("stored radio selected for %s", expectation.protocol())
        .isTrue();
    assertThat(inlineRadio.isSelected())
        .as("inline radio cleared for %s", expectation.protocol())
        .isFalse();

    if (expectation.storedSectionSelector() != null) {
      By storedLocator = By.cssSelector(expectation.storedSectionSelector());
      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(ExpectedConditions.presenceOfElementLocated(storedLocator));
      new WebDriverWait(driver, Duration.ofSeconds(5))
          .until(d -> d.findElement(storedLocator).getAttribute("hidden") == null);
      assertThat(driver.findElement(storedLocator).getAttribute("hidden"))
          .as("stored section hidden attribute for %s", expectation.protocol())
          .isNull();
    }
  }

  private void waitForHotpReplayStoredState() {
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

    WebElement replayTab =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='hotp-panel-tab-replay']")));
    wait.until(ExpectedConditions.attributeToBe(replayTab, "aria-selected", "true"));

    WebElement modeToggle =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='hotp-replay-mode-toggle']")));
    wait.until(d -> "stored".equals(modeToggle.getAttribute("data-mode")));

    WebElement storedRadio =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='hotp-replay-mode-select-stored']")));
    WebElement inlineRadio =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='hotp-replay-mode-select-inline']")));

    assertThat(storedRadio.isSelected()).isTrue();
    assertThat(inlineRadio.isSelected()).isFalse();

    WebElement storedSection =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='hotp-replay-stored-panel']")));
    wait.until(d -> storedSection.getAttribute("hidden") == null);
    assertThat(storedSection.getAttribute("hidden")).isNull();
  }

  private void waitForFido2ReplayStoredState() {
    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

    WebElement replayTab =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='fido2-panel-tab-replay']")));
    wait.until(ExpectedConditions.attributeToBe(replayTab, "aria-selected", "true"));

    WebElement modeToggle =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='fido2-replay-mode-toggle']")));
    wait.until(d -> "stored".equals(modeToggle.getAttribute("data-mode")));

    WebElement storedRadio =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='fido2-replay-mode-select-stored']")));
    WebElement inlineRadio =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='fido2-replay-mode-select-inline']")));

    assertThat(storedRadio.isSelected()).isTrue();
    assertThat(inlineRadio.isSelected()).isFalse();

    WebElement storedSection =
        wait.until(
            ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("[data-testid='fido2-replay-stored-section']")));
    wait.until(d -> storedSection.getAttribute("hidden") == null);
    assertThat(storedSection.getAttribute("hidden")).isNull();
  }

  private static final class StoredModeExpectation {
    private final String protocol;
    private final String query;
    private final String panelSelector;
    private final String storedRadioSelector;
    private final String inlineRadioSelector;
    private final String modeToggleSelector;
    private final String storedSectionSelector;

    private StoredModeExpectation(
        String protocol,
        String query,
        String panelSelector,
        String storedRadioSelector,
        String inlineRadioSelector,
        String modeToggleSelector,
        String storedSectionSelector) {
      this.protocol = protocol;
      this.query = query;
      this.panelSelector = panelSelector;
      this.storedRadioSelector = storedRadioSelector;
      this.inlineRadioSelector = inlineRadioSelector;
      this.modeToggleSelector = modeToggleSelector;
      this.storedSectionSelector = storedSectionSelector;
    }

    private String protocol() {
      return protocol;
    }

    private String query() {
      return query;
    }

    private String panelSelector() {
      return panelSelector;
    }

    private String storedRadioSelector() {
      return storedRadioSelector;
    }

    private String inlineRadioSelector() {
      return inlineRadioSelector;
    }

    private String modeToggleSelector() {
      return modeToggleSelector;
    }

    private String storedSectionSelector() {
      return storedSectionSelector;
    }
  }
}
