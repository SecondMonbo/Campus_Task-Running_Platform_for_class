package edu.campus.taskplatform.credit;

import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.credit.dto.CreditRecordDto;
import edu.campus.taskplatform.credit.dto.MyCreditDto;
import edu.campus.taskplatform.credit.dto.PublicUserProfileDto;
import edu.campus.taskplatform.notification.NotificationService;
import edu.campus.taskplatform.notification.NotificationType;
import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskRepository;
import edu.campus.taskplatform.task.TaskStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 信用分服务：初始化、变更记录、事件记分与查询。
 *
 * <p>硬性规则（API约定 §4.4）：</p>
 * <ul>
 *   <li>新用户与存量用户初始 80 分，分值限定 0–100，越界按边界截断；</li>
 *   <li>分值变化与变更记录在同一事务写入；</li>
 *   <li>以业务资源 ID（评价 ID / 任务 ID）幂等，同一事件不重复记分；</li>
 *   <li>非零变化通知用户，零分值只留痕不通知；</li>
 *   <li>他人主页只返回档位与累计完成单数，不暴露精确分值与明细。</li>
 * </ul>
 *
 * <p>本期接入评价、按时完成、超时送达三类任务事件；取消责任、投诉处罚、管理员调整、
 * 连续履约恢复与低分限制执行属实现边界，尚未接入。</p>
 */
@Service
public class CreditScoreService {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreService.class);

    /** 初始信用分。 */
    public static final int INITIAL_SCORE = 80;
    /** 超时送达判定：送达时间晚于截止时间 24 小时。 */
    public static final Duration OVERTIME_GRACE = Duration.ofHours(24);
    /** 低星扣分的滚动窗口。 */
    public static final Duration LOW_STAR_WINDOW = Duration.ofDays(30);

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
     */
    @Transactional
    public CreditScore ensureAccount(long userId) {
        return scoreRepository.findById(userId).orElseGet(() -> {
            Instant now = Instant.now();
            CreditScore account = scoreRepository.save(new CreditScore(userId, INITIAL_SCORE, now));
            recordRepository.save(new CreditScoreRecord(UUID.randomUUID().toString(), userId,
                    CreditEventType.INITIALIZATION, "user:" + userId, "信用分机制启用，初始化为 80 分",
                    0, 0, null, INITIAL_SCORE, null, now));
            return account;
        });
    }

    /** 读取当前分值；未初始化时先补 80 分与基线记录。 */
    @Transactional
    public int scoreOf(long userId) {
        return ensureAccount(userId).getScore();
    }

    /**
     * 记一次信用事件。同一 (eventType, sourceId) 只记一次，重复调用直接返回 null（幂等）。
     *
     * @param requestedDelta 请求变动值，实际变动按 0–100 截断后计算
     */
    @Transactional
    public CreditScoreRecord applyEvent(long userId, CreditEventType type, String sourceId,
                                       int requestedDelta, String reason) {
        if (sourceId != null && !sourceId.isBlank()
                && recordRepository.existsByEventTypeAndSourceId(type, sourceId)) {
            return null;
        }
        CreditScore account = ensureAccount(userId);
        int before = account.getScore();
        int after = Math.max(0, Math.min(100, before + requestedDelta));
        int actual = after - before;
        Instant now = Instant.now();
        account.update(after, now);
        scoreRepository.save(account);

        CreditScoreRecord record = recordRepository.save(new CreditScoreRecord(
                UUID.randomUUID().toString(), userId, type, sourceId, reason,
                requestedDelta, actual, before, after, null, now));
        notifyChange(userId, type, actual, before, after, reason);
        return record;
    }

    // ---------------------------------------------------------------
    // 任务事件接入
    // ---------------------------------------------------------------

    /**
     * 评价事件（评价保存后调用）：5 星 +2；3–4 星 0；1–2 星仅同一发布者对同一跑腿员
     * 30 天内第一次扣 -3，之后只记零分值记录。
     *
     * @param firstLowStar 本次评价是否为滚动 30 天内的首次低星评价（由调用方在保存前判定）
     */
    @Transactional
    public void applyReview(long reviewerId, long revieweeId, String reviewId, int score,
                            boolean firstLowStar, String taskTitle) {
        if (score == 5) {
            applyEvent(revieweeId, CreditEventType.REVIEW_5_STAR, reviewId, 2,
                    "发布者给出 5 星评价（任务「" + taskTitle + "」）");
            return;
        }
        if (score >= 3) {
            applyEvent(revieweeId, CreditEventType.REVIEW_NEUTRAL, reviewId, 0,
                    "发布者给出 " + score + " 星评价，不加减分（任务「" + taskTitle + "」）");
            return;
        }
        if (firstLowStar) {
            applyEvent(revieweeId, CreditEventType.REVIEW_LOW_STAR, reviewId, -3,
                    "发布者给出 " + score + " 星评价，30 天内首次低星扣分（任务「" + taskTitle + "」）");
        } else {
            applyEvent(revieweeId, CreditEventType.REVIEW_LOW_STAR, reviewId, 0,
                    "发布者给出 " + score + " 星评价，30 天内已扣分，本次仅留痕（任务「" + taskTitle + "」）");
        }
    }

    /**
     * 送达事件：送达时间晚于截止时间 24 小时记 -5；截止后 24 小时内不加不扣。
     * 每任务最多一次，以任务 ID 幂等。
     */
    @Transactional
    public void applyDeliveryOutcome(Task task) {
        if (task.getRunnerId() == null || task.getDeliveredAt() == null) {
            return;
        }
        Instant deliveredAt = task.getDeliveredAt();
        Instant overtimeThreshold = task.getDeadline().plus(OVERTIME_GRACE);
        if (deliveredAt.isAfter(overtimeThreshold)) {
            applyEvent(task.getRunnerId(), CreditEventType.OVERTIME_DELIVERY, task.getTaskId(), -5,
                    "任务「" + task.getTitle() + "」超时 24 小时以上送达");
        }
    }

    /**
     * 完成事件：截止时间前送达 +1（每任务一次，与评价不互抵）；超时 24 小时以上补记 -5。
     */
    @Transactional
    public void applyCompletionOutcome(Task task) {
        if (task.getRunnerId() == null || task.getDeliveredAt() == null) {
            return;
        }
        if (!task.getDeliveredAt().isAfter(task.getDeadline())) {
            applyEvent(task.getRunnerId(), CreditEventType.ON_TIME_COMPLETION, task.getTaskId(), 1,
                    "任务「" + task.getTitle() + "」按时完成");
        }
        applyDeliveryOutcome(task);
    }

    // ---------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------

    /** 本人信用分概览与变更记录（精确分值、档位、权限/恢复说明、分页记录）。 */
    @Transactional
    public MyCreditDto myCredit(long userId, int page, int size) {
        CreditScore account = ensureAccount(userId);
        CreditTier tier = CreditTier.fromScore(account.getScore());
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "recordId"));
        Page<CreditScoreRecord> result = recordRepository.findByUserId(userId, pageable);
        List<CreditRecordDto> items = new ArrayList<>(result.getContent().size());
        for (CreditScoreRecord record : result.getContent()) {
            items.add(CreditRecordDto.from(record));
        }
        return new MyCreditDto(account.getScore(), tier.name(), tier.label(),
                tier.permissionHint(), tier.recoveryHint(),
                PageResult.of(items, page, size, result.getTotalElements()));
    }

    /** 他人主页：仅基本信息 + 档位 + 累计完成单数，无精确分值与明细。 */
    @Transactional
    public PublicUserProfileDto publicProfile(long targetUserId) {
        AppUser user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new TaskException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
        CreditScore account = ensureAccount(targetUserId);
        CreditTier tier = CreditTier.fromScore(account.getScore());
        long completed = taskRepository.countByRunnerIdAndStatus(targetUserId, TaskStatus.COMPLETED);
        return new PublicUserProfileDto(user.getUserId().toString(), user.getDisplayName(),
                user.getRole().name(), tier.name(), tier.label(), completed, user.getCreatedAt());
    }

    // ---------------------------------------------------------------
    // 私有辅助
    // ---------------------------------------------------------------

    // 信用分通知失败不回滚记分：通知为独立事务，此处兜底并记录日志。
    private void notifyChange(long userId, CreditEventType type, int actualDelta,
                              int before, int after, String reason) {
        if (actualDelta == 0) {
            return;
        }
        String sign = actualDelta > 0 ? "+" : "";
        String title = actualDelta > 0 ? "信用分提升" : "信用分变动";
        String content = "你因「" + reason + "」" + sign + actualDelta + " 分，当前信用分 "
                + before + " → " + after + "（" + CreditTier.fromScore(after).label() + "）。";
        try {
            notificationService.notify(userId, NotificationType.CREDIT, title, content, null);
        } catch (Exception e) {
            log.warn("写入信用分通知失败：recipient={}, event={}, reason={}", userId, type, e.getMessage());
        }
    }
}
