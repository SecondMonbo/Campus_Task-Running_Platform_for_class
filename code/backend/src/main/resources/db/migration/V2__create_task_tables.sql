-- 后端A · 任务模块表结构（V1）
-- 说明：余额、流水、钱包等账务表由组长（账务服务）负责，本脚本不创建。

CREATE TABLE task (
    task_id           VARCHAR(36) PRIMARY KEY,
    publisher_id      BIGINT      NOT NULL,
    runner_id         BIGINT,
    title             VARCHAR(100) NOT NULL,
    description       TEXT        NOT NULL,
    reward            BIGINT      NOT NULL CHECK (reward > 0),
    deadline          TIMESTAMPTZ NOT NULL,
    pickup_location   VARCHAR(200) NOT NULL,
    delivery_location VARCHAR(200) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    published_at      TIMESTAMPTZ NOT NULL,
    accepted_at       TIMESTAMPTZ,
    delivered_at      TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    cancel_reason     VARCHAR(200),
    version           BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_task_status    ON task (status);
CREATE INDEX idx_task_publisher ON task (publisher_id);
CREATE INDEX idx_task_runner    ON task (runner_id);

CREATE TABLE task_operation_log (
    log_id     VARCHAR(36) PRIMARY KEY,
    task_id    VARCHAR(36) NOT NULL,
    actor_id   BIGINT      NOT NULL,
    action     VARCHAR(32) NOT NULL,
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_task_log_task ON task_operation_log (task_id);

CREATE TABLE task_review (
    review_id   VARCHAR(36) PRIMARY KEY,
    task_id     VARCHAR(36) NOT NULL,
    reviewer_id BIGINT      NOT NULL,
    reviewee_id BIGINT      NOT NULL,
    score       INT         NOT NULL CHECK (score BETWEEN 1 AND 5),
    content     VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_task_review UNIQUE (task_id, reviewer_id)
);

CREATE INDEX idx_task_review_task ON task_review (task_id);
