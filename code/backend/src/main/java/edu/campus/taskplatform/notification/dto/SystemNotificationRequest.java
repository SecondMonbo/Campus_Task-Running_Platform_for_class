package edu.campus.taskplatform.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员发布系统通知的请求体；标题与内容均去除首尾空白后保存。
 */
public record SystemNotificationRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 500) String content) {
}
