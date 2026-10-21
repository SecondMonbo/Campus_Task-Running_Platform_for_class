package edu.campus.taskplatform.dispute.cancellation;

import java.util.UUID;

import edu.campus.taskplatform.common.api.ApiResponse;
import edu.campus.taskplatform.common.api.PageResult;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationArbitrationRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员使用的取消争议查询与裁决接口。
 */
@RestController
@RequestMapping("/api/v1/admin/cancellation-requests")
public class AdminCancellationController {

	private final CancellationService cancellationService;

	public AdminCancellationController(CancellationService cancellationService) {
		this.cancellationService = cancellationService;
	}

	@GetMapping
	public ApiResponse<PageResult<CancellationRequestDto>> list(
			@RequestParam(required = false) CancellationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(cancellationService.listForAdmin(status, page, size));
	}

	@PostMapping("/{requestId}/arbitration")
	public ApiResponse<CancellationRequestDto> arbitrate(@PathVariable UUID requestId,
			@Valid @RequestBody CancellationArbitrationRequest request) {
		return ApiResponse.ok(cancellationService.arbitrate(requestId, request));
	}
}
