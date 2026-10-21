package edu.campus.taskplatform.dispute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import edu.campus.taskplatform.common.api.PageResult;
import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.cancellation.CancellationDecision;
import edu.campus.taskplatform.dispute.cancellation.CancellationRequestDto;
import edu.campus.taskplatform.dispute.cancellation.CancellationRequestRepository;
import edu.campus.taskplatform.dispute.cancellation.CancellationRuling;
import edu.campus.taskplatform.dispute.cancellation.CancellationService;
import edu.campus.taskplatform.dispute.cancellation.CancellationStatus;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationArbitrationRequest;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationResponseRequest;
import edu.campus.taskplatform.dispute.cancellation.dto.CreateCancellationRequest;
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
class CancellationServiceTest {

	private static final int REWARD = 30;

	@Autowired
	CancellationService service;

	@Autowired
	CancellationRequestRepository repository;

	@Autowired
	MutableCurrentUserPort currentUser;

	@Autowired
	InMemoryTaskPort taskPort;

	@Autowired
	InMemoryLedgerPort ledger;

	UUID publisher;
	UUID runner;
	UUID outsider;
	UUID admin;
	UUID taskId;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		taskPort.reset();
		ledger.reset();
		currentUser.signOut();
		publisher = UUID.randomUUID();
		runner = UUID.randomUUID();
		outsider = UUID.randomUUID();
		admin = UUID.randomUUID();
		taskId = UUID.randomUUID();
	}

	@Test
	void create_byParticipant_startsPendingResponse() {
		givenTask(TaskStatus.IN_PROGRESS);
		currentUser.signInAsUser(publisher);

		CancellationRequestDto dto = service.create(taskId, new CreateCancellationRequest("时间冲突"));

		assertEquals(CancellationStatus.PENDING_RESPONSE, dto.status());
		assertEquals(taskId.toString(), dto.taskId());
		assertEquals(publisher.toString(), dto.requesterId());
		assertEquals("时间冲突", dto.reason());
		assertEquals(0, dto.refundAmount());
		assertNull(dto.ruling());
		assertEquals(1, repository.count());
	}

	@Test
	void create_byRunner_startsPendingResponse() {
		givenTask(TaskStatus.WAITING_FOR_ACCEPTANCE);
		currentUser.signInAsUser(runner);

		CancellationRequestDto dto = service.create(taskId, new CreateCancellationRequest("送错地址"));

		assertEquals(CancellationStatus.PENDING_RESPONSE, dto.status());
		assertEquals(runner.toString(), dto.requesterId());
	}

	@Test
	void create_rejectsNonParticipant() {
		givenTask(TaskStatus.IN_PROGRESS);
		currentUser.signInAsUser(outsider);

		assertApiError(() -> service.create(taskId, new CreateCancellationRequest("与我无关")), ErrorCode.FORBIDDEN);
		assertEquals(0, repository.count());
	}

	@Test
	void create_rejectsTaskNotInCancellableState() {
		givenTask(TaskStatus.WAITING_FOR_TAKER);
		currentUser.signInAsUser(publisher);

		assertApiError(() -> service.create(taskId, new CreateCancellationRequest("尚未接单")), ErrorCode.TASK_STATE_CONFLICT);
	}

	@Test
	void create_rejectsSecondActiveRequest() {
		givenTask(TaskStatus.IN_PROGRESS);
		currentUser.signInAsUser(publisher);
		service.create(taskId, new CreateCancellationRequest("第一次"));

		currentUser.signInAsUser(runner);
		assertApiError(() -> service.create(taskId, new CreateCancellationRequest("第二次")),
				ErrorCode.CANCELLATION_ALREADY_PENDING);
		assertEquals(1, repository.count());
	}

	@Test
	void create_requiresLogin() {
		givenTask(TaskStatus.IN_PROGRESS);

		assertApiError(() -> service.create(taskId, new CreateCancellationRequest("未登录")), ErrorCode.UNAUTHORIZED);
	}

	@Test
	void create_unknownTaskReturnsNotFound() {
		currentUser.signInAsUser(publisher);

		assertApiError(() -> service.create(UUID.randomUUID(), new CreateCancellationRequest("不存在")),
				ErrorCode.TASK_NOT_FOUND);
	}

	@Test
	void respond_approve_cancelsTaskAndRefundsPublisher() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingRequest(publisher);

		currentUser.signInAsUser(runner);
		CancellationRequestDto dto = service.respond(requestId,
				new CancellationResponseRequest(CancellationDecision.APPROVE, "同意取消"));

		assertEquals(CancellationStatus.APPROVED, dto.status());
		assertEquals(REWARD, dto.refundAmount());
		assertEquals(0, dto.runnerAmount());
		assertEquals(TaskStatus.CANCELLED, taskPort.get(taskId).status());
		assertEquals(1, ledger.settlements().size());
		InMemoryLedgerPort.Settlement settlement = ledger.settlements().get(0);
		assertEquals(REWARD, settlement.refundToPublisher());
		assertEquals(0, settlement.payToRunner());
	}

	@Test
	void respond_reject_escalatesWithoutChangingTaskOrLedger() {
		givenTask(TaskStatus.WAITING_FOR_ACCEPTANCE);
		UUID requestId = givenPendingRequest(publisher);

		currentUser.signInAsUser(runner);
		CancellationRequestDto dto = service.respond(requestId,
				new CancellationResponseRequest(CancellationDecision.REJECT, "已送达，不同意取消"));

		assertEquals(CancellationStatus.PENDING_ADMIN, dto.status());
		assertEquals("已送达，不同意取消", dto.responseNote());
		assertEquals(TaskStatus.WAITING_FOR_ACCEPTANCE, taskPort.get(taskId).status());
		assertTrue(ledger.settlements().isEmpty());
	}

	@Test
	void respond_onlyCounterpartyCanRespond() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingRequest(publisher);

		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.respond(requestId,
				new CancellationResponseRequest(CancellationDecision.APPROVE, null)), ErrorCode.FORBIDDEN);

		currentUser.signInAsUser(outsider);
		assertApiError(() -> service.respond(requestId,
				new CancellationResponseRequest(CancellationDecision.APPROVE, null)), ErrorCode.FORBIDDEN);
	}

	@Test
	void respond_twiceIsRejected() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingRequest(publisher);
		currentUser.signInAsUser(runner);
		service.respond(requestId, new CancellationResponseRequest(CancellationDecision.APPROVE, null));

		assertApiError(() -> service.respond(requestId,
				new CancellationResponseRequest(CancellationDecision.APPROVE, null)),
				ErrorCode.CANCELLATION_STATE_CONFLICT);
	}

	@Test
	void respond_rejectsWhenTaskWasCompletedMeanwhile() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingRequest(publisher);
		completeTask();

		currentUser.signInAsUser(runner);
		assertApiError(() -> service.respond(requestId,
				new CancellationResponseRequest(CancellationDecision.APPROVE, null)), ErrorCode.TASK_STATE_CONFLICT);
		assertTrue(ledger.settlements().isEmpty());
	}

	@Test
	void arbitrate_supportContinue_keepsTaskRunning() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingAdminRequest();

		currentUser.signInAsAdmin(admin);
		CancellationRequestDto dto = service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_CONTINUE, 0, 0, "证据不足，继续履约"));

		assertEquals(CancellationStatus.ARBITRATED, dto.status());
		assertEquals("SUPPORT_CONTINUE", dto.ruling());
		assertEquals(TaskStatus.IN_PROGRESS, taskPort.get(taskId).status());
		assertTrue(ledger.settlements().isEmpty());
	}

	@Test
	void arbitrate_supportRequesterCancel_refundsFullReward() {
		givenTask(TaskStatus.WAITING_FOR_ACCEPTANCE);
		UUID requestId = givenPendingAdminRequest();

		currentUser.signInAsAdmin(admin);
		CancellationRequestDto dto = service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_REQUESTER_CANCEL, REWARD, 0, "跑腿员未按约定送达"));

		assertEquals(CancellationStatus.ARBITRATED, dto.status());
		assertEquals(REWARD, dto.refundAmount());
		assertEquals(0, dto.runnerAmount());
		assertEquals(TaskStatus.CANCELLED, taskPort.get(taskId).status());
		assertEquals(REWARD, ledger.settlements().get(0).refundToPublisher());
	}

	@Test
	void arbitrate_cancelProportionally_splitsFrozenReward() {
		givenTask(TaskStatus.WAITING_FOR_ACCEPTANCE);
		UUID requestId = givenPendingAdminRequest();

		currentUser.signInAsAdmin(admin);
		service.arbitrate(requestId, arbitration(
				CancellationRuling.CANCEL_PROPORTIONALLY, 20, 10, "部分履约"));

		assertEquals(TaskStatus.CANCELLED, taskPort.get(taskId).status());
		InMemoryLedgerPort.Settlement settlement = ledger.settlements().get(0);
		assertEquals(20, settlement.refundToPublisher());
		assertEquals(10, settlement.payToRunner());
	}

	@Test
	void arbitrate_rejectsSplitThatDoesNotMatchReward() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingAdminRequest();
		currentUser.signInAsAdmin(admin);

		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.CANCEL_PROPORTIONALLY, 20, 5, "金额错误")), ErrorCode.VALIDATION_ERROR);
		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_REQUESTER_CANCEL, 10, 0, "不是全额")), ErrorCode.VALIDATION_ERROR);
		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_CONTINUE, 1, 0, "继续却给了金额")), ErrorCode.VALIDATION_ERROR);
		assertEquals(CancellationStatus.PENDING_ADMIN, repository.findById(requestId).orElseThrow().getStatus());
	}

	@Test
	void arbitrate_rejectsCompletedTask() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingAdminRequest();
		completeTask();

		currentUser.signInAsAdmin(admin);
		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_REQUESTER_CANCEL, REWARD, 0, "任务已完成")),
				ErrorCode.TASK_STATE_CONFLICT);
	}

	@Test
	void arbitrate_requiresAdmin() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingAdminRequest();

		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_CONTINUE, 0, 0, "越权")), ErrorCode.FORBIDDEN);
	}

	@Test
	void arbitrate_twiceIsRejected() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingAdminRequest();
		currentUser.signInAsAdmin(admin);
		service.arbitrate(requestId, arbitration(CancellationRuling.SUPPORT_CONTINUE, 0, 0, "继续"));

		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_CONTINUE, 0, 0, "重复裁决")), ErrorCode.CANCELLATION_STATE_CONFLICT);
	}

	@Test
	void arbitrate_requiresRejectedRequest() {
		givenTask(TaskStatus.IN_PROGRESS);
		UUID requestId = givenPendingRequest(publisher);

		currentUser.signInAsAdmin(admin);
		assertApiError(() -> service.arbitrate(requestId, arbitration(
				CancellationRuling.SUPPORT_CONTINUE, 0, 0, "尚未拒绝")), ErrorCode.CANCELLATION_STATE_CONFLICT);
	}

	@Test
	void listForAdmin_filtersByStatus() {
		givenTask(TaskStatus.IN_PROGRESS);
		givenPendingAdminRequest();

		UUID secondTask = UUID.randomUUID();
		taskId = secondTask;
		givenTask(TaskStatus.IN_PROGRESS);
		givenPendingRequest(publisher);

		currentUser.signInAsAdmin(admin);
		PageResult<CancellationRequestDto> pendingAdmin =
				service.listForAdmin(CancellationStatus.PENDING_ADMIN, 0, 10);
		PageResult<CancellationRequestDto> all = service.listForAdmin(null, 0, 10);

		assertEquals(1, pendingAdmin.total());
		assertEquals(2, all.total());
		assertEquals(10, all.size());
	}

	@Test
	void listForAdmin_requiresAdminAndValidPagination() {
		currentUser.signInAsUser(publisher);
		assertApiError(() -> service.listForAdmin(null, 0, 10), ErrorCode.FORBIDDEN);

		currentUser.signInAsAdmin(admin);
		assertApiError(() -> service.listForAdmin(null, 0, 15), ErrorCode.VALIDATION_ERROR);
		assertApiError(() -> service.listForAdmin(null, -1, 10), ErrorCode.VALIDATION_ERROR);
	}

	private void givenTask(TaskStatus status) {
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, status, REWARD));
	}

	private UUID givenPendingRequest(UUID requesterId) {
		currentUser.signInAsUser(requesterId);
		return UUID.fromString(service.create(taskId, new CreateCancellationRequest("取消原因")).requestId());
	}

	/**
	 * 走完“发起 → 被申请方拒绝”，得到一条待管理员处理的申请。
	 */
	private UUID givenPendingAdminRequest() {
		UUID requester = publisher;
		UUID responder = runner;
		UUID requestId = givenPendingRequest(requester);
		currentUser.signInAsUser(responder);
		service.respond(requestId, new CancellationResponseRequest(CancellationDecision.REJECT, "拒绝"));
		return requestId;
	}

	private void completeTask() {
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, TaskStatus.COMPLETED, REWARD));
	}

	private CancellationArbitrationRequest arbitration(CancellationRuling ruling, int refund, int runnerAmount,
			String detail) {
		return new CancellationArbitrationRequest(ruling, refund, runnerAmount, detail);
	}

	private void assertApiError(Executable executable, ErrorCode expected) {
		ApiException exception = assertThrows(ApiException.class, executable);
		assertEquals(expected, exception.errorCode(), () -> "实际消息：" + exception.getMessage());
	}
}
