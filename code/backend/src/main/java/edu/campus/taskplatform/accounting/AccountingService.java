package edu.campus.taskplatform.accounting;

import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL-backed ledger service. Task and dispute operations use this service
 * inside their existing database transaction.
 *
 * <p>余额变更与交易流水必须在任务业务所在的同一数据库事务中提交或回滚
 * （见需求 3.3.2/3.3.4 与 API约定 §6）。TaskService 的写操作一律 @Transactional，
 * 组长实现的 AccountingService 应加入同一事务边界，避免任务状态与账务不一致。</p>
 *
 * <p>taskId 为字符串（后端生成的 UUID），与 openapi 中 Transaction.taskId 的类型一致。</p>
 */
public interface AccountingService {

    /**
     * 发布任务时冻结报酬：可用余额 -= amount，冻结余额 += amount。
     *
     * @throws InsufficientBalanceException 可用余额不足
     */
    void freeze(long userId, String taskId, long amount);

    /**
     * 撤回任务时全额解冻：冻结余额 -= amount，可用余额 += amount。
     */
    void unfreeze(long userId, String taskId, long amount);

    /**
     * 修改报酬时按差额调整冻结：delta &gt; 0 追加冻结，delta &lt; 0 按差额解冻。
     *
     * @throws InsufficientBalanceException 追加冻结但可用余额不足
     */
    void adjustFrozen(long userId, String taskId, long delta);

    /**
     * 确认完成时结算：从发布者冻结余额转出 amount 计入跑腿员可用余额。
     *
     * @throws InsufficientBalanceException 发布者冻结余额不足（正常不应发生）
     */
    void settle(String taskId, long publisherId, long runnerId, long amount);

    /** 查询可用余额。 */
    long getAvailableBalance(long userId);

    /** 查询冻结余额。 */
    long getFrozenBalance(long userId);

    /** Creates a wallet for a newly registered user and records its initial credit. */
    void openAccount(long userId, long initialBalance);

    /**
     * 取消任务时从冻结余额释放资金，并按裁决分别记入发布者和跑腿员可用余额。
     */
    void settleFrozenReward(String taskId, Long publisherId, Long runnerId,
                            long refundToPublisher, long payToRunner);

    /**
     * 从一名用户的可用余额向另一名用户划转申诉裁决金额，并记录两边流水。
     */
    List<UUID> transferAvailable(String taskId, UUID appealId, long fromUserId, long toUserId, long amount);
}
