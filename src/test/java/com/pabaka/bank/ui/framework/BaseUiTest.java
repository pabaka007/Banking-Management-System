package com.pabaka.bank.ui.framework;

import com.pabaka.bank.App;
import com.pabaka.bank.db.Database;
import com.pabaka.bank.support.TestData;
import com.pabaka.bank.ui.pages.AuthPage;
import com.pabaka.bank.ui.pages.DashboardPage;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayInputStream;

/**
 * Starts the app once per class (fresh in-memory DB) and a clean browser per test.
 * On failure a screenshot and the page URL are attached to the Allure report.
 */
public abstract class BaseUiTest {

    protected App app;
    protected WebDriver driver;

    @BeforeClass(alwaysRun = true)
    public void startApp() {
        app = new App(Database.inMemory(TestData.dbName())).start(0);
    }

    @AfterClass(alwaysRun = true)
    public void stopApp() {
        if (app != null) app.stop();
    }

    @BeforeMethod(alwaysRun = true)
    public void startBrowser() {
        driver = DriverFactory.start();
    }

    @AfterMethod(alwaysRun = true)
    public void stopBrowser(ITestResult result) {
        try {
            if (!result.isSuccess() && driver != null) {
                byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Allure.addAttachment("Screenshot on failure", "image/png", new ByteArrayInputStream(png), "png");
                Allure.addAttachment("URL on failure", driver.getCurrentUrl());
            }
        } finally {
            DriverFactory.quit();
        }
    }

    protected AuthPage openApp() {
        return new AuthPage(driver).open(app.baseUrl());
    }

    /** Registers a brand-new user through the UI and returns their dashboard. */
    protected DashboardPage registerNewUser(String fullName) {
        return openApp().register(fullName, TestData.username(), TestData.PASSWORD);
    }
}
