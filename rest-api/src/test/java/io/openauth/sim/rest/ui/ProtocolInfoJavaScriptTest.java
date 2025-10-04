package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
final class ProtocolInfoJavaScriptTest {

  @LocalServerPort private int port;

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("protocol-info-js.db");
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
  @DisplayName("ProtocolInfo API exposes schema descriptors with escaped content")
  void protocolInfoApiExposesDescriptorSchemaWithEscaping() {
    driver.get(baseUrl("/ui/console"));

    @SuppressWarnings("unchecked")
    Map<String, Object> evaluation =
        (Map<String, Object>)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return (function () {\n"
                        + "  var api = window.ProtocolInfo;\n"
                        + "  if (!api || typeof api.getDescriptor !== 'function') {\n"
                        + "    return { apiPresent: false };\n"
                        + "  }\n"
                        + "  var descriptor = api.getDescriptor('ocra');\n"
                        + "  if (!descriptor || !Array.isArray(descriptor.sections)) {\n"
                        + "    return { apiPresent: true, descriptorPresent: false };\n"
                        + "  }\n"
                        + "  var unsafe = descriptor.sections.some(function (section) {\n"
                        + "    if (!section || typeof section !== 'object') {\n"
                        + "      return true;\n"
                        + "    }\n"
                        + "    var headingUnsafe = typeof section.heading === 'string' && section.heading.indexOf('<') !== -1;\n"
                        + "    var paragraphUnsafe = Array.isArray(section.paragraphs) && section.paragraphs.some(function (text) {\n"
                        + "      return typeof text === 'string' && text.indexOf('<') !== -1;\n"
                        + "    });\n"
                        + "    return headingUnsafe || paragraphUnsafe;\n"
                        + "  });\n"
                        + "  var overview = descriptor.sections.find(function (section) {\n"
                        + "    return section && section.key === 'overview';\n"
                        + "  });\n"
                        + "  return { apiPresent: true, descriptorPresent: true, unsafeFound: unsafe, overviewDefaultOpen: !!(overview && overview.defaultOpen) };\n"
                        + "})();");

    assertThat(evaluation.get("apiPresent"))
        .as("ProtocolInfo API should be exposed globally")
        .isEqualTo(Boolean.TRUE);
    assertThat(evaluation.get("descriptorPresent"))
        .as("ProtocolInfo API should expose descriptor data")
        .isEqualTo(Boolean.TRUE);
    assertThat(evaluation.get("unsafeFound"))
        .as("ProtocolInfo descriptors should escape unsafe content")
        .isEqualTo(Boolean.FALSE);
    assertThat(evaluation.get("overviewDefaultOpen"))
        .as("Overview section should remain default-open in descriptor metadata")
        .isEqualTo(Boolean.TRUE);
  }

  @Test
  @DisplayName("ProtocolInfo persists state under versioned localStorage keys")
  void protocolInfoStatePersistsUsingVersionedKeys() {
    driver.get(baseUrl("/ui/console"));

    ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");

    WebElement trigger =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='protocol-info-trigger']")));

    WebElement surface = waitForProtocolInfoSurface();
    if ("true".equals(surface.getAttribute("data-open"))) {
      trigger.click();
      waitForAttribute(surface, "data-open", "false");
    }

    trigger.click();
    waitForAttribute(surface, "data-open", "true");

    WebElement howItWorksHeader =
        surface.findElement(
            By.cssSelector(
                "[data-testid='protocol-info-accordion-header'][data-section-key='how-it-works']"));
    howItWorksHeader.click();

    WebElement howItWorksPanel =
        surface.findElement(By.cssSelector("[data-testid='protocol-info-panel-how-it-works']"));
    waitForAttribute(howItWorksPanel, "data-open", "true");

    @SuppressWarnings("unchecked")
    Map<String, Object> storageState =
        (Map<String, Object>)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return {\n"
                        + "  seen: window.localStorage.getItem('protoInfo.v1.seen.ocra'),\n"
                        + "  surface: window.localStorage.getItem('protoInfo.v1.surface.ocra'),\n"
                        + "  panel: window.localStorage.getItem('protoInfo.v1.panel.ocra')\n"
                        + "};");

    assertThat(storageState.get("seen"))
        .as("Protocol info should record once-per-protocol auto-open state")
        .isEqualTo("true");
    assertThat(storageState.get("surface"))
        .as("Protocol info should persist last surface mode")
        .isEqualTo("drawer");
    assertThat(storageState.get("panel"))
        .as("Protocol info should remember last active accordion panel")
        .isEqualTo("how-it-works");

    ((JavascriptExecutor) driver).executeScript("window.ProtocolInfo.close();");
    waitForAttribute(surface, "data-open", "false");

    ((JavascriptExecutor) driver)
        .executeScript(
            "window.localStorage.removeItem('protoInfo.v1.seen.totp');\n"
                + "window.localStorage.removeItem('protoInfo.v1.surface.totp');\n"
                + "window.localStorage.removeItem('protoInfo.v1.panel.totp');\n"
                + "window.matchMedia = function () { return { matches: true, addListener: function () {}, removeListener: function () {} }; };\n"
                + "window.ProtocolInfo.setProtocol('totp', { autoOpen: true, updateUrl: false });");

    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(d -> "totp".equals(surface.getAttribute("data-active-protocol")));
    waitForAttribute(surface, "data-open", "true");

    String totpSeen =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript("return window.localStorage.getItem('protoInfo.v1.seen.totp');");
    assertThat(totpSeen)
        .as("Auto-open should mark TOTP protocol as seen after API-driven setProtocol")
        .isEqualTo("true");
  }

  @Test
  @DisplayName("ProtocolInfo dispatches open/close/spec-click CustomEvents")
  void protocolInfoDispatchesCustomEvents() {
    driver.get(baseUrl("/ui/console"));

    WebElement surface = waitForProtocolInfoSurface();
    if ("true".equals(surface.getAttribute("data-open"))) {
      WebElement initialCloseButton =
          surface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
      initialCloseButton.click();
      waitForAttribute(surface, "data-open", "false");
    }

    ((JavascriptExecutor) driver)
        .executeScript(
            "window.__protocolInfoEvents = [];\n"
                + "['protocolinfo:open','protocolinfo:close','protocolinfo:spec-click'].forEach(function (eventName) {\n"
                + "  document.addEventListener(eventName, function (event) {\n"
                + "    window.__protocolInfoEvents.push({ type: event.type, detail: event.detail });\n"
                + "  });\n"
                + "});");

    WebElement trigger =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='protocol-info-trigger']")));
    trigger.click();

    waitForAttribute(surface, "data-open", "true");

    ((JavascriptExecutor) driver)
        .executeScript(
            "var panel = document.querySelector(\"[data-testid='protocol-info-panel-references']\");\n"
                + "if (panel) { panel.addEventListener('click', function(event) { event.preventDefault(); }, true); }");

    ((JavascriptExecutor) driver)
        .executeScript(
            "var link = document.querySelector(\"[data-testid='protocol-info-panel-references'] a\");\n"
                + "if (link) { link.click(); }");

    WebElement closeButton =
        surface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
    closeButton.click();
    waitForAttribute(surface, "data-open", "false");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> events =
        (List<Map<String, Object>>)
            ((JavascriptExecutor) driver).executeScript("return window.__protocolInfoEvents;");

    List<String> eventTypes =
        events.stream().map(event -> (String) event.get("type")).collect(Collectors.toList());

    assertThat(eventTypes)
        .as("Protocol info should emit open, close, and spec-click events")
        .contains("protocolinfo:open", "protocolinfo:close", "protocolinfo:spec-click");

    assertThat(events)
        .filteredOn(event -> "protocolinfo:spec-click".equals(event.get("type")))
        .anySatisfy(
            event ->
                assertThat(event.get("detail"))
                    .as("Spec click event should identify active protocol")
                    .isInstanceOf(Map.class));
  }

  @Test
  @DisplayName("ProtocolInfo modal traps focus and updates accessibility state")
  void protocolInfoModalTrapsFocusAndUpdatesAccessibilityState() {
    driver.get(baseUrl("/ui/console"));

    WebElement surface = waitForProtocolInfoSurface();
    ensureSurfaceClosed(surface);

    WebElement trigger =
        new WebDriverWait(driver, Duration.ofSeconds(3))
            .until(
                ExpectedConditions.elementToBeClickable(
                    By.cssSelector("[data-testid='protocol-info-trigger']")));
    trigger.click();

    waitForAttribute(surface, "data-open", "true");

    WebElement expandButton =
        surface.findElement(By.cssSelector("[data-testid='protocol-info-expand']"));
    expandButton.click();

    waitForAttribute(surface, "data-surface-mode", "modal");
    assertThat(activeElementTestId())
        .as("Modal should focus the close action when opened")
        .isEqualTo("protocol-info-close");

    String consoleAriaHidden =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return document.querySelector('.operator-console').getAttribute('aria-hidden');");
    assertThat(consoleAriaHidden)
        .as("Underlying console should be hidden from assistive tech while modal open")
        .isEqualTo("true");

    dispatchKeyDown("Tab", false);
    assertThat(activeElementTestId())
        .as("Tab should cycle focus to the expand button within the modal")
        .isEqualTo("protocol-info-expand");

    dispatchKeyDown("Tab", true);
    assertThat(activeElementTestId())
        .as("Shift+Tab should wrap focus back to the close button")
        .isEqualTo("protocol-info-close");

    WebElement closeButton =
        surface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
    closeButton.click();
    waitForAttribute(surface, "data-open", "false");

    String restoredAriaHidden =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return document.querySelector('.operator-console').getAttribute('aria-hidden');");
    assertThat(restoredAriaHidden)
        .as("Console should regain accessibility once modal is closed")
        .isNull();
  }

  @Test
  @DisplayName("ProtocolInfo honours reduced motion preference")
  void protocolInfoHonoursReducedMotionPreference() {
    driver.get(baseUrl("/ui/console"));

    WebElement surface = waitForProtocolInfoSurface();
    ensureSurfaceClosed(surface);

    ((JavascriptExecutor) driver)
        .executeScript(
            "window.matchMedia = function (query) {\n"
                + "  if (query === '(prefers-reduced-motion: reduce)') {\n"
                + "    return { matches: true, addEventListener: function () {}, removeEventListener: function () {}, addListener: function () {}, removeListener: function () {} };\n"
                + "  }\n"
                + "  return { matches: false, addEventListener: function () {}, removeEventListener: function () {}, addListener: function () {}, removeListener: function () {} };\n"
                + "};\n"
                + "window.ProtocolInfo.mount({ refreshPreferences: true });");

    String surfaceMotion =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return document.querySelector('[data-testid=\\'protocol-info-surface\\']').getAttribute('data-motion');");
    String backdropMotion =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return document.querySelector('[data-testid=\\'protocol-info-backdrop\\']').getAttribute('data-motion');");

    assertThat(surfaceMotion)
        .as("Surface should record reduced motion preference")
        .isEqualTo("reduced");
    assertThat(backdropMotion)
        .as("Backdrop should record reduced motion preference")
        .isEqualTo("reduced");

    ((JavascriptExecutor) driver)
        .executeScript(
            "window.matchMedia = function () { return { matches: false, addEventListener: function () {}, removeEventListener: function () {}, addListener: function () {}, removeListener: function () {} }; };\n"
                + "window.ProtocolInfo.mount({ refreshPreferences: true });");

    String resetSurfaceMotion =
        (String)
            ((JavascriptExecutor) driver)
                .executeScript(
                    "return document.querySelector('[data-testid=\\'protocol-info-surface\\']').getAttribute('data-motion');");
    assertThat(resetSurfaceMotion)
        .as("Surface should clear reduced motion flag when preference is disabled")
        .isEqualTo("default");
  }

  private void ensureSurfaceClosed(WebElement surface) {
    if ("true".equals(surface.getAttribute("data-open"))) {
      WebElement closeButton =
          surface.findElement(By.cssSelector("[data-testid='protocol-info-close']"));
      closeButton.click();
      waitForAttribute(surface, "data-open", "false");
    }
  }

  private void dispatchKeyDown(String key, boolean shiftKey) {
    ((JavascriptExecutor) driver)
        .executeScript(
            "document.dispatchEvent(new KeyboardEvent('keydown', { key: arguments[0], shiftKey: arguments[1], code: arguments[0] === 'Tab' ? 'Tab' : arguments[0] }));",
            key,
            shiftKey);
  }

  private String activeElementTestId() {
    return (String)
        ((JavascriptExecutor) driver)
            .executeScript(
                "return document.activeElement ? document.activeElement.getAttribute('data-testid') : null;");
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
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
}
