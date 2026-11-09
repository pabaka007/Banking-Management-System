package com.pabaka.bank.ui.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/** Shared helpers. All locators use data-testid attributes, never styling classes. */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        this.wait.ignoring(StaleElementReferenceException.class); // the UI re-renders lists after each action
    }

    protected static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    protected WebElement visible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected void click(By by) {
        wait.until(ExpectedConditions.elementToBeClickable(by)).click();
    }

    protected void type(By by, String text) {
        WebElement el = visible(by);
        el.clear();
        if (text != null) el.sendKeys(text);
    }

    /** Hides any toast left from a previous action so the next assertion can't read stale text. */
    protected void clearToast() {
        ((JavascriptExecutor) driver).executeScript(
                "const t=document.querySelector(\"[data-testid='toast']\"); t.hidden=true; t.textContent=''; delete t.dataset.state;");
    }

    /** Waits for the toast and returns its text. */
    public String toastText() {
        By toast = testId("toast");
        wait.until(d -> {
            WebElement t = d.findElement(toast);
            return t.isDisplayed() && !t.getText().isBlank();
        });
        return driver.findElement(toast).getText();
    }

    /** "success" or "error" once a toast is showing. */
    public String toastState() {
        toastText();
        return driver.findElement(testId("toast")).getAttribute("data-state");
    }
}
