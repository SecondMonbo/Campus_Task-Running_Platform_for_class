package edu.campus.taskplatform.accounting;

import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

	private final WalletQueryService walletQueryService;
	private final CurrentUserService currentUserService;

	public WalletController(WalletQueryService walletQueryService, CurrentUserService currentUserService) {
		this.walletQueryService = walletQueryService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	public ApiResponse<WalletDto> getWallet() {
		return ApiResponse.ok(walletQueryService.getWallet(currentUserService.requireCurrentUser().userId()));
	}

	@GetMapping("/transactions")
	public ApiResponse<PageResult<WalletTransactionDto>> listTransactions(
			@RequestParam(required = false) WalletTransactionType type,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(walletQueryService.listTransactions(
				currentUserService.requireCurrentUser().userId(), type, page, size));
	}
}
