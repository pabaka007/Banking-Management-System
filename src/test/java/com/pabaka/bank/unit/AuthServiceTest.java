package com.pabaka.bank.unit;

import com.pabaka.bank.db.Database;
import com.pabaka.bank.model.Models.User;
import com.pabaka.bank.service.AccountService;
import com.pabaka.bank.service.AuthService;
import com.pabaka.bank.service.BankException;
import com.pabaka.bank.support.TestData;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

@Epic("Service layer")
@Feature("Authentication")
public class AuthServiceTest {

    private AuthService auth;
    private AccountService accounts;

    @BeforeClass
    public void setUp() {
        Database db = Database.inMemory(TestData.dbName()).migrate();
        accounts = new AccountService(db);
        auth = new AuthService(db, accounts);
    }

    @Test(groups = "smoke", description = "Registering creates the user with a zero-balance savings account")
    public void registerCreatesUserAndSavingsAccount() {
        String uname = TestData.username();
        User u = auth.register(uname, TestData.PASSWORD, "Asha Rao");

        assertEquals(u.username(), uname);
        assertEquals(accounts.listAccounts(u.id()).size(), 1);
        assertEquals(accounts.listAccounts(u.id()).get(0).balance(), new BigDecimal("0.00"));
    }

    @Test(description = "Usernames are normalised to lowercase and trimmed")
    public void usernameIsNormalised() {
        String uname = TestData.username();
        User u = auth.register("  " + uname.toUpperCase() + " ", TestData.PASSWORD, "Asha Rao");
        assertEquals(u.username(), uname);
    }

    @Test(description = "A username cannot be registered twice")
    public void duplicateUsernameIsRejected() {
        String uname = TestData.username();
        auth.register(uname, TestData.PASSWORD, "First User");
        BankException e = expectThrows(BankException.class, () -> auth.register(uname, TestData.PASSWORD, "Second User"));
        assertEquals(e.kind(), BankException.Kind.CONFLICT);
    }

    @DataProvider
    public Object[][] invalidRegistrations() {
        return new Object[][]{
                {"ab", TestData.PASSWORD, "Valid Name", "Username"},              // too short
                {"has space", TestData.PASSWORD, "Valid Name", "Username"},       // illegal char
                {"a_very_long_username_123", TestData.PASSWORD, "Valid Name", "Username"},
                {null, TestData.PASSWORD, "Valid Name", "Username"},
                {"validuser", "short1", "Valid Name", "Password"},                // < 8 chars
                {"validuser", "onlyletters", "Valid Name", "Password"},           // no digit
                {"validuser", "12345678", "Valid Name", "Password"},              // no letter
                {"validuser", null, "Valid Name", "Password"},
                {"validuser", TestData.PASSWORD, "A", "Full name"},               // name too short
                {"validuser", TestData.PASSWORD, "   ", "Full name"},
        };
    }

    @Test(dataProvider = "invalidRegistrations", description = "Registration input validation (boundary + negative cases)")
    @Description("Each row breaks exactly one rule; the error must name the field that is wrong.")
    public void invalidRegistrationIsRejected(String username, String password, String name, String expectedField) {
        BankException e = expectThrows(BankException.class, () -> auth.register(username, password, name));
        assertEquals(e.kind(), BankException.Kind.VALIDATION);
        assertTrue(e.getMessage().startsWith(expectedField), "Unexpected message: " + e.getMessage());
    }

    @Test(groups = "smoke", description = "Login succeeds with correct credentials")
    public void loginWithValidCredentials() {
        String uname = TestData.username();
        User registered = auth.register(uname, TestData.PASSWORD, "Ravi Kumar");
        assertEquals(auth.login(uname, TestData.PASSWORD).id(), registered.id());
    }

    @Test(description = "Wrong password and unknown user give the same generic error")
    public void loginFailuresDoNotLeakWhichPartIsWrong() {
        String uname = TestData.username();
        auth.register(uname, TestData.PASSWORD, "Ravi Kumar");

        BankException wrongPass = expectThrows(BankException.class, () -> auth.login(uname, "Wrong@1234"));
        BankException unknownUser = expectThrows(BankException.class, () -> auth.login("nobody_here", TestData.PASSWORD));

        assertEquals(wrongPass.getMessage(), unknownUser.getMessage());
        assertEquals(wrongPass.kind(), BankException.Kind.AUTH);
    }

    @Test(description = "Passwords are case-sensitive")
    public void passwordIsCaseSensitive() {
        String uname = TestData.username();
        auth.register(uname, TestData.PASSWORD, "Ravi Kumar");
        assertThrows(BankException.class, () -> auth.login(uname, TestData.PASSWORD.toLowerCase()));
    }
}
