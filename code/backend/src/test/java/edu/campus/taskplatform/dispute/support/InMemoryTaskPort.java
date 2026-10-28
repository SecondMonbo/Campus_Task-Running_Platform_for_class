package edu.campus.taskplatform.dispute.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.port.TaskParticipationPort;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;
import edu.campus.taskplatform.dispute.port.TaskStatus;

/**
 * 测试用任务端口：内存任务表，cancelTask 复用真实迁移约束（只允许进行中/待验收 → 已取消）。
 */
public class InMemoryTaskPort implements TaskParticipationPort {

	private final Map<String, TaskSnapshot> tasks = new ConcurrentHashMap<>();

	public void put(TaskSnapshot snapshot) {
		tasks.put(snapshot.taskId(), snapshot);
	}

	public TaskSnapshot get(String taskId) {
		return tasks.get(taskId);
	}

	@Override
	public Optional<TaskSnapshot> findTaskForUpdate(String taskId) {
		return Optional.ofNullable(tasks.get(taskId));
	}

	@Override
	public void cancelTask(String taskId) {
		TaskSnapshot task = tasks.get(taskId);
		if (task == null) {
			throw new ApiException(ErrorCode.TASK_NOT_FOUND);
		}
		if (!task.isCancellable()) {
			throw new ApiException(ErrorCode.TASK_STATE_CONFLICT);
		}
		tasks.put(taskId, new TaskSnapshot(task.taskId(), task.publisherId(), task.runnerId(),
				TaskStatus.CANCELLED, task.reward()));
	}

	public void reset() {
		tasks.clear();
	}
}
