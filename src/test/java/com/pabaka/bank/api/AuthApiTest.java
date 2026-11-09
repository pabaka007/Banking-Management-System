package com.pabaka.bank.api;

import com.pabaka.bank.support.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.filter.cookie.CookieFilter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;

@Epic("REST API")
@Feature("Authentication")
public class AuthApiTest extends BaseApiTest {

    @Test(groups = "smoke", description = "GET /api/health is public and reports UP")
    public void healthIsPublic() {
        anonymous().get("/api/health").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test(groups = "smoke", description = "POST /api/register returns 201 and never echoes the password")
    public void registerReturnsCreated() {
        String uname = TestData.username();
        anonymous()
                .body(Map.of("username", uname, "password", TestData.PASSWORD, "fullName", "Neha Das"))
                .post("/api/register")
                .then().statusCode(201)
                .body("username", equalTo(uname))
                .body("fullName", equalTo("Neha Das"))
                .body("$", not(hasKey("password")))
                .body("$", not(hasKey("passwordHash")));
    }

    @Test(description = "Duplicate username → 409 Conflict")
    public void duplicateRegisterIsConflict() {
        String uname = TestData.username();
        Map<String, String> body = Map.of("username", uname, "password", TestData.PASSWORD, "fullName", "Neha Das");
        anonymous().body(body).post("/api/register").then().statusCode(201);
        anonymous().body(body).post("/api/register").then().statusCode(409)
                .body("error", equalTo("Username '" + uname + "' is already taken"));
    }

    @DataProvider
    public Object[][] protectedEndpoints() {
        return new Object[][]{
                {"GET", "/api/me"},
                {"GET", "/api/accounts"},
                {"POST", "/api/accounts"},
                {"POST", "/api/transfer"},
                {"POST", "/api/accounts/PB0000000001/deposit"},
                {"GET", "/api/accounts/PB0000000001/transactions"},
        };
    }

    @Test(dataProvider = "protectedEndpoints", description = "Protected endpoints return 401 without a session")
    public void protectedEndpointsRequireLogin(String method, String path) {
        anonymous().body("{}").request(method, path).then().statusCode(401).body("error", equalTo("Please log in"));
    }

    @Test(description = "Login → /me works; logout ends the session")
    public void loginAndLogoutLifecycle() {
        String uname = TestData.username();
        anonymous().body(Map.of("username", uname, "password", TestData.PASSWORD, "fullName", "Life Cycle"))
                .post("/api/register").then().statusCode(201);

        CookieFilter session = new CookieFilter();
        client(session).body(Map.of("username", uname, "password", TestData.PASSWORD))
                .post("/api/login").then().statusCode(200).body("username", equalTo(uname));
        client(session).get("/api/me").then().statusCode(200).body("fullName", equalTo("Life Cycle"));

        client(session).post("/api/logout").then().statusCode(204);
        client(session).get("/api/me").then().statusCode(401);
    }

    @Test(description = "Wrong password → 401 with a generic message")
    public void wrongPasswordIsUnauthorized() {
        String uname = TestData.username();
        anonymous().body(Map.of("username", uname, "password", TestData.PASSWORD, "fullName", "Wrong Pass"))
                .post("/api/register").then().statusCode(201);
        anonymous().body(Map.of("username", uname, "password", "Nope@1234"))
                .post("/api/login").then().statusCode(401).body("error", equalTo("Invalid username or password"));
    }

    @Test(description = "Malformed JSON → 400, not a 500 stack trace")
    public void malformedJsonIsBadRequest() {
        anonymous().body("{ this is not json").post("/api/login")
                .then().statusCode(400).body("error", equalTo("Invalid request body"));
    }

    @Test(description = "Validation errors come back as 400 with a readable message")
    public void weakPasswordIsBadRequest() {
        anonymous().body(Map.of("username", TestData.username(), "password", "weak", "fullName", "Weak Pass"))
                .post("/api/register").then().statusCode(400)
                .body("error", matchesPattern("^Password must be.*"));
    }
}
