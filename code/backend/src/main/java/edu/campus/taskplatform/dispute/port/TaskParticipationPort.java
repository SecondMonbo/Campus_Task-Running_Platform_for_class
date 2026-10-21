package edu.campus.taskplatform.dispute.port;

import java.util.Optional;
import java.util.UUID;

/**
 * 任务模块对取消/申诉模块暴露的只读查询与状态迁移端口。
 *
 * <p>任务表由后端 A 维护，取消/申诉模块不得直接写任务表。实现必须做到：</p>
 * <ul>
 *   <li>{@link #findTaskForUpdate(UUID)} 在事务内对目标任务加行锁（等价于 {@code SELECT ... FOR UPDATE}），
 *       避免取消裁决与“确认完成”并发时出现状态错乱；</li>
 *   <li>{@link #cancelTask(UUID)} 只允许把 {@code IN_PROGRESS} 或 {@code WAITING_FOR_ACCEPTANCE}
 *       迁移为 {@code CANCELLED}，并在状态不符时抛出 {@code TASK_STATE_CONFLICT}；</li>
 *   <li>两个方法都加入调用方已有事务，不自行提交。</li>
 * </ul>
 */
public interface TaskParticipationPort {

	Optional<TaskSnapshot> findTaskForUpdate(UUID taskId);

	void cancelTask(UUID taskId);
}
