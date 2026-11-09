package com.pabaka.bank.model;

import java.math.BigDecimal;

/** Domain records. Kept together because they are small and immutable. */
public final class Models {

    private Models() {}

    public record User(long id, String username, String fullName) {}

    public enum AccountType { SAVINGS, CURRENT }

    public record Account(long id, String accountNumber, long userId, AccountType type, BigDecimal balance) {}

    public enum TxnType { DEPOSIT, WITHDRAWAL, TRANSFER_IN, TRANSFER_OUT }

    public record Transaction(long id, String accountNumber, TxnType type, BigDecimal amount,
                              BigDecimal balanceAfter, String counterparty, String note,
                              String createdAt) {}
}
