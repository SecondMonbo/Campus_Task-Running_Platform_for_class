package edu.campus.taskplatform.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.campus.taskplatform.accounting.AccountingService;
import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.AuthService;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.RegisterRequest;
import edu.campus.taskplatform.auth.SessionCurrentUserService;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.complaint.Complaint;
import edu.campus.taskplatform.complaint.ComplaintAction;
import edu.campus.taskplatform.complaint.ComplaintRepository;
import edu.campus.taskplatform.complaint.ComplaintService;
import edu.campus.taskplatform.complaint.ComplaintStatus;
import edu.campus.taskplatform.complaint.dto.ComplaintActionRequest;
import edu.campus.taskplatform.credit.dto.CreditSummaryDto;
import edu.campus.taskplatform.task.PublicationReviewDecision;
import edu.campus.taskplatform.task.PublicationReviewStatus;
import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskPublicationReviewService;
import edu.campus.taskplatform.task.TaskRepository;
import edu.campus.taskplatform.task.TaskReviewRepository;
import edu.campus.taskplatform.task.TaskService;
import edu.campus.taskplatform.task.TaskStatus;
import edu.campus.taskplatform.task.dto.CreateReviewRequest;
import edu.campus.taskplatform.task.dto.PublicationReviewDecisionRequest;
import edu.campus.taskplatform.task.dto.TaskCreateRequest;
import edu.campus.taskplatform.task.dto.TaskView;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 信用分与低分限制的集成测试。显式开启事件即时消费，验证业务事务提交后
 * 信用分、记录、限制状态和发布复核的联动。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"credit.events.auto-process=true",
		"credit.events.retry-enabled=false"
})
class CreditScoreIntegrationTest {

	@Autowired AuthService authService;
	@Autowired CreditScoreService creditScoreService;
	@Autowired CreditEventPublisher creditEvents;
	@Autowired CreditRecordRepository creditRecords;
	@Autowired AppUserRepository users;
	@Autowired TaskService taskService;
	@Autowired TaskRepository tasks;
	@Autowired TaskReviewRepository reviews;
	@Autowired AccountingService accounting;
	@Autowired TaskPublicationReviewService publicationReviewService;
	@Autowired ComplaintService complaintService;
	@Autowired ComplaintRepository complaints;

	@AfterEach
	void clearRequestContext() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void registrationInitializesCreditAt80WithBaselineRecord() {
		String username = "credit-init-" + UUID.randomUUID();
		var registered = authService.register(new RegisterRequest(username, "password123", "初始化用户", null));

		AppUser user = users.findById(Long.valueOf(registered.userId())).orElseThrow();
		assertEquals(80, user.getCreditScore());
		assertFalse(user.isCreditRestricted());
		assertTrue(creditRecords.findByUserId(user.getUserId(), org.springframework.data.domain.PageRequest.of(0, 10))
				.getContent().stream()
				.anyMatch(record -> record.getEventType() == CreditEventType.INITIALIZATION
						&& record.getScoreBefore() == null && record.getScoreAfter() == 80));
	}

	@Test
	void reviewsApplyFiveStarZeroAndThirtyDayLowStarDeduplication() {
		AppUser publisher = user();
		AppUser runner = user();

		taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(5));
		assertEquals(82, reload(runner).getCreditScore());

		taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(3));
		assertEquals(82, reload(runner).getCreditScore());

		taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(1));
		assertEquals(79, reload(runner).getCreditScore());

		taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(2));
		assertEquals(79, reload(runner).getCreditScore());
	}

	@Test
	void completionAppliesOnTimeAndLateRules() {
		AppUser publisher = user();
		AppUser runner = user();
		accounting.openAccount(publisher.getUserId(), 100);
		accounting.openAccount(runner.getUserId(), 0);

		Instant now = Instant.now();
		Task onTime = saveTask(publisher, runner, TaskStatus.WAITING_FOR_ACCEPTANCE,
				now.plus(Duration.ofHours(1)), now, 20);
		accounting.freeze(publisher.getUserId(), onTime.getTaskId(), 20);
		taskService.completeTask(current(publisher), onTime.getTaskId());
		assertEquals(81, reload(runner).getCreditScore());

		Instant lateDeadline = now.minus(Duration.ofDays(2));
		Task late = saveTask(publisher, runner, TaskStatus.WAITING_FOR_ACCEPTANCE,
				lateDeadline, lateDeadline.plus(Duration.ofHours(25)), 20);
		accounting.freeze(publisher.getUserId(), late.getTaskId(), 20);
		taskService.completeTask(current(publisher), late.getTaskId());
		assertEquals(76, reload(runner).getCreditScore());
	}

	@Test
	void lowScoreRestrictionPublishesForReviewAndLiftsAt75() {
		AppUser restricted = user();
		AppUser other = user();
		AppUser admin = user();
		accounting.openAccount(restricted.getUserId(), 100);

		creditScoreService.adjustByAdmin(restricted.getUserId(), -21, "复核扣分", admin.getUserId());
		assertEquals(59, reload(restricted).getCreditScore());
		assertTrue(reload(restricted).isCreditRestricted());

		// 60 分不触发新限制，但已受限用户要继续保持到 75 分。
		creditScoreService.adjustByAdmin(restricted.getUserId(), 1, "复核调整", admin.getUserId());
		assertEquals(60, reload(restricted).getCreditScore());
		assertTrue(reload(restricted).isCreditRestricted());

		TaskView pending = taskService.createTask(current(restricted), taskRequest(10));
		assertEquals(PublicationReviewStatus.PENDING_REVIEW, pending.getPublicationReviewStatus());
		assertTrue(taskService.listTasks(null, null, 0, 10).getItems().stream()
				.noneMatch(task -> task.getTaskId().equals(pending.getTaskId())));
		assertThrows(TaskException.class,
				() -> taskService.getTask(current(other), pending.getTaskId()));

		Task normal = saveTask(other, restricted, TaskStatus.WAITING_FOR_TAKER,
				Instant.now().plus(Duration.ofHours(1)), null, 10);
		TaskException acceptError = assertThrows(TaskException.class,
				() -> taskService.acceptTask(current(restricted), normal.getTaskId()));
		assertEquals(ErrorCode.FORBIDDEN, acceptError.getErrorCode());

		asUser(admin.getUserId(), "ADMIN");
		TaskView approved = publicationReviewService.decide(pending.getTaskId(),
				new PublicationReviewDecisionRequest(PublicationReviewDecision.APPROVE, "资料齐全"));
		assertEquals(PublicationReviewStatus.APPROVED, approved.getPublicationReviewStatus());
		assertEquals(PublicationReviewStatus.APPROVED,
				taskService.getTask(current(other), pending.getTaskId()).getPublicationReviewStatus());

		creditScoreService.adjustByAdmin(restricted.getUserId(), 15, "恢复", admin.getUserId());
		assertEquals(75, reload(restricted).getCreditScore());
		assertFalse(reload(restricted).isCreditRestricted());
		TaskView normalPublish = taskService.createTask(current(restricted), taskRequest(10));
		assertEquals(PublicationReviewStatus.NOT_REQUIRED, normalPublish.getPublicationReviewStatus());
	}

	@Test
	void rejectedPublicationCancelsTaskAndRefundsReward() {
		AppUser restricted = user();
		AppUser admin = user();
		accounting.openAccount(restricted.getUserId(), 100);
		creditScoreService.adjustByAdmin(restricted.getUserId(), -30, "复核扣分", admin.getUserId());

		TaskView pending = taskService.createTask(current(restricted), taskRequest(10));
		assertEquals(90, accounting.getAvailableBalance(restricted.getUserId()));
		assertEquals(10, accounting.getFrozenBalance(restricted.getUserId()));

		asUser(admin.getUserId(), "ADMIN");
		TaskView rejected = publicationReviewService.decide(pending.getTaskId(),
				new PublicationReviewDecisionRequest(PublicationReviewDecision.REJECT, "信息不完整"));

		assertEquals(PublicationReviewStatus.REJECTED, rejected.getPublicationReviewStatus());
		assertEquals(TaskStatus.CANCELLED, rejected.getStatus());
		assertEquals(100, accounting.getAvailableBalance(restricted.getUserId()));
		assertEquals(0, accounting.getFrozenBalance(restricted.getUserId()));
	}

	@Test
	void complaintCreditAdjustmentIsValidatedAndApplied() {
		AppUser reporter = user();
		AppUser target = user();
		AppUser admin = user();
		Complaint complaint = complaints.saveAndFlush(
				new Complaint(reporter.getUserId(), target.getUserId(), null, "违约", "未按约定送达"));
		asUser(admin.getUserId(), "ADMIN");

		complaintService.process(complaint.getComplaintId(), new ComplaintActionRequest(
				List.of(ComplaintAction.CREDIT_ADJUSTMENT), -5, null, "一般违约，扣 5 分"));

		Complaint processed = complaints.findById(complaint.getComplaintId()).orElseThrow();
		assertEquals(ComplaintStatus.PROCESSED, processed.getStatus());
		assertEquals(-5, processed.getCreditAdjustment());
		assertEquals(75, reload(target).getCreditScore());
	}

	@Test
	void complaintCreditAdjustmentRequiresTargetAndAllowedTier() {
		AppUser reporter = user();
		AppUser admin = user();
		Complaint noTarget = complaints.saveAndFlush(
				new Complaint(reporter.getUserId(), null, null, "举报", "任务内容违规"));
		asUser(admin.getUserId(), "ADMIN");

		TaskException missingTarget = assertThrows(TaskException.class,
				() -> complaintService.process(noTarget.getComplaintId(), new ComplaintActionRequest(
						List.of(ComplaintAction.CREDIT_ADJUSTMENT), -2, null, "轻微违规")));
		assertEquals(ErrorCode.VALIDATION_ERROR, missingTarget.getErrorCode());

		AppUser target = user();
		Complaint invalidTier = complaints.saveAndFlush(
				new Complaint(reporter.getUserId(), target.getUserId(), null, "违约", "违规"));
		TaskException badTier = assertThrows(TaskException.class,
				() -> complaintService.process(invalidTier.getComplaintId(), new ComplaintActionRequest(
						List.of(ComplaintAction.CREDIT_ADJUSTMENT), -4, null, "档位错误")));
		assertEquals(ErrorCode.VALIDATION_ERROR, badTier.getErrorCode());
	}

	@Test
	void warningTierPublisherTasksAreOrderedAfterNormalTasks() {
		AppUser warning = user();
		AppUser normal = user();
		AppUser admin = user();
		accounting.openAccount(warning.getUserId(), 100);
		accounting.openAccount(normal.getUserId(), 100);
		creditScoreService.adjustByAdmin(warning.getUserId(), -10, "进入预警档", admin.getUserId());

		String keyword = "sort-" + UUID.randomUUID();
		TaskView normalTask = taskService.createTask(current(normal), taskRequest(10, keyword + "-normal"));
		TaskView warningTask = taskService.createTask(current(warning), taskRequest(10, keyword + "-warning"));

		List<String> orderedIds = taskService.listTasks(TaskStatus.WAITING_FOR_TAKER, keyword, 0, 10)
				.getItems().stream().map(TaskView::getTaskId).toList();
		assertEquals(List.of(normalTask.getTaskId(), warningTask.getTaskId()), orderedIds);
	}

	@Test
	void tenConsecutiveCompletionsRestoreTo75AndLiftRestriction() {
		AppUser user = user();
		AppUser admin = user();
		creditScoreService.adjustByAdmin(user.getUserId(), -30, "复核扣分", admin.getUserId());
		assertEquals(50, reload(user).getCreditScore());
		assertTrue(reload(user).isCreditRestricted());

		for (int i = 0; i < 10; i++) {
			creditEvents.publish(user.getUserId(), CreditEventType.TASK_COMPLETED_IN_WINDOW,
					"recovery-task-" + user.getUserId() + "-" + i, 0, "连续履约测试");
		}

		AppUser recovered = reload(user);
		assertEquals(75, recovered.getCreditScore());
		assertFalse(recovered.isCreditRestricted());
		assertEquals(0, recovered.getConsecutiveCompletedTasks());
		CreditSummaryDto summary = creditScoreService.summary(user.getUserId());
		assertEquals(CreditTier.GOOD, summary.tier());
		assertTrue(summary.canAcceptTasks());
	}

	private AppUser user() {
		String username = "credit-" + UUID.randomUUID();
		return users.saveAndFlush(new AppUser(username, "unused-test-hash", "信用分测试用户", null, Instant.now()));
	}

	private AppUser reload(AppUser user) {
		return users.findById(user.getUserId()).orElseThrow();
	}

	private static CurrentUser current(AppUser user) {
		return new CurrentUser(user.getUserId(), "USER");
	}

	private Task completedTask(AppUser publisher, AppUser runner) {
		Task task = saveTask(publisher, runner, TaskStatus.COMPLETED,
				Instant.now().plus(Duration.ofHours(1)), Instant.now(), 10);
		return task;
	}

	private Task saveTask(AppUser publisher, AppUser runner, TaskStatus status, Instant deadline,
			Instant deliveredAt, long reward) {
		Task task = new Task(UUID.randomUUID().toString(), publisher.getUserId(), "信用分任务",
				"测试说明", reward, deadline, "取件点", "送达点", Instant.now());
		task.setStatus(status);
		task.setRunnerId(runner.getUserId());
		task.setDeliveredAt(deliveredAt);
		return tasks.saveAndFlush(task);
	}

	private static CreateReviewRequest review(int score) {
		CreateReviewRequest request = new CreateReviewRequest();
		request.setScore(score);
		request.setContent("测试评价");
		return request;
	}

	private static TaskCreateRequest taskRequest(int reward) {
		return taskRequest(reward, "信用分发布任务");
	}

	private static TaskCreateRequest taskRequest(int reward, String title) {
		TaskCreateRequest request = new TaskCreateRequest();
		request.setTitle(title);
		request.setDescription("测试发布复核");
		request.setReward(reward);
		request.setDeadline(Instant.now().plus(Duration.ofHours(2)));
		request.setPickupLocation("取件点");
		request.setDeliveryLocation("送达点");
		return request;
	}

	private static void asUser(Long userId, String role) {
		HttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE,
				new CurrentUser(userId, role));
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}
}
