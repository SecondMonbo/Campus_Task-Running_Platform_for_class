-- 后端A · Part A · 通知表
-- 用户收到的任务相关通知，供通知中心列表 / 未读数 / 标记已读使用。

CREATE TABLE notification (
    notification_id VARCHAR(36)  PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    content         VARCHAR(500) NOT NULL,
    related_task_id VARCHAR(36),
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notification_user ON notification (user_id, created_at DESC);
