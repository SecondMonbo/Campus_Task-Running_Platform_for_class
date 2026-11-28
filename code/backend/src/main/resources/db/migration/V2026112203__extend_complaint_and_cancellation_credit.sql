-- 投诉支持按情节扣信用分；取消争议记录明确的责任方。

ALTER TABLE complaints
    ADD COLUMN credit_adjustment INTEGER;

ALTER TABLE complaints
    ADD CONSTRAINT ck_complaints_credit_adjustment
        CHECK (credit_adjustment IS NULL OR credit_adjustment IN (-2, -5, -10));

ALTER TABLE complaint_actions
    DROP CONSTRAINT complaint_actions_action_check;

ALTER TABLE complaint_actions
    ADD CONSTRAINT complaint_actions_action_check
        CHECK (action IN ('WARNING', 'CREDIT_ADJUSTMENT', 'LOG_ONLY', 'OFFLINE_REFERRAL'));

ALTER TABLE cancellation_requests
    ADD COLUMN responsible_party VARCHAR(16);

ALTER TABLE cancellation_requests
    ADD CONSTRAINT ck_cancellation_responsible_party
        CHECK (responsible_party IS NULL OR responsible_party IN ('REQUESTER', 'RESPONDER', 'NONE'));
