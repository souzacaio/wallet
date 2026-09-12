CREATE TABLE transactions (
    id              UUID           NOT NULL,
    wallet_id       UUID           NOT NULL,
    type            VARCHAR(10)    NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    balance_after   NUMERIC(19, 2) NOT NULL,
    idempotency_key VARCHAR(255)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id),
    CONSTRAINT ck_transactions_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_transactions_wallet_idempotency UNIQUE (wallet_id, idempotency_key)
);

CREATE INDEX ix_transactions_wallet_created_at ON transactions (wallet_id, created_at);
