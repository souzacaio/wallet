CREATE TABLE wallets (
    id          UUID           NOT NULL,
    holder_name VARCHAR(255)   NOT NULL,
    balance     NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_wallets PRIMARY KEY (id),
    CONSTRAINT ck_wallets_balance_non_negative CHECK (balance >= 0)
);
