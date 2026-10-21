package edu.campus.taskplatform.dispute.appeal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 已完成任务申诉。申诉不改变任务状态，也不修改原始结算流水；
 * 裁决产生的补偿流水通过 {@link #transactionIds} 关联。
 */
@Entity
@Table(name = "appeals")
public class AppealEntity {

	@Id
	@Column(name = "appeal_id", nullable = false, updatable = false)
	private UUID appealId;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "appellant_id", nullable = false, updatable = false)
	private UUID appellantId;

	@Column(name = "respondent_id", nullable = false, updatable = false)
	private UUID respondentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason", nullable = false, length = 32)
	private AppealReason reason;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private AppealStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "result", length = 32)
	private AppealResult result;

	@Column(name = "transfer_amount", nullable = false)
	private int transferAmount;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "appeal_transaction_ids", joinColumns = @JoinColumn(name = "appeal_id"))
	@OrderColumn(name = "position")
	@Column(name = "transaction_id", nullable = false)
	private List<UUID> transactionIds = new ArrayList<>();

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AppealEntity() {
		// JPA
	}

	private AppealEntity(UUID appealId, UUID taskId, UUID appellantId, UUID respondentId,
			AppealReason reason, String description) {
		this.appealId = appealId;
		this.taskId = taskId;
		this.appellantId = appellantId;
		this.respondentId = respondentId;
		this.reason = reason;
		this.description = description;
		this.status = AppealStatus.PENDING;
		this.transferAmount = 0;
	}

	public static AppealEntity create(UUID taskId, UUID appellantId, UUID respondentId,
			AppealReason reason, String description) {
		return new AppealEntity(UUID.randomUUID(), taskId, appellantId, respondentId, reason, description);
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
	 * 记录管理员裁决结果。驳回时金额固定为 0 且不产生流水。
	 */
	public void decide(UUID adminId, Instant now, AppealResult result, int transferAmount,
			String decisionNote, List<UUID> transactionIds) {
		if (this.status != AppealStatus.PENDING) {
			throw new IllegalStateException("申诉已裁决");
		}
		this.status = AppealStatus.DECIDED;
		this.result = result;
		this.transferAmount = transferAmount;
		this.decisionNote = decisionNote;
		this.transactionIds.clear();
		this.transactionIds.addAll(transactionIds);
		this.decidedBy = adminId;
		this.decidedAt = now;
	}

	public UUID getAppealId() {
		return appealId;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public UUID getAppellantId() {
		return appellantId;
	}

	public UUID getRespondentId() {
		return respondentId;
	}

	public AppealReason getReason() {
		return reason;
	}

	public String getDescription() {
		return description;
	}

	public AppealStatus getStatus() {
		return status;
	}

	public AppealResult getResult() {
		return result;
	}

	public int getTransferAmount() {
		return transferAmount;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public List<UUID> getTransactionIds() {
		return List.copyOf(transactionIds);
	}

	public UUID getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AppealEntity entity && Objects.equals(appealId, entity.appealId);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(appealId);
	}
}
