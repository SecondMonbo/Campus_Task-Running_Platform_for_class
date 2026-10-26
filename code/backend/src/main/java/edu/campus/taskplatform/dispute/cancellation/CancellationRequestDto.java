package edu.campus.taskplatform.dispute.cancellation;

import java.time.Instant;

/**
 * 取消申请响应体，字段与 OpenAPI {@code CancellationRequest} 一致；ID 统一按字符串传递。
 */
public record CancellationRequestDto(
		String requestId,
		String taskId,
		String requesterId,
		String reason,
		String responseNote,
		CancellationStatus status,
		String ruling,
		int refundAmount,
		int runnerAmount,
		Instant createdAt) {

	public static CancellationRequestDto from(CancellationRequestEntity entity) {
		return new CancellationRequestDto(
				entity.getRequestId().toString(),
				entity.getTaskId().toString(),
				entity.getRequesterId().toString(),
				entity.getReason(),
				entity.getResponseNote(),
				entity.getStatus(),
				entity.getRuling() == null ? null : entity.getRuling().name(),
				entity.getRefundAmount(),
				entity.getRunnerAmount(),
				entity.getCreatedAt());
	}
}
