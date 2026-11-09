package com.pabaka.bank.ui.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

import java.math.BigDecimal;
import java.util.List;

/** Logged-in dashboard: accounts, deposit/withdraw, transfer, history. */
public class DashboardPage extends BasePage {

    private static final By VIEW = testId("dashboard");
    private static final By USER_NAME = testId("user-name");
    private static final By TOTAL = testId("total-balance");
    private static final By ACCOUNT_NUMBERS = testId("account-number");
    private static final By OPEN_TYPE = testId("open-account-type");
    private static final By OPEN_SUBMIT = testId("open-account-submit");
    private static final By MONEY_ACCOUNT = testId("money-account");
    private static final By MONEY_AMOUNT = testId("money-amount");
    private static final By DEPOSIT = testId("deposit-btn");
    private static final By WITHDRAW = testId("withdraw-btn");
    private static final By TRANSFER_FROM = testId("transfer-from");
    private static final By TRANSFER_TO = testId("transfer-to");
    private static final By TRANSFER_AMOUNT = testId("transfer-amount");
    private static final By TRANSFER_NOTE = testId("transfer-note");
    private static final By TRANSFER_SUBMIT = testId("transfer-submit");
    private static final By HISTORY_ACCOUNT = testId("history-account");
    private static final By HISTORY_TYPES = testId("history-type");
    private static final By LOGOUT = testId("logout-btn");

    public DashboardPage(WebDriver driver) {
        super(driver);
    }

    public DashboardPage waitUntilLoaded() {
        visible(VIEW);
        wait.until(d -> !d.findElements(ACCOUNT_NUMBERS).isEmpty());
        return this;
    }

    public String userName() {
        return visible(USER_NAME).getText();
    }

    public List<String> accountNumbers() {
        return driver.findElements(ACCOUNT_NUMBERS).stream().map(WebElement::getText).toList();
    }

    public String firstAccount() {
        return accountNumbers().get(0);
    }

    public BigDecimal totalBalance() {
        return parseRupees(visible(TOTAL).getText());
    }

    public BigDecimal balanceOf(String accountNumber) {
        By bal = By.cssSelector("[data-testid='account-card'][data-account='" + accountNumber + "'] [data-testid='account-balance']");
        return parseRupees(visible(bal).getText());
    }

    /** Waits (UI refreshes asynchronously) until the card shows the expected balance; returns what it shows. */
    public BigDecimal waitForBalance(String accountNumber, String expected) {
        BigDecimal want = new BigDecimal(expected);
        try {
            wait.until(d -> balanceOf(accountNumber).compareTo(want) == 0);
        } catch (TimeoutException ignored) {
            // fall through: the assertion in the test reports the real value
        }
        return balanceOf(accountNumber);
    }

    @Step("Open a {type} account")
    public String openAccount(String type) {
        int before = accountNumbers().size();
        clearToast();
        new Select(visible(OPEN_TYPE)).selectByValue(type);
        click(OPEN_SUBMIT);
        wait.until(d -> d.findElements(ACCOUNT_NUMBERS).size() == before + 1);
        List<String> all = accountNumbers();
        return all.get(all.size() - 1);
    }

    @Step("Deposit ₹{amount} into {accountNumber}")
    public DashboardPage deposit(String accountNumber, String amount) {
        fillMoneyForm(accountNumber, amount);
        click(DEPOSIT);
        return this;
    }

    @Step("Withdraw ₹{amount} from {accountNumber}")
    public DashboardPage withdraw(String accountNumber, String amount) {
        fillMoneyForm(accountNumber, amount);
        click(WITHDRAW);
        return this;
    }

    @Step("Transfer ₹{amount} from {from} to {to}")
    public DashboardPage transfer(String from, String to, String amount, String note) {
        clearToast();
        new Select(visible(TRANSFER_FROM)).selectByValue(from);
        type(TRANSFER_TO, to);
        type(TRANSFER_AMOUNT, amount);
        type(TRANSFER_NOTE, note);
        click(TRANSFER_SUBMIT);
        return this;
    }

    @Step("Read transaction history of {accountNumber}")
    public List<String> historyTypes(String accountNumber) {
        new Select(visible(HISTORY_ACCOUNT)).selectByValue(accountNumber);
        return driver.findElements(HISTORY_TYPES).stream().map(WebElement::getText).toList();
    }

    /** Waits until the history for the account has the given number of rows. */
    public List<String> waitForHistory(String accountNumber, int rows) {
        new Select(visible(HISTORY_ACCOUNT)).selectByValue(accountNumber);
        try {
            wait.until(d -> d.findElements(HISTORY_TYPES).size() == rows);
        } catch (TimeoutException ignored) {
            // assertion in the test reports the actual rows
        }
        return historyTypes(accountNumber);
    }

    @Step("Log out")
    public AuthPage logout() {
        click(LOGOUT);
        AuthPage auth = new AuthPage(driver);
        auth.isDisplayed();
        return auth;
    }

    private void fillMoneyForm(String accountNumber, String amount) {
        clearToast();
        new Select(visible(MONEY_ACCOUNT)).selectByValue(accountNumber);
        type(MONEY_AMOUNT, amount);
    }

    /** "₹1,23,456.78" → 123456.78 */
    static BigDecimal parseRupees(String text) {
        return new BigDecimal(text.replaceAll("[^0-9.\\-]", ""));
    }
}
