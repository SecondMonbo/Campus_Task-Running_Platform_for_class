package edu.campus.taskplatform.task;

import edu.campus.taskplatform.accounting.AccountingService;
import edu.campus.taskplatform.accounting.InsufficientBalanceException;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.task.dto.CreateReviewRequest;
import edu.campus.taskplatform.task.dto.TaskCreateRequest;
import edu.campus.taskplatform.task.dto.TaskUpdateRequest;
import edu.campus.taskplatform.task.dto.TaskView;
import edu.campus.taskplatform.task.dto.WithdrawalRequest;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务业务服务：发布、查询、修改、撤回、接单、送达、确认完成、评价。
 *
 * <p>核心规则（需求 3.3.2.3 / API约定 §6）：</p>
 * <ul>
 *   <li>写操作一律重新读取数据库最新状态再校验，不信任前端缓存状态；</li>
 *   <li>状态+账务+流水+日志必须在同一事务中提交或回滚；</li>
 *   <li>重复请求幂等：接单、送达、确认完成状态只前进一次、账务只变动一次；</li>
 *   <li>接单用悲观锁保证并发下同一任务只有一个接单请求成功。</li>
 * </ul>
 *
 * <p>操作者身份由控制器从登录会话解析后传入，服务端不信任请求体中的 userId。</p>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskOperationLogRepository operationLogRepository;
    private final TaskReviewRepository reviewRepository;
    private final AccountingService accounting;

    public TaskService(TaskRepository taskRepository,
                       TaskOperationLogRepository operationLogRepository,
                       TaskReviewRepository reviewRepository,
                       AccountingService accounting) {
        this.taskRepository = taskRepository;
        this.operationLogRepository = operationLogRepository;
        this.reviewRepository = reviewRepository;
        this.accounting = accounting;
    }

    // ---------------------------------------------------------------
    // 发布
    // ---------------------------------------------------------------

    /**
     * 发布任务：创建任务（状态=待接单）+ 冻结报酬 + 发布日志，同一事务。
     */
    @Transactional
    public TaskView createTask(CurrentUser publisher, TaskCreateRequest req) {
        Instant now = Instant.now();
        String taskId = UUID.randomUUID().toString();

        // 冻结报酬；余额不足时整体失败，不创建任务（T1）
        doFreeze(publisher.userId(), taskId, req.getReward());
        Task task = new Task(taskId, publisher.userId(), req.getTitle(), req.getDescription(),
                req.getReward(), req.getDeadline(), req.getPickupLocation(),
                req.getDeliveryLocation(), now);
        taskRepository.save(task);
        writeLog(taskId, publisher.userId(), "PUBLISH", "发布任务，报酬=" + req.getReward() + " 已冻结");
        return TaskView.from(task);
    }

    // ---------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------

    /**
     * 浏览任务：可选按状态、标题/地点关键字筛选，分页；空列表返回空 items。
     */
    public PageResult<TaskView> listTasks(TaskStatus status, String keyword, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<Task> result = taskRepository.findAll(browseSpec(status, keyword), pageable);
        return PageResult.of(toViews(result.getContent()), page, size, result.getTotalElements());
    }

    /**
     * 任务详情。
     */
    public TaskView getTask(String taskId) {
        return TaskView.from(requireTask(taskId));
    }

    /**
     * 查询本人发布或接取的任务（as=publisher | as=runner），可选按状态筛选。
     */
    public PageResult<TaskView> listMyTasks(CurrentUser user, String as, TaskStatus status, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<Task> result;
        if ("publisher".equals(as)) {
            result = status == null
                    ? taskRepository.findByPublisherId(user.userId(), pageable)
                    : taskRepository.findByPublisherIdAndStatus(user.userId(), status, pageable);
        } else if ("runner".equals(as)) {
            result = status == null
                    ? taskRepository.findByRunnerId(user.userId(), pageable)
                    : taskRepository.findByRunnerIdAndStatus(user.userId(), status, pageable);
        } else {
            throw new TaskException(ErrorCode.VALIDATION_ERROR, "as 参数只能是 publisher 或 runner");
        }
        return PageResult.of(toViews(result.getContent()), page, size, result.getTotalElements());
    }

    // ---------------------------------------------------------------
    // 修改（仅待接单）
    // ---------------------------------------------------------------

    /**
     * 修改待接单任务：仅发布者可改本人任务；报酬变动按差额追加冻结或解冻。
     */
    @Transactional
    public TaskView updateTask(CurrentUser publisher, String taskId, TaskUpdateRequest req) {
        if (req.isEmpty()) {
            throw new TaskException(ErrorCode.VALIDATION_ERROR, "至少提供一个待修改字段");
        }
        Task task = requireTask(taskId);
        requireOwner(publisher, task);
        if (task.getStatus() != TaskStatus.WAITING_FOR_TAKER) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "任务已被接单或不在待接单状态，不可修改");
        }

        if (req.getTitle() != null) {
            task.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            task.setDescription(req.getDescription());
        }
        if (req.getDeadline() != null) {
            if (req.getDeadline().isBefore(Instant.now())) {
                throw new TaskException(ErrorCode.VALIDATION_ERROR, "截止时间必须晚于当前时间");
            }
            task.setDeadline(req.getDeadline());
        }
        if (req.getPickupLocation() != null) {
            task.setPickupLocation(req.getPickupLocation());
        }
        if (req.getDeliveryLocation() != null) {
            task.setDeliveryLocation(req.getDeliveryLocation());
        }
        if (req.getReward() != null && req.getReward() != task.getReward()) {
            long delta = req.getReward() - task.getReward();
            // 报酬增加补冻结、减少按差额解冻；余额不足则拒绝修改（整体回滚）
            doAdjustFrozen(publisher.userId(), taskId, delta);
            task.setReward(req.getReward());
        }

        taskRepository.save(task);
        writeLog(taskId, publisher.userId(), "UPDATE", "修改任务");
        return TaskView.from(task);
    }

    // ---------------------------------------------------------------
    // 撤回（仅待接单，全额解冻）
    // ---------------------------------------------------------------

    /**
     * 撤回待接单任务：状态置为已取消 + 全额解冻暂存报酬 + 退款日志，同一事务。
     */
    @Transactional
    public TaskView withdrawTask(CurrentUser publisher, String taskId, WithdrawalRequest req) {
        Task task = requireTask(taskId);
        requireOwner(publisher, task);
        if (task.getStatus() != TaskStatus.WAITING_FOR_TAKER) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "任务已被接单，不可撤回");
        }
        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelReason(req.getReason());
        doUnfreeze(publisher.userId(), taskId, task.getReward());
        taskRepository.save(task);
        writeLog(taskId, publisher.userId(), "WITHDRAW", "撤回任务，报酬=" + task.getReward() + " 已解冻退回");
        return TaskView.from(task);
    }

    // ---------------------------------------------------------------
    // 接单（并发安全）
    // ---------------------------------------------------------------

    /**
     * 接单：非发布者本人、状态=待接单、未过截止；悲观锁保证并发下只有一个成功。
     */
    @Transactional
    public TaskView acceptTask(CurrentUser runner, String taskId) {
        Task task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在"));
        if (task.getPublisherId().equals(runner.userId())) {
            throw new TaskException(ErrorCode.FORBIDDEN, "发布者不能接取自己发布的任务");
        }
        if (task.getStatus() != TaskStatus.WAITING_FOR_TAKER) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "任务已被他人接取或不在待接单状态");
        }
        if (task.getDeadline().isBefore(Instant.now())) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "任务已过截止时间，不可接单");
        }
        task.setRunnerId(runner.userId());
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setAcceptedAt(Instant.now());
        taskRepository.save(task);
        writeLog(taskId, runner.userId(), "ACCEPT", "接单");
        return TaskView.from(task);
    }

    // ---------------------------------------------------------------
    // 送达（进行中 → 待验收）
    // ---------------------------------------------------------------

    /**
     * 确认送达：仅任务跑腿员本人可操作；进行中 → 待验收，记录首次送达时间。
     */
    @Transactional
    public TaskView deliverTask(CurrentUser runner, String taskId) {
        Task task = requireTask(taskId);
        if (task.getRunnerId() == null || !task.getRunnerId().equals(runner.userId())) {
            throw new TaskException(ErrorCode.FORBIDDEN, "仅任务跑腿员可确认送达");
        }
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "该任务当前不可确认送达");
        }
        task.setStatus(TaskStatus.WAITING_FOR_ACCEPTANCE);
        task.setDeliveredAt(Instant.now());
        taskRepository.save(task);
        // 送达通知属通知模块（后端B/组长），此处仅留痕；通知触发由通知服务接入
        writeLog(taskId, runner.userId(), "DELIVER", "确认送达");
        return TaskView.from(task);
    }

    // ---------------------------------------------------------------
    // 确认完成（待验收 → 已完成 + 结算，幂等）
    // ---------------------------------------------------------------

    /**
     * 确认完成：仅发布者本人；待验收 → 已完成，同事务结算给跑腿员。
     * 已完成任务重复确认返回 409，不重复结算、不生成重复流水（幂等）。
     */
    @Transactional
    public TaskView completeTask(CurrentUser publisher, String taskId) {
        Task task = requireTask(taskId);
        requireOwner(publisher, task);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "任务已完成，不能重复确认");
        }
        if (task.getStatus() != TaskStatus.WAITING_FOR_ACCEPTANCE) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "该任务当前不可确认完成");
        }
        Long runnerId = task.getRunnerId();
        long reward = task.getReward();
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        doSettle(taskId, publisher.userId(), runnerId, reward);
        taskRepository.save(task);
        writeLog(taskId, publisher.userId(), "COMPLETE", "确认完成，报酬=" + reward + " 结算给跑腿员");
        return TaskView.from(task);
    }

    // ---------------------------------------------------------------
    // 评价（已完成任务，一条任务一次）
    // ---------------------------------------------------------------

    /**
     * 发布者评价跑腿员：仅已完成任务、仅发布者、每条任务只能评价一次。
     */
    @Transactional
    public TaskReview createReview(CurrentUser publisher, String taskId, CreateReviewRequest req) {
        Task task = requireTask(taskId);
        requireOwner(publisher, task);
        if (task.getStatus() != TaskStatus.COMPLETED) {
            throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "仅已完成任务可评价");
        }
        if (reviewRepository.existsByTaskIdAndReviewerId(taskId, publisher.userId())) {
            throw new TaskException(ErrorCode.DUPLICATE_ACTION, "该任务已被本人评价过");
        }
        TaskReview review = new TaskReview(UUID.randomUUID().toString(), taskId,
                publisher.userId(), task.getRunnerId(), req.getScore(), req.getContent(), Instant.now());
        reviewRepository.save(review);
        writeLog(taskId, publisher.userId(), "REVIEW", "提交评价");
        return review;
    }

    // ---------------------------------------------------------------
    // 私有辅助
    // ---------------------------------------------------------------

    private Task requireTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在"));
    }

    private void requireOwner(CurrentUser actor, Task task) {
        if (!task.getPublisherId().equals(actor.userId())) {
            throw new TaskException(ErrorCode.FORBIDDEN, "无权操作该任务");
        }
    }

    private void writeLog(String taskId, Long actorId, String action, String detail) {
        operationLogRepository.save(new TaskOperationLog(
                UUID.randomUUID().toString(), taskId, actorId, action, detail, Instant.now()));
    }

    // 账务调用统一包装：把账务实现的余额不足异常转成服务层稳定的 TaskException。
    private void doFreeze(long userId, String taskId, long amount) {
        try {
            accounting.freeze(userId, taskId, amount);
        } catch (InsufficientBalanceException e) {
            throw new TaskException(ErrorCode.INSUFFICIENT_BALANCE, e.getMessage());
        }
    }

    private void doUnfreeze(long userId, String taskId, long amount) {
        try {
            accounting.unfreeze(userId, taskId, amount);
        } catch (InsufficientBalanceException e) {
            throw new TaskException(ErrorCode.INSUFFICIENT_BALANCE, e.getMessage());
        }
    }

    private void doAdjustFrozen(long userId, String taskId, long delta) {
        try {
            accounting.adjustFrozen(userId, taskId, delta);
        } catch (InsufficientBalanceException e) {
            throw new TaskException(ErrorCode.INSUFFICIENT_BALANCE, e.getMessage());
        }
    }

    private void doSettle(String taskId, long publisherId, long runnerId, long amount) {
        try {
            accounting.settle(taskId, publisherId, runnerId, amount);
        } catch (InsufficientBalanceException e) {
            throw new TaskException(ErrorCode.INSUFFICIENT_BALANCE, e.getMessage());
        }
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "taskId"));
    }

    private List<TaskView> toViews(List<Task> tasks) {
        List<TaskView> views = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            views.add(TaskView.from(task));
        }
        return views;
    }

    private Specification<Task> browseSpec(TaskStatus status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("pickupLocation")), like),
                        cb.like(cb.lower(root.get("deliveryLocation")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
