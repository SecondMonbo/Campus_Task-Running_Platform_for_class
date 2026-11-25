-- 后端A · 信用分模块
-- 当前分值表与变更记录表；分值 0-100，新用户及存量用户初始化为 80 分。
-- 变更记录以 (event_type, source_id) 唯一约束保证同一业务事件只记分一次（幂等）。

CREATE TABLE credit_score (
    user_id    BIGINT      PRIMARY KEY REFERENCES app_user (user_id),
    score      INTEGER     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_credit_score_range CHECK (score BETWEEN 0 AND 100)
);

CREATE TABLE credit_score_record (
    record_id       VARCHAR(36)  PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (user_id),
    event_type      VARCHAR(32)  NOT NULL,
    source_id       VARCHAR(64),
    reason          VARCHAR(200) NOT NULL,
    requested_delta INTEGER      NOT NULL,
    actual_delta    INTEGER      NOT NULL,
    score_before    INTEGER,
    score_after     INTEGER      NOT NULL,
    admin_id        BIGINT       REFERENCES app_user (user_id),
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_credit_record_before CHECK (score_before IS NULL OR score_before BETWEEN 0 AND 100),
    CONSTRAINT ck_credit_record_after CHECK (score_after BETWEEN 0 AND 100),
    CONSTRAINT uq_credit_event UNIQUE (event_type, source_id)
);

CREATE INDEX idx_credit_record_user ON credit_score_record (user_id, created_at DESC, record_id DESC);

-- 存量用户统一初始化为 80 分
INSERT INTO credit_score (user_id, score, updated_at)
SELECT user_id, 80, now() FROM app_user
ON CONFLICT (user_id) DO NOTHING;

-- 初始化基线记录：变更前分数为空、变动值 0、变更后 80
INSERT INTO credit_score_record
    (record_id, user_id, event_type, source_id, reason, requested_delta, actual_delta, score_before, score_after, created_at)
SELECT gen_random_uuid()::text, user_id, 'INITIALIZATION', 'user:' || user_id,
       '信用分机制启用，初始化为 80 分', 0, 0, NULL, 80, now()
FROM app_user
ON CONFLICT (event_type, source_id) DO NOTHING;
