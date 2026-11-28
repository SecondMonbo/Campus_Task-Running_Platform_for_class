package edu.campus.taskplatform.credit.dto;

import edu.campus.taskplatform.credit.CreditScore;
import edu.campus.taskplatform.credit.CreditTier;

/**
 * 本人或管理员查看的信用分摘要。受限状态由服务端持久化：低于 60 分开启，
 * 恢复至 75 分及以上才解除；因此 60–74 分且从未受限的用户仍可正常发布和接单。
 */
public record CreditSummaryDto(
		String userId,
		int score,
		CreditTier tier,
		boolean restricted,
		boolean canAcceptTasks,
		boolean publishRequiresReview,
		int consecutiveCompletedTasks,
		String restrictionNotice,
		String recoveryCondition) {

	public static final String RECOVERY_CONDITION =
			"连续完成 10 个任务且期间没有负向信用事件，可恢复至 75 分；达到 75 分自动解除低分限制。";

	public static CreditSummaryDto from(CreditScore account) {
		CreditTier tier = CreditTier.fromScore(account.getScore());
		boolean restricted = account.isRestricted();
		String notice;
		if (restricted) {
			notice = "信用分受限：不能接取普通任务，新发布任务需管理员复核；恢复至 75 分解除限制。";
		}
		else if (tier == CreditTier.WARNING) {
			notice = "履约风险提示：信用分处于预警档，请注意按时完成已接取任务。";
		}
		else {
			notice = null;
		}
		return new CreditSummaryDto(account.getUserId().toString(), account.getScore(), tier,
				restricted, !restricted, restricted, account.getConsecutiveCompletedTasks(),
				notice, RECOVERY_CONDITION);
	}
}
