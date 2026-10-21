package edu.campus.taskplatform.task;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskOperationLogRepository extends JpaRepository<TaskOperationLog, String> {
}
