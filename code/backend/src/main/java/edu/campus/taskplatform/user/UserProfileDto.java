package edu.campus.taskplatform.user;

import java.time.Instant;

/**
 * 用户中心资料视图。rating / reviewCount 为信誉分展示预留字段：
 * 当前按跑腿员收到的任务评价实时统计，后续接入更完整的信誉分机制时可直接扩展，
 * 前端无需改接口。
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
