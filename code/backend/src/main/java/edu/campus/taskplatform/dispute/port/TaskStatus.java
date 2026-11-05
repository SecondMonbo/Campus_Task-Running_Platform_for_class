package edu.campus.taskplatform.dispute.port;

/**
 * 与 OpenAPI {@code TaskStatus} 一致的任务状态。
 */
public enum TaskStatus {
	WAITING_FOR_TAKER,
	IN_PROGRESS,
	WAITING_FOR_ACCEPTANCE,
	COMPLETED,
	CANCELLED
}
