package edu.campus.taskplatform.credit.dto;

import java.time.Instant;

/**
 * 他人可见的用户主页信息：仅基本信息 + 信用档位 + 累计完成单数。
 * 按 API约定 §4.4，不返回精确分值，也不返回变更明细。
 */
public record PublicUserProfileDto(
        String userId,
        String displayName,
        String role,
        String creditTier,
        String creditTierLabel,
        long completedTasks,
        Instant memberSince) {
}
