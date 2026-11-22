package edu.campus.taskplatform.credit;

/**
 * 信用分档位。分数越高档位越好；接口使用大写英文枚举，中文仅用于界面展示。
 */
public enum CreditTier {
	/** 90–100 */
	EXCELLENT,
	/** 75–89 */
	GOOD,
	/** 60–74 */
	WARNING,
	/** 0–59 */
	RESTRICTED;

	public static CreditTier fromScore(int score) {
		if (score >= 90) {
			return EXCELLENT;
		}
		if (score >= 75) {
			return GOOD;
		}
		if (score >= 60) {
			return WARNING;
		}
		return RESTRICTED;
	}
}
