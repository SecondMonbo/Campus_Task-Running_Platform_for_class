package edu.campus.taskplatform.credit;

/**
 * 信用分事件类型。每条事件以 (eventType, sourceId) 幂等，同一业务资源不会重复记分。
 */
public enum CreditEventType {
	/** 首次启用机制时的基线记录，变动值为 0。 */
	INITIALIZATION,
	/** 收到 5 星评价，+2。 */
	REVIEW_5_STAR,
	/** 收到 3–4 星评价，0 分留痕。 */
	REVIEW_3_4_STAR,
	/** 收到 1–2 星评价；30 天内同一评价人首次扣 -3，后续为 0 分留痕。 */
	REVIEW_LOW_STAR,
	/** 按时完成任务，+1。 */
	TASK_COMPLETED_ON_TIME,
	/** 在截止时间至 24 小时窗口内完成，仅累计连续履约，不加减分。 */
	TASK_COMPLETED_IN_WINDOW,
	/** 超时超过 24 小时送达，-5。 */
	TASK_COMPLETED_LATE,
	/** 取消争议中认定的有责方，-5。 */
	RESPONSIBLE_CANCELLATION,
	/** 投诉成立，按情节扣 -2 / -5 / -10。 */
	COMPLAINT_UPHELD,
	/** 连续完成 10 个任务且无负向事件，补至 75 分。 */
	CONSECUTIVE_COMPLETION_RECOVERY,
	/** 管理员复核调整，由管理员指定整数。 */
	ADMIN_REVIEW_ADJUSTMENT;

	public boolean isCompletion() {
		return this == TASK_COMPLETED_ON_TIME
				|| this == TASK_COMPLETED_IN_WINDOW
				|| this == TASK_COMPLETED_LATE;
	}
}
