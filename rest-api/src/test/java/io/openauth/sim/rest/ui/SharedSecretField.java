package io.openauth.sim.rest.ui;

import java.time.Duration;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

final class SharedSecretField {

    enum Mode {
        HEX,
        BASE32
    }

    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private final WebDriver driver;
    private final By containerLocator;

    SharedSecretField(WebDriver driver, By containerLocator) {
        this.driver = driver;
        this.containerLocator = containerLocator;
    }

    WebElement container() {
        return new WebDriverWait(driver, DEFAULT_WAIT)
                .until(ExpectedConditions.presenceOfElementLocated(containerLocator));
    }

    WebElement textarea() {
        return container().findElement(By.cssSelector("[data-secret-input]"));
    }

    WebElement lengthNode() {
        return container().findElement(By.cssSelector("[data-secret-length]"));
    }

    WebElement messageNode() {
        return container().findElement(By.cssSelector("[data-secret-message]"));
    }

    String message() {
        return messageNode().getText().trim();
    }

    String messageState() {
        String state = messageNode().getAttribute("data-secret-message-state");
        return state == null ? "" : state.trim();
    }

    void switchTo(Mode mode) {
        if (currentMode() == mode) {
            return;
        }
        WebElement button = modeButton(mode);
        if (button == null) {
            throw new IllegalStateException("Unable to locate secret mode button for " + mode);
        }
        button.click();
        new WebDriverWait(driver, DEFAULT_WAIT).until(ignored -> currentMode() == mode);
    }

    Mode currentMode() {
        String modeAttr = container().getAttribute("data-secret-mode");
        if (modeAttr == null) {
            return Mode.HEX;
        }
        String normalized = modeAttr.trim().toLowerCase(Locale.ROOT);
        if ("base32".equals(normalized)) {
            return Mode.BASE32;
        }
        return Mode.HEX;
    }

    void setSecret(CharSequence value) {
        WebElement input = textarea();
        input.clear();
        if (value != null && value.length() > 0) {
            input.sendKeys(value);
        }
    }

    void waitUntilValueEquals(String expected) {
        new WebDriverWait(driver, DEFAULT_WAIT)
                .until(ignored -> expected.equals(textarea().getAttribute("value")));
    }

    String value() {
        return textarea().getAttribute("value");
    }

    String submissionName() {
        String attribute = textarea().getAttribute("name");
        return attribute == null ? "" : attribute;
    }

    String lengthLabel() {
        return lengthNode().getText();
    }

    String validationMessage() {
        return textarea().getDomProperty("validationMessage");
    }

    private WebElement modeButton(Mode mode) {
        String key = mode == Mode.BASE32 ? "base32" : "hex";
        return container().findElement(By.cssSelector("[data-secret-mode-button='" + key + "']"));
    }
}
