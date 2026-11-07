package edu.campus.taskplatform.accounting;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

	private final AdminTransactionQueryService service;

	public AdminTransactionController(AdminTransactionQueryService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<PageResult<WalletTransactionDto>> list(
			@RequestParam(required = false) Long userId,
			@RequestParam(required = false) String taskId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(service.list(userId, taskId, page, size));
	}
}
