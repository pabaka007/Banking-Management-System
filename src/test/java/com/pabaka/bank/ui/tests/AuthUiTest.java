package com.pabaka.bank.ui.tests;

import com.pabaka.bank.support.TestData;
import com.pabaka.bank.ui.framework.BaseUiTest;
import com.pabaka.bank.ui.pages.AuthPage;
import com.pabaka.bank.ui.pages.DashboardPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Epic("Web UI (Selenium)")
@Feature("Authentication")
public class AuthUiTest extends BaseUiTest {

    @Test(groups = "smoke", description = "New user registers and lands on a dashboard with one empty savings account")
    public void registerLandsOnDashboard() {
        DashboardPage dash = registerNewUser("Priya Sharma");

        assertEquals(dash.userName(), "Priya Sharma");
        assertEquals(dash.accountNumbers().size(), 1);
        assertTrue(dash.firstAccount().matches("PB\\d{10}"), dash.firstAccount());
        assertEquals(dash.totalBalance(), new BigDecimal("0.00"));
        assertEquals(dash.toastState(), "success");
    }

    @Test(groups = "smoke", description = "Existing user can log out and log back in")
    public void logoutAndLoginAgain() {
        String uname = TestData.username();
        AuthPage auth = openApp().register("Karan Mehta", uname, TestData.PASSWORD).logout();

        assertTrue(auth.isDisplayed());
        assertFalse(auth.isUserMenuVisible(), "user menu must be hidden after logout");
        DashboardPage dash = auth.login(uname, TestData.PASSWORD);
        assertEquals(dash.userName(), "Karan Mehta");
    }

    @Test(groups = "smoke", description = "Logged-out visitors see only the login screen (regression: header leaked)")
    public void loggedOutHeaderIsHidden() {
        AuthPage auth = openApp();
        assertTrue(auth.isDisplayed());
        assertFalse(auth.isUserMenuVisible(), "'Hi, … / Log out' must not show before login");
    }

    @Test(description = "Session survives a page reload")
    public void sessionPersistsOnReload() {
        DashboardPage dash = registerNewUser("Reload Test");
        String acc = dash.firstAccount();

        driver.navigate().refresh();
        DashboardPage reloaded = new DashboardPage(driver).waitUntilLoaded();
        assertEquals(reloaded.firstAccount(), acc);
    }

    @Test(description = "Wrong password shows an error toast and stays on the login screen")
    public void wrongPasswordShowsError() {
        String uname = TestData.username();
        openApp().register("Wrong Pass", uname, TestData.PASSWORD).logout();

        AuthPage auth = new AuthPage(driver).loginExpectingError(uname, "Wrong@9999");
        assertEquals(auth.toastText(), "Invalid username or password");
        assertEquals(auth.toastState(), "error");
        assertTrue(auth.isDisplayed());
    }

    @DataProvider
    public Object[][] invalidRegistrations() {
        return new Object[][]{
                {"Valid Name", "ab", TestData.PASSWORD, "Username must be"},
                {"Valid Name", TestData.username(), "nodigits", "Password must be"},
                {"V", TestData.username(), TestData.PASSWORD, "Full name must be"},
        };
    }

    @Test(dataProvider = "invalidRegistrations", description = "Registration form shows server-side validation errors")
    public void invalidRegistrationShowsError(String name, String username, String password, String expectedStart) {
        AuthPage auth = openApp().registerExpectingError(name, username, password);
        assertTrue(auth.toastText().startsWith(expectedStart), "Toast was: " + auth.toastText());
        assertEquals(auth.toastState(), "error");
    }
}
