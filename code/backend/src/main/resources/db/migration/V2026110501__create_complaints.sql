CREATE TABLE complaints (
    complaint_id   UUID PRIMARY KEY,
    reporter_id    BIGINT NOT NULL REFERENCES app_user (user_id),
    target_user_id BIGINT REFERENCES app_user (user_id),
    task_id        VARCHAR(36) REFERENCES task (task_id),
    category       VARCHAR(100) NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    status         VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSED')),
    decision_note  VARCHAR(1000),
    handled_by     BIGINT REFERENCES app_user (user_id),
    handled_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    CHECK (target_user_id IS NULL OR target_user_id <> reporter_id),
    CHECK ((status = 'PENDING' AND handled_by IS NULL AND handled_at IS NULL AND decision_note IS NULL)
        OR (status = 'PROCESSED' AND handled_by IS NOT NULL AND handled_at IS NOT NULL AND decision_note IS NOT NULL))
);

CREATE INDEX ix_complaints_status_created ON complaints (status, created_at DESC, complaint_id DESC);
CREATE INDEX ix_complaints_created ON complaints (created_at DESC, complaint_id DESC);

CREATE TABLE complaint_actions (
    complaint_id UUID NOT NULL REFERENCES complaints (complaint_id) ON DELETE CASCADE,
    position     INTEGER NOT NULL,
    action       VARCHAR(32) NOT NULL CHECK (action IN ('WARNING', 'LOG_ONLY', 'OFFLINE_REFERRAL')),
    PRIMARY KEY (complaint_id, position),
    UNIQUE (complaint_id, action)
);
