package com.pabaka.bank.api;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.filter.cookie.CookieFilter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;

@Epic("REST API")
@Feature("Accounts & money movement")
public class AccountApiTest extends BaseApiTest {

    @Test(groups = "smoke", description = "Deposit then withdraw through the API")
    public void depositAndWithdraw() {
        CookieFilter s = newLoggedInUser();
        String acc = firstAccount(s);

        client(s).body(Map.of("amount", 2500)).post("/api/accounts/" + acc + "/deposit")
                .then().statusCode(200).body("balance", equalTo(2500.00f));
        client(s).body(Map.of("amount", "499.99")).post("/api/accounts/" + acc + "/withdraw")
                .then().statusCode(200).body("balance", equalTo(2000.01f));

        client(s).get("/api/accounts/" + acc + "/transactions")
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].type", equalTo("WITHDRAWAL"))
                .body("[1].type", equalTo("DEPOSIT"));
    }

    @Test(description = "Open a CURRENT account: 201 and PB + 10 digit number")
    public void openCurrentAccount() {
        CookieFilter s = newLoggedInUser();
        client(s).body(Map.of("type", "current")).post("/api/accounts")
                .then().statusCode(201)
                .body("type", equalTo("CURRENT"))
                .body("accountNumber", matchesPattern("PB\\d{10}"));
        client(s).get("/api/accounts").then().body("$", hasSize(2));
    }

    @Test(description = "Unknown account type → 400")
    public void invalidAccountType() {
        CookieFilter s = newLoggedInUser();
        client(s).body(Map.of("type", "GOLD")).post("/api/accounts")
                .then().statusCode(400).body("error", equalTo("Account type must be SAVINGS or CURRENT"));
    }

    @DataProvider
    public Object[][] badAmounts() {
        Map<String, Object> missing = new HashMap<>();
        return new Object[][]{
                {Map.of("amount", 0), "Amount must be greater than zero"},
                {Map.of("amount", -10), "Amount must be greater than zero"},
                {Map.of("amount", "12.345"), "Amount can have at most 2 decimal places"},
                {Map.of("amount", "abc"), "Invalid request body"},
                {missing, "Amount is required"},
        };
    }

    @Test(dataProvider = "badAmounts", description = "Bad deposit amounts → 400 with the right message")
    public void badAmountsAreRejected(Map<String, Object> body, String message) {
        CookieFilter s = newLoggedInUser();
        client(s).body(body).post("/api/accounts/" + firstAccount(s) + "/deposit")
                .then().statusCode(400).body("error", equalTo(message));
    }

    @Test(groups = "smoke", description = "Transfer between two users updates both balances")
    public void transferBetweenUsers() {
        CookieFilter alice = newLoggedInUser();
        CookieFilter bob = newLoggedInUser();
        String a = firstAccount(alice);
        String b = firstAccount(bob);

        client(alice).body(Map.of("amount", 1000)).post("/api/accounts/" + a + "/deposit").then().statusCode(200);
        client(alice).body(Map.of("from", a, "to", b, "amount", 250, "note", "Dinner"))
                .post("/api/transfer").then().statusCode(200).body("balance", equalTo(750.00f));

        client(bob).get("/api/accounts").then().body("[0].balance", equalTo(250.00f));
        client(bob).get("/api/accounts/" + b + "/transactions")
                .then().body("[0].type", equalTo("TRANSFER_IN"))
                .body("[0].counterparty", equalTo(a))
                .body("[0].note", equalTo("Dinner"));
    }

    @Test(description = "Insufficient funds → 400 and no money moves")
    public void transferInsufficientFunds() {
        CookieFilter alice = newLoggedInUser();
        CookieFilter bob = newLoggedInUser();
        String a = firstAccount(alice);
        String b = firstAccount(bob);

        client(alice).body(Map.of("from", a, "to", b, "amount", 1))
                .post("/api/transfer").then().statusCode(400).body("error", equalTo("Insufficient funds"));
        client(bob).get("/api/accounts").then().body("[0].balance", equalTo(0.0f));
    }

    @Test(description = "Security: a user cannot read or spend from another user's account (IDOR)")
    @Severity(SeverityLevel.BLOCKER)
    public void cannotAccessOtherUsersAccount() {
        CookieFilter alice = newLoggedInUser();
        CookieFilter mallory = newLoggedInUser();
        String a = firstAccount(alice);
        String m = firstAccount(mallory);
        client(alice).body(Map.of("amount", 500)).post("/api/accounts/" + a + "/deposit").then().statusCode(200);

        client(mallory).get("/api/accounts/" + a + "/transactions").then().statusCode(404);
        client(mallory).body(Map.of("amount", 100)).post("/api/accounts/" + a + "/withdraw").then().statusCode(404);
        client(mallory).body(Map.of("from", a, "to", m, "amount", 100)).post("/api/transfer").then().statusCode(404);

        client(alice).get("/api/accounts").then().body("[0].balance", equalTo(500.00f));
    }
}
