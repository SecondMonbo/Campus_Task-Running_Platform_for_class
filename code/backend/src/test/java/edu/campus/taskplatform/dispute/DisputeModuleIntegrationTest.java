package edu.campus.taskplatform.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import edu.campus.taskplatform.accounting.AccountingService;
import edu.campus.taskplatform.accounting.WalletAccount;
import edu.campus.taskplatform.accounting.WalletAccountRepository;
import edu.campus.taskplatform.accounting.WalletTransactionRepository;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.SessionCurrentUserService;
import edu.campus.taskplatform.dispute.appeal.AppealResult;
import edu.campus.taskplatform.dispute.appeal.AppealService;
import edu.campus.taskplatform.dispute.appeal.dto.AppealDecisionRequest;
import edu.campus.taskplatform.dispute.appeal.dto.CreateAppealRequest;
import edu.campus.taskplatform.dispute.cancellation.CancellationDecision;
import edu.campus.taskplatform.dispute.cancellation.CancellationService;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationResponseRequest;
import edu.campus.taskplatform.dispute.cancellation.dto.CreateCancellationRequest;
import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskRepository;
import edu.campus.taskplatform.task.TaskStatus;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class DisputeModuleIntegrationTest {

	private static final Long PUBLISHER_ID = 101L;
	private static final Long RUNNER_ID = 102L;
	private static final Long ADMIN_ID = 103L;
	private static final String TASK_ID = "00000000-0000-0000-0000-000000000101";
	private static final int REWARD = 30;

	@Autowired
	CancellationService cancellationService;

	@Autowired
	AppealService appealService;

	@Autowired
	TaskRepository taskRepository;

	@Autowired
	WalletAccountRepository accountRepository;

	@Autowired
	WalletTransactionRepository transactionRepository;

	@Autowired
	AccountingService accountingService;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		taskRepository.deleteAll();
		accountRepository.save(new WalletAccount(PUBLISHER_ID, 100, 0));
		accountRepository.save(new WalletAccount(RUNNER_ID, 100, 0));
	}

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void cancellationUsesSessionTaskRepositoryAndPersistentLedger() {
		accountingService.freeze(PUBLISHER_ID, TASK_ID, REWARD);
		Task task = task(TaskStatus.IN_PROGRESS);
		task.setRunnerId(RUNNER_ID);
		taskRepository.save(task);

		useUser(PUBLISHER_ID, "USER");
		var request = cancellationService.create(TASK_ID, new CreateCancellationRequest("无法继续"));

		useUser(RUNNER_ID, "USER");
		cancellationService.respond(UUID.fromString(request.requestId()),
				new CancellationResponseRequest(CancellationDecision.APPROVE, "同意"));

		assertEquals(TaskStatus.CANCELLED, taskRepository.findById(TASK_ID).orElseThrow().getStatus());
		assertEquals(100, accountingService.getAvailableBalance(PUBLISHER_ID));
		assertEquals(0, accountingService.getFrozenBalance(PUBLISHER_ID));
		assertEquals(2, transactionRepository.findByTaskIdAndType(TASK_ID,
				edu.campus.taskplatform.accounting.WalletTransactionType.FREEZE).size()
				+ transactionRepository.findByTaskIdAndType(TASK_ID,
						edu.campus.taskplatform.accounting.WalletTransactionType.UNFREEZE).size());
	}

	@Test
	void appealDecisionTransfersPersistentFundsAndStoresLinkedTransactions() {
		taskRepository.save(task(TaskStatus.COMPLETED));
		useUser(PUBLISHER_ID, "USER");
		var appeal = appealService.create(TASK_ID,
				new CreateAppealRequest(edu.campus.taskplatform.dispute.appeal.AppealReason.OTHER, "履约有问题"));

		useUser(ADMIN_ID, "ADMIN");
		var decided = appealService.decide(UUID.fromString(appeal.appealId()),
				new AppealDecisionRequest(AppealResult.SUPPORTED, 20, "部分支持"));

		assertEquals(2, decided.transactionIds().size());
		assertEquals(80, accountingService.getAvailableBalance(RUNNER_ID));
		assertEquals(120, accountingService.getAvailableBalance(PUBLISHER_ID));
		assertEquals(2, transactionRepository.findAll().stream()
				.filter(transaction -> UUID.fromString(appeal.appealId()).equals(transaction.getAppealId()))
				.count());
	}

	private static Task task(TaskStatus status) {
		Task task = new Task(TASK_ID, PUBLISHER_ID, "任务", "说明", REWARD,
				Instant.now().plusSeconds(3600), "取件地", "送达地", Instant.now());
		task.setStatus(status);
		task.setRunnerId(RUNNER_ID);
		return task;
	}

	private static void useUser(Long userId, String role) {
		HttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE, new CurrentUser(userId, role));
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}
}
