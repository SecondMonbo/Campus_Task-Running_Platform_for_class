package edu.campus.taskplatform.dispute.config;

import java.util.List;
import java.util.UUID;

import edu.campus.taskplatform.accounting.AccountingService;
import edu.campus.taskplatform.dispute.port.LedgerPort;

import org.springframework.stereotype.Component;

@Component
public class AccountingLedgerAdapter implements LedgerPort {

	private final AccountingService accountingService;

	public AccountingLedgerAdapter(AccountingService accountingService) {
		this.accountingService = accountingService;
	}

	@Override
	public void settleFrozenReward(String taskId, Long publisherId, Long runnerId,
			long refundToPublisher, long payToRunner) {
		accountingService.settleFrozenReward(taskId, publisherId, runnerId, refundToPublisher, payToRunner);
	}

	@Override
	public List<UUID> transferAvailable(String taskId, UUID appealId,
			Long fromUserId, Long toUserId, long amount) {
		return accountingService.transferAvailable(taskId, appealId, fromUserId, toUserId, amount);
	}

	@Override
	public long availableBalance(Long userId) {
		return accountingService.getAvailableBalance(userId);
	}
}
