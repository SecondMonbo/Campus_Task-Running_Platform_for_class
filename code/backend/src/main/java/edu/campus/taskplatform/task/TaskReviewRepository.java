package edu.campus.taskplatform.task;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskReviewRepository extends JpaRepository<TaskReview, String> {

    boolean existsByTaskIdAndReviewerId(String taskId, Long reviewerId);
}
