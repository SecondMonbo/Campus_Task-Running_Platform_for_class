-- 信用分变更通知使用独立类型 CREDIT，与任务通知、系统通知区分。

ALTER TABLE notification DROP CONSTRAINT ck_notification_type;

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_type CHECK (type IN ('TASK', 'SYSTEM', 'CREDIT'));
