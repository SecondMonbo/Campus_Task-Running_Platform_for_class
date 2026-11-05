package edu.campus.taskplatform.accounting;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallet_transaction")
public class WalletTransaction {

	@Id
	@Column(name = "transaction_id", nullable = false, updatable = false)
	private UUID transactionId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "task_id", length = 36)
	private String taskId;

	@Column(name = "appeal_id")
	private UUID appealId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 24)
	private WalletTransactionType type;

	@Column(name = "amount", nullable = false)
	private long amount;

	@Column(name = "balance_after", nullable = false)
	private long balanceAfter;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WalletTransaction() {
	}

	public WalletTransaction(UUID transactionId, Long userId, String taskId, UUID appealId,
			WalletTransactionType type, long amount, long balanceAfter, Instant createdAt) {
		this.transactionId = transactionId;
		this.userId = userId;
		this.taskId = taskId;
		this.appealId = appealId;
		this.type = type;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.createdAt = createdAt;
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getTaskId() {
		return taskId;
	}

	public UUID getAppealId() {
		return appealId;
	}

	public WalletTransactionType getType() {
		return type;
	}

	public long getAmount() {
		return amount;
	}

	public long getBalanceAfter() {
		return balanceAfter;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
