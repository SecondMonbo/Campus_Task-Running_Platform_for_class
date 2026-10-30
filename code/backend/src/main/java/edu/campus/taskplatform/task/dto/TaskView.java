package edu.campus.taskplatform.task.dto;

import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskStatus;

import java.time.Instant;

/**
 * 任务响应视图，字段与 openapi.yaml 的 Task 完全一致。
 * ID 统一按字符串返回。
 */
public class TaskView {

    private String taskId;
    private String title;
    private String description;
    private long reward;
    private Instant deadline;
    private String pickupLocation;
    private String deliveryLocation;
    private TaskStatus status;
    private String publisherId;
    private String runnerId;
    private Instant createdAt;

    public static TaskView from(Task task) {
        TaskView v = new TaskView();
        v.taskId = task.getTaskId();
        v.title = task.getTitle();
        v.description = task.getDescription();
        v.reward = task.getReward();
        v.deadline = task.getDeadline();
        v.pickupLocation = task.getPickupLocation();
        v.deliveryLocation = task.getDeliveryLocation();
        v.status = task.getStatus();
        v.publisherId = String.valueOf(task.getPublisherId());
        v.runnerId = task.getRunnerId() == null ? null : String.valueOf(task.getRunnerId());
        v.createdAt = task.getPublishedAt();
        return v;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public long getReward() {
        return reward;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public String getRunnerId() {
        return runnerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
