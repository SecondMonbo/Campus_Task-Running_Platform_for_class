package edu.campus.taskplatform.credit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 信用分变更记录。台账与当前分值在同一事务中写入；同一 (eventType, sourceId) 只允许一条，
 * 数据库唯一约束是幂等的最后一道防线。
 */
@Entity
@Table(name = "credit_score_record", uniqueConstraints = @UniqueConstraint(
        name = "uq_credit_event", columnNames = {"event_type", "source_id"}))
public class CreditScoreRecord {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private CreditEventType eventType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "requested_delta", nullable = false)
    private int requestedDelta;

    @Column(name = "actual_delta", nullable = false)
    private int actualDelta;

    @Column(name = "score_before")
    private Integer scoreBefore;

    @Column(name = "score_after", nullable = false)
    private int scoreAfter;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditScoreRecord() {
    }

    public CreditScoreRecord(String recordId, Long userId, CreditEventType eventType, String sourceId,
                             String reason, int requestedDelta, int actualDelta, Integer scoreBefore,
                             int scoreAfter, Long adminId, Instant createdAt) {
        this.recordId = recordId;
        this.userId = userId;
        this.eventType = eventType;
        this.sourceId = sourceId;
        this.reason = reason;
        this.requestedDelta = requestedDelta;
        this.actualDelta = actualDelta;
        this.scoreBefore = scoreBefore;
        this.scoreAfter = scoreAfter;
        this.adminId = adminId;
        this.createdAt = createdAt;
    }

    public String getRecordId() {
        return recordId;
    }

    public Long getUserId() {
        return userId;
    }

    public CreditEventType getEventType() {
        return eventType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getReason() {
        return reason;
    }

    public int getRequestedDelta() {
        return requestedDelta;
    }

    public int getActualDelta() {
        return actualDelta;
    }

    public Integer getScoreBefore() {
        return scoreBefore;
    }

    public int getScoreAfter() {
        return scoreAfter;
    }

    public Long getAdminId() {
        return adminId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
