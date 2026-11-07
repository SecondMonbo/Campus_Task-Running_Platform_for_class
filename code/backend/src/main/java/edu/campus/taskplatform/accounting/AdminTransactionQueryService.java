package edu.campus.taskplatform.accounting;

import java.util.ArrayList;

import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTransactionQueryService {

	private final WalletTransactionRepository repository;
	private final CurrentUserService currentUserService;

	public AdminTransactionQueryService(WalletTransactionRepository repository,
			CurrentUserService currentUserService) {
		this.repository = repository;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public PageResult<WalletTransactionDto> list(Long userId, String taskId, int page, int size) {
		currentUserService.requireAdmin();
		if (userId != null && userId <= 0) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "userId 必须是正整数");
		}
		String taskFilter = taskId == null ? "" : taskId.trim();
		if (taskFilter.length() > 36) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "taskId 不能超过 36 个字符");
		}
		var pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "transactionId")));
		Specification<WalletTransaction> specification = (root, query, builder) -> {
			var predicates = new ArrayList<Predicate>();
			if (userId != null) predicates.add(builder.equal(root.get("userId"), userId));
			if (!taskFilter.isEmpty()) predicates.add(builder.equal(root.get("taskId"), taskFilter));
			return builder.and(predicates.toArray(Predicate[]::new));
		};
		var result = repository.findAll(specification, pageable);
		return PageResult.of(result.getContent().stream().map(WalletTransactionDto::from).toList(),
				page, size, result.getTotalElements());
	}
}
