-- Accounts table
-- version column is managed by Hibernate @Version (optimistic locking).
-- A concurrent UPDATE with a stale version will fail with 0 rows affected,
-- which Hibernate surfaces as OptimisticLockException.

CREATE TABLE accounts (
    id          UUID        NOT NULL,
    owner_id    VARCHAR(100) NOT NULL,
    balance     NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    currency    CHAR(3)     NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);
