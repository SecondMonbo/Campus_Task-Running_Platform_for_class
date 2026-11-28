package edu.campus.taskplatform.credit;

/** 信用分待处理事件状态；失败事件保留并重试，不静默丢弃。 */
public enum CreditEventStatus {
	PENDING,
	PROCESSED,
	FAILED
}
