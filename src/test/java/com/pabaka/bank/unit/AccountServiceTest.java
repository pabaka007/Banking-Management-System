package com.pabaka.bank.unit;

import com.pabaka.bank.db.Database;
import com.pabaka.bank.model.Models.Account;
import com.pabaka.bank.model.Models.AccountType;
import com.pabaka.bank.model.Models.Transaction;
import com.pabaka.bank.model.Models.TxnType;
import com.pabaka.bank.model.Models.User;
import com.pabaka.bank.service.AccountService;
import com.pabaka.bank.service.AuthService;
import com.pabaka.bank.service.BankException;
import com.pabaka.bank.support.TestData;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

@Epic("Service layer")
@Feature("Accounts & money movement")
public class AccountServiceTest {

    private AuthService auth;
    private AccountService accounts;

    private User alice;
    private String aliceSavings;
    private User bob;
    private String bobSavings;

    @BeforeClass
    public void setUpDb() {
        Database db = Database.inMemory(TestData.dbName()).migrate();
        accounts = new AccountService(db);
        auth = new AuthService(db, accounts);
    }

    /** Fresh users per test so every test starts from a known state. */
    @BeforeMethod
    public void setUpUsers() {
        alice = auth.register(TestData.username(), TestData.PASSWORD, "Alice");
        aliceSavings = accounts.listAccounts(alice.id()).get(0).accountNumber();
        bob = auth.register(TestData.username(), TestData.PASSWORD, "Bob");
        bobSavings = accounts.listAccounts(bob.id()).get(0).accountNumber();
    }

    private static BigDecimal money(String v) {
        return new BigDecimal(v);
    }

    // ------------------------------------------------------------ deposit / withdraw

    @Test(groups = "smoke", description = "Deposit increases balance and records a DEPOSIT transaction")
    public void depositIncreasesBalance() {
        Account after = accounts.deposit(alice.id(), aliceSavings, money("1500.50"));

        assertEquals(after.balance(), money("1500.50"));
        List<Transaction> h = accounts.history(alice.id(), aliceSavings, 10);
        assertEquals(h.size(), 1);
        assertEquals(h.get(0).type(), TxnType.DEPOSIT);
        assertEquals(h.get(0).balanceAfter(), money("1500.50"));
    }

    @Test(description = "Withdraw decreases balance")
    public void withdrawDecreasesBalance() {
        accounts.deposit(alice.id(), aliceSavings, money("1000"));
        Account after = accounts.withdraw(alice.id(), aliceSavings, money("250.25"));
        assertEquals(after.balance(), money("749.75"));
    }

    @Test(description = "Withdrawing the exact balance leaves zero (boundary)")
    public void withdrawExactBalance() {
        accounts.deposit(alice.id(), aliceSavings, money("100.00"));
        assertEquals(accounts.withdraw(alice.id(), aliceSavings, money("100.00")).balance(), money("0.00"));
    }

    @Test(description = "Withdrawing one paisa more than the balance fails and changes nothing")
    @Severity(SeverityLevel.CRITICAL)
    public void overdraftIsRejected() {
        accounts.deposit(alice.id(), aliceSavings, money("100.00"));
        BankException e = expectThrows(BankException.class, () -> accounts.withdraw(alice.id(), aliceSavings, money("100.01")));

        assertEquals(e.getMessage(), "Insufficient funds");
        assertEquals(accounts.getOwnedAccount(alice.id(), aliceSavings).balance(), money("100.00"));
        assertEquals(accounts.history(alice.id(), aliceSavings, 10).size(), 1, "failed withdrawal must not be logged");
    }

    @DataProvider
    public Object[][] invalidAmounts() {
        return new Object[][]{
                {null, "Amount is required"},
                {money("0"), "Amount must be greater than zero"},
                {money("-5"), "Amount must be greater than zero"},
                {money("10.001"), "Amount can have at most 2 decimal places"},
                {money("1000000.01"), "Amount cannot exceed 1,000,000.00 per transaction"},
        };
    }

    @Test(dataProvider = "invalidAmounts", description = "Amount validation: null, zero, negative, precision and upper limit")
    public void invalidAmountsAreRejected(BigDecimal amount, String expectedMessage) {
        BankException e = expectThrows(BankException.class, () -> accounts.deposit(alice.id(), aliceSavings, amount));
        assertEquals(e.getMessage(), expectedMessage);
    }

    @Test(description = "Exactly 1,000,000.00 is accepted (upper boundary) and 10.10 keeps its scale")
    public void amountBoundariesAccepted() {
        assertEquals(accounts.deposit(alice.id(), aliceSavings, money("1000000.00")).balance(), money("1000000.00"));
        assertEquals(accounts.deposit(alice.id(), aliceSavings, money("10.10")).balance(), money("1000010.10"));
    }

    @Test(description = "A user cannot touch someone else's account")
    @Severity(SeverityLevel.BLOCKER)
    public void cannotOperateOnAnotherUsersAccount() {
        accounts.deposit(bob.id(), bobSavings, money("500"));

        BankException e = expectThrows(BankException.class, () -> accounts.withdraw(alice.id(), bobSavings, money("10")));
        assertEquals(e.kind(), BankException.Kind.NOT_FOUND);
        expectThrows(BankException.class, () -> accounts.history(alice.id(), bobSavings, 10));
        expectThrows(BankException.class, () -> accounts.transfer(alice.id(), bobSavings, aliceSavings, money("10"), null));
        assertEquals(accounts.getOwnedAccount(bob.id(), bobSavings).balance(), money("500.00"));
    }

    // ------------------------------------------------------------------ transfers

    @Test(groups = "smoke", description = "Transfer moves money and logs both sides")
    public void transferMovesMoney() {
        accounts.deposit(alice.id(), aliceSavings, money("1000"));
        Account src = accounts.transfer(alice.id(), aliceSavings, bobSavings, money("300"), "Rent share");

        assertEquals(src.balance(), money("700.00"));
        assertEquals(accounts.getOwnedAccount(bob.id(), bobSavings).balance(), money("300.00"));

        Transaction out = accounts.history(alice.id(), aliceSavings, 1).get(0);
        Transaction in = accounts.history(bob.id(), bobSavings, 1).get(0);
        assertEquals(out.type(), TxnType.TRANSFER_OUT);
        assertEquals(out.counterparty(), bobSavings);
        assertEquals(in.type(), TxnType.TRANSFER_IN);
        assertEquals(in.counterparty(), aliceSavings);
        assertEquals(in.note(), "Rent share");
    }

    @Test(description = "Account numbers are case-insensitive in transfers")
    public void transferAcceptsLowercaseAccountNumbers() {
        accounts.deposit(alice.id(), aliceSavings, money("50"));
        accounts.transfer(alice.id(), aliceSavings.toLowerCase(), " " + bobSavings.toLowerCase() + " ", money("50"), null);
        assertEquals(accounts.getOwnedAccount(bob.id(), bobSavings).balance(), money("50.00"));
    }

    @DataProvider
    public Object[][] invalidTransfers() {
        return new Object[][]{
                {"SELF", "Cannot transfer to the same account"},
                {"PB0000000000", "Destination account PB0000000000 not found"},
                {"", "Both source and destination accounts are required"},
        };
    }

    @Test(dataProvider = "invalidTransfers", description = "Transfer negative cases leave balances untouched")
    public void invalidTransfersAreRejected(String to, String expectedMessage) {
        accounts.deposit(alice.id(), aliceSavings, money("100"));
        String dest = "SELF".equals(to) ? aliceSavings : to;
        BankException e = expectThrows(BankException.class, () -> accounts.transfer(alice.id(), aliceSavings, dest, money("10"), null));
        assertEquals(e.getMessage(), expectedMessage);
        assertEquals(accounts.getOwnedAccount(alice.id(), aliceSavings).balance(), money("100.00"));
    }

    @Test(description = "Transfer with insufficient funds is rolled back completely")
    @Severity(SeverityLevel.CRITICAL)
    public void insufficientFundsTransferIsAtomic() {
        accounts.deposit(alice.id(), aliceSavings, money("50"));
        expectThrows(BankException.class, () -> accounts.transfer(alice.id(), aliceSavings, bobSavings, money("50.01"), null));

        assertEquals(accounts.getOwnedAccount(alice.id(), aliceSavings).balance(), money("50.00"));
        assertEquals(accounts.getOwnedAccount(bob.id(), bobSavings).balance(), money("0.00"));
        assertEquals(accounts.history(bob.id(), bobSavings, 10).size(), 0);
    }

    @Test(description = "100 concurrent transfers in both directions never create or lose money")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Many threads hammer A→B and B→A at the same time. Row locks taken in id order must "
            + "prevent lost updates and deadlocks; the combined balance must stay exactly 2,000.00.")
    public void concurrentTransfersPreserveTotal() throws Exception {
        accounts.deposit(alice.id(), aliceSavings, money("1000"));
        accounts.deposit(bob.id(), bobSavings, money("1000"));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            boolean aToB = i % 2 == 0;
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    if (aToB) accounts.transfer(alice.id(), aliceSavings, bobSavings, money("7.00"), null);
                    else accounts.transfer(bob.id(), bobSavings, aliceSavings, money("3.00"), null);
                } catch (BankException ignored) {
                    // insufficient funds is acceptable under contention; inconsistency is not
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        BigDecimal a = accounts.getOwnedAccount(alice.id(), aliceSavings).balance();
        BigDecimal b = accounts.getOwnedAccount(bob.id(), bobSavings).balance();
        assertEquals(a.add(b), money("2000.00"), "money was created or destroyed: A=" + a + " B=" + b);
        assertEquals(a, money("800.00"));   // 1000 - 50*7 + 50*3
        assertEquals(b, money("1200.00"));
    }

    // ------------------------------------------------------------------ accounts

    @Test(description = "A user can open up to 5 accounts; the 6th is refused")
    public void accountLimitEnforced() {
        for (int i = 0; i < 4; i++) accounts.openAccount(alice.id(), i % 2 == 0 ? AccountType.CURRENT : AccountType.SAVINGS);
        assertEquals(accounts.listAccounts(alice.id()).size(), 5);
        BankException e = expectThrows(BankException.class, () -> accounts.openAccount(alice.id(), AccountType.CURRENT));
        assertTrue(e.getMessage().contains("at most 5"));
    }

    @Test(description = "Account numbers follow the PB + 10 digits format")
    public void accountNumberFormat() {
        Account acc = accounts.openAccount(alice.id(), AccountType.CURRENT);
        assertTrue(acc.accountNumber().matches("PB\\d{10}"), acc.accountNumber());
    }
}
