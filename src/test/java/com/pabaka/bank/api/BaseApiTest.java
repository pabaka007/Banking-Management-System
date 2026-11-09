package com.pabaka.bank.api;

import com.pabaka.bank.App;
import com.pabaka.bank.db.Database;
import com.pabaka.bank.support.TestData;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import java.util.Map;

/** Starts the real app on a random port with a private in-memory database. */
public abstract class BaseApiTest {

    protected App app;

    @BeforeClass(alwaysRun = true)
    public void startApp() {
        app = new App(Database.inMemory(TestData.dbName())).start(0);
    }

    @AfterClass(alwaysRun = true)
    public void stopApp() {
        if (app != null) app.stop();
    }

    /** Anonymous client (no session). Every request/response is attached to the Allure report. */
    protected RequestSpecification anonymous() {
        return RestAssured.given()
                .baseUri(app.baseUrl())
                .contentType(ContentType.JSON)
                .filter(new AllureRestAssured());
    }

    /** A client with its own cookie jar, i.e. its own browser session. */
    protected RequestSpecification client(CookieFilter session) {
        return anonymous().filter(session);
    }

    /** Registers a fresh user and returns a session that is logged in as them. */
    protected CookieFilter newLoggedInUser() {
        CookieFilter session = new CookieFilter();
        client(session)
                .body(Map.of("username", TestData.username(), "password", TestData.PASSWORD, "fullName", "API Tester"))
                .post("/api/register")
                .then().statusCode(201);
        return session;
    }

    protected String firstAccount(CookieFilter session) {
        return client(session).get("/api/accounts").then().statusCode(200).extract().path("[0].accountNumber");
    }
}
