package edu.campus.taskplatform.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 通知仓储：按用户分页查询、按未读筛选、定位本人通知。
 */
public interface NotificationRepository extends JpaRepository<Notification, String> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFalse(Long userId, Pageable pageable);

    Optional<Notification> findByNotificationIdAndUserId(String notificationId, Long userId);
}
