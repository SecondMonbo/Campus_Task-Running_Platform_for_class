-- Align dispute references with the task module: task IDs are UUID strings
-- and user IDs are BIGINT. Dispute-owned IDs remain UUID.
--
-- UUID user IDs cannot be mapped to the task module's numeric user IDs without
-- an authoritative identity mapping. Fail rather than silently discard or guess.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cancellation_requests)
       OR EXISTS (SELECT 1 FROM appeals)
       OR EXISTS (SELECT 1 FROM appeal_transaction_ids) THEN
        RAISE EXCEPTION USING
            MESSAGE = 'Cannot align dispute IDs because dispute data already exists',
            HINT = 'Export the records and provide an authoritative UUID-to-BIGINT user mapping before migrating.';
    END IF;
END
$$;

ALTER TABLE cancellation_requests
    ALTER COLUMN task_id TYPE VARCHAR(36) USING task_id::text,
    ALTER COLUMN requester_id TYPE BIGINT USING NULL::BIGINT,
    ALTER COLUMN responded_by TYPE BIGINT USING NULL::BIGINT,
    ALTER COLUMN arbitrated_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE appeals
    ALTER COLUMN task_id TYPE VARCHAR(36) USING task_id::text,
    ALTER COLUMN appellant_id TYPE BIGINT USING NULL::BIGINT,
    ALTER COLUMN respondent_id TYPE BIGINT USING NULL::BIGINT,
    ALTER COLUMN decided_by TYPE BIGINT USING NULL::BIGINT;
