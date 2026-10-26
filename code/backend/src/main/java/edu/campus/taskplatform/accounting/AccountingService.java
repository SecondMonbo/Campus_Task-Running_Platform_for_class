package edu.campus.taskplatform.accounting;

/**
 * 账务服务接入接口（组长提供账务公共能力，任务模块只通过本接口读写）。
 *
 * <p>余额变更与交易流水必须在任务业务所在的同一数据库事务中提交或回滚
 * （见需求 3.3.2/3.3.4 与 API约定 §6）。TaskService 的写操作一律 @Transactional，
 * 组长实现的 AccountingService 应加入同一事务边界，避免任务状态与账务不一致。</p>
 *
 * <p>当前仓库提供 {@link DemoAccountingService}（内存实现，供联调与测试）。
 * 组长账务服务就绪后，仅需实现本接口并用真实实现替换该 Bean，任务模块无需改动。</p>
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
}
