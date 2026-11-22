package edu.campus.taskplatform.user;

import java.time.Instant;

/**
 * 用户中心资料视图。rating / reviewCount 按跑腿员收到的任务评价实时统计；
 * creditScore 为当前信用分（0–100），新用户初始化为 80。
 */
public record UserProfileDto(
		String userId,
		String username,
		String displayName,
		String phone,
		String role,
		Instant createdAt,
		Double rating,
		long reviewCount,
		Integer creditScore) {
}
