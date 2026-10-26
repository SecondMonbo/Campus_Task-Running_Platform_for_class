package edu.campus.taskplatform.task.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 发布任务请求，字段与 openapi.yaml 的 CreateTaskRequest 一致。
 */
public class TaskCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100")
    private String title;

    @NotBlank(message = "描述不能为空")
    @Size(max = 2000, message = "描述长度不能超过2000")
    private String description;

    @NotNull(message = "报酬不能为空")
    @Min(value = 1, message = "报酬至少为1")
    @Max(value = 1000, message = "报酬最多为1000")
    private Integer reward;

    @NotNull(message = "截止时间不能为空")
    @Future(message = "截止时间必须晚于当前时间")
    private Instant deadline;

    @NotBlank(message = "取件地点不能为空")
    @Size(max = 200, message = "取件地点长度不能超过200")
    private String pickupLocation;

    @NotBlank(message = "送件地点不能为空")
    @Size(max = 200, message = "送件地点长度不能超过200")
    private String deliveryLocation;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getReward() {
        return reward;
    }

    public void setReward(Integer reward) {
        this.reward = reward;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(String pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public String getDeliveryLocation() {
        return deliveryLocation;
    }

    public void setDeliveryLocation(String deliveryLocation) {
        this.deliveryLocation = deliveryLocation;
    }
}
