package edu.campus.taskplatform.notification;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.notification.dto.SystemNotificationRequest;
import edu.campus.taskplatform.notification.dto.SystemNotificationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员发布系统通知。角色校验在服务层完成，URL 层不按角色拦截。
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final AdminNotificationService service;

    public AdminNotificationController(AdminNotificationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SystemNotificationResult> publish(
            @Valid @RequestBody SystemNotificationRequest request) {
        return ApiResponse.ok(service.publishSystemNotification(request));
    }
}
