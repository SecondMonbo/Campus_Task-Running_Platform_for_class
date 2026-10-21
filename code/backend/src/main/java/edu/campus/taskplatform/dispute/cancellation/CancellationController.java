package edu.campus.taskplatform.dispute.cancellation;

import java.util.UUID;

import edu.campus.taskplatform.common.api.ApiResponse;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationResponseRequest;
import edu.campus.taskplatform.dispute.cancellation.dto.CreateCancellationRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务参与者使用的取消申请接口。
 */
@RestController
@RequestMapping("/api/v1")
public class CancellationController {

	private final CancellationService cancellationService;

	public CancellationController(CancellationService cancellationService) {
		this.cancellationService = cancellationService;
	}

	@PostMapping("/tasks/{taskId}/cancellation-requests")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CancellationRequestDto> create(@PathVariable UUID taskId,
			@Valid @RequestBody CreateCancellationRequest request) {
		return ApiResponse.ok(cancellationService.create(taskId, request));
	}

	@PostMapping("/cancellation-requests/{requestId}/response")
	public ApiResponse<CancellationRequestDto> respond(@PathVariable UUID requestId,
			@Valid @RequestBody CancellationResponseRequest request) {
		return ApiResponse.ok(cancellationService.respond(requestId, request));
	}
}
