package edu.campus.taskplatform.dispute.appeal;

import java.util.UUID;

import edu.campus.taskplatform.common.api.ApiResponse;
import edu.campus.taskplatform.common.api.PageResult;
import edu.campus.taskplatform.dispute.appeal.dto.AppealDecisionRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员使用的申诉查询与裁决接口。
 */
@RestController
@RequestMapping("/api/v1/admin/appeals")
public class AdminAppealController {

	private final AppealService appealService;

	public AdminAppealController(AppealService appealService) {
		this.appealService = appealService;
	}

	@GetMapping
	public ApiResponse<PageResult<AppealDto>> list(
			@RequestParam(required = false) AppealStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(appealService.listForAdmin(status, page, size));
	}

	@PostMapping("/{appealId}/decision")
	public ApiResponse<AppealDto> decide(@PathVariable UUID appealId,
			@Valid @RequestBody AppealDecisionRequest request) {
		return ApiResponse.ok(appealService.decide(appealId, request));
	}
}
