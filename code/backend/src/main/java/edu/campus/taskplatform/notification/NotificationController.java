package edu.campus.taskplatform.notification;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.notification.dto.NotificationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知模块 REST 接口，路径与 openapi.yaml 的 Notifications 一致。
 * 操作者身份一律从登录会话解析，请求体不接受前端传 userId。
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private static final int[] ALLOWED_SIZES = {10, 20, 50};

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                  CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    // 查询本人通知；type 省略表示全部，TASK 为任务通知，SYSTEM 为系统通知
    @GetMapping("/notifications")
    public ApiResponse<PageResult<NotificationView>> listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) NotificationType type) {
        resolvePaging(page, size);
        return ApiResponse.ok(notificationService.listForUser(current(), page, size, unreadOnly, type));
    }

    // 标记通知已读
    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<Void> markNotificationRead(@PathVariable String notificationId) {
        notificationService.markRead(current(), notificationId);
        return ApiResponse.ok(null);
    }

    private CurrentUser current() {
        return currentUserService.requireCurrentUser();
    }

    private void resolvePaging(int page, int size) {
        if (page < 0) {
            throw new TaskException(ErrorCode.VALIDATION_ERROR, "page 必须从 0 开始");
        }
        for (int allowed : ALLOWED_SIZES) {
            if (size == allowed) {
                return;
            }
        }
        throw new TaskException(ErrorCode.VALIDATION_ERROR, "size 只允许 10、20 或 50");
    }
}
