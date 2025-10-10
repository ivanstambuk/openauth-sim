package io.openauth.sim.rest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.openauth.sim.core.fido2.WebAuthnCredentialDescriptor;
import io.openauth.sim.core.fido2.WebAuthnFixtures;
import io.openauth.sim.core.fido2.WebAuthnFixtures.WebAuthnFixture;
import io.openauth.sim.core.fido2.WebAuthnSignatureAlgorithm;
import io.openauth.sim.core.model.Credential;
import io.openauth.sim.core.store.CredentialStore;
import io.openauth.sim.core.store.serialization.VersionedCredentialRecordMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
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
 * Selenium scaffolding for the FIDO2/WebAuthn operator console coverage. Tests remain red until the
 * console implements the WebAuthn panel interactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
final class Fido2OperatorUiSeleniumTest {

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final String STORED_CREDENTIAL_ID = "fido2-packed-es256";

  @TempDir static Path tempDir;
  private static Path databasePath;

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    databasePath = tempDir.resolve("fido2-operator.db");
    registry.add(
        "openauth.sim.persistence.database-path", () -> databasePath.toAbsolutePath().toString());
    registry.add("openauth.sim.persistence.enable-store", () -> "true");
  }

  @Autowired private CredentialStore credentialStore;
  @LocalServerPort private int port;

  private HtmlUnitDriver driver;

  private final io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter persistenceAdapter =
      new io.openauth.sim.core.fido2.WebAuthnCredentialPersistenceAdapter();

  @BeforeEach
  void setUp() {
    driver = new HtmlUnitDriver(true);
    driver.setJavascriptEnabled(true);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));

    credentialStore.delete(STORED_CREDENTIAL_ID);
    seedStoredCredential();
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  @DisplayName("Stored WebAuthn evaluation flow renders and submits")
  void storedEvaluationFlowRenders() {
    navigateToWebAuthnPanel();

    WebElement storedToggle = waitFor(By.cssSelector("[data-testid='fido2-mode-toggle']"));
    assertThat(storedToggle.getAttribute("data-mode")).isEqualTo("stored");

    Select credentialSelect = new Select(waitFor(By.id("fido2StoredCredentialId")));
    assertThat(credentialSelect.getOptions()).hasSizeGreaterThanOrEqualTo(2);
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement originInput = driver.findElement(By.id("fido2StoredOrigin"));
    originInput.clear();
    originInput.sendKeys("https://example.org");

    WebElement submitButton =
        driver.findElement(By.cssSelector("[data-testid='fido2-stored-evaluate-submit']"));
    submitButton.click();

    WebElement result =
        waitFor(
            By.cssSelector("[data-testid='fido2-stored-result'] [data-testid='result-status']"));
    assertThat(result.getText()).isEqualToIgnoringCase("pending");
  }

  @Test
  @DisplayName("Inline evaluation exposes sample preset button and sanitised telemetry")
  void inlineEvaluationPresetLoadsVectors() {
    navigateToWebAuthnPanel();

    WebElement inlineButton = waitFor(By.cssSelector("[data-testid='fido2-inline-mode-button']"));
    inlineButton.click();
    waitUntilAttribute(By.cssSelector("[data-testid='fido2-mode-toggle']"), "data-mode", "inline");

    WebElement loadSample = waitFor(By.cssSelector("[data-testid='fido2-inline-load-sample']"));
    loadSample.click();

    WebElement algorithmField = waitFor(By.id("fido2InlineAlgorithm"));
    assertThat(algorithmField.getAttribute("value")).isEqualTo("ES256");

    WebElement telemetryPanel = waitFor(By.cssSelector("[data-testid='fido2-inline-telemetry']"));
    assertThat(telemetryPanel.getText()).doesNotContain("challenge=").doesNotContain("signature=");
  }

  @Test
  @DisplayName("Stored replay keeps telemetry sanitised")
  void storedReplayKeepsTelemetrySanitised() {
    navigateToWebAuthnPanel();

    WebElement replayButton = waitFor(By.cssSelector("[data-testid='fido2-replay-mode-button']"));
    replayButton.click();
    waitUntilAttribute(By.cssSelector("[data-testid='fido2-mode-toggle']"), "data-mode", "replay");

    Select credentialSelect = new Select(waitFor(By.id("fido2ReplayCredentialId")));
    credentialSelect.selectByValue(STORED_CREDENTIAL_ID);

    WebElement replaySubmit =
        driver.findElement(By.cssSelector("[data-testid='fido2-replay-submit']"));
    replaySubmit.click();

    WebElement telemetry = waitFor(By.cssSelector("[data-testid='fido2-replay-telemetry']"));
    assertThat(telemetry.getText()).doesNotContain("challenge=").doesNotContain("signature=");
  }

  private void seedStoredCredential() {
    WebAuthnFixture fixture = WebAuthnFixtures.loadPackedEs256();
    WebAuthnCredentialDescriptor descriptor =
        WebAuthnCredentialDescriptor.builder()
            .name(STORED_CREDENTIAL_ID)
            .relyingPartyId(fixture.storedCredential().relyingPartyId())
            .credentialId(fixture.storedCredential().credentialId())
            .publicKeyCose(fixture.storedCredential().publicKeyCose())
            .signatureCounter(fixture.storedCredential().signatureCounter())
            .userVerificationRequired(fixture.storedCredential().userVerificationRequired())
            .algorithm(WebAuthnSignatureAlgorithm.ES256)
            .build();
    Credential credential =
        VersionedCredentialRecordMapper.toCredential(persistenceAdapter.serialize(descriptor));
    credentialStore.save(credential);
  }

  private void navigateToWebAuthnPanel() {
    driver.get(baseUrl("/ui/console?protocol=fido2"));
    waitFor(By.cssSelector("[data-testid='protocol-tab-fido2']"));

    WebElement tab = driver.findElement(By.cssSelector("[data-testid='protocol-tab-fido2']"));
    if (!"true".equals(tab.getAttribute("aria-selected"))) {
      tab.click();
    }

    waitFor(By.cssSelector("[data-protocol-panel='fido2']"));
  }

  private WebElement waitFor(By selector) {
    return new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.presenceOfElementLocated(selector));
  }

  private void waitUntilAttribute(By selector, String attribute, String expectedValue) {
    new WebDriverWait(driver, Duration.ofSeconds(3))
        .until(ExpectedConditions.attributeToBe(selector, attribute, expectedValue));
  }

  private String baseUrl(String path) {
    return "http://localhost:" + port + path;
  }
}
