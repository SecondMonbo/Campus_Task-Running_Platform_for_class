package edu.campus.taskplatform.credit;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.credit.dto.CreditAdjustmentRequest;
import edu.campus.taskplatform.credit.dto.CreditRecordDto;
import edu.campus.taskplatform.credit.dto.CreditSummaryDto;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员信用分查询与复核调整；服务层再次校验管理员角色。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminCreditController {

	private final AdminCreditService adminCreditService;

	public AdminCreditController(AdminCreditService adminCreditService) {
		this.adminCreditService = adminCreditService;
	}

	@GetMapping("/{userId}/credit")
	public ApiResponse<CreditSummaryDto> summary(@PathVariable long userId) {
		return ApiResponse.ok(adminCreditService.summary(userId));
	}

	@GetMapping("/{userId}/credit/records")
	public ApiResponse<PageResult<CreditRecordDto>> records(@PathVariable long userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(adminCreditService.records(userId, page, size));
	}

	@PostMapping("/{userId}/credit-adjustments")
	public ApiResponse<CreditSummaryDto> adjust(@PathVariable long userId,
			@Valid @RequestBody CreditAdjustmentRequest request) {
		return ApiResponse.ok(adminCreditService.adjust(userId, request.delta(), request.reason()));
	}
}
