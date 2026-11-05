package edu.campus.taskplatform.dispute.port;

import java.util.List;
import java.util.UUID;

/**
 * 账务公共服务端口。业务模块不得绕过该端口直接修改余额或流水。
 *
 * <p>实现必须加入调用方已有事务：余额变更与流水写入要么一起提交，要么一起回滚。</p>
 */
public interface LedgerPort {

	/**
	 * 释放发布任务时冻结的报酬，用于取消生效或管理员裁决取消。
	 *
	 * @param taskId 任务 ID
	 * @param publisherId 发布者 ID
	 * @param runnerId 跑腿员 ID；任务尚无跑腿员时可为 {@code null}
	 * @param refundToPublisher 退回发布者可用余额的金额，不小于 0
	 * @param payToRunner 计入跑腿员可用余额的金额，不小于 0
	 */
	void settleFrozenReward(String taskId, Long publisherId, Long runnerId,
			long refundToPublisher, long payToRunner);

	/**
	 * 从被申诉方可用余额向申诉方划转裁决金额，并写入双方关联流水。
	 *
	 * <p>调用方会把返回值作为“划转已留痕”的依据写入申诉记录。实现必须返回 1—2 条真实流水 ID；
	 * <strong>不得返回 {@code null} 或空列表</strong>——那表示划转没有产生可核对的流水，调用方会按
	 * 账务异常处理并回滚整个事务，包括本方法在同一事务内已写入的余额与流水。</p>
	 *
	 * @return 非空的流水 ID 列表（出账、入账），顺序稳定，供申诉记录回填
	 * @throws edu.campus.taskplatform.common.error.ApiException 可用余额不足时抛出
	 *         {@code INSUFFICIENT_BALANCE}（409）
	 */
	List<UUID> transferAvailable(String taskId, UUID appealId,
			Long fromUserId, Long toUserId, long amount);

	/**
	 * 查询用户当前可用余额；账户不存在时视为 0。
	 */
	long availableBalance(Long userId);
}
