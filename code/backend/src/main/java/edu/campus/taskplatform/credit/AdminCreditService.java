package edu.campus.taskplatform.credit;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.credit.dto.CreditRecordDto;
import edu.campus.taskplatform.credit.dto.CreditSummaryDto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员信用分入口：在服务层再次校验管理员角色，避免只依赖 URL 层
 * {@code /api/v1/admin/**} 的权限规则。
 */
@Service
public class AdminCreditService {

	private final CreditScoreService creditScoreService;
	private final CurrentUserService currentUserService;

	public AdminCreditService(CreditScoreService creditScoreService,
			CurrentUserService currentUserService) {
		this.creditScoreService = creditScoreService;
		this.currentUserService = currentUserService;
	}

	@Transactional
	public CreditSummaryDto summary(long userId) {
		currentUserService.requireAdmin();
		return creditScoreService.summary(userId);
	}

	@Transactional
	public PageResult<CreditRecordDto> records(long userId, int page, int size) {
		currentUserService.requireAdmin();
		return creditScoreService.records(userId, page, size);
	}

	@Transactional
	public CreditSummaryDto adjust(long userId, int delta, String reason) {
		CurrentUser admin = currentUserService.requireAdmin();
		return creditScoreService.adjustByAdmin(userId, delta, reason, admin.userId());
	}
}
