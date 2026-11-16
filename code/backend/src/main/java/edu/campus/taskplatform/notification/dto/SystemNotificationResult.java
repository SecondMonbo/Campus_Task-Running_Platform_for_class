package edu.campus.taskplatform.notification.dto;

import java.time.Instant;

/**
 * 系统通知发布结果：本次推送覆盖的用户数量与统一发布时间。
 */
public record SystemNotificationResult(int recipientCount, Instant publishedAt) {
}
