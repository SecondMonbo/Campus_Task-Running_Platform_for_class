package edu.campus.taskplatform.dispute.appeal;

import java.util.UUID;

import edu.campus.taskplatform.common.api.ApiResponse;
import edu.campus.taskplatform.dispute.appeal.dto.CreateAppealRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务参与者对已完成任务提交申诉。
 */
@RestController
@RequestMapping("/api/v1")
public class AppealController {

	private final AppealService appealService;

	public AppealController(AppealService appealService) {
		this.appealService = appealService;
	}

	@PostMapping("/tasks/{taskId}/appeals")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AppealDto> create(@PathVariable UUID taskId,
			@Valid @RequestBody CreateAppealRequest request) {
		return ApiResponse.ok(appealService.create(taskId, request));
	}
}
