package com.pabaka.bank.web;

import com.fasterxml.jackson.core.JacksonException;
import com.pabaka.bank.model.Models.AccountType;
import com.pabaka.bank.model.Models.User;
import com.pabaka.bank.service.AccountService;
import com.pabaka.bank.service.AuthService;
import com.pabaka.bank.service.BankException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/** JSON REST API + static single-page UI. */
public final class WebServer {

    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);
    private static final String SESSION_USER = "uid";

    // Request bodies
    public record RegisterRequest(String username, String password, String fullName) {}
    public record LoginRequest(String username, String password) {}
    public record AmountRequest(BigDecimal amount) {}
    public record TransferRequest(String from, String to, BigDecimal amount, String note) {}
    public record OpenAccountRequest(String type) {}

    private WebServer() {}

    public static Javalin create(AuthService auth, AccountService accounts) {
        Javalin app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/public", Location.CLASSPATH);
            cfg.showJavalinBanner = false;
        });

        // ---- auth guard for everything except register/login
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (path.equals("/api/register") || path.equals("/api/login") || path.equals("/api/health")) return;
            if (ctx.sessionAttribute(SESSION_USER) == null) {
                ctx.status(HttpStatus.UNAUTHORIZED).json(error("Please log in"));
                ctx.skipRemainingHandlers();
            }
        });

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "UP")));

        app.post("/api/register", ctx -> {
            RegisterRequest r = ctx.bodyAsClass(RegisterRequest.class);
            User u = auth.register(r.username(), r.password(), r.fullName());
            ctx.sessionAttribute(SESSION_USER, u.id());
            ctx.status(HttpStatus.CREATED).json(u);
        });

        app.post("/api/login", ctx -> {
            LoginRequest r = ctx.bodyAsClass(LoginRequest.class);
            User u = auth.login(r.username(), r.password());
            ctx.req().getSession(true);
            ctx.req().changeSessionId(); // prevent session fixation
            ctx.sessionAttribute(SESSION_USER, u.id());
            ctx.json(u);
        });

        app.post("/api/logout", ctx -> {
            ctx.req().getSession().invalidate();
            ctx.status(HttpStatus.NO_CONTENT);
        });

        app.get("/api/me", ctx -> ctx.json(auth.findById(uid(ctx))
                .orElseThrow(() -> BankException.auth("Please log in"))));

        app.get("/api/accounts", ctx -> ctx.json(accounts.listAccounts(uid(ctx))));

        app.post("/api/accounts", ctx -> {
            OpenAccountRequest r = ctx.bodyAsClass(OpenAccountRequest.class);
            AccountType type;
            try {
                type = AccountType.valueOf(String.valueOf(r.type()).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BankException.validation("Account type must be SAVINGS or CURRENT");
            }
            ctx.status(HttpStatus.CREATED).json(accounts.openAccount(uid(ctx), type));
        });

        app.post("/api/accounts/{number}/deposit", ctx ->
                ctx.json(accounts.deposit(uid(ctx), ctx.pathParam("number"), ctx.bodyAsClass(AmountRequest.class).amount())));

        app.post("/api/accounts/{number}/withdraw", ctx ->
                ctx.json(accounts.withdraw(uid(ctx), ctx.pathParam("number"), ctx.bodyAsClass(AmountRequest.class).amount())));

        app.get("/api/accounts/{number}/transactions", ctx -> {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50);
            ctx.json(accounts.history(uid(ctx), ctx.pathParam("number"), limit));
        });

        app.post("/api/transfer", ctx -> {
            TransferRequest r = ctx.bodyAsClass(TransferRequest.class);
            ctx.json(accounts.transfer(uid(ctx), r.from(), r.to(), r.amount(), r.note()));
        });

        // ---- error mapping
        app.exception(BankException.class, (e, ctx) -> ctx.status(switch (e.kind()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case AUTH -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        }).json(error(e.getMessage())));

        app.exception(Exception.class, (e, ctx) -> {
            if (hasCause(e, JacksonException.class) || hasCause(e, NumberFormatException.class)) {
                ctx.status(HttpStatus.BAD_REQUEST).json(error("Invalid request body"));
            } else {
                LOG.error("Unhandled error on {} {}", ctx.method(), ctx.path(), e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(error("Something went wrong"));
            }
        });

        return app;
    }

    private static long uid(Context ctx) {
        Long id = ctx.sessionAttribute(SESSION_USER);
        if (id == null) throw BankException.auth("Please log in");
        return id;
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) if (type.isInstance(c)) return true;
        return false;
    }
}
