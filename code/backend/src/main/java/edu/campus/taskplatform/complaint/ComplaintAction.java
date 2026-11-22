package edu.campus.taskplatform.complaint;

/**
 * 投诉处理动作。当前开放警告/留痕/线下移交与信用扣分；
 * 账务调整、功能限制和冻结账号仍未开放。
 */
public enum ComplaintAction {
	WARNING,
	CREDIT_ADJUSTMENT,
	FINANCIAL_ADJUSTMENT,
	FUNCTION_RESTRICTION,
	ACCOUNT_FREEZE,
	LOG_ONLY,
	OFFLINE_REFERRAL;

	public boolean supportsRecordOnly() {
		return this == WARNING || this == LOG_ONLY || this == OFFLINE_REFERRAL;
	}

	public boolean supportsCreditAdjustment() {
		return this == CREDIT_ADJUSTMENT;
	}

	public boolean isSupported() {
		return supportsRecordOnly() || supportsCreditAdjustment();
	}
}
