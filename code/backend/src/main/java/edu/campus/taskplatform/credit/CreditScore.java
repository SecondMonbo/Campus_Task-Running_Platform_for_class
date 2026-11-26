package edu.campus.taskplatform.credit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 用户当前信用分账户，对应 credit_score 表；一人一行。
 *
 * <p>分数、低分限制状态与连续履约计数放在同一行，由悲观写锁串行化更新，
 * 避免分数与限制状态在不同表中出现不一致。</p>
 */
@Entity
@Table(name = "credit_score")
public class CreditScore {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int score;

    /** 低分限制状态：分数低于 60 时开启，恢复至 75 及以上才解除。 */
    @Column(nullable = false)
    private boolean restricted;

    /** 连续完成且期间没有负向信用事件的任务数，用于恢复至 75 分。 */
    @Column(name = "consecutive_completed_tasks", nullable = false)
    private int consecutiveCompletedTasks;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreditScore() {
    }

    public CreditScore(Long userId, int score, boolean restricted, int consecutiveCompletedTasks,
                       Instant updatedAt) {
        this.userId = userId;
        this.score = score;
        this.restricted = restricted;
        this.consecutiveCompletedTasks = consecutiveCompletedTasks;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public int getScore() {
        return score;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public int getConsecutiveCompletedTasks() {
        return consecutiveCompletedTasks;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** 信用分服务：整行更新分数、限制状态与连续完成计数。 */
    public void update(int score, boolean restricted, int consecutiveCompletedTasks, Instant updatedAt) {
        this.score = score;
        this.restricted = restricted;
        this.consecutiveCompletedTasks = consecutiveCompletedTasks;
        this.updatedAt = updatedAt;
    }
}
