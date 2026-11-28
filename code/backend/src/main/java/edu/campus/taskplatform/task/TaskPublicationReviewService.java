package edu.campus.taskplatform.task;

import java.time.Instant;
import java.util.UUID;

import edu.campus.taskplatform.accounting.AccountingService;
import edu.campus.taskplatform.accounting.InsufficientBalanceException;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.notification.NotificationService;
import edu.campus.taskplatform.task.dto.PublicationReviewDecisionRequest;
import edu.campus.taskplatform.task.dto.TaskView;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 低分用户发布任务的复核队列。任务保留原状态，仅以发布复核状态控制公开可见性；
 * 驳回时取消任务并全额解冻报酬，批准后进入公开待接单列表。
 */
@Service
public class TaskPublicationReviewService {

	private final TaskRepository taskRepository;
	private final TaskOperationLogRepository operationLogRepository;
	private final AccountingService accountingService;
	private final NotificationService notificationService;
	private final CurrentUserService currentUserService;

	public TaskPublicationReviewService(TaskRepository taskRepository,
			TaskOperationLogRepository operationLogRepository, AccountingService accountingService,
			NotificationService notificationService, CurrentUserService currentUserService) {
		this.taskRepository = taskRepository;
		this.operationLogRepository = operationLogRepository;
		this.accountingService = accountingService;
		this.notificationService = notificationService;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public PageResult<TaskView> list(PublicationReviewStatus status, int page, int size) {
		currentUserService.requireAdmin();
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "taskId")));
		Page<Task> result;
		if (status == null) {
			result = taskRepository.findAll(pageable);
		}
		else if (status == PublicationReviewStatus.PENDING_REVIEW) {
			// 发布者撤回后任务已取消，不应再出现在待复核队列。
			result = taskRepository.findByPublicationReviewStatusAndStatus(
					status, TaskStatus.WAITING_FOR_TAKER, pageable);
		}
		else {
			result = taskRepository.findByPublicationReviewStatus(status, pageable);
		}
		return PageResult.of(result.getContent().stream().map(TaskView::from).toList(),
				page, size, result.getTotalElements());
	}

	@Transactional
	public TaskView decide(String taskId, PublicationReviewDecisionRequest request) {
		CurrentUser admin = currentUserService.requireAdmin();
		Task task = taskRepository.findByIdForUpdate(taskId)
				.orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "任务不存在"));
		if (task.getPublicationReviewStatus() != PublicationReviewStatus.PENDING_REVIEW) {
			throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "该任务不在待复核状态");
		}
		if (task.getStatus() != TaskStatus.WAITING_FOR_TAKER) {
			throw new TaskException(ErrorCode.TASK_STATE_CONFLICT, "任务已结束，不能复核");
		}
		String note = request.note().trim();
		if (request.decision() == PublicationReviewDecision.APPROVE) {
			task.setPublicationReviewStatus(PublicationReviewStatus.APPROVED);
			taskRepository.save(task);
			writeLog(taskId, admin.userId(), "PUBLICATION_REVIEW_APPROVE", "发布复核通过：" + note);
			safeNotify(task.getPublisherId(), "发布复核已通过",
					"你的任务「" + task.getTitle() + "」已通过管理员复核，现已公开待接单。", taskId);
		}
		else {
			task.setPublicationReviewStatus(PublicationReviewStatus.REJECTED);
			task.setStatus(TaskStatus.CANCELLED);
			task.setCancelReason(note.length() > 200 ? note.substring(0, 200) : note);
			unfreeze(task.getPublisherId(), taskId, task.getReward());
			taskRepository.save(task);
			writeLog(taskId, admin.userId(), "PUBLICATION_REVIEW_REJECT", "发布复核驳回：" + note);
			safeNotify(task.getPublisherId(), "发布复核未通过",
					"你的任务「" + task.getTitle() + "」未通过管理员复核，任务已取消，"
							+ "冻结报酬 " + task.getReward() + " 虚拟币已退回。", taskId);
		}
		return TaskView.from(task);
	}

	private void unfreeze(long publisherId, String taskId, long reward) {
		try {
			accountingService.unfreeze(publisherId, taskId, reward);
		}
		catch (InsufficientBalanceException exception) {
			throw new TaskException(ErrorCode.INSUFFICIENT_BALANCE, exception.getMessage());
		}
	}

	private void writeLog(String taskId, Long actorId, String action, String detail) {
		operationLogRepository.save(new TaskOperationLog(
				UUID.randomUUID().toString(), taskId, actorId, action, detail, Instant.now()));
	}

	private void safeNotify(Long userId, String title, String content, String taskId) {
		try {
			notificationService.notify(userId, title, content, taskId);
		}
		catch (RuntimeException failure) {
			// 通知失败不回滚复核主流程。
		}
	}
}
