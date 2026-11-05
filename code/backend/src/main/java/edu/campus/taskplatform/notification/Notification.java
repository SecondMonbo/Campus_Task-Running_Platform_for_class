package edu.campus.taskplatform.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 通知实体，对应 Flyway 迁移脚本中的 notification 表。
 * userId 以 Long 保存，JSON 序列化相关字段见 NotificationView。
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(name = "notification_id", length = 36)
    private String notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "related_task_id", length = 36)
    private String relatedTaskId;

    @Column(nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(String notificationId, Long userId, String title, String content,
                        String relatedTaskId, Instant createdAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.relatedTaskId = relatedTaskId;
        this.read = false;
        this.createdAt = createdAt;
    }

    public String getNotificationId() {
        return notificationId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getRelatedTaskId() {
        return relatedTaskId;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
