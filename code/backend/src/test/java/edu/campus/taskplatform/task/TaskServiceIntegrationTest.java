package edu.campus.taskplatform.task;

import edu.campus.taskplatform.accounting.DemoAccountingService;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.task.dto.CreateReviewRequest;
import edu.campus.taskplatform.task.dto.TaskCreateRequest;
import edu.campus.taskplatform.task.dto.TaskUpdateRequest;
import edu.campus.taskplatform.task.dto.TaskView;
import edu.campus.taskplatform.task.dto.WithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 任务服务集成测试：以需求 3.3.2.3 的系统测试要点为蓝本，
 * 覆盖合法迁移（T1-T8）、非法迁移（T9-T14）、并发（T15）与幂等（T18）的关键场景。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TaskServiceIntegrationTest {

    @Autowired
    TaskService taskService;

    @Autowired
    DemoAccountingService accounting;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskOperationLogRepository operationLogRepository;

    @Autowired
    TaskReviewRepository reviewRepository;

    private CurrentUser publisher;
    private CurrentUser runner;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        operationLogRepository.deleteAll();
        taskRepository.deleteAll();
        accounting.reset();

        publisher = new CurrentUser(1L, "USER");
        runner = new CurrentUser(2L, "USER");
        accounting.deposit(publisher.userId(), 5000);
        accounting.deposit(runner.userId(), 5000);
    }

    // ---------------- 合法迁移 ----------------

    @Test
    void T1_publish_freezes_reward_and_creates_waiting_task() {
        TaskView created = publish("代取资料", 30);
        assertEquals(TaskStatus.WAITING_FOR_TAKER, created.getStatus());
        assertEquals(30, created.getReward());
        assertEquals(5000 - 30, accounting.getAvailableBalance(publisher.userId()));
        assertEquals(30, accounting.getFrozenBalance(publisher.userId()));
    }

    @Test
    void T2_accept_binds_runner_and_advances_to_in_progress() {
        TaskView created = publish("代取资料", 30);
        TaskView accepted = taskService.acceptTask(runner, created.getTaskId());
        assertEquals(TaskStatus.IN_PROGRESS, accepted.getStatus());
        assertEquals(runner.userId(), Long.valueOf(accepted.getRunnerId()));
    }

    @Test
    void T3_deliver_advances_to_waiting_acceptance_and_records_time() {
        String taskId = publishedAndAccepted();
        taskService.acceptTask(runner, taskId);
        TaskView delivered = taskService.deliverTask(runner, taskId);
        assertEquals(TaskStatus.WAITING_FOR_ACCEPTANCE, delivered.getStatus());
        assertNotNull(delivered.getDeliveredAt());
        // 报酬继续暂存，不因送达入账：跑腿员仍是初始 5000
        assertEquals(30, accounting.getFrozenBalance(publisher.userId()));
        assertEquals(5000, accounting.getAvailableBalance(runner.userId()));
    }

    @Test
    void T4_complete_settles_reward_to_runner() {
        String taskId = deliveredTask();
        TaskView completed = taskService.completeTask(publisher, taskId);
        assertEquals(TaskStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        // 发布者冻结清零（30 转出），跑腿员入账 30（跑腿员初始 5000）
        assertEquals(0, accounting.getFrozenBalance(publisher.userId()));
        assertEquals(5030, accounting.getAvailableBalance(runner.userId()));
    }

    @Test
    void T5_withdraw_cancels_and_full_refund() {
        TaskView created = publish("代取资料", 30);
        TaskView withdrawn = taskService.withdrawTask(publisher, created.getTaskId(), reason("不发布了"));
        assertEquals(TaskStatus.CANCELLED, withdrawn.getStatus());
        assertEquals(5000, accounting.getAvailableBalance(publisher.userId()));
        assertEquals(0, accounting.getFrozenBalance(publisher.userId()));
    }

    @Test
    void T6_update_increases_freeze_and_stays_waiting() {
        TaskView created = publish("代取资料", 30);
        TaskUpdateRequest req = new TaskUpdateRequest();
        req.setReward(50);
        TaskView updated = taskService.updateTask(publisher, created.getTaskId(), req);
        assertEquals(50, updated.getReward());
        assertEquals(TaskStatus.WAITING_FOR_TAKER, updated.getStatus());
        assertEquals(5000 - 50, accounting.getAvailableBalance(publisher.userId()));
        assertEquals(50, accounting.getFrozenBalance(publisher.userId()));
    }

    @Test
    void T6b_update_decreases_reward_unfreezes_delta() {
        TaskView created = publish("代取资料", 30);
        TaskUpdateRequest req = new TaskUpdateRequest();
        req.setReward(20);
        taskService.updateTask(publisher, created.getTaskId(), req);
        assertEquals(5000 - 20, accounting.getAvailableBalance(publisher.userId()));
        assertEquals(20, accounting.getFrozenBalance(publisher.userId()));
    }

    // ---------------- 非法迁移 / 越权 / 终态保护 ----------------

    @Test
    void T9_cannot_deliver_before_accept() {
        TaskView created = publish("代取资料", 30);
        assertThrows(TaskException.class, () -> taskService.deliverTask(runner, created.getTaskId()));
    }

    @Test
    void T10_cannot_complete_before_accept() {
        TaskView created = publish("代取资料", 30);
        assertThrows(TaskException.class, () -> taskService.completeTask(publisher, created.getTaskId()));
    }

    @Test
    void T11_completed_task_cannot_reopen() {
        String taskId = completedTask();
        assertThrows(TaskException.class, () -> taskService.acceptTask(runner, taskId));
        assertThrows(TaskException.class, () -> taskService.deliverTask(runner, taskId));
        assertThrows(TaskException.class, () -> taskService.completeTask(publisher, taskId));
    }

    @Test
    void T13_non_runner_cannot_deliver() {
        String taskId = publishedAndAccepted();
        CurrentUser stranger = new CurrentUser(99L, "USER");
        assertThrows(TaskException.class, () -> taskService.deliverTask(stranger, taskId));
    }

    @Test
    void T13b_non_publisher_cannot_complete() {
        String taskId = deliveredTask();
        CurrentUser stranger = new CurrentUser(99L, "USER");
        assertThrows(TaskException.class, () -> taskService.completeTask(stranger, taskId));
    }

    @Test
    void T13c_publisher_cannot_accept_own_task() {
        TaskView created = publish("代取资料", 30);
        assertThrows(TaskException.class, () -> taskService.acceptTask(publisher, created.getTaskId()));
    }

    // ---------------- 幂等 / 重复 ----------------

    @Test
    void T18_cannot_accept_twice() {
        String taskId = publishedAndAccepted();
        taskService.acceptTask(runner, taskId); // 第一次成功
        assertThrows(TaskException.class, () -> taskService.acceptTask(runner, taskId)); // 第二次拒绝
        assertEquals(1, taskRepository.findByRunnerId(runner.userId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
    }

    @Test
    void T18b_cannot_complete_twice_no_double_settle() {
        String taskId = completedTask();
        assertThrows(TaskException.class, () -> taskService.completeTask(publisher, taskId));
        assertEquals(5030, accounting.getAvailableBalance(runner.userId())); // 只入账一次
        assertEquals(0, accounting.getFrozenBalance(publisher.userId()));
    }

    @Test
    void T18c_update_only_in_waiting() {
        String taskId = deliveredTask();
        TaskUpdateRequest req = new TaskUpdateRequest();
        req.setTitle("改标题");
        assertThrows(TaskException.class, () -> taskService.updateTask(publisher, taskId, req));
    }

    // ---------------- 账务 / 权限 ----------------

    @Test
    void insufficient_balance_on_publish_rejected() {
        accounting.reset();
        accounting.deposit(publisher.userId(), 5); // 可用只有 5，报酬 100 不够
        assertThrows(TaskException.class, () -> taskService.createTask(publisher, request("太贵", 100)));
        // 失败时不创建任务（T1 失败流）
        assertEquals(0, taskRepository.count());
    }

    @Test
    void update_reward_beyond_balance_rejected() {
        TaskView created = publish("代取资料", 30);
        TaskUpdateRequest req = new TaskUpdateRequest();
        req.setReward(9000); // 差额远超可用余额，应拒绝
        assertThrows(TaskException.class, () -> taskService.updateTask(publisher, created.getTaskId(), req));
        // 字段与金额不变
        TaskView unchanged = taskService.getTask(created.getTaskId());
        assertEquals(30, unchanged.getReward());
        assertEquals(5000 - 30, accounting.getAvailableBalance(publisher.userId()));
    }

    @Test
    void task_not_found_returns_404_error() {
        TaskException ex = assertThrows(TaskException.class, () -> taskService.getTask("nope"));
        assertEquals(edu.campus.taskplatform.common.ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    // ---------------- 查询 ----------------

    @Test
    void list_my_publisher_and_runner_tasks() {
        String taskId = deliveredTask();
        PageResult<TaskView> mineAsPublisher = taskService.listMyTasks(publisher, "publisher", null, 0, 10);
        PageResult<TaskView> mineAsRunner = taskService.listMyTasks(runner, "runner", null, 0, 10);
        assertEquals(1, mineAsPublisher.getTotal());
        assertEquals(1, mineAsRunner.getTotal());
        assertEquals(taskId, mineAsPublisher.getItems().get(0).getTaskId());
        assertEquals(taskId, mineAsRunner.getItems().get(0).getTaskId());
    }

    @Test
    void browse_filters_by_status_and_keyword() {
        publishTo("代取资料到宿舍", 30, "宿舍区");
        publishTo("取快递到教学楼", 20, "图书馆");
        PageResult<TaskView> waiting = taskService.listTasks(TaskStatus.WAITING_FOR_TAKER, null, 0, 10);
        assertEquals(2, waiting.getTotal());
        PageResult<TaskView> byKeyword = taskService.listTasks(null, "宿舍", 0, 10);
        assertEquals(1, byKeyword.getTotal());
    }

    // ---------------- 评价 ----------------

    @Test
    void review_after_complete_and_once_only() {
        String taskId = completedTask();
        CreateReviewRequest req = new CreateReviewRequest();
        req.setScore(5);
        req.setContent("很好");
        TaskReview review = taskService.createReview(publisher, taskId, req);
        assertNotNull(review.getReviewId());
        assertThrows(TaskException.class, () -> taskService.createReview(publisher, taskId, req));
    }

    // ---------------- 并发接单 ----------------

    @Test
    void T15_concurrent_accept_only_one_succeeds() throws Exception {
        TaskView created = publish("并发单", 30);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                final long userId = 100 + i;
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        taskService.acceptTask(new CurrentUser(userId, "USER"), created.getTaskId());
                        success.incrementAndGet();
                    } catch (TaskException expected) {
                        // 并发冲突：忽略
                    }
                    return null;
                });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, success.get(), "并发接单只应有一个成功");
        TaskView after = taskService.getTask(created.getTaskId());
        assertEquals(TaskStatus.IN_PROGRESS, after.getStatus());
        assertNotNull(after.getRunnerId());
    }

    // ---------------- 辅助 ----------------

    private TaskView publish(String title, int reward) {
        return taskService.createTask(publisher, request(title, reward));
    }

    private TaskView publishTo(String title, int reward, String deliveryLocation) {
        TaskCreateRequest req = request(title, reward);
        req.setDeliveryLocation(deliveryLocation);
        return taskService.createTask(publisher, req);
    }

    private TaskCreateRequest request(String title, int reward) {
        TaskCreateRequest req = new TaskCreateRequest();
        req.setTitle(title);
        req.setDescription("到教学楼领取资料并送到宿舍区");
        req.setReward(reward);
        req.setDeadline(Instant.now().plus(2, ChronoUnit.HOURS));
        req.setPickupLocation("教学楼A座");
        req.setDeliveryLocation("宿舍区");
        return req;
    }

    private WithdrawalRequest reason(String text) {
        WithdrawalRequest req = new WithdrawalRequest();
        req.setReason(text);
        return req;
    }

    private String publishedAndAccepted() {
        TaskView created = publish("代取资料", 30);
        return created.getTaskId();
    }

    private String deliveredTask() {
        String taskId = publishedAndAccepted();
        taskService.acceptTask(runner, taskId);
        taskService.deliverTask(runner, taskId);
        return taskId;
    }

    private String completedTask() {
        String taskId = deliveredTask();
        taskService.completeTask(publisher, taskId);
        return taskId;
    }
}
