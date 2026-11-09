package com.pabaka.bank.ui.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** Login / Register screen. */
public class AuthPage extends BasePage {

    private static final By VIEW = testId("auth-view");
    private static final By TAB_LOGIN = testId("tab-login");
    private static final By TAB_REGISTER = testId("tab-register");
    private static final By LOGIN_USERNAME = testId("login-username");
    private static final By LOGIN_PASSWORD = testId("login-password");
    private static final By LOGIN_SUBMIT = testId("login-submit");
    private static final By REG_NAME = testId("register-fullname");
    private static final By REG_USERNAME = testId("register-username");
    private static final By REG_PASSWORD = testId("register-password");
    private static final By REG_SUBMIT = testId("register-submit");

    public AuthPage(WebDriver driver) {
        super(driver);
    }

    @Step("Open the banking app at {baseUrl}")
    public AuthPage open(String baseUrl) {
        driver.get(baseUrl);
        visible(VIEW);
        return this;
    }

    public boolean isDisplayed() {
        return visible(VIEW).isDisplayed();
    }

    /** The "Hi, name / Log out" header must not show to logged-out visitors. */
    public boolean isUserMenuVisible() {
        return driver.findElement(testId("logout-btn")).isDisplayed();
    }

    @Step("Log in as {username}")
    public DashboardPage login(String username, String password) {
        submitLogin(username, password);
        return new DashboardPage(driver).waitUntilLoaded();
    }

    @Step("Try to log in as {username} (expecting an error)")
    public AuthPage loginExpectingError(String username, String password) {
        submitLogin(username, password);
        return this;
    }

    @Step("Register {fullName} as {username}")
    public DashboardPage register(String fullName, String username, String password) {
        submitRegister(fullName, username, password);
        return new DashboardPage(driver).waitUntilLoaded();
    }

    @Step("Try to register '{username}' (expecting an error)")
    public AuthPage registerExpectingError(String fullName, String username, String password) {
        submitRegister(fullName, username, password);
        return this;
    }

    private void submitLogin(String username, String password) {
        clearToast();
        click(TAB_LOGIN);
        type(LOGIN_USERNAME, username);
        type(LOGIN_PASSWORD, password);
        click(LOGIN_SUBMIT);
    }

    private void submitRegister(String fullName, String username, String password) {
        clearToast();
        click(TAB_REGISTER);
        type(REG_NAME, fullName);
        type(REG_USERNAME, username);
        type(REG_PASSWORD, password);
        click(REG_SUBMIT);
    }
}
