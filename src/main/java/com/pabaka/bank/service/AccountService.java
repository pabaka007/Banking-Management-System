package com.pabaka.bank.service;

import com.pabaka.bank.db.Database;
import com.pabaka.bank.model.Models.Account;
import com.pabaka.bank.model.Models.AccountType;
import com.pabaka.bank.model.Models.Transaction;
import com.pabaka.bank.model.Models.TxnType;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Accounts and money movement. Every operation that changes a balance runs in a
 * single JDBC transaction with row locks (SELECT ... FOR UPDATE), so concurrent
 * transfers can never create or lose money.
 */
public class AccountService {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    public static final int MAX_ACCOUNTS_PER_USER = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Database db;

    public AccountService(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ queries

    public List<Account> listAccounts(long userId) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, account_number, user_id, account_type, balance FROM accounts WHERE user_id = ? ORDER BY id")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Account> out = new ArrayList<>();
                while (rs.next()) out.add(mapAccount(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Account getOwnedAccount(long userId, String accountNumber) {
        try (Connection c = db.connect()) {
            return requireOwned(lockOrFind(c, accountNumber, false), userId);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Transaction> history(long userId, String accountNumber, int limit) {
        Account acc = getOwnedAccount(userId, accountNumber);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, txn_type, amount, balance_after, counterparty, note, created_at "
                             + "FROM transactions WHERE account_id = ? ORDER BY id DESC LIMIT ?")) {
            ps.setLong(1, acc.id());
            ps.setInt(2, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Transaction(rs.getLong("id"), acc.accountNumber(),
                            TxnType.valueOf(rs.getString("txn_type")), rs.getBigDecimal("amount"),
                            rs.getBigDecimal("balance_after"), rs.getString("counterparty"),
                            rs.getString("note"), rs.getTimestamp("created_at").toLocalDateTime().toString()));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- commands

    public Account openAccount(long userId, AccountType type) {
        if (type == null) throw BankException.validation("Account type is required");
        if (listAccounts(userId).size() >= MAX_ACCOUNTS_PER_USER)
            throw BankException.validation("You can have at most " + MAX_ACCOUNTS_PER_USER + " accounts");
        for (int attempt = 0; attempt < 5; attempt++) {
            String number = "PB" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
            try (Connection c = db.connect();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO accounts (account_number, user_id, account_type, balance) VALUES (?, ?, ?, 0.00)",
                         Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, number);
                ps.setLong(2, userId);
                ps.setString(3, type.name());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new Account(keys.getLong(1), number, userId, type, new BigDecimal("0.00"));
                }
            } catch (SQLException e) {
                if (!AuthService.isDuplicate(e)) throw new IllegalStateException(e);
                // account number collision: try another one
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    public Account deposit(long userId, String accountNumber, BigDecimal amount) {
        BigDecimal amt = validateAmount(amount);
        return inTransaction(c -> {
            Account acc = requireOwned(lockOrFind(c, accountNumber, true), userId);
            BigDecimal newBalance = acc.balance().add(amt);
            updateBalance(c, acc.id(), newBalance);
            insertTxn(c, acc.id(), TxnType.DEPOSIT, amt, newBalance, null, null);
            return withBalance(acc, newBalance);
        });
    }

    public Account withdraw(long userId, String accountNumber, BigDecimal amount) {
        BigDecimal amt = validateAmount(amount);
        return inTransaction(c -> {
            Account acc = requireOwned(lockOrFind(c, accountNumber, true), userId);
            if (acc.balance().compareTo(amt) < 0) throw BankException.validation("Insufficient funds");
            BigDecimal newBalance = acc.balance().subtract(amt);
            updateBalance(c, acc.id(), newBalance);
            insertTxn(c, acc.id(), TxnType.WITHDRAWAL, amt, newBalance, null, null);
            return withBalance(acc, newBalance);
        });
    }

    /** Moves money from one of the user's accounts to any account. Returns the updated source account. */
    public Account transfer(long userId, String fromNumber, String toNumber, BigDecimal amount, String note) {
        BigDecimal amt = validateAmount(amount);
        String cleanNote = note == null ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > 100) throw BankException.validation("Note must be at most 100 characters");
        if (cleanNote != null && cleanNote.isEmpty()) cleanNote = null;
        if (fromNumber == null || toNumber == null || fromNumber.isBlank() || toNumber.isBlank())
            throw BankException.validation("Both source and destination accounts are required");
        String from = fromNumber.strip().toUpperCase();
        String to = toNumber.strip().toUpperCase();
        if (from.equals(to)) throw BankException.validation("Cannot transfer to the same account");
        final String finalNote = cleanNote;

        return inTransaction(c -> {
            // Lock both rows in a fixed order (by id) to avoid deadlocks between opposite transfers.
            Account a = lockOrFind(c, from, false);
            Account b = lockOrFind(c, to, false);
            requireOwned(a, userId);
            if (b == null) throw BankException.notFound("Destination account " + to + " not found");
            Account first = a.id() < b.id() ? a : b;
            Account second = first == a ? b : a;
            Account lockedFirst = lockOrFind(c, first.accountNumber(), true);
            Account lockedSecond = lockOrFind(c, second.accountNumber(), true);
            Account src = requireOwned(lockedFirst.accountNumber().equals(from) ? lockedFirst : lockedSecond, userId);
            Account dst = src == lockedFirst ? lockedSecond : lockedFirst;

            if (src.balance().compareTo(amt) < 0) throw BankException.validation("Insufficient funds");
            BigDecimal srcBalance = src.balance().subtract(amt);
            BigDecimal dstBalance = dst.balance().add(amt);
            updateBalance(c, src.id(), srcBalance);
            updateBalance(c, dst.id(), dstBalance);
            insertTxn(c, src.id(), TxnType.TRANSFER_OUT, amt, srcBalance, dst.accountNumber(), finalNote);
            insertTxn(c, dst.id(), TxnType.TRANSFER_IN, amt, dstBalance, src.accountNumber(), finalNote);
            return withBalance(src, srcBalance);
        });
    }

    // ----------------------------------------------------------------- helpers

    static BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) throw BankException.validation("Amount is required");
        BigDecimal stripped = amount.stripTrailingZeros();
        if (amount.signum() <= 0) throw BankException.validation("Amount must be greater than zero");
        if (stripped.scale() > 2) throw BankException.validation("Amount can have at most 2 decimal places");
        if (amount.compareTo(MAX_AMOUNT) > 0) throw BankException.validation("Amount cannot exceed 1,000,000.00 per transaction");
        return amount.setScale(2);
    }

    private interface TxWork<T> { T run(Connection c) throws SQLException; }

    private <T> T inTransaction(TxWork<T> work) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Account lockOrFind(Connection c, String accountNumber, boolean lock) throws SQLException {
        if (accountNumber == null || accountNumber.isBlank()) return null;
        String sql = "SELECT id, account_number, user_id, account_type, balance FROM accounts WHERE account_number = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, accountNumber.strip().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapAccount(rs) : null;
            }
        }
    }

    private static Account requireOwned(Account acc, long userId) {
        // Same message whether it doesn't exist or belongs to someone else: don't leak account existence.
        if (acc == null || acc.userId() != userId) throw BankException.notFound("Account not found");
        return acc;
    }

    private static void updateBalance(Connection c, long accountId, BigDecimal balance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?")) {
            ps.setBigDecimal(1, balance);
            ps.setLong(2, accountId);
            ps.executeUpdate();
        }
    }

    private static void insertTxn(Connection c, long accountId, TxnType type, BigDecimal amount,
                                  BigDecimal balanceAfter, String counterparty, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (account_id, txn_type, amount, balance_after, counterparty, note) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, accountId);
            ps.setString(2, type.name());
            ps.setBigDecimal(3, amount);
            ps.setBigDecimal(4, balanceAfter);
            ps.setString(5, counterparty);
            ps.setString(6, note);
            ps.executeUpdate();
        }
    }

    private static Account mapAccount(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("account_number"), rs.getLong("user_id"),
                AccountType.valueOf(rs.getString("account_type")), rs.getBigDecimal("balance"));
    }

    private static Account withBalance(Account a, BigDecimal balance) {
        return new Account(a.id(), a.accountNumber(), a.userId(), a.type(), balance);
    }
}
