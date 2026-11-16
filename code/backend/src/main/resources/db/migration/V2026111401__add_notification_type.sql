-- Part A · 通知类型
-- 区分任务进度通知（TASK）与管理员发布的系统通知（SYSTEM）。
-- 历史数据全部来自任务事件，默认按 TASK 回填。

ALTER TABLE notification
    ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'TASK';

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_type CHECK (type IN ('TASK', 'SYSTEM'));

CREATE INDEX idx_notification_user_type ON notification (user_id, type, created_at DESC);
