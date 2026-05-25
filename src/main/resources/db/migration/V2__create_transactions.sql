-- Transactions table
-- idempotency_key has a UNIQUE constraint as the last-resort safeguard
-- against duplicate transfers that somehow slip past the application check.
-- Indexes on from/to account and created_at support efficient history queries.

CREATE TABLE transactions (
    id               UUID          NOT NULL,
    idempotency_key  VARCHAR(64)   NOT NULL,
    from_account_id  UUID          NOT NULL,
    to_account_id    UUID          NOT NULL,
    amount           NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency         CHAR(3)       NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    status           VARCHAR(30)   NOT NULL,
    failure_reason   VARCHAR(1000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ,

    CONSTRAINT pk_transactions         PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_key      UNIQUE (idempotency_key),
    CONSTRAINT fk_txn_from_account     FOREIGN KEY (from_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_txn_to_account       FOREIGN KEY (to_account_id)   REFERENCES accounts (id),
    CONSTRAINT chk_txn_status          CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED',
        'COMPENSATING', 'COMPENSATED', 'NEEDS_MANUAL_REVIEW'
    )),
    CONSTRAINT chk_different_accounts  CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_txn_from_account_id ON transactions (from_account_id, created_at DESC);
CREATE INDEX idx_txn_to_account_id   ON transactions (to_account_id,   created_at DESC);
CREATE INDEX idx_txn_status          ON transactions (status) WHERE status NOT IN ('COMPLETED', 'FAILED');
