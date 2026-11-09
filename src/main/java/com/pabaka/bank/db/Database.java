package com.pabaka.bank.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Thin JDBC connection factory. Uses MySQL when DB_URL points at it,
 * otherwise an H2 database running in MySQL compatibility mode.
 */
public final class Database {

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Reads DB_URL / DB_USER / DB_PASSWORD, falling back to a local H2 file database. */
    public static Database fromEnvironment() {
        String url = env("DB_URL", "jdbc:h2:file:./data/bank;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        return new Database(url, env("DB_USER", "sa"), env("DB_PASSWORD", ""));
    }

    /** A fresh, isolated in-memory database — used by the tests. */
    public static Database inMemory(String name) {
        return new Database("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public String url() {
        return url;
    }

    /** Creates the tables if they do not exist yet. */
    public Database migrate() {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql not found on classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read schema.sql", e);
        }
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String stmt : sql.split(";")) {
                String clean = stmt.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b).strip();
                if (!clean.isEmpty()) st.execute(clean);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Schema migration failed: " + e.getMessage(), e);
        }
        return this;
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
