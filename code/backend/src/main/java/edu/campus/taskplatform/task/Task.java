package edu.campus.taskplatform.task;

import edu.campus.taskplatform.auth.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 任务实体，对应 Flyway 迁移脚本中的 task 表。
 * publisherId / runnerId 以 Long 保存，JSON 序列化为字符串（见 API约定 §1）。
 */
@Entity
@Table(name = "task")
public class Task {

    @Id
    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "publisher_id", nullable = false)
    private Long publisherId;

    @Column(name = "runner_id")
    private Long runnerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private long reward;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "pickup_location", nullable = false, length = 200)
    private String pickupLocation;

    @Column(name = "delivery_location", nullable = false, length = 200)
    private String deliveryLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    /** 发布复核状态；与任务状态解耦，用于控制是否进入公开待接单列表。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "publication_review_status", nullable = false, length = 24)
    private PublicationReviewStatus publicationReviewStatus = PublicationReviewStatus.NOT_REQUIRED;

    /** 只读关联，用于任务列表按发布者信用档位排序；写入仍使用 publisherId。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AppUser publisher;

    /** 乐观锁版本号；接单并发另用悲观锁兜底。 */
    @Version
    @Column(nullable = false)
    private long version;

    protected Task() {
    }

    public Task(String taskId, Long publisherId, String title, String description,
                long reward, Instant deadline, String pickupLocation, String deliveryLocation,
                Instant publishedAt) {
        this.taskId = taskId;
        this.publisherId = publisherId;
        this.title = title;
        this.description = description;
        this.reward = reward;
        this.deadline = deadline;
        this.pickupLocation = pickupLocation;
        this.deliveryLocation = deliveryLocation;
        this.status = TaskStatus.WAITING_FOR_TAKER;
        this.publishedAt = publishedAt;
        this.publicationReviewStatus = PublicationReviewStatus.NOT_REQUIRED;
    }

    public String getTaskId() {
        return taskId;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public Long getRunnerId() {
        return runnerId;
    }

    public void setRunnerId(Long runnerId) {
        this.runnerId = runnerId;
    }

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

    public long getReward() {
        return reward;
    }

    public void setReward(long reward) {
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public PublicationReviewStatus getPublicationReviewStatus() {
        return publicationReviewStatus;
    }

    public void setPublicationReviewStatus(PublicationReviewStatus publicationReviewStatus) {
        this.publicationReviewStatus = publicationReviewStatus;
    }
}
