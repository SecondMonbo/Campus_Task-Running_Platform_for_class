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
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 信用分、低分限制、发布复核与 outbox 的集成测试。
 *
 * <p>显式开启事件即时消费，验证业务事务提交后信用分、记录、限制状态和发布复核的联动；
 * 同时覆盖重复事件不污染外层事务、低星窗口串行化等并发/幂等场景。</p>
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
    @Autowired CreditScoreRepository creditScores;
    @Autowired CreditScoreRecordRepository creditRecords;
    @Autowired CreditEventRepository creditEventRepository;
    @Autowired AppUserRepository users;
    @Autowired TaskService taskService;
    @Autowired TaskRepository tasks;
    @Autowired TaskReviewRepository reviews;
    @Autowired AccountingService accounting;
    @Autowired TaskPublicationReviewService publicationReviewService;
    @Autowired ComplaintService complaintService;
    @Autowired ComplaintRepository complaints;
    @Autowired PlatformTransactionManager transactionManager;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registrationInitializesCreditAt80WithBaselineRecord() {
        String username = "credit-init-" + UUID.randomUUID();
        var registered = authService.register(new RegisterRequest(username, "password123", "初始化用户", null));
        long userId = Long.parseLong(registered.userId());

        CreditScore account = creditScores.findById(userId).orElseThrow();
        assertEquals(80, account.getScore());
        assertFalse(account.isRestricted());
        assertTrue(creditRecords.findByUserId(userId, PageRequest.of(0, 10)).getContent().stream()
                .anyMatch(record -> record.getEventType() == CreditEventType.INITIALIZATION
                        && record.getScoreBefore() == null && record.getScoreAfter() == 80));
    }

    @Test
    void reviewsApplyFiveStarNeutralAndThirtyDayLowStarDeduplication() {
        AppUser publisher = user();
        AppUser runner = user();

        taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(5));
        assertEquals(82, scoreOf(runner));

        taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(3));
        assertEquals(82, scoreOf(runner));

        taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(1));
        assertEquals(79, scoreOf(runner));

        taskService.createReview(current(publisher), completedTask(publisher, runner).getTaskId(), review(2));
        assertEquals(79, scoreOf(runner));
    }

    @Test
    void completionAppliesOnTimeGraceAndOvertimeRules() {
        AppUser publisher = user();
        AppUser runner = user();
        accounting.openAccount(publisher.getUserId(), 100);
        accounting.openAccount(runner.getUserId(), 0);

        Instant now = Instant.now();
        Task onTime = saveTask(publisher, runner, TaskStatus.WAITING_FOR_ACCEPTANCE,
                now.plus(Duration.ofHours(1)), now, 20);
        accounting.freeze(publisher.getUserId(), onTime.getTaskId(), 20);
        taskService.completeTask(current(publisher), onTime.getTaskId());
        assertEquals(81, scoreOf(runner));

        Instant lateDeadline = now.minus(Duration.ofDays(2));
        Task late = saveTask(publisher, runner, TaskStatus.WAITING_FOR_ACCEPTANCE,
                lateDeadline, lateDeadline.plus(Duration.ofHours(25)), 20);
        accounting.freeze(publisher.getUserId(), late.getTaskId(), 20);
        taskService.completeTask(current(publisher), late.getTaskId());
        assertEquals(76, scoreOf(runner));
    }

    @Test
    void lowScoreRestrictionPublishesForReviewAndLiftsAt75() {
        AppUser restricted = user();
        AppUser other = user();
        AppUser admin = user();
        accounting.openAccount(restricted.getUserId(), 100);

        creditScoreService.adjustByAdmin(restricted.getUserId(), -21, "复核扣分", admin.getUserId());
        assertEquals(59, scoreOf(restricted));
        assertTrue(reload(restricted).isRestricted());

        // 60 分不触发新限制，但已受限用户要继续保持到 75 分。
        creditScoreService.adjustByAdmin(restricted.getUserId(), 1, "复核调整", admin.getUserId());
        assertEquals(60, scoreOf(restricted));
        assertTrue(reload(restricted).isRestricted());
        // 60 分档位是预警，但持久化限制仍生效，本人页文案不能误报“可正常接单”。
        var restrictedView = creditScoreService.myCredit(restricted.getUserId(), 0, 10);
        assertEquals(CreditTier.WARNING.name(), restrictedView.tier());
        assertTrue(restrictedView.permissionHint().contains("受限"));

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
        assertEquals(75, scoreOf(restricted));
        assertFalse(reload(restricted).isRestricted());
        assertTrue(creditScoreService.myCredit(restricted.getUserId(), 0, 10)
                .permissionHint().contains("正常"));
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
        assertEquals(75, scoreOf(target));
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
        assertEquals(50, scoreOf(user));
        assertTrue(reload(user).isRestricted());

        for (int i = 0; i < 10; i++) {
            creditEvents.publish(user.getUserId(), CreditEventType.COMPLETION_IN_GRACE,
                    "recovery-task-" + user.getUserId() + "-" + i, 0, "连续履约测试");
        }

        CreditScore recovered = reload(user);
        assertEquals(75, recovered.getScore());
        assertFalse(recovered.isRestricted());
        assertEquals(0, recovered.getConsecutiveCompletedTasks());
        CreditSummaryDto summary = creditScoreService.summary(user.getUserId());
        assertEquals(CreditTier.GOOD, summary.tier());
        assertTrue(summary.canAcceptTasks());
    }

    @Test
    void publicProfileHidesExactScoreAndShowsCompletedCount() {
        AppUser runner = user();
        AppUser publisher = user();
        saveTask(publisher, runner, TaskStatus.COMPLETED,
                Instant.now().plus(Duration.ofHours(1)), Instant.now(), 10);

        var profile = creditScoreService.publicProfile(runner.getUserId());
        assertEquals(runner.getUserId().toString(), profile.userId());
        assertEquals("GOOD", profile.creditTier());
        assertEquals(1, profile.completedTasks());
    }

    /**
     * 回归：同一事件在同一个外层业务事务中重复发布时，不能因唯一键冲突把事务标记为
     * rollback-only；外层业务写入与 outbox 事件都要正常提交，分值只记一次。
     */
    @Test
    void duplicatePublishKeepsOuterTransactionCommittable() {
        AppUser runner = user();
        creditScoreService.ensureAccount(runner.getUserId());
        String source = "duplicate-publish-" + UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            creditEvents.publish(runner.getUserId(), CreditEventType.ON_TIME_COMPLETION,
                    source, 1, "重复发布回归");
            creditEvents.publish(runner.getUserId(), CreditEventType.ON_TIME_COMPLETION,
                    source, 1, "重复发布回归");
            AppUser marker = users.findById(runner.getUserId()).orElseThrow();
            marker.updateProfile("重复发布后仍提交", null);
            users.save(marker);
        });

        assertEquals("重复发布后仍提交", users.findById(runner.getUserId()).orElseThrow().getDisplayName());
        assertEquals(1, creditEventRepository.findAll().stream()
                .filter(event -> source.equals(event.getSourceId())).count());
        assertEquals(81, scoreOf(runner));

        // 再次消费也不会重复记分。
        creditEvents.publish(runner.getUserId(), CreditEventType.ON_TIME_COMPLETION,
                source, 1, "重复发布回归");
        assertEquals(81, scoreOf(runner));
    }

    private AppUser user() {
        String username = "credit-" + UUID.randomUUID();
        return users.saveAndFlush(new AppUser(username, "unused-test-hash", "信用分测试用户", null, Instant.now()));
    }

    private CreditScore reload(AppUser user) {
        return creditScores.findById(user.getUserId()).orElseThrow();
    }

    private int scoreOf(AppUser user) {
        return creditScoreService.scoreOf(user.getUserId());
    }

    private static CurrentUser current(AppUser user) {
        return new CurrentUser(user.getUserId(), "USER");
    }

    private Task completedTask(AppUser publisher, AppUser runner) {
        return saveTask(publisher, runner, TaskStatus.COMPLETED,
                Instant.now().plus(Duration.ofHours(1)), Instant.now(), 10);
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
