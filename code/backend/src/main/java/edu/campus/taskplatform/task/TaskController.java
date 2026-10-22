package edu.campus.taskplatform.task;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.task.dto.CreateReviewRequest;
import edu.campus.taskplatform.task.dto.TaskCreateRequest;
import edu.campus.taskplatform.task.dto.TaskReviewView;
import edu.campus.taskplatform.task.dto.TaskUpdateRequest;
import edu.campus.taskplatform.task.dto.TaskView;
import edu.campus.taskplatform.task.dto.WithdrawalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务模块 REST 接口，路径与 openapi.yaml 的 Tasks 相关路径一致。
 * 操作者身份一律从登录会话解析（CurrentUserService），请求体不接受前端传 userId。
 */
@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private static final int[] ALLOWED_SIZES = {10, 20, 50};

    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    public TaskController(TaskService taskService, CurrentUserService currentUserService) {
        this.taskService = taskService;
        this.currentUserService = currentUserService;
    }

    // 浏览任务
    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskView>> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        resolvePaging(page, size);
        return ApiResponse.ok(taskService.listTasks(status, keyword, page, size));
    }

    // 发布任务
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskView> createTask(@Valid @RequestBody TaskCreateRequest req) {
        return ApiResponse.ok(taskService.createTask(current(), req));
    }

    // 任务详情
    @GetMapping("/tasks/{taskId}")
    public ApiResponse<TaskView> getTask(@PathVariable String taskId) {
        return ApiResponse.ok(taskService.getTask(taskId));
    }

    // 修改待接单任务
    @PatchMapping("/tasks/{taskId}")
    public ApiResponse<TaskView> updateTask(@PathVariable String taskId,
                                            @Valid @RequestBody TaskUpdateRequest req) {
        return ApiResponse.ok(taskService.updateTask(current(), taskId, req));
    }

    // 撤回任务
    @PostMapping("/tasks/{taskId}/withdrawal")
    public ApiResponse<TaskView> withdrawTask(@PathVariable String taskId,
                                              @Valid @RequestBody WithdrawalRequest req) {
        return ApiResponse.ok(taskService.withdrawTask(current(), taskId, req));
    }

    // 接单
    @PostMapping("/tasks/{taskId}/acceptance")
    public ApiResponse<TaskView> acceptTask(@PathVariable String taskId) {
        return ApiResponse.ok(taskService.acceptTask(current(), taskId));
    }

    // 送达
    @PostMapping("/tasks/{taskId}/delivery")
    public ApiResponse<TaskView> deliverTask(@PathVariable String taskId) {
        return ApiResponse.ok(taskService.deliverTask(current(), taskId));
    }

    // 确认完成（结算）
    @PostMapping("/tasks/{taskId}/completion")
    public ApiResponse<TaskView> completeTask(@PathVariable String taskId) {
        return ApiResponse.ok(taskService.completeTask(current(), taskId));
    }

    // 评价跑腿员
    @PostMapping("/tasks/{taskId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskReviewView> createReview(@PathVariable String taskId,
                                                    @Valid @RequestBody CreateReviewRequest req) {
        return ApiResponse.ok(TaskReviewView.from(taskService.createReview(current(), taskId, req)));
    }

    // 我的任务（as=publisher | as=runner）
    @GetMapping("/users/me/tasks")
    public ApiResponse<PageResult<TaskView>> listMyTasks(
            @RequestParam String as,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        resolvePaging(page, size);
        return ApiResponse.ok(taskService.listMyTasks(current(), as, status, page, size));
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
