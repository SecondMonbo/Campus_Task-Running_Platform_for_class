package edu.campus.taskplatform.dispute.appeal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.campus.taskplatform.common.api.PageResult;
import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.dispute.appeal.dto.AppealDecisionRequest;
import edu.campus.taskplatform.dispute.appeal.dto.CreateAppealRequest;
import edu.campus.taskplatform.dispute.port.AuthenticatedUser;
import edu.campus.taskplatform.dispute.port.CurrentUserPort;
import edu.campus.taskplatform.dispute.port.LedgerPort;
import edu.campus.taskplatform.dispute.port.TaskParticipationPort;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;
import edu.campus.taskplatform.dispute.port.TaskStatus;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 已完成任务申诉与管理员裁决。
 *
 * <p>申诉裁决遵循《API约定.md》4.2 节规则：不改变任务 COMPLETED 状态、不修改原始结算流水；
 * 支持或部分支持时从被申诉方可用余额向申诉方划转，余额更新、申诉状态更新和流水写入必须在
 * 同一事务内完成。</p>
 */
@Service
public class AppealService {

	private final AppealRepository repository;
	private final TaskParticipationPort taskPort;
	private final LedgerPort ledgerPort;
	private final CurrentUserPort currentUserPort;

	public AppealService(AppealRepository repository, TaskParticipationPort taskPort,
			LedgerPort ledgerPort, CurrentUserPort currentUserPort) {
		this.repository = repository;
		this.taskPort = taskPort;
		this.ledgerPort = ledgerPort;
		this.currentUserPort = currentUserPort;
	}

	/**
	 * 提交申诉：仅任务参与者、仅已完成任务、每位参与者对同一任务最多一次。
	 */
	@Transactional
	public AppealDto create(UUID taskId, CreateAppealRequest command) {
		AuthenticatedUser user = currentUserPort.requireUser();
		TaskSnapshot task = taskPort.findTaskForUpdate(taskId)
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
		if (!task.isParticipant(user.userId())) {
			throw new ApiException(ErrorCode.FORBIDDEN, "仅任务参与者可以提交申诉");
		}
		if (task.status() != TaskStatus.COMPLETED) {
			throw new ApiException(ErrorCode.TASK_STATE_CONFLICT, "仅已完成任务可以提交申诉");
		}
		UUID respondentId = task.counterpartOf(user.userId())
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_STATE_CONFLICT, "任务缺少另一位参与者"));
		if (repository.existsByTaskIdAndAppellantId(taskId, user.userId())) {
			throw new ApiException(ErrorCode.APPEAL_ALREADY_EXISTS);
		}
		AppealEntity appeal = AppealEntity.create(taskId, user.userId(), respondentId,
				command.reason(), command.description().trim());
		try {
			return AppealDto.from(repository.saveAndFlush(appeal));
		}
		catch (DataIntegrityViolationException exception) {
			// 并发提交穿透了存在性检查，由 (task_id, appellant_id) 唯一约束兜底。
			throw new ApiException(ErrorCode.APPEAL_ALREADY_EXISTS);
		}
	}

	/**
	 * 管理员裁决：驳回不划转；支持或部分支持时校验被申诉方可用余额后划转。
	 *
	 * <p>划转要求账务端口返回 1—2 条关联流水 ID。返回 {@code null} 或空列表时视为账务异常，
	 * 抛出 500 {@code INTERNAL_ERROR} 并使事务回滚，申诉保持 {@code PENDING}。</p>
	 */
	@Transactional
	public AppealDto decide(UUID appealId, AppealDecisionRequest command) {
		AuthenticatedUser admin = currentUserPort.requireAdmin();
		AppealEntity appeal = repository.findByIdForUpdate(appealId)
				.orElseThrow(() -> new ApiException(ErrorCode.APPEAL_NOT_FOUND));
		if (appeal.getStatus() != AppealStatus.PENDING) {
			throw new ApiException(ErrorCode.APPEAL_ALREADY_DECIDED);
		}
		TaskSnapshot task = taskPort.findTaskForUpdate(appeal.getTaskId())
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
		if (task.status() != TaskStatus.COMPLETED) {
			throw new ApiException(ErrorCode.TASK_STATE_CONFLICT, "关联任务已不是已完成状态，不能裁决申诉");
		}

		int amount = command.transferAmount();
		List<UUID> transactionIds = List.of();
		if (command.result() == AppealResult.REJECTED) {
			if (amount != 0) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "驳回申诉时 transferAmount 必须为 0");
			}
		}
		else {
			if (amount <= 0) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "支持或部分支持申诉时 transferAmount 必须大于 0");
			}
			int available = ledgerPort.availableBalance(appeal.getRespondentId());
			if (amount > available) {
				throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
			}
			transactionIds = ledgerPort.transferAvailable(appeal.getTaskId(), appeal.getAppealId(),
					appeal.getRespondentId(), appeal.getAppellantId(), amount);
			// 划转成功必须留下可核对的流水：null（实现漏返回）和空列表（未产生任何流水）
			// 都不是合法的成功结果。此时申诉不能标记为已处理，抛异常让整个事务回滚，
			// 包括账务端口在同一事务内写入的余额与流水。
			if (transactionIds == null || transactionIds.isEmpty()) {
				throw new ApiException(ErrorCode.INTERNAL_ERROR, "账务服务未返回有效流水，申诉裁决已回滚");
			}
			if (transactionIds.size() > 2) {
				throw new ApiException(ErrorCode.INTERNAL_ERROR, "申诉划转最多产生两条关联流水");
			}
		}

		appeal.decide(admin.userId(), Instant.now(), command.result(), amount,
				command.decisionNote().trim(), transactionIds);
		return AppealDto.from(appeal);
	}

	/**
	 * 管理员分页查询申诉，可按状态过滤。
	 */
	@Transactional(readOnly = true)
	public PageResult<AppealDto> listForAdmin(AppealStatus status, int page, int size) {
		currentUserPort.requireAdmin();
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "appealId")));
		Page<AppealEntity> result = status == null
				? repository.findAll(pageable)
				: repository.findByStatus(status, pageable);
		return PageResult.of(result, AppealDto::from);
	}
}
