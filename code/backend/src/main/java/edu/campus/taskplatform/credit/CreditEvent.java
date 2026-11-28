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
 * 信用分待处理事件（outbox）。业务事务只负责可靠落库，独立消费者更新分值与记录；
 * 消费失败时保留事件并重试，避免信用分写入失败回滚已完成的业务操作。
 */
@Entity
@Table(name = "credit_event", uniqueConstraints = @UniqueConstraint(
		name = "uq_credit_event_source", columnNames = {"event_type", "source_id"}))
public class CreditEvent {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId;

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private CreditEventStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	protected CreditEvent() {
	}

	public CreditEvent(Long userId, CreditEventType eventType, String sourceId, int requestedDelta,
			String reason, Instant createdAt) {
		this.eventId = UUID.randomUUID();
		this.userId = userId;
		this.eventType = eventType;
		this.sourceId = sourceId;
		this.requestedDelta = requestedDelta;
		this.reason = reason;
		this.status = CreditEventStatus.PENDING;
		this.attempts = 0;
		this.createdAt = createdAt;
	}

	public void markProcessed(Instant now) {
		this.status = CreditEventStatus.PROCESSED;
		this.processedAt = now;
		this.lastError = null;
	}

	public void markFailed(String error) {
		this.status = CreditEventStatus.FAILED;
		this.attempts++;
		this.lastError = error == null ? null : error.substring(0, Math.min(1000, error.length()));
	}

	public UUID getEventId() {
		return eventId;
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

	public CreditEventStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}
