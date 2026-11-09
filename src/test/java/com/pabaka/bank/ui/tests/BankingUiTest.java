package com.pabaka.bank.ui.tests;

import com.pabaka.bank.ui.framework.BaseUiTest;
import com.pabaka.bank.ui.pages.DashboardPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Epic("Web UI (Selenium)")
@Feature("Accounts & money movement")
public class BankingUiTest extends BaseUiTest {

    @Test(groups = "smoke", description = "Deposit updates the account card, total and history")
    public void depositUpdatesBalanceAndHistory() {
        DashboardPage dash = registerNewUser("Deposit User");
        String acc = dash.firstAccount();

        dash.deposit(acc, "2500");
        assertTrue(dash.toastText().startsWith("Deposited successfully"), dash.toastText());
        assertEquals(dash.waitForBalance(acc, "2500.00"), new BigDecimal("2500.00"));
        assertEquals(dash.totalBalance(), new BigDecimal("2500.00"));
        assertEquals(dash.waitForHistory(acc, 1), List.of("DEPOSIT"));
    }

    @Test(description = "Withdrawing more than the balance shows 'Insufficient funds' and keeps the balance")
    @Severity(SeverityLevel.CRITICAL)
    public void overdraftShowsError() {
        DashboardPage dash = registerNewUser("Overdraft User");
        String acc = dash.firstAccount();
        dash.deposit(acc, "100");
        dash.waitForBalance(acc, "100.00");

        dash.withdraw(acc, "100.01");
        assertEquals(dash.toastText(), "Insufficient funds");
        assertEquals(dash.toastState(), "error");
        assertEquals(dash.balanceOf(acc), new BigDecimal("100.00"));
    }

    @Test(groups = "smoke", description = "Open a current account and move money between own accounts")
    public void transferBetweenOwnAccounts() {
        DashboardPage dash = registerNewUser("Two Accounts");
        String savings = dash.firstAccount();
        String current = dash.openAccount("CURRENT");
        assertEquals(dash.accountNumbers().size(), 2);

        dash.deposit(savings, "10000");
        dash.waitForBalance(savings, "10000.00");

        dash.transfer(savings, current, "3500.50", "Monthly budget");
        assertTrue(dash.toastText().startsWith("Transfer successful"), dash.toastText());
        assertEquals(dash.waitForBalance(savings, "6499.50"), new BigDecimal("6499.50"));
        assertEquals(dash.waitForBalance(current, "3500.50"), new BigDecimal("3500.50"));
        assertEquals(dash.totalBalance(), new BigDecimal("10000.00"), "total must be unchanged by an internal transfer");
        assertEquals(dash.waitForHistory(current, 1), List.of("TRANSFER IN"));
    }

    @Test(description = "Transfer to a non-existent account shows an error")
    public void transferToUnknownAccount() {
        DashboardPage dash = registerNewUser("Unknown Dest");
        String acc = dash.firstAccount();
        dash.deposit(acc, "500");
        dash.waitForBalance(acc, "500.00");

        dash.transfer(acc, "PB0000000000", "10", "");
        assertEquals(dash.toastText(), "Destination account PB0000000000 not found");
        assertEquals(dash.balanceOf(acc), new BigDecimal("500.00"));
    }

    @Test(description = "Invalid amount text is rejected by the API and reported in the UI")
    public void nonNumericAmountShowsError() {
        DashboardPage dash = registerNewUser("Bad Amount");
        dash.deposit(dash.firstAccount(), "12abc");
        assertEquals(dash.toastText(), "Invalid request body");
        assertEquals(dash.toastState(), "error");
    }
}
