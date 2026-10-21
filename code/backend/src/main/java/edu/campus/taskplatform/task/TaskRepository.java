package edu.campus.taskplatform.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 任务仓储。findByIdForUpdate 用于接单的并发控制：
 * 在事务内加悲观写锁，保证同一任务最多只有一个接单请求成功。
 */
public interface TaskRepository extends JpaRepository<Task, String>, JpaSpecificationExecutor<Task> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Task t where t.taskId = :taskId")
    Optional<Task> findByIdForUpdate(@Param("taskId") String taskId);

    Page<Task> findByPublisherId(Long publisherId, Pageable pageable);

    Page<Task> findByPublisherIdAndStatus(Long publisherId, TaskStatus status, Pageable pageable);

    Page<Task> findByRunnerId(Long runnerId, Pageable pageable);

    Page<Task> findByRunnerIdAndStatus(Long runnerId, TaskStatus status, Pageable pageable);
}
