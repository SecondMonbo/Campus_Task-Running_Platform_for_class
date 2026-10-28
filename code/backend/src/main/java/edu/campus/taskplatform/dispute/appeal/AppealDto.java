package edu.campus.taskplatform.dispute.appeal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 申诉响应体，字段与 OpenAPI {@code Appeal} 一致；ID 统一按字符串传递。
 */
public record AppealDto(
		String appealId,
		String taskId,
		String appellantId,
		String respondentId,
		AppealReason reason,
		String description,
		AppealStatus status,
		AppealResult result,
		int transferAmount,
		String decisionNote,
		List<String> transactionIds,
		Instant createdAt,
		Instant decidedAt) {

	public static AppealDto from(AppealEntity entity) {
		return new AppealDto(
				entity.getAppealId().toString(),
				entity.getTaskId(),
				entity.getAppellantId().toString(),
				entity.getRespondentId().toString(),
				entity.getReason(),
				entity.getDescription(),
				entity.getStatus(),
				entity.getResult(),
				entity.getTransferAmount(),
				entity.getDecisionNote(),
				entity.getTransactionIds().stream().map(UUID::toString).toList(),
				entity.getCreatedAt(),
				entity.getDecidedAt());
	}
}
