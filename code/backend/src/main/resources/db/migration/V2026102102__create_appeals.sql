-- 已完成任务申诉业务表（后端 B：取消与申诉）
-- 说明：task_id / appellant_id / respondent_id 等列暂不建外键，待组长合并公共表结构后统一补充。

CREATE TABLE appeals (
    appeal_id       UUID PRIMARY KEY,
    task_id         UUID NOT NULL,
    appellant_id    UUID NOT NULL,
    respondent_id   UUID NOT NULL,
    reason          VARCHAR(32) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    result          VARCHAR(32),
    transfer_amount INTEGER NOT NULL DEFAULT 0,
    decision_note   VARCHAR(500),
    decided_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_appeal_task_appellant UNIQUE (task_id, appellant_id),
    CONSTRAINT ck_appeal_reason
        CHECK (reason IN ('FULFILLMENT_ISSUE', 'SETTLEMENT_ISSUE', 'OTHER')),
    CONSTRAINT ck_appeal_status
        CHECK (status IN ('PENDING', 'DECIDED')),
    CONSTRAINT ck_appeal_result
        CHECK (result IS NULL OR result IN ('SUPPORTED', 'PARTIALLY_SUPPORTED', 'REJECTED')),
    CONSTRAINT ck_appeal_amount
        CHECK (transfer_amount >= 0),
    CONSTRAINT ck_appeal_participants
        CHECK (appellant_id <> respondent_id)
);

CREATE INDEX ix_appeal_status_created ON appeals (status, created_at DESC);
CREATE INDEX ix_appeal_task ON appeals (task_id);

-- 申诉裁决产生的关联流水（最多两条：被申诉方出账、申诉方入账）。
CREATE TABLE appeal_transaction_ids (
    appeal_id      UUID NOT NULL REFERENCES appeals (appeal_id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL,
    position       INTEGER NOT NULL,
    PRIMARY KEY (appeal_id, position)
);
