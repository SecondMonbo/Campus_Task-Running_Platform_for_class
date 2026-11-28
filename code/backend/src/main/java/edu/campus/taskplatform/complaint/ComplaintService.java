package edu.campus.taskplatform.complaint;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.complaint.dto.ComplaintActionRequest;
import edu.campus.taskplatform.complaint.dto.CreateComplaintRequest;
import edu.campus.taskplatform.credit.CreditEventPublisher;
import edu.campus.taskplatform.credit.CreditEventType;
import edu.campus.taskplatform.task.TaskRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintService {

	private final ComplaintRepository repository;
	private final CurrentUserService currentUserService;
	private final TaskRepository taskRepository;
	private final AppUserRepository userRepository;
	private final CreditEventPublisher creditEvents;

	public ComplaintService(ComplaintRepository repository, CurrentUserService currentUserService,
			TaskRepository taskRepository, AppUserRepository userRepository,
			CreditEventPublisher creditEvents) {
		this.repository = repository;
		this.currentUserService = currentUserService;
		this.taskRepository = taskRepository;
		this.userRepository = userRepository;
		this.creditEvents = creditEvents;
	}

	@Transactional
	public ComplaintDto create(CreateComplaintRequest request) {
		var reporter = currentUserService.requireCurrentUser();
		String taskId = request.taskId() == null || request.taskId().isBlank() ? null : request.taskId().trim();
		Long targetId = parseUserId(request.targetUserId());
		if (taskId != null) {
			var task = taskRepository.findById(taskId)
					.orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "关联任务不存在"));
			boolean participant = Objects.equals(task.getPublisherId(), reporter.userId())
					|| Objects.equals(task.getRunnerId(), reporter.userId());
			if (participant) {
				Long counterpart = Objects.equals(task.getPublisherId(), reporter.userId())
						? task.getRunnerId() : task.getPublisherId();
				if (targetId == null) targetId = counterpart;
				if (targetId != null && !targetId.equals(counterpart)) {
					throw new TaskException(ErrorCode.VALIDATION_ERROR, "被投诉人必须是任务另一位参与者");
				}
			}
			else if (targetId != null) {
				// 非参与者可以针对任务内容本身举报，但不能指定被投诉用户。
				throw new TaskException(ErrorCode.FORBIDDEN, "非任务参与者只能举报任务内容，不能指定被投诉用户");
			}
		}
		if (Objects.equals(targetId, reporter.userId())) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "不能投诉自己");
		}
		if (targetId != null && !userRepository.existsById(targetId)) {
			throw new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "被投诉用户不存在");
		}
		return ComplaintDto.from(repository.saveAndFlush(new Complaint(reporter.userId(), targetId, taskId,
				request.category().trim(), request.description().trim())));
	}

	@Transactional(readOnly = true)
	public PageResult<ComplaintDto> list(ComplaintStatus status, int page, int size) {
		currentUserService.requireAdmin();
		var pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "complaintId")));
		var result = status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
		return PageResult.of(result.getContent().stream().map(ComplaintDto::from).toList(),
				page, size, result.getTotalElements());
	}

	@Transactional
	public ComplaintDto process(UUID complaintId, ComplaintActionRequest request) {
		var admin = currentUserService.requireAdmin();
		var complaint = repository.findByIdForUpdate(complaintId)
				.orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "投诉不存在"));
		if (complaint.getStatus() != ComplaintStatus.PENDING) {
			throw new TaskException(ErrorCode.DUPLICATE_ACTION, "投诉已处理，请刷新列表");
		}
		if (request.actions().stream().anyMatch(action -> !action.isSupported())
				|| request.financialAdjustment() != null) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR,
					"当前仅支持处理留痕与信用扣分，账务调整、功能限制和冻结账号暂未开放");
		}
		if (new HashSet<>(request.actions()).size() != request.actions().size()) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "处理方式不能重复");
		}
		boolean deductCredit = request.actions().stream()
				.anyMatch(ComplaintAction::supportsCreditAdjustment);
		Integer creditAdjustment = request.creditAdjustment();
		if (deductCredit) {
			if (creditAdjustment == null || !Set.of(-2, -5, -10).contains(creditAdjustment)) {
				throw new TaskException(ErrorCode.VALIDATION_ERROR,
						"投诉扣分档位必须为 -2、-5 或 -10");
			}
			if (complaint.getTargetUserId() == null) {
				throw new TaskException(ErrorCode.VALIDATION_ERROR,
						"未指定被投诉用户的举报不能扣信用分");
			}
		}
		else if (creditAdjustment != null) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR,
					"未选择信用扣分处理方式时 creditAdjustment 必须为空");
		}
		String note = request.decisionNote().trim();
		complaint.process(admin.userId(), request.actions(),
				deductCredit ? creditAdjustment : null, note);
		if (deductCredit) {
			creditEvents.publish(complaint.getTargetUserId(), CreditEventType.COMPLAINT_UPHELD,
					complaint.getComplaintId().toString(), creditAdjustment,
					truncate("投诉成立（" + complaint.getCategory() + "）：" + note, 500));
		}
		return ComplaintDto.from(complaint);
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private Long parseUserId(String userId) {
		if (userId == null) return null;
		try {
			long value = Long.parseLong(userId);
			if (value > 0) return value;
		}
		catch (NumberFormatException ignored) {
			// 超出 BIGINT 范围的编号仍是参数错误。
		}
		throw new TaskException(ErrorCode.VALIDATION_ERROR, "targetUserId 必须是有效的正整数编号");
	}
}
