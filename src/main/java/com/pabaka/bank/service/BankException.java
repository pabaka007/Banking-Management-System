package com.pabaka.bank.service;

/** A business-rule violation that is safe to show to the user. */
public class BankException extends RuntimeException {

    public enum Kind { VALIDATION, AUTH, NOT_FOUND, CONFLICT }

    private final Kind kind;

    public BankException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static BankException validation(String msg) { return new BankException(Kind.VALIDATION, msg); }
    public static BankException auth(String msg) { return new BankException(Kind.AUTH, msg); }
    public static BankException notFound(String msg) { return new BankException(Kind.NOT_FOUND, msg); }
    public static BankException conflict(String msg) { return new BankException(Kind.CONFLICT, msg); }
}
