-- 取消申请业务表（后端 B：取消与申诉）
-- 说明：task_id / requester_id 等列暂不建外键，待组长合并公共 users、tasks 表结构后统一补充，
--       避免与后端 A、账务模块的迁移脚本产生建表顺序冲突。

CREATE TABLE cancellation_requests (
    request_id      UUID PRIMARY KEY,
    task_id         UUID NOT NULL,
    requester_id    UUID NOT NULL,
    reason          VARCHAR(200) NOT NULL,
    response_note   VARCHAR(500),
    status          VARCHAR(32) NOT NULL,
    ruling          VARCHAR(48),
    ruling_detail   VARCHAR(500),
    refund_amount   INTEGER NOT NULL DEFAULT 0,
    runner_amount   INTEGER NOT NULL DEFAULT 0,
    responded_by    UUID,
    responded_at    TIMESTAMP WITH TIME ZONE,
    arbitrated_by   UUID,
    arbitrated_at   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_cancellation_status
        CHECK (status IN ('PENDING_RESPONSE', 'PENDING_ADMIN', 'APPROVED', 'ARBITRATED')),
    CONSTRAINT ck_cancellation_ruling
        CHECK (ruling IS NULL OR ruling IN ('SUPPORT_REQUESTER_CANCEL', 'SUPPORT_CONTINUE', 'CANCEL_PROPORTIONALLY')),
    CONSTRAINT ck_cancellation_amounts
        CHECK (refund_amount >= 0 AND runner_amount >= 0)
);

-- 同一任务同时最多一条待应答或待裁决的取消申请（并发发起的最终防线）。
CREATE UNIQUE INDEX ux_cancellation_active_task
    ON cancellation_requests (task_id)
    WHERE status IN ('PENDING_RESPONSE', 'PENDING_ADMIN');

CREATE INDEX ix_cancellation_task ON cancellation_requests (task_id);
CREATE INDEX ix_cancellation_status_created ON cancellation_requests (status, created_at DESC);
