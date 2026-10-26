package edu.campus.taskplatform.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 任务评价（需求 3.3.4.5）：任务已完成时发布者评价跑腿员，一条任务只能评价一次。
 */
@Entity
@Table(name = "task_review")
public class TaskReview {

    @Id
    @Column(name = "review_id", length = 36)
    private String reviewId;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    @Column(nullable = false)
    private int score;

    @Column(length = 200)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskReview() {
    }

    public TaskReview(String reviewId, String taskId, Long reviewerId, Long revieweeId,
                      int score, String content, Instant createdAt) {
        this.reviewId = reviewId;
        this.taskId = taskId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.score = score;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getReviewId() {
        return reviewId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public Long getRevieweeId() {
        return revieweeId;
    }

    public int getScore() {
        return score;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
