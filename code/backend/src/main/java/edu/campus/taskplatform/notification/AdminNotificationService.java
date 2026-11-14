package edu.campus.taskplatform.notification;

import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.notification.dto.SystemNotificationRequest;
import edu.campus.taskplatform.notification.dto.SystemNotificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 管理员系统通知：发布后为每位现有用户各写入一条 SYSTEM 类型通知。
 *
 * <p>系统通知没有关联任务，与任务进度通知通过 {@link NotificationType} 区分；
 * 用户已读状态仍按人保存，复用 notification 表。</p>
 */
@Service
public class AdminNotificationService {

    private final AppUserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public AdminNotificationService(AppUserRepository userRepository,
                                    NotificationRepository notificationRepository,
                                    CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * 发布系统通知。仅管理员可调用；所有用户在同一事务中收到同一份通知。
     */
    @Transactional
    public SystemNotificationResult publishSystemNotification(SystemNotificationRequest request) {
        currentUserService.requireAdmin();
        String title = request.title().trim();
        String content = request.content().trim();
        Instant publishedAt = Instant.now();
        List<Notification> notifications = userRepository.findAll().stream()
                .map(user -> new Notification(UUID.randomUUID().toString(), user.getUserId(),
                        NotificationType.SYSTEM, title, content, null, publishedAt))
                .toList();
        notificationRepository.saveAll(notifications);
        return new SystemNotificationResult(notifications.size(), publishedAt);
    }
}
