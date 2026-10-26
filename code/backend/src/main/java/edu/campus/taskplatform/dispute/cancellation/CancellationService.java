package edu.campus.taskplatform.dispute.cancellation;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import edu.campus.taskplatform.common.api.PageResult;
import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationArbitrationRequest;
import edu.campus.taskplatform.dispute.cancellation.dto.CancellationResponseRequest;
import edu.campus.taskplatform.dispute.cancellation.dto.CreateCancellationRequest;
import edu.campus.taskplatform.dispute.port.AuthenticatedUser;
import edu.campus.taskplatform.dispute.port.CurrentUserPort;
import edu.campus.taskplatform.dispute.port.LedgerPort;
import edu.campus.taskplatform.dispute.port.TaskParticipationPort;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取消申请与取消争议裁决。
 *
 * <p>状态迁移：</p>
 * <pre>
 * PENDING_RESPONSE --同意--> APPROVED      （任务转 CANCELLED，冻结报酬全额退回发布者）
 * PENDING_RESPONSE --拒绝--> PENDING_ADMIN （任务与账务均不变）
 * PENDING_ADMIN    --裁决--> ARBITRATED    （按裁决结果取消或保持任务）
 * </pre>
 *
 * <p>所有状态变更都在事务内先对申请行加锁；任务与账务操作分别通过端口委托，
 * 不直接写任务表或余额表。</p>
 */
@Service
public class CancellationService {

	private static final Set<CancellationStatus> ACTIVE_STATUSES =
			EnumSet.of(CancellationStatus.PENDING_RESPONSE, CancellationStatus.PENDING_ADMIN);

	private final CancellationRequestRepository repository;
	private final TaskParticipationPort taskPort;
	private final LedgerPort ledgerPort;
	private final CurrentUserPort currentUserPort;

	public CancellationService(CancellationRequestRepository repository, TaskParticipationPort taskPort,
			LedgerPort ledgerPort, CurrentUserPort currentUserPort) {
		this.repository = repository;
		this.taskPort = taskPort;
		this.ledgerPort = ledgerPort;
		this.currentUserPort = currentUserPort;
	}

	/**
	 * 发起取消申请：仅任务参与者、仅进行中或待验收任务、同一任务同时只允许一条活动申请。
	 */
	@Transactional
	public CancellationRequestDto create(UUID taskId, CreateCancellationRequest command) {
		AuthenticatedUser user = currentUserPort.requireUser();
		TaskSnapshot task = taskPort.findTaskForUpdate(taskId)
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
		if (!task.isParticipant(user.userId())) {
			throw new ApiException(ErrorCode.FORBIDDEN, "仅任务参与者可以申请取消");
		}
		if (!task.isCancellable()) {
			throw new ApiException(ErrorCode.TASK_STATE_CONFLICT, "仅进行中或待验收任务可以申请取消");
		}
		if (repository.existsByTaskIdAndStatusIn(taskId, ACTIVE_STATUSES)) {
			throw new ApiException(ErrorCode.CANCELLATION_ALREADY_PENDING);
		}
		CancellationRequestEntity request =
				CancellationRequestEntity.create(taskId, user.userId(), command.reason().trim());
		try {
			return CancellationRequestDto.from(repository.saveAndFlush(request));
		}
		catch (DataIntegrityViolationException exception) {
			// 并发请求穿透了上面的存在性检查，由数据库唯一索引兜底。
			throw new ApiException(ErrorCode.CANCELLATION_ALREADY_PENDING);
		}
	}

	/**
	 * 被申请方应答：同意则取消生效并全额退款；拒绝则转入待管理员处理。
	 */
	@Transactional
	public CancellationRequestDto respond(UUID requestId, CancellationResponseRequest command) {
		AuthenticatedUser user = currentUserPort.requireUser();
		CancellationRequestEntity request = lockRequest(requestId);
		if (request.getStatus() != CancellationStatus.PENDING_RESPONSE) {
			throw new ApiException(ErrorCode.CANCELLATION_STATE_CONFLICT, "该申请已不应答");
		}
		TaskSnapshot task = taskPort.findTaskForUpdate(request.getTaskId())
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
		UUID counterpartId = task.counterpartOf(request.getRequesterId())
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_STATE_CONFLICT, "任务尚无可应答的另一方"));
		if (!counterpartId.equals(user.userId())) {
			throw new ApiException(ErrorCode.FORBIDDEN, "仅被申请方可以应答取消申请");
		}

		Instant now = Instant.now();
		String note = trimToNull(command.note());
		if (command.decision() == CancellationDecision.APPROVE) {
			if (!task.isCancellable()) {
				throw new ApiException(ErrorCode.TASK_STATE_CONFLICT, "任务当前状态不允许取消");
			}
			request.approve(user.userId(), now, task.reward(), note);
			ledgerPort.settleFrozenReward(task.taskId(), task.publisherId(), task.runnerId(), task.reward(), 0);
			taskPort.cancelTask(task.taskId());
		}
		else {
			request.reject(user.userId(), now, note);
		}
		return CancellationRequestDto.from(request);
	}

	/**
	 * 管理员裁决取消争议：只处理仍处于待管理员处理、且关联任务尚未完成的申请。
	 */
	@Transactional
	public CancellationRequestDto arbitrate(UUID requestId, CancellationArbitrationRequest command) {
		AuthenticatedUser admin = currentUserPort.requireAdmin();
		CancellationRequestEntity request = lockRequest(requestId);
		if (request.getStatus() != CancellationStatus.PENDING_ADMIN) {
			throw new ApiException(ErrorCode.CANCELLATION_STATE_CONFLICT, "仅待管理员处理的申请可以裁决");
		}
		TaskSnapshot task = taskPort.findTaskForUpdate(request.getTaskId())
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
		if (!task.isCancellable()) {
			throw new ApiException(ErrorCode.TASK_STATE_CONFLICT, "关联任务已结束，不能裁决取消");
		}

		int refundAmount = command.refundAmount();
		int runnerAmount = command.runnerAmount();
		switch (command.ruling()) {
			case SUPPORT_CONTINUE -> {
				if (refundAmount != 0 || runnerAmount != 0) {
					throw new ApiException(ErrorCode.VALIDATION_ERROR, "不支持取消时 refundAmount 与 runnerAmount 必须为 0");
				}
			}
			case SUPPORT_REQUESTER_CANCEL -> {
				if (refundAmount != task.reward() || runnerAmount != 0) {
					throw new ApiException(ErrorCode.VALIDATION_ERROR, "支持取消时应将冻结报酬全额退回发布者");
				}
				applyCancellation(task, refundAmount, runnerAmount);
			}
			case CANCEL_PROPORTIONALLY -> {
				if ((long) refundAmount + runnerAmount != task.reward()) {
					throw new ApiException(ErrorCode.VALIDATION_ERROR,
							"按比例取消时 refundAmount 与 runnerAmount 之和必须等于冻结报酬");
				}
				applyCancellation(task, refundAmount, runnerAmount);
			}
		}

		request.arbitrate(admin.userId(), Instant.now(), command.ruling(),
				command.rulingDetail().trim(), refundAmount, runnerAmount);
		return CancellationRequestDto.from(request);
	}

	/**
	 * 管理员分页查询取消申请，可按状态过滤。
	 */
	@Transactional(readOnly = true)
	public PageResult<CancellationRequestDto> listForAdmin(CancellationStatus status, int page, int size) {
		currentUserPort.requireAdmin();
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "requestId")));
		Page<CancellationRequestEntity> result = status == null
				? repository.findAll(pageable)
				: repository.findByStatus(status, pageable);
		return PageResult.of(result, CancellationRequestDto::from);
	}

	private void applyCancellation(TaskSnapshot task, int refundAmount, int runnerAmount) {
		ledgerPort.settleFrozenReward(task.taskId(), task.publisherId(), task.runnerId(), refundAmount, runnerAmount);
		taskPort.cancelTask(task.taskId());
	}

	private CancellationRequestEntity lockRequest(UUID requestId) {
		return repository.findByIdForUpdate(requestId)
				.orElseThrow(() -> new ApiException(ErrorCode.CANCELLATION_REQUEST_NOT_FOUND));
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
