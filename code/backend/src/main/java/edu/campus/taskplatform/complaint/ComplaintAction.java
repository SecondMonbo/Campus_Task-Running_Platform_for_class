package edu.campus.taskplatform.complaint;

/** 保留公共契约枚举；当前仅开放处理留痕，不执行处罚或通知。 */
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
}
