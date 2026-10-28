CREATE TABLE wallet_account (
    user_id           BIGINT PRIMARY KEY,
    available_balance BIGINT NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    frozen_balance    BIGINT NOT NULL DEFAULT 0 CHECK (frozen_balance >= 0)
);

CREATE TABLE wallet_transaction (
    transaction_id UUID PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    task_id        VARCHAR(36),
    appeal_id      UUID,
    type           VARCHAR(24) NOT NULL
                   CHECK (type IN ('INCOME', 'EXPENSE', 'FINE', 'COMPENSATION', 'FREEZE', 'UNFREEZE')),
    amount         BIGINT NOT NULL CHECK (amount <> 0),
    balance_after  BIGINT NOT NULL CHECK (balance_after >= 0),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_wallet_transaction_user_created
    ON wallet_transaction (user_id, created_at DESC);
CREATE INDEX ix_wallet_transaction_task
    ON wallet_transaction (task_id);
CREATE INDEX ix_wallet_transaction_appeal
    ON wallet_transaction (appeal_id);
