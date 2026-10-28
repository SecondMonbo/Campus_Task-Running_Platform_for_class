package edu.campus.taskplatform.dispute.cancellation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 取消申请。一条申请记录一次完整的取消争议过程：
 * 发起（PENDING_RESPONSE）→ 被申请方同意（APPROVED）或拒绝（PENDING_ADMIN）→ 管理员裁决（ARBITRATED）。
 */
@Entity
@Table(name = "cancellation_requests")
public class CancellationRequestEntity {

	@Id
	@Column(name = "request_id", nullable = false, updatable = false)
	private UUID requestId;

	@Column(name = "task_id", nullable = false, updatable = false, length = 36)
	private String taskId;

	@Column(name = "requester_id", nullable = false, updatable = false)
	private Long requesterId;

	@Column(name = "reason", nullable = false, length = 200)
	private String reason;

	@Column(name = "response_note", length = 500)
	private String responseNote;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private CancellationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "ruling", length = 48)
	private CancellationRuling ruling;

	@Column(name = "ruling_detail", length = 500)
	private String rulingDetail;

	@Column(name = "refund_amount", nullable = false)
	private int refundAmount;

	@Column(name = "runner_amount", nullable = false)
	private int runnerAmount;

	@Column(name = "responded_by")
	private Long respondedBy;

	@Column(name = "responded_at")
	private Instant respondedAt;

	@Column(name = "arbitrated_by")
	private Long arbitratedBy;

	@Column(name = "arbitrated_at")
	private Instant arbitratedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CancellationRequestEntity() {
		// JPA
	}

	private CancellationRequestEntity(UUID requestId, String taskId, Long requesterId, String reason) {
		this.requestId = requestId;
		this.taskId = taskId;
		this.requesterId = requesterId;
		this.reason = reason;
		this.status = CancellationStatus.PENDING_RESPONSE;
		this.refundAmount = 0;
		this.runnerAmount = 0;
	}

	public static CancellationRequestEntity create(String taskId, Long requesterId, String reason) {
		return new CancellationRequestEntity(UUID.randomUUID(), taskId, requesterId, reason);
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	/**
	 * 被申请方同意取消：申请直接生效，冻结报酬全额退回发布者。
	 */
	public void approve(Long responderId, Instant now, int fullReward, String note) {
		requirePendingResponse();
		this.status = CancellationStatus.APPROVED;
		this.respondedBy = responderId;
		this.respondedAt = now;
		this.responseNote = note;
		this.refundAmount = fullReward;
		this.runnerAmount = 0;
	}

	/**
	 * 被申请方拒绝：申请转入待管理员处理，任务与账务均不变化。
	 */
	public void reject(Long responderId, Instant now, String note) {
		requirePendingResponse();
		this.status = CancellationStatus.PENDING_ADMIN;
		this.respondedBy = responderId;
		this.respondedAt = now;
		this.responseNote = note;
	}

	/**
	 * 管理员裁决。支持继续时金额必须为 0；取消类裁决由调用方校验金额拆分。
	 */
	public void arbitrate(Long adminId, Instant now, CancellationRuling ruling, String detail,
			int refundAmount, int runnerAmount) {
		if (this.status != CancellationStatus.PENDING_ADMIN) {
			throw new IllegalStateException("只有待管理员处理的取消申请可以裁决");
		}
		this.status = CancellationStatus.ARBITRATED;
		this.ruling = ruling;
		this.rulingDetail = detail;
		this.refundAmount = refundAmount;
		this.runnerAmount = runnerAmount;
		this.arbitratedBy = adminId;
		this.arbitratedAt = now;
	}

	private void requirePendingResponse() {
		if (this.status != CancellationStatus.PENDING_RESPONSE) {
			throw new IllegalStateException("只有待应答的取消申请可以应答");
		}
	}

	public UUID getRequestId() {
		return requestId;
	}

	public String getTaskId() {
		return taskId;
	}

	public Long getRequesterId() {
		return requesterId;
	}

	public String getReason() {
		return reason;
	}

	public String getResponseNote() {
		return responseNote;
	}

	public CancellationStatus getStatus() {
		return status;
	}

	public CancellationRuling getRuling() {
		return ruling;
	}

	public String getRulingDetail() {
		return rulingDetail;
	}

	public int getRefundAmount() {
		return refundAmount;
	}

	public int getRunnerAmount() {
		return runnerAmount;
	}

	public Long getRespondedBy() {
		return respondedBy;
	}

	public Instant getRespondedAt() {
		return respondedAt;
	}

	public Long getArbitratedBy() {
		return arbitratedBy;
	}

	public Instant getArbitratedAt() {
		return arbitratedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CancellationRequestEntity entity
				&& Objects.equals(requestId, entity.requestId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(requestId);
	}
}
