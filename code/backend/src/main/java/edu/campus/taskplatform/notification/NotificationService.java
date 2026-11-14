package edu.campus.taskplatform.notification;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.notification.dto.NotificationView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 通知服务：本人通知分页（含未读筛选）、标记已读，以及供任务等模块写入通知。
 *
 * <p>notify 使用独立事务（REQUIRES_NEW）：通知写入失败不影响任务主流程，
 * 由调用方 try/catch 兜底后仅记录日志。</p>
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页查询本人通知，支持仅看未读与按类型筛选。
     * type 为 null 表示全部类型。
     */
    public PageResult<NotificationView> listForUser(CurrentUser user, int page, int size,
                                                    boolean unreadOnly, NotificationType type) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "notificationId"));
        Page<Notification> result;
        if (type == null) {
            result = unreadOnly
                    ? repository.findByUserIdAndReadFalse(user.userId(), pageable)
                    : repository.findByUserId(user.userId(), pageable);
        } else {
            result = unreadOnly
                    ? repository.findByUserIdAndTypeAndReadFalse(user.userId(), type, pageable)
                    : repository.findByUserIdAndType(user.userId(), type, pageable);
        }
        return PageResult.of(toViews(result.getContent()), page, size, result.getTotalElements());
    }

    /**
     * 标记本人通知已读；重复标记幂等。通知不存在或不属于当前用户时返回 404。
     */
    @Transactional
    public void markRead(CurrentUser user, String notificationId) {
        Notification notification = repository
                .findByNotificationIdAndUserId(notificationId, user.userId())
                .orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在"));
        if (!notification.isRead()) {
            notification.markRead();
            repository.save(notification);
        }
    }

    /**
     * 写入一条任务进度通知。独立事务，失败回滚且不抛给调用方主流程（异常由调用方处理）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long userId, String title, String content, String relatedTaskId) {
        Notification notification = new Notification(UUID.randomUUID().toString(), userId,
                NotificationType.TASK, title, content, relatedTaskId, Instant.now());
        repository.save(notification);
    }

    private List<NotificationView> toViews(List<Notification> notifications) {
        List<NotificationView> views = new java.util.ArrayList<>(notifications.size());
        for (Notification n : notifications) {
            views.add(NotificationView.from(n));
        }
        return views;
    }
}
