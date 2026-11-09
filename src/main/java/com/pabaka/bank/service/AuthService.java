package com.pabaka.bank.service;

import com.pabaka.bank.db.Database;
import com.pabaka.bank.model.Models.AccountType;
import com.pabaka.bank.model.Models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Optional;
import java.util.regex.Pattern;

/** Registration and login. */
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{3,20}$");
    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");

    private final Database db;
    private final AccountService accounts;

    public AuthService(Database db, AccountService accounts) {
        this.db = db;
        this.accounts = accounts;
    }

    /** Creates a user and opens their first SAVINGS account. */
    public User register(String username, String password, String fullName) {
        String uname = username == null ? "" : username.strip().toLowerCase();
        String name = fullName == null ? "" : fullName.strip();
        if (!USERNAME.matcher(uname).matches())
            throw BankException.validation("Username must be 3-20 characters: lowercase letters, digits or _");
        if (name.length() < 2 || name.length() > 80)
            throw BankException.validation("Full name must be 2-80 characters");
        if (password == null || password.length() < 8 || password.length() > 64
                || !HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find())
            throw BankException.validation("Password must be 8-64 characters with at least one letter and one digit");

        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users (username, full_name, password_hash) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uname);
            ps.setString(2, name);
            ps.setString(3, PasswordHasher.hash(password));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                User user = new User(keys.getLong(1), uname, name);
                accounts.openAccount(user.id(), AccountType.SAVINGS);
                return user;
            }
        } catch (SQLException e) {
            if (isDuplicate(e)) throw BankException.conflict("Username '" + uname + "' is already taken");
            throw new IllegalStateException(e);
        }
    }

    /** Returns the user if the credentials are valid. Same error for unknown user and wrong password. */
    public User login(String username, String password) {
        String uname = username == null ? "" : username.strip().toLowerCase();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, full_name, password_hash FROM users WHERE username = ?")) {
            ps.setString(1, uname);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && password != null && PasswordHasher.verify(password, rs.getString("password_hash"))) {
                    return new User(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        throw BankException.auth("Invalid username or password");
    }

    public Optional<User> findById(long id) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, username, full_name FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new User(rs.getLong(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean isDuplicate(SQLException e) {
        // MySQL: SQLIntegrityConstraintViolationException / 1062; H2: SQLState 23505
        return e instanceof SQLIntegrityConstraintViolationException
                || "23505".equals(e.getSQLState()) || e.getErrorCode() == 1062;
    }
}
