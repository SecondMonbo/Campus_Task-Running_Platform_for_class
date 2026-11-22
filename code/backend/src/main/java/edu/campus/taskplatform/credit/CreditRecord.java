package edu.campus.taskplatform.credit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 信用分变更记录。记录只追加、不覆盖；初始化记录允许变更前分值为空。
 */
@Entity
@Table(name = "credit_record", uniqueConstraints = @UniqueConstraint(
		name = "uq_credit_record_source", columnNames = {"event_type", "source_id"}))
public class CreditRecord {

	@Id
	@Column(name = "record_id", nullable = false, updatable = false)
	private UUID recordId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, updatable = false, length = 40)
	private CreditEventType eventType;

	@Column(name = "source_id", nullable = false, updatable = false, length = 100)
	private String sourceId;

	@Column(nullable = false, updatable = false, length = 500)
	private String reason;

	@Column(name = "requested_delta", nullable = false, updatable = false)
	private int requestedDelta;

	@Column(name = "actual_delta", nullable = false, updatable = false)
	private int actualDelta;

	@Column(name = "score_before", updatable = false)
	private Integer scoreBefore;

	@Column(name = "score_after", nullable = false, updatable = false)
	private int scoreAfter;

	@Column(name = "admin_id", updatable = false)
	private Long adminId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CreditRecord() {
	}

	public CreditRecord(Long userId, CreditEventType eventType, String sourceId, String reason,
			int requestedDelta, int actualDelta, Integer scoreBefore, int scoreAfter,
			Long adminId, Instant createdAt) {
		this.recordId = UUID.randomUUID();
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

	public UUID getRecordId() {
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
