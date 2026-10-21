package edu.campus.taskplatform.dispute.appeal;

/**
 * 申诉原因，与 OpenAPI {@code Appeal.reason} 一致。
 */
public enum AppealReason {
	/** 履约问题：实际送达或服务与约定不符 */
	FULFILLMENT_ISSUE,
	/** 结算问题：报酬结算有误 */
	SETTLEMENT_ISSUE,
	OTHER
}
