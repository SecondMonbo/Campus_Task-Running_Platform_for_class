-- 信用分与低分限制。
-- 信用分与钱包虚拟币完全独立；低分限制使用持久化状态：低于 60 开启，恢复至 75 解除。

ALTER TABLE app_user
    ADD COLUMN credit_score INTEGER NOT NULL DEFAULT 80;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_credit_score CHECK (credit_score BETWEEN 0 AND 100);

ALTER TABLE app_user
    ADD COLUMN credit_restricted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_user
    ADD COLUMN consecutive_completed_tasks INTEGER NOT NULL DEFAULT 0;

CREATE TABLE credit_record (
    record_id       UUID PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    event_type      VARCHAR(40) NOT NULL,
    source_id       VARCHAR(100) NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    requested_delta INTEGER NOT NULL,
    actual_delta    INTEGER NOT NULL,
    score_before    INTEGER,
    score_after     INTEGER NOT NULL CHECK (score_after BETWEEN 0 AND 100),
    admin_id        BIGINT,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_credit_record_source UNIQUE (event_type, source_id)
);

CREATE INDEX ix_credit_record_user_created
    ON credit_record (user_id, created_at DESC, record_id DESC);

CREATE TABLE credit_event (
    event_id        UUID PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    event_type      VARCHAR(40) NOT NULL,
    source_id       VARCHAR(100) NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    requested_delta INTEGER NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    attempts        INTEGER NOT NULL DEFAULT 0,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL,
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uq_credit_event_source UNIQUE (event_type, source_id)
);

CREATE INDEX ix_credit_event_status_created ON credit_event (status, created_at);

-- 首次启用机制时，为已有用户写入 80 分基线记录（变更前分值为空，变动值为 0）。
INSERT INTO credit_record (record_id, user_id, event_type, source_id, reason,
                           requested_delta, actual_delta, score_before, score_after,
                           admin_id, created_at)
SELECT gen_random_uuid(), user_id, 'INITIALIZATION', 'user:' || user_id,
       '信用分机制启用初始化', 0, 0, NULL, 80, NULL, NOW()
FROM app_user;
