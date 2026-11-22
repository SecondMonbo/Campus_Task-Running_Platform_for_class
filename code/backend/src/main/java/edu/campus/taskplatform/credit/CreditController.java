package edu.campus.taskplatform.credit;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.credit.dto.CreditRecordDto;
import edu.campus.taskplatform.credit.dto.CreditSummaryDto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 本人信用分查询：精确分数、档位、权限说明、恢复条件与变更记录。 */
@RestController
@RequestMapping("/api/v1/users/me/credit")
public class CreditController {

	private final CreditScoreService creditScoreService;
	private final CurrentUserService currentUserService;

	public CreditController(CreditScoreService creditScoreService, CurrentUserService currentUserService) {
		this.creditScoreService = creditScoreService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	public ApiResponse<CreditSummaryDto> summary() {
		CurrentUser user = currentUserService.requireCurrentUser();
		return ApiResponse.ok(creditScoreService.summary(user.userId()));
	}

	@GetMapping("/records")
	public ApiResponse<PageResult<CreditRecordDto>> records(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		CurrentUser user = currentUserService.requireCurrentUser();
		return ApiResponse.ok(creditScoreService.records(user.userId(), page, size));
	}
}
