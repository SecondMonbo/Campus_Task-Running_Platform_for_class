package edu.campus.taskplatform.dispute.cancellation;

/**
 * 管理员对取消争议的裁决方式，与 OpenAPI {@code CancellationArbitrationRequest.ruling} 一致。
 */
public enum CancellationRuling {
	/** 支持申请人取消，冻结报酬全额退回发布者 */
	SUPPORT_REQUESTER_CANCEL,
	/** 不支持取消，任务保持原状态 */
	SUPPORT_CONTINUE,
	/** 按比例取消，冻结报酬在发布者与跑腿员之间拆分 */
	CANCEL_PROPORTIONALLY
}
