-- 受限用户（信用分低于 60）发布的任务进入管理员复核队列。
-- 任务状态保持原状态机，仅用独立的发布复核状态控制公开可见性。

ALTER TABLE task
    ADD COLUMN publication_review_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED';

ALTER TABLE task
    ADD CONSTRAINT ck_task_publication_review_status
        CHECK (publication_review_status IN ('NOT_REQUIRED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED'));

CREATE INDEX ix_task_publication_review
    ON task (publication_review_status, published_at DESC);
