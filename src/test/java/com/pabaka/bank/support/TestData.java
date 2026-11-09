package com.pabaka.bank.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Generates unique, valid test data so tests never depend on each other. */
public final class TestData {

    public static final String PASSWORD = "Test@1234";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private TestData() {}

    /** e.g. qa_3f9a1c_7 — always matches the username rules (3-20 chars, a-z 0-9 _). */
    public static String username() {
        return "qa_" + UUID.randomUUID().toString().substring(0, 6) + "_" + SEQ.incrementAndGet();
    }

    public static String dbName() {
        return "test_" + UUID.randomUUID().toString().replace("-", "");
    }
}
