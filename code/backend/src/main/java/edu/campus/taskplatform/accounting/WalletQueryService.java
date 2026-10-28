package edu.campus.taskplatform.accounting;

import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.web.Pagination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletQueryService {

	private final WalletAccountRepository accountRepository;
	private final WalletTransactionRepository transactionRepository;

	public WalletQueryService(WalletAccountRepository accountRepository,
			WalletTransactionRepository transactionRepository) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
	}

	@Transactional(readOnly = true)
	public WalletDto getWallet(long userId) {
		return accountRepository.findById(userId)
				.map(account -> new WalletDto(account.getAvailableBalance(), account.getFrozenBalance()))
				.orElseGet(() -> new WalletDto(0, 0));
	}

	@Transactional(readOnly = true)
	public PageResult<WalletTransactionDto> listTransactions(long userId,
			WalletTransactionType type, int page, int size) {
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "transactionId")));
		Page<WalletTransaction> result = type == null
				? transactionRepository.findByUserId(userId, pageable)
				: transactionRepository.findByUserIdAndType(userId, type, pageable);
		return PageResult.of(result.getContent().stream().map(WalletTransactionDto::from).toList(),
				page, size, result.getTotalElements());
	}
}
