package edu.campus.taskplatform.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 任务操作日志，满足需求中对发布/修改/撤回/接单/送达/完成等操作的留痕要求。
 */
@Entity
@Table(name = "task_operation_log")
public class TaskOperationLog {

    @Id
    @Column(name = "log_id", length = 36)
    private String logId;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TaskOperationLog() {
    }

    public TaskOperationLog(String logId, String taskId, Long actorId, String action,
                            String detail, Instant createdAt) {
        this.logId = logId;
        this.taskId = taskId;
        this.actorId = actorId;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public String getLogId() {
        return logId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
