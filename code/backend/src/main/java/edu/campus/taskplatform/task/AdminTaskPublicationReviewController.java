package edu.campus.taskplatform.task;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.task.dto.PublicationReviewDecisionRequest;
import edu.campus.taskplatform.task.dto.TaskView;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员发布复核队列与决定；URL 层由安全配置限制为 ADMIN。 */
@RestController
@RequestMapping("/api/v1/admin/task-publication-reviews")
public class AdminTaskPublicationReviewController {

	private final TaskPublicationReviewService service;

	public AdminTaskPublicationReviewController(TaskPublicationReviewService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<PageResult<TaskView>> list(
			@RequestParam(defaultValue = "PENDING_REVIEW") PublicationReviewStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(service.list(status, page, size));
	}

	@PostMapping("/{taskId}/decision")
	public ApiResponse<TaskView> decide(@PathVariable String taskId,
			@Valid @RequestBody PublicationReviewDecisionRequest request) {
		return ApiResponse.ok(service.decide(taskId, request));
	}
}
