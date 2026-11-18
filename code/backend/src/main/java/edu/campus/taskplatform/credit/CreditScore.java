package edu.campus.taskplatform.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 用户当前信用分，对应 credit_score 表；一人一行。 */
@Entity
@Table(name = "credit_score")
public class CreditScore {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private int score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CreditScore() {
    }

    public CreditScore(Long userId, int score, Instant updatedAt) {
        this.userId = userId;
        this.score = score;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public int getScore() {
        return score;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(int score, Instant updatedAt) {
        this.score = score;
        this.updatedAt = updatedAt;
    }
}
