-- 合并两套信用分实现：
--   * credit_score / credit_score_record（main）作为唯一分数与追加式台账来源；
--   * credit_event（dev-Aurora）继续作为业务事件 outbox；
--   * 低分限制状态与连续履约计数并入 credit_score，按用户行锁一起更新；
--   * app_user 上的信用分冗余列移除，避免两处分数漂移。

ALTER TABLE credit_score
    ADD COLUMN restricted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE credit_score
    ADD COLUMN consecutive_completed_tasks INTEGER NOT NULL DEFAULT 0;

-- 为尚未建立信用分账户的用户补行；优先沿用 dev-Aurora 已算出的分值。
INSERT INTO credit_score (user_id, score, updated_at, restricted, consecutive_completed_tasks)
SELECT u.user_id, u.credit_score, now(), u.credit_restricted, u.consecutive_completed_tasks
FROM app_user u
WHERE NOT EXISTS (SELECT 1 FROM credit_score c WHERE c.user_id = u.user_id);

-- 若 credit_score 仍是 main 迁移写入的默认 80，而 dev-Aurora 已算出别的分数，采用后者。
UPDATE credit_score c
SET score = u.credit_score
FROM app_user u
WHERE c.user_id = u.user_id
  AND c.score = 80
  AND u.credit_score <> 80;

UPDATE credit_score c
SET restricted = u.credit_restricted,
    consecutive_completed_tasks = u.consecutive_completed_tasks
FROM app_user u
WHERE c.user_id = u.user_id;

-- 按持久化规则纠正边界：低于 60 必须受限，达到 75 必须解除；60–74 保留原状态。
UPDATE credit_score SET restricted = TRUE WHERE score < 60;
UPDATE credit_score SET restricted = FALSE WHERE score >= 75;

-- 把 dev-Aurora 的历史记录并入 credit_score_record，事件名统一到合并后的枚举。
INSERT INTO credit_score_record
    (record_id, user_id, event_type, source_id, reason, requested_delta, actual_delta,
     score_before, score_after, admin_id, created_at)
SELECT r.record_id::text,
       r.user_id,
       CASE r.event_type
           WHEN 'TASK_COMPLETED_ON_TIME' THEN 'ON_TIME_COMPLETION'
           WHEN 'TASK_COMPLETED_IN_WINDOW' THEN 'COMPLETION_IN_GRACE'
           WHEN 'TASK_COMPLETED_LATE' THEN 'OVERTIME_DELIVERY'
           WHEN 'REVIEW_3_4_STAR' THEN 'REVIEW_NEUTRAL'
           ELSE r.event_type
       END,
       LEFT(r.source_id, 64),
       LEFT(r.reason, 200),
       r.requested_delta,
       r.actual_delta,
       r.score_before,
       r.score_after,
       r.admin_id,
       r.created_at
FROM credit_record r
ON CONFLICT (event_type, source_id) DO NOTHING;

-- credit_record 保留为历史备份表，不再由实体读写。
ALTER TABLE app_user DROP COLUMN credit_score;
ALTER TABLE app_user DROP COLUMN credit_restricted;
ALTER TABLE app_user DROP COLUMN consecutive_completed_tasks;
