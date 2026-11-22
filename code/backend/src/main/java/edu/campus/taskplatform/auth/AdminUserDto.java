package edu.campus.taskplatform.auth;

import java.time.Instant;

import edu.campus.taskplatform.credit.CreditTier;

/**
 * 管理员用户查询视图。用户编号按统一约定输出字符串，不包含密码哈希。
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

    public static AdminUserDto from(AppUser user) {
        return new AdminUserDto(user.getUserId().toString(), user.getUsername(),
                user.getDisplayName(), user.getRole().name(), user.getPhone(), user.getCreatedAt(),
                user.getCreditScore(), CreditTier.fromScore(user.getCreditScore()),
                user.isCreditRestricted());
    }
}
