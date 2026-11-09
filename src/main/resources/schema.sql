-- Works on MySQL 8 and on H2 in MySQL compatibility mode.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(20)  NOT NULL UNIQUE,
    full_name     VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(12)    NOT NULL UNIQUE,
    user_id        BIGINT         NOT NULL,
    account_type   VARCHAR(10)    NOT NULL,
    balance        DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS transactions (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT         NOT NULL,
    txn_type      VARCHAR(12)    NOT NULL,
    amount        DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    counterparty  VARCHAR(12),
    note          VARCHAR(100),
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);
