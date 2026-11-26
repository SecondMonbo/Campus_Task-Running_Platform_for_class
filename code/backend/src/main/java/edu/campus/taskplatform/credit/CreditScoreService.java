package edu.campus.taskplatform.credit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.AppUserRole;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.credit.dto.CreditRecordDto;
import edu.campus.taskplatform.credit.dto.CreditSummaryDto;
import edu.campus.taskplatform.credit.dto.MyCreditDto;
import edu.campus.taskplatform.credit.dto.PublicUserProfileDto;
import edu.campus.taskplatform.notification.NotificationService;
import edu.campus.taskplatform.notification.NotificationType;
import edu.campus.taskplatform.task.TaskRepository;
import edu.campus.taskplatform.task.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信用分服务：初始化、变更记录、事件记分与查询。
 *
 * <p>分值唯一来源是 {@code credit_score} 表；账面变化与 {@code credit_score_record}
 * 追加记录在同一事务提交。业务事件先写入 {@code credit_event} outbox，
 * 提交后由 {@link CreditEventProcessor} 在独立事务中消费，信用分失败不回滚业务操作。</p>
 *
 * <p>低分限制使用持久化状态：低于 60 分开启，恢复至 75 分及以上解除；
 * 连续完成 10 个任务且期间无负向事件时，当前分数低于 75 则补至 75。</p>
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

	/** 超时送达判定：送达时间晚于截止时间 24 小时。 */
	public static final Duration OVERTIME_GRACE = Duration.ofHours(24);
	/** 低星扣分的滚动窗口。 */
	public static final Duration LOW_STAR_WINDOW = Duration.ofDays(30);

	private static final String RESTRICTED_PERMISSION_HINT =
			"信用受限，暂不可接取普通任务；新发布任务需管理员复核后公开。";
	private static final String RESTRICTED_RECOVERY_HINT =
			"连续完成 10 个任务且期间无负向信用事件后，分值恢复至 75，并解除接单限制。";

	private final CreditScoreRepository scoreRepository;
	private final CreditScoreRecordRepository recordRepository;
	private final AppUserRepository userRepository;
	private final TaskRepository taskRepository;
	private final NotificationService notificationService;

	public CreditScoreService(CreditScoreRepository scoreRepository,
			CreditScoreRecordRepository recordRepository,
			AppUserRepository userRepository,
			TaskRepository taskRepository,
			NotificationService notificationService) {
		this.scoreRepository = scoreRepository;
		this.recordRepository = recordRepository;
		this.userRepository = userRepository;
		this.taskRepository = taskRepository;
		this.notificationService = notificationService;
	}

	// ---------------------------------------------------------------
	// 初始化与记分
	// ---------------------------------------------------------------

	/**
	 * 确保用户已有信用分账户：无则创建 80 分 + 初始化基线记录（变更前为空、变动 0）。
	 * 读取时对 {@code credit_score} 行加悲观写锁，保证后续分值变更在同一用户上串行化。
	 */
	@Transactional
	public CreditScore ensureAccount(long userId) {
		return scoreRepository.findByIdForUpdate(userId).orElseGet(() -> {
			Instant now = Instant.now();
			CreditScore account = scoreRepository.save(
					new CreditScore(userId, INITIAL_SCORE, false, 0, now));
			recordRepository.save(new CreditScoreRecord(UUID.randomUUID().toString(), userId,
					CreditEventType.INITIALIZATION, "user:" + userId, "信用分机制启用，初始化为 80 分",
					0, 0, null, INITIAL_SCORE, null, now));
			return account;
		});
	}

	/**
	 * 提前锁定某用户的信用分行，用于把“窗口判定 + 记分”整体串到同一把用户锁下：
	 * 并发评价先在该行锁上排队，后提交的事务能看到先提交的评价，不会重复判定首次低星。
	 */
	@Transactional
	public CreditScore acquireAccountLock(long userId) {
		return ensureAccount(userId);
	}

	/** 读取当前分值；未初始化时先补 80 分与基线记录。 */
	@Transactional
	public int scoreOf(long userId) {
		return ensureAccount(userId).getScore();
	}

	/** 是否处于低分限制状态；未初始化时视为未受限，避免读接口产生写入。 */
	public boolean isRestricted(long userId) {
		return scoreRepository.findById(userId).map(CreditScore::isRestricted).orElse(false);
	}

	/**
	 * 消费一条 outbox 事件。由 {@link CreditEventProcessor} 在独立事务中调用。
	 * 同一 (eventType, sourceId) 只记一次，重复调用直接返回 null。
	 */
	@Transactional
	public CreditScoreRecord applyEvent(long userId, CreditEventType type, String sourceId,
			int requestedDelta, String reason) {
		if (sourceId != null && !sourceId.isBlank()
				&& recordRepository.existsByEventTypeAndSourceId(type, sourceId)) {
			return null;
		}
		// 行锁：同一用户的分值变更在此串行化；拿到锁后二次查重，兜住并发重复事件。
		CreditScore account = ensureAccount(userId);
		if (sourceId != null && !sourceId.isBlank()
				&& recordRepository.findByEventTypeAndSourceId(type, sourceId).isPresent()) {
			return null;
		}
		return applyChange(account, type, sourceId, reason, requestedDelta, null);
	}

	/**
	 * 管理员复核调整：锁定用户行后按指定整数调整，结果截断在 0–100；历史记录只追加。
	 * 管理员调整不经过 outbox，在同一管理事务中直接更新分数并追加记录。
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
		requireUser(userId);
		CreditScore account = ensureAccount(userId);
		applyChange(account, CreditEventType.ADMIN_REVIEW_ADJUSTMENT,
				"admin:" + UUID.randomUUID(), truncate(trimmedReason, 500), delta, adminId);
		return CreditSummaryDto.from(account);
	}

	private CreditScoreRecord applyChange(CreditScore account, CreditEventType type, String sourceId,
			String reason, int requestedDelta, Long adminId) {
		Instant now = Instant.now();
		int before = account.getScore();
		int after = clamp(before + requestedDelta);
		boolean beforeRestricted = account.isRestricted();
		boolean afterRestricted = nextRestricted(beforeRestricted, after);
		int streak = nextStreak(account.getConsecutiveCompletedTasks(), type, requestedDelta);

		account.update(after, afterRestricted, streak, now);
		scoreRepository.save(account);
		int actualDelta = after - before;
		CreditScoreRecord record = recordRepository.save(new CreditScoreRecord(
				UUID.randomUUID().toString(), account.getUserId(), type, sourceId,
				truncate(reason, 200), requestedDelta, actualDelta, before, after, adminId, now));

		if (actualDelta != 0 || CreditTier.fromScore(before) != CreditTier.fromScore(after)
				|| beforeRestricted != afterRestricted) {
			notifyScoreChange(account.getUserId(), before, after, beforeRestricted,
					afterRestricted, reason, actualDelta);
		}
		if (after == 0 && before > 0) {
			notifyAdminsOfZeroScore(account.getUserId());
		}
		if (type.isCompletion() && streak >= RECOVERY_STREAK && after < RESTORE_SCORE) {
			int recoveryDelta = RESTORE_SCORE - after;
			account.update(RESTORE_SCORE, false, 0, now);
			scoreRepository.save(account);
			recordRepository.save(new CreditScoreRecord(UUID.randomUUID().toString(),
					account.getUserId(), CreditEventType.CONSECUTIVE_COMPLETION_RECOVERY,
					"recovery:" + UUID.randomUUID(),
					"连续完成 " + RECOVERY_STREAK + " 个任务且期间无负向信用事件",
					recoveryDelta, recoveryDelta, after, RESTORE_SCORE, adminId, now));
			safeNotifyCredit(account.getUserId(), "低分限制已解除",
					"你已连续完成 " + RECOVERY_STREAK + " 个任务且期间没有负向信用事件，"
							+ "信用分恢复至 " + RESTORE_SCORE + " 分，低分限制已解除。");
		}
		return record;
	}

	// ---------------------------------------------------------------
	// 查询
	// ---------------------------------------------------------------

	@Transactional
	public CreditSummaryDto summary(long userId) {
		requireUser(userId);
		return CreditSummaryDto.from(ensureAccount(userId));
	}

	/** 本人信用分概览与变更记录（精确分值、档位、权限/恢复说明、分页记录）。 */
	@Transactional
	public MyCreditDto myCredit(long userId, int page, int size) {
		requireUser(userId);
		CreditScore account = ensureAccount(userId);
		CreditTier tier = CreditTier.fromScore(account.getScore());
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "recordId")));
		Page<CreditScoreRecord> result = recordRepository.findByUserId(userId, pageable);
		List<CreditRecordDto> items = result.getContent().stream().map(CreditRecordDto::from).toList();
		// 限制状态是独立持久化的：60–74 分但曾低于 60 的用户仍然受限，不能只看档位文案。
		String permissionHint = account.isRestricted()
				? RESTRICTED_PERMISSION_HINT
				: tier.permissionHint();
		String recoveryHint = account.isRestricted()
				? RESTRICTED_RECOVERY_HINT
				: tier.recoveryHint();
		return new MyCreditDto(account.getScore(), tier.name(), tier.label(),
				permissionHint, recoveryHint,
				PageResult.of(items, page, size, result.getTotalElements()));
	}

	/** 管理员查询指定用户的变更记录。 */
	@Transactional
	public PageResult<CreditRecordDto> records(long userId, int page, int size) {
		requireUser(userId);
		ensureAccount(userId);
		Pageable pageable = Pagination.of(page, size,
				Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "recordId")));
		Page<CreditScoreRecord> records = recordRepository.findByUserId(userId, pageable);
		List<CreditRecordDto> items = records.getContent().stream().map(CreditRecordDto::from).toList();
		return PageResult.of(items, page, size, records.getTotalElements());
	}

	/** 他人主页：仅基本信息 + 档位 + 累计完成单数，无精确分值与明细。 */
	@Transactional
	public PublicUserProfileDto publicProfile(long targetUserId) {
		AppUser user = requireUser(targetUserId);
		CreditScore account = ensureAccount(targetUserId);
		CreditTier tier = CreditTier.fromScore(account.getScore());
		long completed = taskRepository.countByRunnerIdAndStatus(targetUserId, TaskStatus.COMPLETED);
		return new PublicUserProfileDto(user.getUserId().toString(), user.getDisplayName(),
				user.getRole().name(), tier.name(), tier.label(), completed, user.getCreatedAt());
	}

	// ---------------------------------------------------------------
	// 私有辅助
	// ---------------------------------------------------------------

	private AppUser requireUser(long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
	}

	private void notifyScoreChange(Long userId, int before, int after, boolean beforeRestricted,
			boolean afterRestricted, String reason, int actualDelta) {
		StringBuilder content = new StringBuilder();
		content.append("你的信用分由 ").append(before).append(" 分变为 ").append(after)
				.append(" 分（").append(CreditTier.fromScore(after).label()).append("）。");
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
		String title = actualDelta > 0 ? "信用分提升" : "信用分变动";
		safeNotifyCredit(userId, title, content.toString());
	}

	private void notifyAdminsOfZeroScore(Long userId) {
		List<AppUser> admins = userRepository.findByRole(AppUserRole.ADMIN);
		String content = "用户 " + userId + " 的信用分已降至 0 分，请管理员复核。";
		for (AppUser admin : admins) {
			try {
				notificationService.notifySystem(admin.getUserId(), "信用分降至 0 分", content);
			}
			catch (RuntimeException failure) {
				log.warn("写入管理员信用分通知失败：recipient={}, reason={}",
						admin.getUserId(), failure.getMessage());
			}
		}
	}

	private void safeNotifyCredit(Long userId, String title, String content) {
		try {
			notificationService.notify(userId, NotificationType.CREDIT, title,
					truncate(content, 500), null);
		}
		catch (RuntimeException failure) {
			// 通知为独立事务，失败不回滚记分。
			log.warn("写入信用分通知失败：recipient={}, reason={}", userId, failure.getMessage());
		}
	}

	private static int nextStreak(int current, CreditEventType type, int requestedDelta) {
		if (type.isCompletion()) {
			return requestedDelta < 0 ? 0 : current + 1;
		}
		if (requestedDelta < 0) {
			return 0;
		}
		return current;
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

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}
}
