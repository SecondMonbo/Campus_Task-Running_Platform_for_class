package edu.campus.taskplatform.accounting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed wallet operations. All mutations join the caller's
 * transaction so task state, balances, and ledger entries commit together.
 */
@Service
public class PersistentAccountingService implements AccountingService {

	private final WalletAccountRepository accountRepository;
	private final WalletTransactionRepository transactionRepository;

	public PersistentAccountingService(WalletAccountRepository accountRepository,
			WalletTransactionRepository transactionRepository) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
	}

	@Override
	@Transactional
	public void freeze(long userId, String taskId, long amount) {
		requirePositive(amount);
		WalletAccount account = lockOrCreate(userId);
		requireAvailable(account, amount);
		account.setAvailableBalance(account.getAvailableBalance() - amount);
		account.setFrozenBalance(Math.addExact(account.getFrozenBalance(), amount));
		record(userId, taskId, null, WalletTransactionType.FREEZE, -amount, account.getAvailableBalance());
	}

	@Override
	@Transactional
	public void unfreeze(long userId, String taskId, long amount) {
		requirePositive(amount);
		WalletAccount account = lockOrCreate(userId);
		requireFrozen(account, amount);
		account.setFrozenBalance(account.getFrozenBalance() - amount);
		account.setAvailableBalance(Math.addExact(account.getAvailableBalance(), amount));
		record(userId, taskId, null, WalletTransactionType.UNFREEZE, amount, account.getAvailableBalance());
	}

	@Override
	@Transactional
	public void adjustFrozen(long userId, String taskId, long delta) {
		if (delta > 0) {
			freeze(userId, taskId, delta);
		}
		else if (delta < 0) {
			unfreeze(userId, taskId, Math.negateExact(delta));
		}
	}

	@Override
	@Transactional
	public void settle(String taskId, long publisherId, long runnerId, long amount) {
		requirePositive(amount);
		if (publisherId == runnerId) {
			throw new IllegalArgumentException("Task participants must be different users");
		}
		List<WalletAccount> accounts = lockAccounts(publisherId, runnerId);
		WalletAccount publisher = account(accounts, publisherId);
		WalletAccount runner = account(accounts, runnerId);
		requireFrozen(publisher, amount);
		publisher.setFrozenBalance(publisher.getFrozenBalance() - amount);
		runner.setAvailableBalance(Math.addExact(runner.getAvailableBalance(), amount));
		record(publisherId, taskId, null, WalletTransactionType.EXPENSE,
				-amount, publisher.getAvailableBalance());
		record(runnerId, taskId, null, WalletTransactionType.INCOME,
				amount, runner.getAvailableBalance());
	}

	@Override
	@Transactional(readOnly = true)
	public long getAvailableBalance(long userId) {
		return accountRepository.findById(userId).map(WalletAccount::getAvailableBalance).orElse(0L);
	}

	@Override
	@Transactional(readOnly = true)
	public long getFrozenBalance(long userId) {
		return accountRepository.findById(userId).map(WalletAccount::getFrozenBalance).orElse(0L);
	}

	@Override
	@Transactional
	public void openAccount(long userId, long initialBalance) {
		if (initialBalance < 0) {
			throw new IllegalArgumentException("Initial balance cannot be negative");
		}
		if (accountRepository.existsById(userId)) {
			throw new IllegalStateException("Wallet account already exists");
		}
		accountRepository.saveAndFlush(new WalletAccount(userId, initialBalance, 0));
		if (initialBalance > 0) {
			record(userId, null, null, WalletTransactionType.INCOME, initialBalance, initialBalance);
		}
	}

	@Override
	@Transactional
	public void settleFrozenReward(String taskId, Long publisherId, Long runnerId,
			long refundToPublisher, long payToRunner) {
		if (publisherId == null || refundToPublisher < 0 || payToRunner < 0
				|| (payToRunner > 0 && (runnerId == null || publisherId.equals(runnerId)))) {
			throw new IllegalArgumentException("Invalid cancellation settlement participants or amounts");
		}
		long total = Math.addExact(refundToPublisher, payToRunner);
		requirePositive(total);

		List<WalletAccount> accounts = payToRunner > 0
				? lockAccounts(publisherId, runnerId)
				: List.of(lockOrCreate(publisherId));
		WalletAccount publisher = account(accounts, publisherId);
		requireFrozen(publisher, total);
		publisher.setFrozenBalance(publisher.getFrozenBalance() - total);
		if (refundToPublisher > 0) {
			publisher.setAvailableBalance(Math.addExact(publisher.getAvailableBalance(), refundToPublisher));
			record(publisherId, taskId, null, WalletTransactionType.UNFREEZE,
					refundToPublisher, publisher.getAvailableBalance());
		}
		if (payToRunner > 0) {
			WalletAccount runner = account(accounts, runnerId);
			runner.setAvailableBalance(Math.addExact(runner.getAvailableBalance(), payToRunner));
			record(runnerId, taskId, null, WalletTransactionType.INCOME,
					payToRunner, runner.getAvailableBalance());
		}
	}

	@Override
	@Transactional
	public List<UUID> transferAvailable(String taskId, UUID appealId,
			long fromUserId, long toUserId, long amount) {
		requirePositive(amount);
		if (fromUserId == toUserId) {
			throw new IllegalArgumentException("Transfer parties must be different users");
		}
		List<WalletAccount> accounts = lockAccounts(fromUserId, toUserId);
		WalletAccount from = account(accounts, fromUserId);
		WalletAccount to = account(accounts, toUserId);
		requireAvailable(from, amount);
		from.setAvailableBalance(from.getAvailableBalance() - amount);
		to.setAvailableBalance(Math.addExact(to.getAvailableBalance(), amount));

		UUID debitId = UUID.randomUUID();
		UUID creditId = UUID.randomUUID();
		Instant now = Instant.now();
		transactionRepository.save(new WalletTransaction(debitId, fromUserId, taskId, appealId,
				WalletTransactionType.EXPENSE, -amount, from.getAvailableBalance(), now));
		transactionRepository.save(new WalletTransaction(creditId, toUserId, taskId, appealId,
				WalletTransactionType.COMPENSATION, amount, to.getAvailableBalance(), now));
		return List.of(debitId, creditId);
	}

	private WalletAccount lockOrCreate(long userId) {
		return accountRepository.findByIdForUpdate(userId).orElseGet(() -> {
			accountRepository.saveAndFlush(new WalletAccount(userId, 0, 0));
			return accountRepository.findByIdForUpdate(userId)
					.orElseThrow(() -> new IllegalStateException("Wallet account was not created"));
		});
	}

	private List<WalletAccount> lockAccounts(long firstUserId, long secondUserId) {
		List<Long> userIds = new ArrayList<>(List.of(firstUserId, secondUserId));
		userIds.sort(Comparator.naturalOrder());
		WalletAccount first = lockOrCreate(userIds.get(0));
		WalletAccount second = lockOrCreate(userIds.get(1));
		return List.of(first, second);
	}

	private static WalletAccount account(List<WalletAccount> accounts, long userId) {
		return accounts.stream()
				.filter(account -> account.getUserId() == userId)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Wallet account lock is missing"));
	}

	private void record(long userId, String taskId, UUID appealId, WalletTransactionType type,
			long amount, long balanceAfter) {
		transactionRepository.save(new WalletTransaction(UUID.randomUUID(), userId, taskId, appealId,
				type, amount, balanceAfter, Instant.now()));
	}

	private static void requireAvailable(WalletAccount account, long amount) {
		if (account.getAvailableBalance() < amount) {
			throw new InsufficientBalanceException("余额不足");
		}
	}

	private static void requireFrozen(WalletAccount account, long amount) {
		if (account.getFrozenBalance() < amount) {
			throw new IllegalStateException("冻结余额不足，账务状态与业务状态不一致");
		}
	}

	private static void requirePositive(long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
	}
}
