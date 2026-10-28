package edu.campus.taskplatform.dispute.config;

import java.util.Optional;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.port.TaskParticipationPort;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;
import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TaskParticipationAdapter implements TaskParticipationPort {

	private final TaskRepository taskRepository;

	public TaskParticipationAdapter(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	@Override
	@Transactional
	public Optional<TaskSnapshot> findTaskForUpdate(String taskId) {
		return taskRepository.findByIdForUpdate(taskId).map(TaskParticipationAdapter::snapshot);
	}

	@Override
	@Transactional
	public void cancelTask(String taskId) {
		Task task = taskRepository.findByIdForUpdate(taskId)
				.orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND));
		if (task.getStatus() != edu.campus.taskplatform.task.TaskStatus.IN_PROGRESS
				&& task.getStatus() != edu.campus.taskplatform.task.TaskStatus.WAITING_FOR_ACCEPTANCE) {
			throw new ApiException(ErrorCode.TASK_STATE_CONFLICT, "任务当前状态不允许取消");
		}
		task.setStatus(edu.campus.taskplatform.task.TaskStatus.CANCELLED);
		taskRepository.save(task);
	}

	private static TaskSnapshot snapshot(Task task) {
		return new TaskSnapshot(task.getTaskId(), task.getPublisherId(), task.getRunnerId(),
				edu.campus.taskplatform.dispute.port.TaskStatus.valueOf(task.getStatus().name()),
				Math.toIntExact(task.getReward()));
	}
}
