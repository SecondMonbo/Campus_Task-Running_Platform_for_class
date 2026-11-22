package edu.campus.taskplatform.credit;

import java.time.Instant;
import java.util.List;

import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.AppUserRole;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.credit.dto.CreditRecordDto;
import edu.campus.taskplatform.credit.dto.CreditSummaryDto;
import edu.campus.taskplatform.notification.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 信用分服务：初始化、业务事件记账、管理员复核调整与查询。
 *
 * <p>分值变化与变更记录在同一事务中提交；负向事件清零连续履约计数；
 * 连续完成 10 个任务且期间无负向事件时，当前分数低于 75 分则补至 75 分。
 * 低分限制使用持久化状态：低于 60 分开启，恢复至 75 分及以上解除。</p>
 */
@Service
public class CreditScoreService {

	private static final Logger log = LoggerFactory.getLogger(CreditScoreService.class);

	public static final int INITIAL_SCORE = 80;
	public static final int MIN_SCORE = 0;
	public static final int MAX_SCORE = 100;
	public static final int RESTRICTED_BELOW = 60;
	public static final int RESTORE_SCORE = 75;
	public static final int RECOVERY_STREAK = 10;

	private final AppUserRepository userRepository;
	private final CreditRecordRepository recordRepository;
	private final NotificationService notificationService;

	public CreditScoreService(AppUserRepository userRepository, CreditRecordRepository recordRepository,
			NotificationService notificationService) {
		this.userRepository = userRepository;
		this.recordRepository = recordRepository;
		this.notificationService = notificationService;
	}

	/** 查询用户当前分数；用户不存在时按初始值返回，便于测试夹具与历史数据兼容。 */
	public int scoreOf(Long userId) {
		return userRepository.findById(userId).map(AppUser::getCreditScore).orElse(INITIAL_SCORE);
	}

	/** 是否处于低分限制状态（不能接取普通任务、发布任务需复核）。 */
	public boolean isRestricted(Long userId) {
		return userRepository.findById(userId).map(AppUser::isCreditRestricted).orElse(false);
	}

	public CreditSummaryDto summary(long userId) {
		return CreditSummaryDto.from(requireUser(userId));
	}

	public PageResult<CreditRecordDto> records(long userId, int page, int size) {
		requireUser(userId);
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "recordId")));
		Page<CreditRecord> records = recordRepository.findByUserId(userId, pageable);
		List<CreditRecordDto> items = records.getContent().stream().map(CreditRecordDto::from).toList();
		return PageResult.of(items, page, size, records.getTotalElements());
	}

	/** 新用户初始化：分数 80，并写入一条 INITIALIZATION 基线记录。 */
	public void initialize(AppUser user) {
		user.updateCredit(INITIAL_SCORE, false, 0);
		if (recordRepository.existsByUserIdAndEventType(user.getUserId(), CreditEventType.INITIALIZATION)) {
			return;
		}
		recordRepository.save(new CreditRecord(user.getUserId(), CreditEventType.INITIALIZATION,
				"user:" + user.getUserId(), "信用分机制启用初始化", 0, 0, null, INITIAL_SCORE,
				null, Instant.now()));
	}

	/**
	 * 管理员复核调整：锁定用户行后按指定整数调整，结果截断在 0–100；历史记录只追加。
	 */
	@Transactional
	public CreditSummaryDto adjustByAdmin(long userId, int delta, String reason, long adminId) {
		if (delta == 0) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "delta 不能为 0");
		}
		String trimmedReason = reason == null ? "" : reason.trim();
		if (trimmedReason.isEmpty()) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "复核说明不能为空");
		}
		AppUser user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
		String storedReason = trimmedReason.length() > 500
				? trimmedReason.substring(0, 500)
				: trimmedReason;
		applyDelta(user, CreditEventType.ADMIN_REVIEW_ADJUSTMENT,
				"admin-adjustment:" + java.util.UUID.randomUUID(), storedReason, delta, adminId);
		return CreditSummaryDto.from(user);
	}

	/**
	 * 消费一条待处理事件。由 {@link CreditEventProcessor} 在独立事务中调用，
	 * 与事件状态更新同事务提交。
	 */
	@Transactional
	public void applyEvent(CreditEvent event) {
		AppUser user = userRepository.findByIdForUpdate(event.getUserId())
				.orElseThrow(() -> new IllegalStateException("信用分用户不存在：" + event.getUserId()));
		applyDelta(user, event.getEventType(), event.getSourceId(), event.getReason(),
				event.getRequestedDelta(), null);
	}

	private void applyDelta(AppUser user, CreditEventType eventType, String sourceId, String reason,
			int requestedDelta, Long adminId) {
		int before = user.getCreditScore();
		int after = clamp(before + requestedDelta);
		boolean beforeRestricted = user.isCreditRestricted();
		boolean afterRestricted = nextRestricted(beforeRestricted, after);
		int streak = user.getConsecutiveCompletedTasks();

		if (eventType.isCompletion()) {
			streak = requestedDelta < 0 ? 0 : streak + 1;
		}
		else if (requestedDelta < 0) {
			streak = 0;
		}

		user.updateCredit(after, afterRestricted, streak);
		int actualDelta = after - before;
		saveRecord(user.getUserId(), eventType, sourceId, reason, requestedDelta, actualDelta,
				before, after, adminId);

		if (actualDelta != 0 || CreditTier.fromScore(before) != CreditTier.fromScore(after)
				|| beforeRestricted != afterRestricted) {
			notifyScoreChange(user.getUserId(), before, after, beforeRestricted, afterRestricted, reason);
		}
		if (after == 0 && before > 0) {
			notifyAdminsOfZeroScore(user.getUserId());
		}

		if (eventType.isCompletion() && streak >= RECOVERY_STREAK && after < RESTORE_SCORE) {
			int recoveryDelta = RESTORE_SCORE - after;
			user.updateCredit(RESTORE_SCORE, false, 0);
			saveRecord(user.getUserId(), CreditEventType.CONSECUTIVE_COMPLETION_RECOVERY,
					"recovery:" + user.getUserId() + ":" + java.util.UUID.randomUUID(),
					"连续完成 " + RECOVERY_STREAK + " 个任务且期间无负向信用事件", recoveryDelta,
					recoveryDelta, after, RESTORE_SCORE, adminId);
			notifyAfterCommit(() -> safeNotifySystem(user.getUserId(), "低分限制已解除",
					"你已连续完成 " + RECOVERY_STREAK + " 个任务且期间没有负向信用事件，"
							+ "信用分恢复至 " + RESTORE_SCORE + " 分，低分限制已解除。"));
		}
	}

	private void saveRecord(Long userId, CreditEventType eventType, String sourceId, String reason,
			int requestedDelta, int actualDelta, Integer scoreBefore, int scoreAfter, Long adminId) {
		recordRepository.save(new CreditRecord(userId, eventType, sourceId, reason, requestedDelta,
				actualDelta, scoreBefore, scoreAfter, adminId, Instant.now()));
	}

	private void notifyScoreChange(Long userId, int before, int after, boolean beforeRestricted,
			boolean afterRestricted, String reason) {
		StringBuilder content = new StringBuilder();
		content.append("你的信用分由 ").append(before).append(" 分变为 ").append(after)
				.append(" 分（").append(tierLabel(CreditTier.fromScore(after))).append("）。");
		if (reason != null && !reason.isBlank()) {
			content.append("变动原因：").append(reason).append('。');
		}
		if (!beforeRestricted && afterRestricted) {
			content.append("已进入低分受限状态：不能接取普通任务，新发布任务需管理员复核；")
					.append("恢复至 ").append(RESTORE_SCORE).append(" 分解除限制。");
		}
		else if (beforeRestricted && !afterRestricted) {
			content.append("低分限制已解除，可正常接取和发布任务。");
		}
		String text = content.toString();
		notifyAfterCommit(() -> safeNotifySystem(userId, "信用分变更", text));
	}

	private void notifyAdminsOfZeroScore(Long userId) {
		List<AppUser> admins = userRepository.findByRole(AppUserRole.ADMIN);
		String content = "用户 " + userId + " 的信用分已降至 0 分，请管理员复核。";
		notifyAfterCommit(() -> {
			for (AppUser admin : admins) {
				safeNotifySystem(admin.getUserId(), "信用分降至 0 分", content);
			}
		});
	}

	private void notifyAfterCommit(Runnable action) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					action.run();
				}
			});
		}
		else {
			action.run();
		}
	}

	private void safeNotifySystem(Long userId, String title, String content) {
		try {
			String text = content.length() > 500 ? content.substring(0, 500) : content;
			notificationService.notifySystem(userId, title, text);
		}
		catch (RuntimeException failure) {
			log.warn("写入信用分通知失败：recipient={}, reason={}", userId, failure.getMessage());
		}
	}

	private AppUser requireUser(long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
	}

	static int clamp(int score) {
		return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
	}

	static boolean nextRestricted(boolean currentlyRestricted, int score) {
		if (score < RESTRICTED_BELOW) {
			return true;
		}
		if (score >= RESTORE_SCORE) {
			return false;
		}
		return currentlyRestricted;
	}

	private static String tierLabel(CreditTier tier) {
		return switch (tier) {
			case EXCELLENT -> "优秀";
			case GOOD -> "良好";
			case WARNING -> "预警";
			case RESTRICTED -> "受限";
		};
	}
}
