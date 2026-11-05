package edu.campus.taskplatform.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PersistentAccountingServiceTest {

	@Autowired
	AccountingService accounting;

	@Autowired
	WalletAccountRepository accountRepository;

	@Autowired
	WalletTransactionRepository transactionRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		accountRepository.save(new WalletAccount(11L, 100, 0));
		accountRepository.save(new WalletAccount(22L, 0, 0));
	}

	@Test
	void freezeUnfreezeAndTaskSettlementPersistBalancesAndTransactions() {
		accounting.freeze(11L, "task-a", 30);
		assertEquals(70, accounting.getAvailableBalance(11L));
		assertEquals(30, accounting.getFrozenBalance(11L));

		accounting.settle("task-a", 11L, 22L, 20);
		assertEquals(70, accounting.getAvailableBalance(11L));
		assertEquals(10, accounting.getFrozenBalance(11L));
		assertEquals(20, accounting.getAvailableBalance(22L));

		accounting.unfreeze(11L, "task-a", 10);
		assertEquals(80, accounting.getAvailableBalance(11L));
		assertEquals(0, accounting.getFrozenBalance(11L));
		assertEquals(4, transactionRepository.findByTaskIdAndType("task-a", WalletTransactionType.FREEZE).size()
				+ transactionRepository.findByTaskIdAndType("task-a", WalletTransactionType.EXPENSE).size()
				+ transactionRepository.findByTaskIdAndType("task-a", WalletTransactionType.INCOME).size()
				+ transactionRepository.findByTaskIdAndType("task-a", WalletTransactionType.UNFREEZE).size());
	}

	@Test
	void appealTransferWritesPairedTraceableLedgerEntries() {
		accounting.freeze(11L, "task-b", 40);
		UUID appealId = UUID.randomUUID();

		var ids = accounting.transferAvailable("task-b", appealId, 11L, 22L, 15);

		assertEquals(2, ids.size());
		assertEquals(45, accounting.getAvailableBalance(11L));
		assertEquals(15, accounting.getAvailableBalance(22L));
		assertEquals(2, transactionRepository.findAll().stream()
				.filter(transaction -> appealId.equals(transaction.getAppealId())).count());
	}

	@Test
	void failedOuterTransactionRollsBackBalancesAndLedgerEntries() {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
			accounting.transferAvailable("task-c", UUID.randomUUID(), 11L, 22L, 25);
			throw new IllegalStateException("force rollback");
		}));

		assertEquals(100, accounting.getAvailableBalance(11L));
		assertEquals(0, accounting.getAvailableBalance(22L));
		assertEquals(0, transactionRepository.count());
	}
}
