package edu.campus.taskplatform.auth;

import java.time.Instant;

import edu.campus.taskplatform.credit.CreditScore;
import edu.campus.taskplatform.credit.CreditScoreService;
import edu.campus.taskplatform.credit.CreditTier;

/**
 * 管理员用户查询视图。用户编号按统一约定输出字符串，不包含密码哈希。
 * 信用分来自 credit_score 表；未初始化时按初始 80 分展示。
 */
public record AdminUserDto(
        String userId,
        String username,
        String displayName,
        String role,
        String phone,
        Instant createdAt,
        int creditScore,
        CreditTier creditTier,
        boolean creditRestricted) {

    public static AdminUserDto from(AppUser user, CreditScore account) {
        int score = account == null ? CreditScoreService.INITIAL_SCORE : account.getScore();
        boolean restricted = account != null && account.isRestricted();
        return new AdminUserDto(user.getUserId().toString(), user.getUsername(),
                user.getDisplayName(), user.getRole().name(), user.getPhone(), user.getCreatedAt(),
                score, CreditTier.fromScore(score), restricted);
    }
}
