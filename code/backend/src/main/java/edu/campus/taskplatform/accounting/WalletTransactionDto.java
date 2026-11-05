package edu.campus.taskplatform.accounting;

import java.time.Instant;

public record WalletTransactionDto(
		String transactionId,
		String userId,
		String taskId,
		String appealId,
		WalletTransactionType type,
		long amount,
		long balanceAfter,
		Instant createdAt) {

	public static WalletTransactionDto from(WalletTransaction transaction) {
		return new WalletTransactionDto(
				transaction.getTransactionId().toString(),
				transaction.getUserId().toString(),
				transaction.getTaskId(),
				transaction.getAppealId() == null ? null : transaction.getAppealId().toString(),
				transaction.getType(),
				transaction.getAmount(),
				transaction.getBalanceAfter(),
				transaction.getCreatedAt());
	}
}
