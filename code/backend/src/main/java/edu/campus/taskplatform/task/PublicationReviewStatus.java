package edu.campus.taskplatform.task;

/**
 * 发布复核状态。任务状态仍按原状态机流转，本状态只控制公开可见性：
 * 受限用户发布的任务进入 PENDING_REVIEW，不进入公开待接单列表。
 */
public enum PublicationReviewStatus {
	/** 普通用户发布，无需复核（历史任务同样使用该值）。 */
	NOT_REQUIRED,
	/** 受限用户发布，等待管理员复核。 */
	PENDING_REVIEW,
	/** 管理员批准，进入公开待接单列表。 */
	APPROVED,
	/** 管理员驳回，任务转为已取消并全额解冻。 */
	REJECTED;

	public boolean isPubliclyVisible() {
		return this == NOT_REQUIRED || this == APPROVED;
	}

	public boolean isPending() {
		return this == PENDING_REVIEW;
	}
}
