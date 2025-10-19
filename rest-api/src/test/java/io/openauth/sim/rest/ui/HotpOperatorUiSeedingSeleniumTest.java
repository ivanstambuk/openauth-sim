package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.model.CredentialType;
import io.openauth.sim.core.store.CredentialStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class HotpOperatorUiSeedingSeleniumTest {

  private static final List<SeedExpectation> CANONICAL_CREDENTIALS =
      List.of(
          new SeedExpectation(
              "ui-hotp-demo",
              "ui-hotp-demo (SHA1, 6 digits, RFC 4226)",
              "3132333435363738393031323334353637383930",
              "SHA1",
              6,
              0L),
          new SeedExpectation(
              "ui-hotp-demo-sha1-8",
              "ui-hotp-demo-sha1-8 (SHA1, 8 digits)",
              "3132333435363738393031323334353637383930",
              "SHA1",
              8,
              5L),
          new SeedExpectation(
              "ui-hotp-demo-sha256",
              "ui-hotp-demo-sha256 (SHA256, 8 digits)",
              "3132333435363738393031323334353637383930",
              "SHA256",
              8,
              5L),
          new SeedExpectation(
              "ui-hotp-demo-sha256-6",
              "ui-hotp-demo-sha256-6 (SHA256, 6 digits)",
              "3132333435363738393031323334353637383930",
              "SHA256",
              6,
              5L),
          new SeedExpectation(
              "ui-hotp-demo-sha512",
              "ui-hotp-demo-sha512 (SHA512, 8 digits)",
              "3132333435363738393031323334353637383930313233343536373839303132",
              "SHA512",
              8,
              5L),
          new SeedExpectation(
              "ui-hotp-demo-sha512-6",
              "ui-hotp-demo-sha512-6 (SHA512, 6 digits)",
              "3132333435363738393031323334353637383930313233343536373839303132",
              "SHA512",
              6,
              5L));

  @TempDir static Path tempDir;
  private static Path databasePath;
  private static final int DOCUMENT_POSITION_PRECEDING = 0x02;
  private static final int DOCUMENT_POSITION_FOLLOWING = 0x04;
  private static final double MIN_SEED_MARGIN_PX = 12.0;

  @DynamicPropertySource
  static void configurePersistence(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("hotp-operator-ui-seed.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @LocalServerPort private int port;

  @Autowired private CredentialStore credentialStore;

  private HtmlUnitDriver driver;

  @BeforeEach
  void setUp() {
    credentialStore.findAll().forEach(credential -> credentialStore.delete(credential.name()));
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.getWebClient().getOptions().setFetchPolyfillEnabled(true);
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
  @DisplayName("Operator seeds canonical HOTP credentials when registry empty")
  void operatorSeedsCanonicalHotpCredentials() {
    driver.get(baseUrl("/ui/console?protocol=hotp"));

    WebElement storedToggle =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(ExpectedConditions.elementToBeClickable(By.id("hotpModeStored")));
    storedToggle.click();

    WebElement seedActions =
        new WebDriverWait(driver, Duration.ofSeconds(5))
            .until(
                ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("[data-testid='hotp-seed-actions']")));

    WebElement seedButton =
        seedActions.findElement(By.cssSelector("button[data-testid='hotp-seed-credentials']"));
    WebElement seedHint = seedActions.findElement(By.cssSelector("[data-testid='hotp-seed-hint']"));
    WebElement seedStatus =
        seedActions.findElement(By.cssSelector("[data-testid='hotp-seed-status']"));

    assertHidden(seedActions, false);
    assertHidden(seedStatus, true);
    assertThat(normalizeText(seedHint.getText())).contains("Seed sample credentials");

    // Switch to inline mode to confirm the button hides.
    driver.findElement(By.id("hotpModeInline")).click();
    waitUntil(driver1 -> isHidden(seedActions));

    // Switch back to stored mode to expose the seeding control.
    storedToggle.click();
    waitUntil(driver1 -> !isHidden(seedActions));

    double marginTop =
        Double.parseDouble(
            driver
                .executeScript(
                    "return window.getComputedStyle(arguments[0]).marginTop;", seedActions)
                .toString()
                .replace("px", ""));
    assertThat(marginTop)
        .as("Seed button should have margin above it for visual separation")
        .isGreaterThanOrEqualTo(MIN_SEED_MARGIN_PX);

    waitUntil(driver1 -> storedSelectHasOptionCount(1));
    waitUntil(driver1 -> storedSelectShowsPlaceholder());
    Select storedSelect = locateStoredSelect();
    assertThat(normalizeText(storedSelect.getFirstSelectedOption().getText()))
        .contains("No HOTP credentials available");

    int positionMask =
        ((Number)
                driver.executeScript(
                    "return arguments[0].compareDocumentPosition(arguments[1]);",
                    seedActions,
                    storedSelect.getWrappedElement()))
            .intValue();
    assertThat(positionMask & DOCUMENT_POSITION_FOLLOWING)
        .as("Seed actions should precede the stored credential selector")
        .isNotZero();
    assertThat(positionMask & DOCUMENT_POSITION_PRECEDING).isZero();

    seedButton.click();
    waitForBackgroundJavaScript();

    waitUntil(driver1 -> storedSelectHasOptionCount(CANONICAL_CREDENTIALS.size() + 1));

    storedSelect = locateStoredSelect();

    Set<String> optionLabels =
        storedSelect.getOptions().stream()
            .skip(1)
            .map(WebElement::getText)
            .map(this::normalizeText)
            .collect(Collectors.toSet());
    assertThat(optionLabels)
        .containsExactlyInAnyOrderElementsOf(
            CANONICAL_CREDENTIALS.stream().map(SeedExpectation::optionLabel).toList());

    waitUntil(driver1 -> credentialStore.findAll().size() == CANONICAL_CREDENTIALS.size());
    Map<String, Credential> persisted =
        credentialStore.findAll().stream()
            .collect(Collectors.toMap(Credential::name, credential -> credential));
    assertThat(persisted.keySet())
        .containsExactlyInAnyOrderElementsOf(
            CANONICAL_CREDENTIALS.stream().map(SeedExpectation::credentialId).toList());
    for (SeedExpectation expectation : CANONICAL_CREDENTIALS) {
      Credential credential = persisted.get(expectation.credentialId());
      assertThat(credential.type()).isEqualTo(CredentialType.OATH_HOTP);
      assertThat(credential.secret().asHex()).isEqualTo(expectation.secretHex());
      assertThat(credential.attributes().get("hotp.algorithm")).isEqualTo(expectation.algorithm());
      assertThat(credential.attributes().get("hotp.digits"))
          .isEqualTo(Integer.toString(expectation.digits()));
      assertThat(credential.attributes().get("hotp.counter"))
          .isEqualTo(Long.toString(expectation.counter()));
    }

    // Reseed to ensure the status message surfaces idempotent behaviour without adding duplicates.
    seedButton.click();
    waitForBackgroundJavaScript();

    waitUntil(driver1 -> normalizeText(seedStatus.getText()).contains("Seeded"));
    assertHidden(seedStatus, false);
    assertThat(seedStatus.getAttribute("class"))
        .contains("credential-status--warning")
        .doesNotContain("credential-status--error");

    waitUntil(driver1 -> storedSelectHasOptionCount(CANONICAL_CREDENTIALS.size() + 1));
    storedSelect = locateStoredSelect();
    waitUntil(driver1 -> credentialStore.findAll().size() == CANONICAL_CREDENTIALS.size());
    Set<String> reseedOptionLabels =
        storedSelect.getOptions().stream()
            .skip(1)
            .map(WebElement::getText)
            .map(this::normalizeText)
            .collect(Collectors.toSet());
    assertThat(reseedOptionLabels)
        .containsExactlyInAnyOrderElementsOf(
            CANONICAL_CREDENTIALS.stream().map(SeedExpectation::optionLabel).toList());
  }

  private boolean isHidden(WebElement element) {
    return element.getAttribute("hidden") != null;
  }

  private Select locateStoredSelect() {
    return new Select(driver.findElement(By.id("hotpStoredCredentialId")));
  }

  private boolean storedSelectHasOptionCount(int expectedCount) {
    try {
      return locateStoredSelect().getOptions().size() == expectedCount;
    } catch (StaleElementReferenceException exception) {
      return false;
    }
  }

  private boolean storedSelectShowsPlaceholder() {
    try {
      return normalizeText(locateStoredSelect().getFirstSelectedOption().getText())
          .contains("No HOTP credentials available");
    } catch (StaleElementReferenceException exception) {
      return false;
    }
  }

  private void assertHidden(WebElement element, boolean expectedHidden) {
    if (expectedHidden) {
      assertThat(element.getAttribute("hidden")).isNotNull();
    } else {
      assertThat(element.getAttribute("hidden")).isNull();
    }
  }

  private void waitForBackgroundJavaScript() {
    driver.getWebClient().waitForBackgroundJavaScript(Duration.ofSeconds(2).toMillis());
  }

  private void waitUntil(ExpectedCondition<?> condition) {
    new WebDriverWait(driver, Duration.ofSeconds(5)).until(condition);
  }

  private String normalizeText(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }

  private record SeedExpectation(
      String credentialId,
      String optionLabel,
      String secretHex,
      String algorithm,
      int digits,
      long counter) {
    // Marker record for canonical seeding expectations.
  }
}
