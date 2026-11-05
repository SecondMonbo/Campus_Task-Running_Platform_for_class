package edu.campus.taskplatform.dispute.port;

import java.util.Optional;

/**
 * 取消/申诉模块需要读取的任务快照。该结构只读，任务状态的写入一律委托给任务模块。
 *
 * @param taskId 任务 ID
 * @param publisherId 发布者 ID
 * @param runnerId 跑腿员 ID；待接单任务为 {@code null}
 * @param status 当前任务状态
 * @param reward 发布时已冻结的报酬（虚拟币整数）
 */
public record TaskSnapshot(String taskId, Long publisherId, Long runnerId, TaskStatus status, int reward) {

	public boolean isParticipant(Long userId) {
		return userId != null && (userId.equals(publisherId) || userId.equals(runnerId));
	}

	/**
	 * 返回另一位任务参与者；发起人不是参与者或任务尚无跑腿员时返回空。
	 */
	public Optional<Long> counterpartOf(Long userId) {
		if (userId == null) {
			return Optional.empty();
		}
		if (userId.equals(publisherId)) {
			return Optional.ofNullable(runnerId);
		}
		if (userId.equals(runnerId)) {
			return Optional.ofNullable(publisherId);
		}
		return Optional.empty();
	}

	/**
	 * 允许发起取消申请、同意取消或裁决取消的任务状态。
	 */
	public boolean isCancellable() {
		return status == TaskStatus.IN_PROGRESS || status == TaskStatus.WAITING_FOR_ACCEPTANCE;
	}
}
