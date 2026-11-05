package edu.campus.taskplatform.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import edu.campus.taskplatform.common.api.PageResult;
import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.appeal.AppealDto;
import edu.campus.taskplatform.dispute.appeal.AppealEntity;
import edu.campus.taskplatform.dispute.appeal.AppealReason;
import edu.campus.taskplatform.dispute.appeal.AppealRepository;
import edu.campus.taskplatform.dispute.appeal.AppealResult;
import edu.campus.taskplatform.dispute.appeal.AppealService;
import edu.campus.taskplatform.dispute.appeal.AppealStatus;
import edu.campus.taskplatform.dispute.appeal.dto.AppealDecisionRequest;
import edu.campus.taskplatform.dispute.appeal.dto.CreateAppealRequest;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;
import edu.campus.taskplatform.dispute.port.TaskStatus;
import edu.campus.taskplatform.dispute.support.DisputeTestPortConfiguration;
import edu.campus.taskplatform.dispute.support.InMemoryLedgerPort;
import edu.campus.taskplatform.dispute.support.InMemoryTaskPort;
import edu.campus.taskplatform.dispute.support.MutableCurrentUserPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DisputeTestPortConfiguration.class)
class AppealServiceTest {

	private static final int REWARD = 30;

	@Autowired
	AppealService service;

	@Autowired
	AppealRepository repository;

	@Autowired
	MutableCurrentUserPort currentUser;

	@Autowired
	InMemoryTaskPort taskPort;

	@Autowired
	InMemoryLedgerPort ledger;

	Long publisher;
	Long runner;
	Long outsider;
	Long admin;
	String taskId;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		taskPort.reset();
		ledger.reset();
		currentUser.signOut();
		publisher = 101L;
		runner = 102L;
		outsider = 103L;
		admin = 104L;
		taskId = UUID.randomUUID().toString();
	}

	@Test
	void create_byPublisher_setsRunnerAsRespondent() {
		givenTask(TaskStatus.COMPLETED);
		currentUser.signInAsUser(publisher);

		AppealDto dto = service.create(taskId, new CreateAppealRequest(
				AppealReason.FULFILLMENT_ISSUE, "实际送达物品与约定不符"));

		assertEquals(AppealStatus.PENDING, dto.status());
		assertEquals(publisher.toString(), dto.appellantId());
		assertEquals(runner.toString(), dto.respondentId());
		assertEquals(AppealReason.FULFILLMENT_ISSUE, dto.reason());
		assertEquals(0, dto.transferAmount());
		assertNull(dto.result());
		assertTrue(dto.transactionIds().isEmpty());
		assertEquals(1, repository.count());
	}

	@Test
	void create_byRunner_setsPublisherAsRespondent() {
		givenTask(TaskStatus.COMPLETED);
		currentUser.signInAsUser(runner);

		AppealDto dto = service.create(taskId, new CreateAppealRequest(AppealReason.SETTLEMENT_ISSUE, "少结算"));

		assertEquals(publisher.toString(), dto.respondentId());
	}

	@Test
	void create_rejectsNonParticipant() {
		givenTask(TaskStatus.COMPLETED);
		currentUser.signInAsUser(outsider);

		assertApiError(() -> service.create(taskId, new CreateAppealRequest(AppealReason.OTHER, "路人")),
				ErrorCode.FORBIDDEN);
		assertEquals(0, repository.count());
	}

	@Test
	void create_rejectsTaskNotCompleted() {
		givenTask(TaskStatus.WAITING_FOR_ACCEPTANCE);
		currentUser.signInAsUser(publisher);

		assertApiError(() -> service.create(taskId, new CreateAppealRequest(AppealReason.OTHER, "未完成")),
				ErrorCode.TASK_STATE_CONFLICT);
	}

	@Test
	void create_eachParticipantMayAppealOnce() {
		givenTask(TaskStatus.COMPLETED);
		givenAppeal(publisher);

		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.create(taskId, new CreateAppealRequest(AppealReason.OTHER, "重复申诉")),
				ErrorCode.APPEAL_ALREADY_EXISTS);

		currentUser.signInAsUser(runner);
		AppealDto second = service.create(taskId, new CreateAppealRequest(AppealReason.SETTLEMENT_ISSUE, "结算有误"));
		assertEquals(runner.toString(), second.appellantId());
		assertEquals(2, repository.count());
	}

	@Test
	void create_requiresLoginAndExistingTask() {
		givenTask(TaskStatus.COMPLETED);
		assertApiError(() -> service.create(taskId, new CreateAppealRequest(AppealReason.OTHER, "未登录")),
				ErrorCode.UNAUTHORIZED);

		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.create(UUID.randomUUID().toString(),
				new CreateAppealRequest(AppealReason.OTHER, "不存在")),
				ErrorCode.TASK_NOT_FOUND);
	}

	@Test
	void decide_rejected_keepsBalancesUntouched() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 50);

		currentUser.signInAsAdmin(admin);
		AppealDto dto = service.decide(appealId, new AppealDecisionRequest(AppealResult.REJECTED, 0, "证据不足"));

		assertEquals(AppealStatus.DECIDED, dto.status());
		assertEquals(AppealResult.REJECTED, dto.result());
		assertEquals(0, dto.transferAmount());
		assertTrue(dto.transactionIds().isEmpty());
		assertEquals(50, ledger.availableBalance(runner));
		assertEquals(0, ledger.availableBalance(publisher));
	}

	@Test
	void decide_supported_transfersFromRespondentToAppellant() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);

		currentUser.signInAsAdmin(admin);
		AppealDto dto = service.decide(appealId,
				new AppealDecisionRequest(AppealResult.SUPPORTED, 10, "核实属实"));

		assertEquals(AppealResult.SUPPORTED, dto.result());
		assertEquals(10, dto.transferAmount());
		assertEquals(2, dto.transactionIds().size());
		assertEquals(5, ledger.availableBalance(runner));
		assertEquals(10, ledger.availableBalance(publisher));
		assertEquals(TaskStatus.COMPLETED, taskPort.get(taskId).status());
	}

	@Test
	void decide_partiallySupported_alsoTransfers() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);

		currentUser.signInAsAdmin(admin);
		AppealDto dto = service.decide(appealId,
				new AppealDecisionRequest(AppealResult.PARTIALLY_SUPPORTED, 5, "部分属实"));

		assertEquals(5, dto.transferAmount());
		assertEquals(5, ledger.availableBalance(publisher));
		assertEquals(10, ledger.availableBalance(runner));
	}

	@Test
	void decide_nullTransferIds_rollsBackAndAppealStaysPending() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);
		ledger.overrideNextTransfer(InMemoryLedgerPort.TransferOutcome.NULL_IDS);
		currentUser.signInAsAdmin(admin);

		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.SUPPORTED, 10, "账务漏返回流水")), ErrorCode.INTERNAL_ERROR);

		assertAppealRemainsPending(appealId);
	}

	@Test
	void decide_emptyTransferIds_rollsBackAndAppealStaysPending() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);
		ledger.overrideNextTransfer(InMemoryLedgerPort.TransferOutcome.EMPTY_IDS);
		currentUser.signInAsAdmin(admin);

		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.SUPPORTED, 10, "账务未产生流水")), ErrorCode.INTERNAL_ERROR);

		assertAppealRemainsPending(appealId);
	}

	@Test
	void decide_tooManyTransferIds_rollsBackAndAppealStaysPending() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);
		ledger.overrideNextTransfer(InMemoryLedgerPort.TransferOutcome.TOO_MANY_IDS);
		currentUser.signInAsAdmin(admin);

		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.SUPPORTED, 10, "流水条数非法")), ErrorCode.INTERNAL_ERROR);

		assertAppealRemainsPending(appealId);
	}

	@Test
	void decide_rejectsAmountOutsideResultSemantics() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);
		currentUser.signInAsAdmin(admin);

		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.REJECTED, 5, "驳回却划转")), ErrorCode.VALIDATION_ERROR);
		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.SUPPORTED, 0, "支持却不划转")), ErrorCode.VALIDATION_ERROR);
	}

	@Test
	void decide_rejectsTransferExceedingAvailableBalance() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 3);
		currentUser.signInAsAdmin(admin);

		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.SUPPORTED, 10, "余额不足")), ErrorCode.INSUFFICIENT_BALANCE);
		assertEquals(AppealStatus.PENDING, repository.findById(appealId).orElseThrow().getStatus());
		assertEquals(3, ledger.availableBalance(runner));
	}

	@Test
	void decide_requiresAdmin() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		ledger.setBalance(runner, 15);

		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.REJECTED, 0, "越权")), ErrorCode.FORBIDDEN);
	}

	@Test
	void decide_twiceIsRejected() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		currentUser.signInAsAdmin(admin);
		service.decide(appealId, new AppealDecisionRequest(AppealResult.REJECTED, 0, "驳回"));

		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.REJECTED, 0, "重复裁决")), ErrorCode.APPEAL_ALREADY_DECIDED);
	}

	@Test
	void decide_rejectsWhenTaskIsNoLongerCompleted() {
		givenTask(TaskStatus.COMPLETED);
		UUID appealId = givenAppeal(publisher);
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, TaskStatus.CANCELLED, REWARD));

		currentUser.signInAsAdmin(admin);
		assertApiError(() -> service.decide(appealId,
				new AppealDecisionRequest(AppealResult.REJECTED, 0, "任务状态异常")), ErrorCode.TASK_STATE_CONFLICT);
	}

	@Test
	void listForAdmin_filtersAndRequiresAdmin() {
		givenTask(TaskStatus.COMPLETED);
		UUID first = givenAppeal(publisher);
		currentUser.signInAsAdmin(admin);
		service.decide(first, new AppealDecisionRequest(AppealResult.REJECTED, 0, "驳回"));

		currentUser.signInAsUser(runner);
		service.create(taskId, new CreateAppealRequest(AppealReason.OTHER, "第二条"));

		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.listForAdmin(null, 0, 10), ErrorCode.FORBIDDEN);

		currentUser.signInAsAdmin(admin);
		PageResult<AppealDto> pending = service.listForAdmin(AppealStatus.PENDING, 0, 10);
		PageResult<AppealDto> all = service.listForAdmin(null, 0, 10);
		assertEquals(1, pending.total());
		assertEquals(2, all.total());
	}

	private void givenTask(TaskStatus status) {
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, status, REWARD));
	}

	private UUID givenAppeal(Long appellantId) {
		currentUser.signInAsUser(appellantId);
		return UUID.fromString(service.create(taskId,
				new CreateAppealRequest(AppealReason.OTHER, "申诉说明")).appealId());
	}

	/**
	 * 重新查询申诉，确认裁决失败后没有留下任何“已处理”痕迹。
	 * 不访问懒加载的 transactionIds，避免在事务外触发 LazyInitializationException。
	 */
	private void assertAppealRemainsPending(UUID appealId) {
		AppealEntity reloaded = repository.findById(appealId).orElseThrow();
		assertEquals(AppealStatus.PENDING, reloaded.getStatus());
		assertNull(reloaded.getResult());
		assertEquals(0, reloaded.getTransferAmount());
		assertNull(reloaded.getDecidedAt());
		assertNull(reloaded.getDecidedBy());
	}

	private void assertApiError(Executable executable, ErrorCode expected) {
		ApiException exception = assertThrows(ApiException.class, executable);
		assertEquals(expected, exception.errorCode(), () -> "实际消息：" + exception.getMessage());
	}
}
