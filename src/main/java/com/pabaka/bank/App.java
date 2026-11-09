package com.pabaka.bank;

import com.pabaka.bank.db.Database;
import com.pabaka.bank.model.Models.AccountType;
import com.pabaka.bank.model.Models.User;
import com.pabaka.bank.service.AccountService;
import com.pabaka.bank.service.AuthService;
import com.pabaka.bank.service.BankException;
import com.pabaka.bank.web.WebServer;
import io.javalin.Javalin;

import java.math.BigDecimal;

/** Wires the database, services and web server together. */
public final class App {

    private final Javalin web;
    private final Database db;

    public App(Database db) {
        this(db, false);
    }

    public App(Database db, boolean seedDemoData) {
        this.db = db.migrate();
        AccountService accounts = new AccountService(db);
        AuthService auth = new AuthService(db, accounts);
        this.web = WebServer.create(auth, accounts);
        if (seedDemoData) seedDemo(auth, accounts);
    }

    /** Starts on the given port (0 = any free port) and returns this app. */
    public App start(int port) {
        web.start(port);
        return this;
    }

    public int port() {
        return web.port();
    }

    public String baseUrl() {
        return "http://localhost:" + port();
    }

    public void stop() {
        web.stop();
    }

    public Database database() {
        return db;
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        Database db = Database.fromEnvironment();
        boolean seed = Boolean.parseBoolean(System.getenv().getOrDefault("DEMO_SEED", "true"));
        App app = new App(db, seed).start(port);
        System.out.println("Banking app running at " + app.baseUrl() + "  (database: " + db.url().split(";")[0] + ")");
    }

    /** Creates a demo user (demo / Demo@1234) the first time the app runs. */
    private static void seedDemo(AuthService auth, AccountService accounts) {
        try {
            User demo = auth.register("demo", "Demo@1234", "Demo User");
            String savings = accounts.listAccounts(demo.id()).get(0).accountNumber();
            accounts.deposit(demo.id(), savings, new BigDecimal("25000.00"));
            String current = accounts.openAccount(demo.id(), AccountType.CURRENT).accountNumber();
            accounts.transfer(demo.id(), savings, current, new BigDecimal("5000.00"), "Monthly budget");
        } catch (BankException alreadySeeded) {
            // demo user exists already
        }
    }
}
