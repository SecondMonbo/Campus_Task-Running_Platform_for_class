package edu.campus.taskplatform.notification.dto;

import edu.campus.taskplatform.notification.Notification;
import edu.campus.taskplatform.notification.NotificationType;

import java.time.Instant;

/**
 * 通知响应视图；字段与 openapi.yaml 的 Notification 一致，
 * ID 统一按字符串返回。
 */
public class NotificationView {

    private String notificationId;
    private NotificationType type;
    private String title;
    private String content;
    private boolean read;
    private String relatedTaskId;
    private Instant createdAt;

    public static NotificationView from(Notification n) {
        NotificationView v = new NotificationView();
        v.notificationId = n.getNotificationId();
        v.type = n.getType();
        v.title = n.getTitle();
        v.content = n.getContent();
        v.read = n.isRead();
        v.relatedTaskId = n.getRelatedTaskId();
        v.createdAt = n.getCreatedAt();
        return v;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isRead() {
        return read;
    }

    public String getRelatedTaskId() {
        return relatedTaskId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
