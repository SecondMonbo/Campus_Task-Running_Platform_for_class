package edu.campus.taskplatform.dispute.cancellation;

/**
 * 取消申请状态，与 OpenAPI {@code CancellationStatus} 一致。
 */
public enum CancellationStatus {
	/** 已发起，等待被申请方应答 */
	PENDING_RESPONSE,
	/** 被拒绝，等待管理员裁决 */
	PENDING_ADMIN,
	/** 被申请方同意，取消已生效 */
	APPROVED,
	/** 管理员已裁决 */
	ARBITRATED
}
