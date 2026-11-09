package com.pabaka.bank.ui.framework;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

/**
 * One WebDriver per thread so the suite can run in parallel.
 * Selenium Manager downloads the matching chromedriver automatically.
 * Run with -Dheadless=false to watch the browser.
 */
public final class DriverFactory {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    // We don't use Chrome DevTools features; silence the "no matching CDP version" warning on new Chrome builds.
    // Held in static fields: java.util.logging only keeps weak references, so an unreferenced logger loses its level.
    private static final java.util.logging.Logger DEVTOOLS_LOG = quiet("org.openqa.selenium.devtools");
    private static final java.util.logging.Logger CHROMIUM_LOG = quiet("org.openqa.selenium.chromium");

    private static java.util.logging.Logger quiet(String name) {
        java.util.logging.Logger l = java.util.logging.Logger.getLogger(name);
        l.setLevel(java.util.logging.Level.SEVERE);
        return l;
    }

    private DriverFactory() {}

    public static WebDriver start() {
        ChromeOptions options = new ChromeOptions();
        if (Boolean.parseBoolean(System.getProperty("headless", "true"))) options.addArguments("--headless=new");
        options.addArguments("--window-size=1366,900", "--disable-gpu", "--no-sandbox",
                "--disable-dev-shm-usage", "--disable-search-engine-choice-screen");
        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        DRIVER.set(driver);
        return driver;
    }

    public static WebDriver get() {
        WebDriver d = DRIVER.get();
        if (d == null) throw new IllegalStateException("WebDriver not started for this thread");
        return d;
    }

    public static void quit() {
        WebDriver d = DRIVER.get();
        if (d != null) {
            d.quit();
            DRIVER.remove();
        }
    }
}
